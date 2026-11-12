package com.closecode.tools

import java.io.File
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Item 3: a download that breaks continues where it stopped.
 *
 * The test serves a file over HTTP, cuts the connection in the middle, and then lets the
 * download run again. The result has to be byte-for-byte the file that was being served —
 * which can only happen if the second attempt asked for the rest and appended it.
 */
class ResumableDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A server that serves one file and can cut the connection in the middle. */
    private class Server(
        private val bytes: ByteArray,
        private val cutAfter: Int,
    ) {
        val requests = AtomicInteger(0)
        private val socket = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))

        val url: String get() = "http://127.0.0.1:${socket.localPort}/file"

        fun start() {
            val thread = Thread {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    Thread { serve(client) }.apply { isDaemon = true }.start()
                }
            }
            thread.isDaemon = true
            thread.start()
        }

        private fun serve(client: Socket) {
            runCatching {
                client.use { connection ->
                    val input = connection.getInputStream().bufferedReader()
                    val request = input.readLine() ?: return
                    var range: String? = null
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Range:", ignoreCase = true)) {
                            range = line.substringAfter(':').trim()
                        }
                    }
                    requests.incrementAndGet()
                    val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                    val body = bytes.copyOfRange(start, bytes.size)
                    val limit = if (start == 0) cutAfter else body.size
                    val sent = body.copyOf(minOf(limit, body.size))
                    val out: OutputStream = connection.getOutputStream()
                    // A real server answers a range request with 206 and the range it sent.
                    val status = if (range != null) "206 Partial Content" else "200 OK"
                    val rangeHeader = if (range != null) {
                        "Content-Range: bytes $start-${start + sent.size - 1}/${bytes.size}\r\n"
                    } else {
                        ""
                    }
                    out.write(
                        (
                            "HTTP/1.1 $status\r\n" +
                                "Content-Length: ${sent.size}\r\n" +
                                rangeHeader +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(),
                    )
                    out.write(sent)
                    out.flush()
                    if (start == 0 && cutAfter < bytes.size) {
                        // The connection dies in the middle, like a phone losing signal.
                        connection.close()
                    }
                }
            }
        }

        fun stop() {
            runCatching { socket.close() }
        }
    }

    @Test
    fun anInterruptedDownloadKeepsWhatItDownloadedAndResumes() {
        val whole = ByteArray(300_000) { (it % 251).toByte() }
        val target = File(tmp.root, "payload.bin")
        val server = Server(whole, cutAfter = 120_000)
        server.start()

        try {
            val first = runCatching {
                ResumableDownload.download(server.url, target, whole.size.toLong())
            }
            assertTrue("the first attempt should not have finished", first.isFailure)
            assertTrue("the partial download was thrown away", target.isFile)
            assertEquals(120_000L, target.length())

            // The second attempt continues from there.
            ResumableDownload.download(server.url, target, whole.size.toLong())

            assertEquals(whole.size.toLong(), target.length())
            assertArrayEquals(whole, target.readBytes())
            assertEquals("the second attempt did not ask for a range", 2, server.requests.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun aCompleteDownloadIsNotFetchedAgain() {
        val whole = ByteArray(1_000) { it.toByte() }
        val target = File(tmp.root, "payload.bin")
        val server = Server(whole, cutAfter = whole.size)
        server.start()

        try {
            ResumableDownload.download(server.url, target, whole.size.toLong())
            ResumableDownload.download(server.url, target, whole.size.toLong())

            assertArrayEquals(whole, target.readBytes())
            assertEquals("the finished file was downloaded twice", 1, server.requests.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun theProgressShowsThatTheDownloadContinued() {
        val whole = ByteArray(300_000) { (it % 97).toByte() }
        val target = File(tmp.root, "payload.bin")
        val server = Server(whole, cutAfter = 120_000)
        server.start()

        try {
            runCatching { ResumableDownload.download(server.url, target, whole.size.toLong()) }

            val seen = mutableListOf<Long>()
            ResumableDownload.download(server.url, target, whole.size.toLong()) { done, _ -> seen += done }

            assertTrue("the resume was not reported", seen.any { it >= 120_000L })
        } finally {
            server.stop()
        }
    }
}
