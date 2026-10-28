package com.closecode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class OpenCodeProxyTest {

    @Test
    fun theEnvironmentPointsEveryToolAtTheLoopbackProxy() {
        val environment = OpenCodeProxy.environment(4099)

        assertEquals("http://127.0.0.1:4099", environment["HTTP_PROXY"])
        assertEquals("http://127.0.0.1:4099", environment["HTTPS_PROXY"])
        assertEquals("http://127.0.0.1:4099", environment["ALL_PROXY"])
        assertEquals("http://127.0.0.1:4099", environment["http_proxy"])
        assertEquals("http://127.0.0.1:4099", environment["https_proxy"])
        assertEquals("http://127.0.0.1:4099", environment["all_proxy"])
        assertEquals("localhost,127.0.0.1,::1", environment["NO_PROXY"])
        assertEquals("localhost,127.0.0.1,::1", environment["no_proxy"])
    }

    @Test
    fun anAbsoluteHttpRequestIsForwarded() {
        val upstream = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val seen = AtomicReference<String>()
        val server = Thread {
            upstream.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                val lines = mutableListOf<String>()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    lines.add(line)
                }
                seen.set(lines.joinToString("\n"))
                val body = "ok"
                val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: ${body.length}\r\n" +
                    "Connection: close\r\n\r\n" +
                    body
                socket.getOutputStream().write(response.toByteArray(StandardCharsets.ISO_8859_1))
                socket.getOutputStream().flush()
            }
        }.apply {
            isDaemon = true
            start()
        }

        val proxy = OpenCodeProxy()
        val port = proxy.start()
        try {
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 5_000
                val request = "GET http://127.0.0.1:${upstream.localPort}/hello?x=1 HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:${upstream.localPort}\r\n" +
                    "Proxy-Connection: keep-alive\r\n\r\n"
                client.getOutputStream().write(request.toByteArray(StandardCharsets.ISO_8859_1))
                client.getOutputStream().flush()
                val response = client.getInputStream().readBytes()
                    .toString(StandardCharsets.ISO_8859_1)
                assertTrue(response.startsWith("HTTP/1.1 200 OK"))
                assertTrue(response.endsWith("ok"))
            }

            val request = seen.get()
            assertNotNull(request)
            assertTrue(request.startsWith("GET /hello?x=1 HTTP/1.1"))
            assertTrue(request.contains("Host: 127.0.0.1:${upstream.localPort}"))
            assertFalse(request.contains("Proxy-Connection", ignoreCase = true))
        } finally {
            proxy.stop()
            upstream.close()
            server.join(2_000)
        }
    }
}
