package com.closecode.build

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs a toolchain main class in-process through an isolated classloader — the same
 * call shape on ART (jars pre-dexed, loaded from the toolchain dir) and on JVM
 * (plain jars). kotlinc, ECJ and D8 all expose a `main(Array<String>)`, so one
 * runner drives all three with no per-tool code.
 *
 * Single-flight: stdout/stderr are swapped process-wide during the call, so only one
 * build runs at a time (the engine never starts a second).
 */
object ToolRunner {
    data class Result(val code: Int, val output: String)

    private val lock = Any()

    private class ExitCall(val code: Int) : SecurityException("exit:$code")

    /**
     * Compilers call exit() instead of returning — even on success — which would kill
     * the app (or the test VM) outright. The trap turns it into a catchable result.
     */
    private class ExitTrap : SecurityManager() {
        override fun checkExit(status: Int): Nothing = throw ExitCall(status)
        override fun checkPermission(perm: java.security.Permission) {}
    }

    /**
     * Loads [mainClass] from [jars] and calls its main with [args]. Returns the exit
     * code the tool reported (or 1 when it threw) plus everything it printed, capped.
     */
    fun runMain(
        jars: List<File>,
        mainClass: String,
        args: List<String>,
        timeoutMs: Long = 300_000,
    ): Result = synchronized(lock) {
        val missing = jars.filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            return Result(1, "Missing toolchain jars: ${missing.joinToString { it.name }}")
        }
        val loader = URLClassLoader(
            jars.map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getSystemClassLoader().parent,
        )
        val out = ByteArrayOutputStream()
        val previousOut = System.out
        val previousErr = System.err
        val previousManager = System.getSecurityManager()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "tool-runner").apply { isDaemon = true }
        }
        // Without the trap a successful compile would exit() the whole process.
        // If the platform refuses the trap, running is worse than refusing.
        try {
            System.setSecurityManager(ExitTrap())
        } catch (error: Exception) {
            runCatching { loader.close() }
            return Result(1, "Exit trap unavailable (${error.message}): not running ${mainClass.substringAfterLast('.')}.")
        }
        try {
            System.setOut(PrintStream(out, true))
            System.setErr(PrintStream(out, true))
            val future = executor.submit<Int> {
                val main = loader.loadClass(mainClass)
                    .getMethod("main", Array<String>::class.java)
                try {
                    main.invoke(null, args.toTypedArray() as Any)
                    0
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    (error.targetException as? ExitCall)?.code ?: run {
                        error.targetException.printStackTrace()
                        1
                    }
                }
            }
            return try {
                Result(future.get(timeoutMs, TimeUnit.MILLISECONDS), out.toString().takeLast(8000))
            } catch (error: TimeoutException) {
                future.cancel(true)
                Result(-1, (out.toString() + "\nTimed out.").takeLast(8000))
            }
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
            runCatching {
                if (previousManager == null) {
                    // Clearing is denied once a manager exists; restoring null is best-effort.
                    System.setSecurityManager(null)
                } else {
                    System.setSecurityManager(previousManager)
                }
            }
            executor.shutdownNow()
            runCatching { loader.close() }
        }
    }
}
