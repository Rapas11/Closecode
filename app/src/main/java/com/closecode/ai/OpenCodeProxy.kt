package com.closecode.ai

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import com.closecode.debug.DebugLog
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class OpenCodeProxy(
    private val onLog: (String) -> Unit = {},
) : Closeable {

    private val running = AtomicBoolean(false)

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "opencode-proxy").apply { isDaemon = true }
    }

    val port: Int get() = server?.localPort ?: 0

    fun start(): Int {
        check(running.compareAndSet(false, true)) { "The OpenCode proxy is already running." }
        return try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(HOST), 0))
            }
            server = socket
            acceptThread = Thread({ acceptLoop(socket) }, "opencode-proxy-accept").apply {
                isDaemon = true
                start()
            }
            onLog("Proxy: $HOST:${socket.localPort}")
            socket.localPort
        } catch (error: Throwable) {
            running.set(false)
            closeQuietly(server)
            server = null
            workers.shutdownNow()
            throw error
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        closeQuietly(server)
        server = null
        acceptThread?.interrupt()
        acceptThread = null
        workers.shutdownNow()
    }

    override fun close() = stop()

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: Throwable) {
                break
            }
            runCatching { workers.execute { handle(client) } }
                .onFailure { closeQuietly(client) }
        }
    }

    /**
     * A connection the proxy serves.
     *
     * Nothing may escape this method: the worker pool would turn an exception into a dead
     * thread, and the app's crash handler would report it as a crash of the whole app.
     */
    private fun handle(client: Socket) {
        try {
            serve(client)
        } catch (interrupted: InterruptedException) {
            // The proxy is stopping. That is a normal end, not a failure.
            Thread.currentThread().interrupt()
            closeQuietly(client)
        } catch (error: Throwable) {
            DebugLog.crumb(
                "PROXY",
                "connection failed: ${error.message ?: error.javaClass.simpleName}",
            )
            closeQuietly(client)
        }
    }

    private fun serve(client: Socket) {
        client.use {
            it.soTimeout = HEADER_TIMEOUT_MS
            val input = BufferedInputStream(it.getInputStream())
            val output = BufferedOutputStream(it.getOutputStream())
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val parts = lines.firstOrNull().orEmpty().trim().split(' ')
            if (parts.size < 3) {
                writeStatus(output, "400 Bad Request")
                return
            }
            val method = parts[0]
            val target = parts[1]
            if (method.equals("CONNECT", ignoreCase = true)) {
                val authority = parseAuthority(target)
                if (authority == null) {
                    writeStatus(output, "400 Bad Request")
                    return
                }
                val upstream = connect(authority) ?: run {
                    writeStatus(output, "502 Bad Gateway")
                    return
                }
                output.write(
                    "HTTP/1.1 200 Connection Established\r\n\r\n"
                        .toByteArray(StandardCharsets.ISO_8859_1),
                )
                output.flush()
                tunnel(it, input, output, upstream)
                return
            }

            val uri = runCatching { URI(target) }.getOrNull()
            if (uri == null || !uri.scheme.equals("http", ignoreCase = true) ||
                uri.host.isNullOrBlank()
            ) {
                writeStatus(output, "400 Bad Request")
                return
            }
            val upstream = connect(Authority(uri.host, if (uri.port > 0) uri.port else 80))
                ?: run {
                    writeStatus(output, "502 Bad Gateway")
                    return
                }
            val path = buildString {
                append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
                uri.rawQuery?.let { append('?').append(it) }
            }
            val headers = lines.drop(1).filter { it.isNotBlank() }
            val hasHost = headers.any { it.substringBefore(':').trim().equals("Host", true) }
            val request = buildString {
                append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                headers.forEach { line ->
                    val name = line.substringBefore(':').trim()
                    if (name.equals("Proxy-Connection", true) ||
                        name.equals("Proxy-Authorization", true) ||
                        name.equals("Connection", true) ||
                        name.equals("Keep-Alive", true)
                    ) {
                        return@forEach
                    }
                    append(line).append("\r\n")
                }
                if (!hasHost) {
                    append("Host: ").append(uri.host)
                    if (uri.port > 0 && uri.port != 80) append(':').append(uri.port)
                    append("\r\n")
                }
                append("Connection: close\r\n\r\n")
            }
            val upstreamOutput = upstream.getOutputStream()
            upstreamOutput.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            upstreamOutput.flush()
            tunnel(it, input, output, upstream)
        }
    }

    private fun tunnel(
        client: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream,
        upstream: Socket,
    ) {
        client.soTimeout = 0
        upstream.soTimeout = 0
        val upstreamInput = upstream.getInputStream()
        val upstreamOutput = upstream.getOutputStream()
        val toUpstream = Thread {
            runCatching { copy(clientInput, upstreamOutput) }
            closeQuietly(upstream)
        }.apply {
            isDaemon = true
            start()
        }
        val toClient = Thread {
            runCatching { copy(upstreamInput, clientOutput) }
            closeQuietly(client)
        }.apply {
            isDaemon = true
            start()
        }
        // Stopping the proxy interrupts this thread. `join` throws when that happens, and an
        // exception that escapes a worker would be reported as a crash of the whole app.
        try {
            toUpstream.join()
            toClient.join()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            closeQuietly(upstream)
            closeQuietly(client)
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            output.flush()
        }
    }

    private fun connect(authority: Authority): Socket? = runCatching {
        Socket().apply {
            connect(InetSocketAddress(authority.host, authority.port), CONNECT_TIMEOUT_MS)
        }
    }.getOrNull()

    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < MAX_HEAD_BYTES) {
            val byte = input.read()
            if (byte < 0) return null
            out.write(byte)
            matched = when {
                matched == 0 && byte == '\r'.code -> 1
                matched == 1 && byte == '\n'.code -> 2
                matched == 2 && byte == '\r'.code -> 3
                matched == 3 && byte == '\n'.code -> 4
                byte == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) {
                return String(out.toByteArray(), StandardCharsets.ISO_8859_1)
            }
        }
        return null
    }

    private fun writeStatus(output: OutputStream, status: String) {
        val text = "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        runCatching {
            output.write(text.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()
        }
    }

    private data class Authority(val host: String, val port: Int)

    private fun parseAuthority(value: String): Authority? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end < 0) return null
            val host = text.substring(1, end)
            val port = text.substring(end + 1).removePrefix(":").toIntOrNull() ?: 443
            return Authority(host, port)
        }
        val colon = text.lastIndexOf(':')
        if (colon > 0 && text.indexOf(':') == colon) {
            val host = text.substring(0, colon)
            val port = text.substring(colon + 1).toIntOrNull() ?: return null
            return Authority(host, port)
        }
        return Authority(text, 443)
    }

    private fun closeQuietly(closeable: Closeable?) {
        runCatching { closeable?.close() }
    }

    private fun closeQuietly(socket: Socket?) {
        runCatching { socket?.close() }
    }

    companion object {
        const val HOST = "127.0.0.1"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val HEADER_TIMEOUT_MS = 30_000
        private const val MAX_HEAD_BYTES = 64 * 1024

        fun environment(port: Int): Map<String, String> {
            val url = "http://$HOST:$port"
            val noProxy = "localhost,127.0.0.1,::1"
            return mapOf(
                "HTTP_PROXY" to url,
                "HTTPS_PROXY" to url,
                "ALL_PROXY" to url,
                "http_proxy" to url,
                "https_proxy" to url,
                "all_proxy" to url,
                "NO_PROXY" to noProxy,
                "no_proxy" to noProxy,
            )
        }
    }
}
