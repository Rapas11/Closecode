package com.closecode.tools

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * One download that resumes.
 *
 * Every download in the app — the Gradle distribution, the Android SDK packages, the Termux
 * packs, the pip wheel — goes through here, so they all behave the same way:
 *
 * 1. The bytes go to the target file itself, so an interrupted transfer leaves what it
 *    downloaded behind instead of throwing it away.
 * 2. The next attempt asks for the rest with a `Range` header and appends to the file.
 * 3. A server that ignores the range is handled: the download starts again rather than
 *    producing a file with two copies of the first bytes in it.
 * 4. The size is checked when the caller knows it, so a truncated file is never used.
 */
object ResumableDownload {

    /**
     * Downloads [url] into [target], continuing from what is already there.
     *
     * [onProgress] is called with the number of bytes on disk and the total, so the caller
     * can show that the download continued instead of starting over.
     */
    fun download(
        url: String,
        target: File,
        expectedSize: Long = 0L,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) {
        target.parentFile?.mkdirs()
        var done = if (target.isFile) target.length() else 0L
        if (expectedSize > 0 && done > expectedSize) {
            target.delete()
            done = 0L
        }
        if (expectedSize > 0 && done == expectedSize && done > 0L) return
        if (done > 0L) {
            // The user must see that the download continues instead of starting over.
            onProgress(done, expectedSize)
        }
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
                // The server did not continue where we asked. Starting again is the only way
                // to end up with a file that is not two downloads in one.
                target.delete()
                done = 0L
                if (code == 416) {
                    // The server thinks we already have the whole file; it does not.
                    connection.disconnect()
                    download(url, target, expectedSize, onProgress)
                    return
                }
                if (code !in 200..299) {
                    throw IOException("The server answered HTTP $code.")
                }
            } else if (code !in 200..299) {
                throw IOException("The server answered HTTP $code.")
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
                // The transfer stopped early. What is on disk stays there, and the next
                // attempt continues from it.
                throw IOException("Short download: $done of $expectedSize bytes.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
