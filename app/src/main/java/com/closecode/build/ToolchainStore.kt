package com.closecode.build

import com.closecode.ui.settings.KeyValueStore
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Downloads, verifies and keeps the on-device build toolchain. One component at a time,
 * resumable across launches (Range requests against a .part file), SHA-256 verified before
 * use. Everything here runs off the main thread; the UI observes [state].
 *
 * Storage: <filesDir>/toolchain/<fileName>. A component counts as present only when its
 * final file exists at exactly the manifest size — a hash check then promotes it ready.
 */
class ToolchainStore(
    private val dir: File,
    private val kv: KeyValueStore,
) {
    private val _state = MutableStateFlow<ToolchainState>(ToolchainState.NotStarted)
    val state: StateFlow<ToolchainState> = _state

    fun isReady(): Boolean =
        ToolchainManifest.components.all { componentFile(it).let { f -> f.isFile && f.length() == it.sizeBytes } } &&
            kv.getInt(KEY_READY, 0) == 1

    fun missing(): List<ToolComponent> =
        ToolchainManifest.components.filterNot { componentFile(it).let { f -> f.isFile && f.length() == it.sizeBytes } }

    /**
     * Downloads everything missing, then verifies. Returns false with [ToolchainState.Failed]
     * on the first problem; already-present files are re-hashed, not re-downloaded.
     */
    suspend fun ensureAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val pending = ToolchainManifest.components
            for (component in pending) {
                val file = componentFile(component)
                if (!(file.isFile && file.length() == component.sizeBytes)) {
                    download(component, file)
                }
                _state.value = ToolchainState.Verifying(component.fileName)
                if (!matchesSha256(file, component.sha256)) {
                    file.delete()
                    _state.value = ToolchainState.Failed(
                        "${component.fileName} did not match its hash and was deleted. " +
                            "Check the connection and try again.",
                    )
                    return@withContext false
                }
            }
            kv.putInt(KEY_READY, 1)
            _state.value = ToolchainState.Ready
            true
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            _state.value = ToolchainState.Failed("Download stopped.")
            false
        } catch (error: Exception) {
            _state.value = ToolchainState.Failed(
                "Setup failed: ${error.message ?: error.javaClass.simpleName}",
            )
            false
        }
    }

    fun jar(id: String): File? =
        ToolchainManifest.components.firstOrNull { it.id == id }?.let { componentFile(it) }
            ?.takeIf { it.isFile }

    private fun componentFile(component: ToolComponent): File = File(dir, component.fileName)

    private fun download(component: ToolComponent, target: File) {
        val part = File(dir, component.fileName + PART_SUFFIX)
        var done = if (part.isFile) part.length() else 0L
        // A part file larger than the manifest means a stale version: start over.
        if (done > component.sizeBytes) {
            part.delete()
            done = 0L
        }
        val connection = (URL(component.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            if (done > 0) setRequestProperty("Range", "bytes=$done-")
        }
        connection.connect()
        val code = connection.responseCode
        if (done > 0 && code != HttpURLConnection.HTTP_PARTIAL) {
            // The server ignored the resume: restart the file rather than appending garbage.
            part.delete()
            done = 0L
        } else if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            throw java.io.IOException("Server answered $code for ${component.fileName}.")
        }
        RandomAccessFile(part, "rw").use { out ->
            out.seek(done)
            val buffer = ByteArray(64 * 1024)
            val input = connection.inputStream
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                done += read
                _state.value = ToolchainState.Downloading(done, ToolchainManifest.totalBytes, component.fileName)
            }
        }
        connection.disconnect()
        if (!part.renameTo(target)) {
            target.delete()
            if (!part.renameTo(target)) throw java.io.IOException("Could not store ${component.fileName}.")
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        private const val KEY_READY = "toolchain_ready"
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
