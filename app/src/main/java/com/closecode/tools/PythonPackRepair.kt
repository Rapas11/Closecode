package com.closecode.tools

import android.content.Context
import com.closecode.build.matchesSha256
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * P1-1: Termux's python package ships an empty `ensurepip/_bundled`, so `python3 -m venv`
 * dies with "ensurepip is not available" and the new environment has no pip.
 *
 * The installer owns the fix — never the user, never the agent: after the pack is unpacked the
 * pinned pip wheel is downloaded (size and SHA-256 verified) into the directory ensurepip
 * actually reads. The download resumes like every other one, and a wheel that fails its
 * checksum is deleted instead of installed.
 */
object PythonPackRepair {

    const val PIP_VERSION = "25.2"
    const val PIP_FILE = "pip-$PIP_VERSION-py3-none-any.whl"
    const val PIP_URL = "https://files.pythonhosted.org/packages/b7/3f/" +
        "945ef7ab14dc4f9d7f40288d2df998d1837ee0888ec3659c813487572faa/$PIP_FILE"
    const val PIP_SIZE = 1_752_557L
    const val PIP_SHA256 = "6d67a2b4e7f14d8b31b8b52648866fa717f45a1eb70e83002f4331d07e953717"

    /** The ensurepip bundle directory inside an unpacked prefix, or null without python. */
    fun bundledDir(prefix: File): File? {
        val lib = File(prefix, "lib")
        val python = lib.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("python3.") }
            ?.maxByOrNull { it.name }
            ?: return null
        return File(python, "ensurepip/_bundled")
    }

    /** True when the pack has python but ensurepip has no wheel to install. */
    fun needsRepair(prefix: File): Boolean {
        val bundled = bundledDir(prefix) ?: return false
        val wheels = bundled.listFiles()?.filter { it.isFile && it.name.endsWith(".whl") }
        return wheels.isNullOrEmpty()
    }

    /** True when the prefix already has a usable pip wheel. */
    fun isRepaired(prefix: File): Boolean {
        val bundled = bundledDir(prefix) ?: return false
        val wheels = bundled.listFiles()?.filter { it.isFile && it.name.endsWith(".whl") }
        return !wheels.isNullOrEmpty()
    }

    /**
     * Puts the pinned wheel in place. Returns the wheel, or null when the prefix has no
     * python at all. Fails loudly rather than leaving a half-written wheel behind.
     */
    fun repair(
        context: Context,
        prefix: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File?> = runCatching {
        val bundled = bundledDir(prefix) ?: return@runCatching null
        val target = File(bundled, PIP_FILE)
        if (target.isFile && target.length() == PIP_SIZE) return@runCatching target
        bundled.mkdirs()
        val part = File(context.cacheDir, "$PIP_FILE.part")
        download(PIP_URL, part, PIP_SIZE, onProgress)
        if (!matchesSha256(part, PIP_SHA256)) {
            part.delete()
            throw IOException("The pip wheel failed its checksum.")
        }
        part.copyTo(target, overwrite = true)
        part.delete()
        target
    }

    /** Resumable download: a `.part` file, a Range request, and a size check before use. */
    private fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        var done = if (target.isFile) target.length() else 0L
        if (expectedSize > 0 && done > expectedSize) {
            target.delete()
            done = 0L
        }
        if (expectedSize > 0 && done == expectedSize && done > 0L) return
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            if (done > 0L) setRequestProperty("Range", "bytes=$done-")
            setRequestProperty("User-Agent", "CloseCode")
        }
        try {
            val code = connection.responseCode
            if (done > 0L && code != HttpURLConnection.HTTP_PARTIAL) {
                target.delete()
                done = 0L
                if (code !in 200..299) throw IOException("The pip server returned HTTP $code.")
            } else if (code !in 200..299) {
                throw IOException("The pip server returned HTTP $code.")
            }
            val remaining = connection.contentLengthLong.takeIf { it > 0 }
            val total = if (remaining != null) done + remaining else expectedSize
            RandomAccessFile(target, "rw").use { output ->
                output.seek(done)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        onProgress(done, total)
                    }
                }
            }
            if (expectedSize > 0 && done != expectedSize) {
                throw IOException("Short download: $done of $expectedSize bytes.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
