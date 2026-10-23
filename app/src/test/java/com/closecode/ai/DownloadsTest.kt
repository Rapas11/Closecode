package com.closecode.ai

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * What the download does with a connection that will not stay up.
 *
 * The server here is a plain socket: it promises a file, hangs up part way through it, and only
 * serves the rest when asked for the bytes it did not send — which is the whole reason the
 * download keeps what it already has. A file the server refuses to serve has to stop there and
 * say why, not be asked for five more times.
 */
class DownloadsTest {

    private val dir = File(
        System.getProperty("java.io.tmpdir"),
        "opencode-download-test-${System.nanoTime()}",
    ).apply { mkdirs() }

    private var server: HangingServer? = null

    @After
    fun stopTheServer() {
        server?.stop()
        server = null
        dir.deleteRecursively()
    }

    @Test
    fun aConnectionThatBreaksIsPickedUpWhereItStopped() {
        val data = ByteArray(300 * 1024) { (it % 251).toByte() }
        val hanging = HangingServer(data, hangAfter = 96 * 1024)
        server = hanging
        val into = File(dir, "pack.tar.gz")

        var last = -1
        Downloads.get(hanging.open(), into) { percent -> last = percent }

        // The whole file, byte for byte — nothing lost, nothing doubled.
        assertArrayEquals(data, into.readBytes())
        assertEquals(100, last)
        // The later request asked for the rest by name, rather than starting again.
        assertTrue("the second request asked for a range", hanging.rangedRequests > 0)
        assertTrue("it hung up once and then served the rest", hanging.requests >= 2)
        // A .part is what a download is made of, but there must not be one left over.
        assertFalse("a .part was left behind", File(dir, "pack.tar.gz.part").exists())
    }

    @Test
    fun aFileTheServerRefusesSaysWhyAndIsNotAskedAgain() {
        val hanging = HangingServer(ByteArray(16), hangAfter = 0, status = 404)
        server = hanging
        val into = File(dir, "missing.tar.gz")

        try {
            Downloads.get(hanging.open(), into) { }
            throw AssertionError("a refused download must fail")
        } catch (error: IllegalStateException) {
            assertTrue("the reason is said: ${error.message}", error.message!!.contains("404"))
        }
        assertEquals("a refused download is not retried", 1, hanging.requests)
        assertFalse(into.exists())
    }

    @Test
    fun theHashIsTakenOverEveryByteOfTheFile() {
        val file = File(dir, "pinned.txt").apply { writeText("abc") }

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Downloads.sha256(file),
        )
    }

    /**
     * Serves [data] — but the first time, it stops after [hangAfter] bytes and closes the
     * socket, so the client is left with fewer bytes than were promised.
     *
     * Later requests (they carry a Range) are answered with the part they asked for, the way a
     * real server answers a resume. A different [status] is answered as-is, with no body.
     */
    private class HangingServer(
        private val data: ByteArray,
        private val hangAfter: Int,
        private val status: Int = 200,
    ) {

        private val lock = Any()
        private var seen = 0
        private var seenRanges = 0
        private var listening: ServerSocket? = null

        val requests: Int get() = synchronized(lock) { seen }

        val rangedRequests: Int get() = synchronized(lock) { seenRanges }

        /** Starts serving on a free port and returns the URL of the file. */
        fun open(): String {
            val listener = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
            listening = listener
            Thread({
                while (true) {
                    val client = try {
                        listener.accept()
                    } catch (error: Throwable) {
                        // The socket was closed: serving is over.
                        return@Thread
                    }
                    try {
                        answer(client)
                    } catch (error: Throwable) {
                        // A truncated answer is not a mistake, it is the test.
                        runCatching { client.close() }
                    }
                }
            }, "downloads-test-server").apply {
                isDaemon = true
                start()
            }
            return "http://127.0.0.1:${listener.localPort}/file"
        }

        /** Closes the listener, so no test outlives its server. */
        fun stop() {
            runCatching { listening?.close() }
        }

        private fun answer(client: Socket) {
            client.use { it ->
                var range: String? = null
                // The reader is deliberately not closed here: on Windows closing the input
                // stream shuts the connection down and the answer is never read. Only the
                // socket itself is closed, at the end.
                val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        range = line.substringAfter(":").trim()
                    }
                }
                synchronized(lock) {
                    seen++
                    if (range != null) seenRanges++
                }

                val out = it.getOutputStream()
                if (status != 200) {
                    out.write(status.toByteArray())
                    out.flush()
                    return
                }

                val from = range
                    ?.takeIf { it.startsWith("bytes=") }
                    ?.removePrefix("bytes=")
                    ?.substringBefore("-")
                    ?.toIntOrNull()
                    ?: 0

                if (from in 1 until data.size) {
                    // The part that was asked for, exactly.
                    val rest = data.size - from
                    val head = "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Range: bytes $from-${data.size - 1}/${data.size}\r\n" +
                        "Content-Length: $rest\r\n" +
                        "Connection: close\r\n\r\n"
                    out.write(head.toByteArray(Charsets.ISO_8859_1))
                    out.write(data, from, rest)
                    out.flush()
                    return
                }

                // Everything promised, part of it sent, and then silence.
                val head = "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: ${data.size}\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                if (hangAfter > 0) out.write(data, 0, hangAfter)
                out.flush()
            }
        }

        private fun Int.toByteArray(): ByteArray =
            "HTTP/1.1 $this Refused\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.ISO_8859_1)
    }
}
