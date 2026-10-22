package com.closecode.ai

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Getting a file off the web, the way the app does it everywhere.
 *
 * Straight to disk through a small buffer, renamed into place only once every byte arrived, so
 * an interrupted download leaves a `.part` behind rather than a half file that would be taken
 * for the real thing. Everything the app fetches at runtime comes with a hash pinned in the
 * source, and [sha256] is what those pins are checked against.
 */
internal object Downloads {

    /** Reads [url] into [into], reporting the percent as it goes. [what] names it in errors. */
    fun get(url: String, into: File, what: String = "download", onProgress: (Int) -> Unit) {
        val partial = File(into.parentFile, into.name + ".part")
        into.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("The $what failed ($code).")
            }
            val total = connection.contentLength.toLong()
            var written = 0L
            FileOutputStream(partial).use { out ->
                connection.inputStream.use { input: InputStream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            // Only now is the file real.
            if (!partial.renameTo(into)) {
                partial.copyTo(into, overwrite = true)
                partial.delete()
            }
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    /** The hash of a file, in the lower-case hex its pin is written in. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
