package com.closecode.build

import com.closecode.debug.DebugLog
import com.closecode.ui.settings.KeyValueStore
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Downloads, verifies and keeps the on-device build toolchain. One component at a time,
 * resumable across launches (Range requests against a .part file), SHA-256 verified before
 * use. A download lands in a `.part` file, is hashed, and only then is moved into place —
 * a damaged byte never becomes a tool. Everything runs off the main thread; the UI observes
 * [state].
 *
 * Storage: <filesDir>/toolchain/<pack version>/<fileName>. A component counts as present
 * only when its final file exists at exactly the manifest size and its hash checked out.
 * The pack version that verified cleanly is remembered, which is what "Check for updates"
 * and the build panel's "Build tools need an update" compare against.
 */
class ToolchainStore(
    private val toolchain: Toolchain,
    private val dir: File,
    private val kv: KeyValueStore,
    private val network: NetworkGate? = null,
) {
    private val _state = MutableStateFlow<ToolchainState>(ToolchainState.NotStarted)
    val state: StateFlow<ToolchainState> = _state

    /** Where this pack's files live: `files/toolchain/<pack version>/`. */
    val directory: File get() = dir

    /** Pause keeps .part halves for a resume; cancel wipes them with the rest. */
    private enum class Stop { Pause, Cancel }

    @Volatile
    private var stop: Stop? = null

    fun pause() {
        stop = Stop.Pause
    }

    fun cancel() {
        stop = Stop.Cancel
    }

    /** The "Use mobile data" switch. The gate reads the same stored answer. */
    fun mobileAllowed(): Boolean = kv.getBoolean(KEY_MOBILE_ALLOWED, false)

    fun setMobileAllowed(allowed: Boolean) {
        kv.putBoolean(KEY_MOBILE_ALLOWED, allowed)
    }

    fun isReady(): Boolean =
        toolchain.files.isNotEmpty() &&
            toolchain.files.all { present(it) } &&
            kv.getString(KEY_PACK_VERSION, null).orEmpty() == toolchain.version

    /** All files are on disk but the pack version differs: the app wants a different pack. */
    fun needsUpdate(): Boolean =
        toolchain.files.isNotEmpty() &&
            toolchain.files.all { present(it) } &&
            kv.getString(KEY_PACK_VERSION, null).orEmpty() != toolchain.version

    fun missing(): List<ToolComponent> = toolchain.files.filterNot { present(it) }

    /** Every file that is on disk, in bytes — the "Size on phone" row. */
    fun sizeOnPhone(): Long =
        toolchain.files.sumOf { component -> if (present(component)) component.sizeBytes else 0L }

    /**
     * Downloads everything missing, then verifies. Returns false with [ToolchainState.Failed]
     * on the first problem. Problems that retrying can fix (a damaged file, a dropped
     * connection) are retried up to [MAX_TRIES] times per file; a file already on disk and
     * matching its size is verified, never downloaded again.
     */
    suspend fun ensureAll(): Boolean = withContext(Dispatchers.IO) {
        stop = null
        try {
            dir.mkdirs()
            if (dir.usableSpace < FREE_SPACE_NEEDED_BYTES) {
                _state.value = ToolchainState.Failed(
                    "Not enough storage. You need 1.5 GB free. Free up space and try again.",
                )
                return@withContext false
            }
            DebugLog.line("TOOLS") {
                "setup started · ${missing().size} of ${toolchain.files.size} files to download"
            }
            toolchain.files.forEachIndexed { position, component ->
                checkStop()
                val final = componentFile(component)
                val part = File(dir, component.fileName + PART_SUFFIX)
                var attempt = 0
                while (true) {
                    attempt++
                    checkStop()
                    if (final.isFile && final.length() == component.sizeBytes) {
                        // Already in place: verify, do not fetch again.
                        _state.value = ToolchainState.Verifying(component.fileName)
                        if (matchesSha256(final, component.sha256)) break
                        final.delete()
                        DebugLog.crumb("TOOLS", "hash mismatch, file deleted: ${component.fileName}")
                    }
                    if (part.isFile && part.length() == component.sizeBytes &&
                        matchesSha256(part, component.sha256)
                    ) {
                        promote(part, final)
                        break
                    }
                    _state.value = ToolchainState.Downloading(
                        index = position + 1,
                        count = toolchain.files.size,
                        doneBytes = downloadedBytes(),
                        totalBytes = toolchain.totalBytes,
                        current = component.fileName,
                    )
                    try {
                        waitForNetwork()
                        download(component, part)
                    } catch (error: InterruptedException) {
                        throw error
                    } catch (error: NetworkLost) {
                        // Not a failure: wait for permission, then resume from the .part offset.
                        waitForNetwork()
                        attempt--
                        continue
                    } catch (error: IOException) {
                        if (noInternet(error)) {
                            _state.value = ToolchainState.Failed("No internet connection.")
                            return@withContext false
                        }
                        if (noSpace(error)) {
                            _state.value = ToolchainState.Failed(
                                "Not enough storage. You need 1.5 GB free. Free up space and try again.",
                            )
                            return@withContext false
                        }
                        DebugLog.crumb("TOOLS", "download failed: ${error.message ?: error.javaClass.simpleName}")
                        if (attempt >= MAX_TRIES) {
                            _state.value = ToolchainState.Failed(
                                "Could not get ${component.fileName}. Try again later.",
                            )
                            return@withContext false
                        }
                        continue
                    }
                    _state.value = ToolchainState.Verifying(component.fileName)
                    checkStop()
                    if (matchesSha256(part, component.sha256)) {
                        promote(part, final)
                        break
                    }
                    part.delete()
                    DebugLog.crumb("TOOLS", "hash mismatch, file deleted: ${component.fileName}")
                    if (attempt >= MAX_TRIES) {
                        _state.value = ToolchainState.Failed(
                            "Could not get ${component.fileName}. Try again later.",
                        )
                        return@withContext false
                    }
                    _state.value = ToolchainState.Downloading(
                        index = position + 1,
                        count = toolchain.files.size,
                        doneBytes = downloadedBytes(),
                        totalBytes = toolchain.totalBytes,
                        current = component.fileName,
                        note = "One file was damaged. Downloading it again…",
                    )
                }
                DebugLog.line("TOOLS") { "verified ${component.fileName}" }
            }
            kv.putString(KEY_PACK_VERSION, toolchain.version)
            _state.value = ToolchainState.Ready
            DebugLog.crumb("TOOLS", "setup finished · ${toolchain.files.size} files verified")
            true
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            if (stop == Stop.Cancel) {
                dir.listFiles()?.filter { it.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
                DebugLog.crumb("TOOLS", "setup cancelled")
            } else {
                DebugLog.crumb("TOOLS", "setup paused")
            }
            _state.value = ToolchainState.NotStarted
            false
        } catch (error: Exception) {
            DebugLog.crumb("TOOLS", "setup failed: ${error.message ?: error.javaClass.simpleName}")
            _state.value = ToolchainState.Failed(
                "Setup failed: ${error.message ?: error.javaClass.simpleName}",
            )
            false
        } finally {
            stop = null
        }
    }

    /**
     * Re-computes every checksum (Check files). A file that does not match is deleted; the
     * caller then runs [ensureAll] to fetch it again. Returns the bad ones — empty means
     * everything on disk is good.
     */
    suspend fun verifyAll(): List<ToolComponent> = withContext(Dispatchers.IO) {
        _state.value = ToolchainState.Checking
        val bad = toolchain.files.filter { component ->
            val file = componentFile(component)
            if (!present(component)) {
                true
            } else {
                matchesSha256(file, component.sha256).also { ok ->
                    if (!ok) file.delete()
                }
            }
        }
        _state.value = if (bad.isEmpty()) ToolchainState.Ready else ToolchainState.NotStarted
        if (bad.isEmpty()) kv.putString(KEY_PACK_VERSION, toolchain.version)
        bad
    }

    fun jar(id: String): File? =
        toolchain.byId(id)?.let { componentFile(it) }?.takeIf { it.isFile }

    /**
     * Deletes every downloaded file (including `.part` halves) and forgets that the pack was
     * ready, so the next setup starts from zero. Returns false only when nothing could be
     * removed at all — a missing folder is already the wanted state.
     */
    fun deleteAll(): Boolean = runCatching {
        stop = Stop.Cancel
        dir.listFiles()?.forEach { it.delete() }
        kv.putString(KEY_PACK_VERSION, "")
        _state.value = ToolchainState.NotStarted
        DebugLog.crumb("TOOLS", "build tools deleted")
        true
    }.getOrDefault(false)

    private fun present(component: ToolComponent): Boolean =
        componentFile(component).let { f -> f.isFile && f.length() == component.sizeBytes }

    private fun downloadedBytes(): Long =
        toolchain.files.sumOf { component ->
            val final = componentFile(component).takeIf { it.isFile }?.length() ?: 0L
            val part = File(dir, component.fileName + PART_SUFFIX)
                .takeIf { it.isFile }?.length() ?: 0L
            final + part
        }

    private fun checkStop() {
        if (stop != null) throw InterruptedException("stopped")
    }

    /**
     * Wi-Fi only, unless mobile data was allowed. Called before a file and again whenever a
     * connection check inside a download says the allowed network is gone. Pause/cancel are
     * honored while waiting.
     */
    private suspend fun waitForNetwork() {
        val gate = network ?: return
        if (gate.isWifi() || gate.mobileAllowed()) return
        DebugLog.crumb("TOOLS", "waiting for Wi-Fi")
        while (!gate.isWifi() && !gate.mobileAllowed()) {
            checkStop()
            _state.value = ToolchainState.WaitingForWifi
            delay(1_500)
        }
    }

    private class NetworkLost : IOException("Wi-Fi is gone and mobile data is not allowed.")

    private fun componentFile(component: ToolComponent): File = File(dir, component.fileName)

    private fun promote(part: File, target: File) {
        if (part.renameTo(target)) return
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not store ${target.name}.")
    }

    private fun download(component: ToolComponent, part: File) {
        var done = if (part.isFile) part.length() else 0L
        // A part file larger than the manifest means a stale version: start over.
        if (done > component.sizeBytes) {
            part.delete()
            done = 0L
        }
        if (done == component.sizeBytes && done > 0) return
        val connection = (URL(component.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            if (done > 0) setRequestProperty("Range", "bytes=$done-")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (done > 0 && code != HttpURLConnection.HTTP_PARTIAL) {
                // The server ignored the resume: restart the file rather than appending garbage.
                part.delete()
                done = 0L
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server answered $code for ${component.fileName}.")
                }
            } else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("Server answered $code for ${component.fileName}.")
            }
            DebugLog.line("TOOLS") {
                "downloading ${component.fileName} · $code · from $done of ${component.sizeBytes} bytes"
            }
            RandomAccessFile(part, "rw").use { out ->
                out.seek(done)
                val buffer = ByteArray(64 * 1024)
                val input = connection.inputStream
                var lastGateCheck = 0L
                while (true) {
                    checkStop()
                    val now = System.currentTimeMillis()
                    if (network != null && now - lastGateCheck > 2_000) {
                        lastGateCheck = now
                        if (!network.isWifi() && !network.mobileAllowed()) throw NetworkLost()
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    done += read
                    _state.value = ToolchainState.Downloading(
                        index = toolchain.files.indexOf(component) + 1,
                        count = toolchain.files.size,
                        doneBytes = downloadedBytes(),
                        totalBytes = toolchain.totalBytes,
                        current = component.fileName,
                    )
                }
            }
            if (done != component.sizeBytes) {
                throw IOException(
                    "Short download for ${component.fileName}: $done of ${component.sizeBytes} bytes.",
                )
            }
            DebugLog.line("TOOLS") { "stored ${component.fileName} · $done bytes" }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        const val MAX_TRIES = 3

        /** Tools plus their caches: the screen states this number and refuses below it. */
        const val FREE_SPACE_NEEDED_BYTES = 1_500_000_000L

        /** "Use mobile data", off by default. The screen writes it; the gate reads it. */
        const val KEY_MOBILE_ALLOWED = "toolchain_mobile_allowed"

        private const val KEY_PACK_VERSION = "toolchain_pack_version"

        private fun noInternet(error: IOException): Boolean =
            error is UnknownHostException ||
                error is ConnectException ||
                error is SocketTimeoutException ||
                error.message.orEmpty().let {
                    it.contains("Unable to resolve host") ||
                        it.contains("Network is unreachable") ||
                        it.contains("Failed to connect")
                }

        private fun noSpace(error: IOException): Boolean =
            error.message.orEmpty().let {
                it.contains("ENOSPC") || it.contains("No space left")
            }
    }
}

/** SHA-256 of a file, lowercase hex. Streams so 60 MB jars never sit fully in memory. */
internal fun matchesSha256(file: File, expected: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
}
