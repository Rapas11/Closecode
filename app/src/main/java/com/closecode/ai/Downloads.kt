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
 * Everything fetched at runtime is checked against a hash that is pinned in the source, and
 * everything is straight to disk through a small buffer: an interrupted fetch leaves a `.part`
 * behind rather than a half file that would be taken for the real thing.
 *
 * The files are sixty megabytes and up, and a connection does not always survive that. So the
 * bytes that arrived are kept, and the next attempt asks the server for *the rest of them* — a
 * dropped connection costs a wait and a request, not the whole download again. The file becomes
 * real only when every byte is there.
 */
internal object Downloads {

    /** How often a broken connection is picked up again before the download is given up on. */
    private const val ATTEMPTS = 6

    /** The server will not serve this file at all, so asking again would buy nothing. */
    private class Refused(message: String) : IllegalStateException(message)

    /** Reads [url] into [into], reporting the percent as it goes. [what] names it in errors. */
    fun get(url: String, into: File, what: String = "download", onProgress: (Int) -> Unit) {
        into.parentFile?.mkdirs()
        val partial = File(into.parentFile, into.name + ".part")
        var attempt = 1
        while (true) {
            val problem = try {
                transfer(url, partial, what, onProgress)
            } catch (error: Refused) {
                throw error
            } catch (error: Throwable) {
                // A connection that died mid-transfer: the bytes are kept, ask for the rest.
                error.message ?: "the connection broke"
            }

            if (problem == null) {
                // Only now is the file real.
                if (!partial.renameTo(into)) {
                    partial.copyTo(into, overwrite = true)
                    partial.delete()
                }
                onProgress(100)
                return
            }
            if (attempt >= ATTEMPTS) {
                throw IllegalStateException("The $what failed: $problem.")
            }
            // A pause that grows with each try, so a server that is struggling is not hammered.
            Thread.sleep(1_000L * attempt)
            attempt++
        }
    }

    /**
     * One try at the server: however much of [partial] is already there, ask for the rest of it.
     *
     * Null when every byte arrived. Otherwise the reason, with what arrived kept for the next
     * attempt — which is the whole point: a sixty megabyte download that broke at ninety percent
     * picks up at ninety percent.
     */
    private fun transfer(
        url: String,
        partial: File,
        what: String,
        onProgress: (Int) -> Unit,
    ): String? {
        val already = if (partial.isFile) partial.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            when {
                // The server had no byte at that offset: the half file is not worth keeping.
                code == 416 -> {
                    partial.delete()
                    return "the server had nothing at that offset"
                }

                code in 400..499 -> throw Refused("The $what failed ($code).")

                code !in 200..299 -> return "the server answered $code"
            }

            // 206 is "here is the rest of it"; 200 means it ignored the ask and is sending all
            // of it, so the half file goes and the count starts over.
            val resumed = code == 206
            val rest = connection.contentLength.toLong()
            val total = if (resumed) {
                connection.getHeaderField("Content-Range")
                    ?.substringAfter("/", "")
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?: if (rest > 0) already + rest else -1L
            } else {
                rest
            }

            var written = if (resumed) already else 0L
            FileOutputStream(partial, resumed).use { out ->
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

            // Ended short of what the server promised, or promised nothing at all: keep what
            // arrived so the next attempt picks up where this one stopped.
            if (total > 0 && written < total) {
                return "the connection broke with ${total - written} of $total bytes to go"
            }
            if (total <= 0 && partial.length() == 0L) return "the server sent nothing"
            return null
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
