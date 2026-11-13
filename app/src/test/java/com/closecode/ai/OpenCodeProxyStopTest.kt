package com.closecode.ai

import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stopping OpenCode must not crash the app.
 *
 * The proxy tunnels a connection in two threads and waits for both. Stopping the proxy
 * interrupts the worker, `Thread.join` throws, and the exception used to escape into the
 * worker pool — where the app's crash handler reported it as a crash of the whole app.
 */
class OpenCodeProxyStopTest {

    @Test
    fun stoppingTheProxyWhileATunnelIsOpenDoesNotCrash() {
        val uncaught = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> synchronized(uncaught) { uncaught += error } }
        val upstream = ServerSocket(0)
        val proxy = OpenCodeProxy()
        var client: Socket? = null
        try {
            val port = proxy.start()
            client = Socket("127.0.0.1", port)
            // A CONNECT so the proxy opens a tunnel and waits for it to finish.
            client!!.getOutputStream().write(
                "CONNECT 127.0.0.1:${upstream.localPort} HTTP/1.1\r\n\r\n".toByteArray(),
            )
            client!!.getOutputStream().flush()
            // Let the tunnel settle.
            Thread.sleep(700)

            proxy.stop()
            Thread.sleep(700)

            val failures = synchronized(uncaught) { uncaught.toList() }
            assertTrue(
                "stopping the proxy crashed the app: " + failures.joinToString { it.toString() },
                failures.isEmpty(),
            )
        } finally {
            runCatching { client?.close() }
            runCatching { upstream.close() }
            runCatching { proxy.stop() }
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun stoppingTheProxyTwiceIsHarmless() {
        val proxy = OpenCodeProxy()
        proxy.start()

        proxy.stop()
        proxy.stop()
    }
}
