package com.closecode.build

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs a toolchain main class in-process through an isolated classloader — the same call
 * shape on ART (jars dexed by the pack script, loaded with DexClassLoader) and on the JVM
 * (plain jars, URLClassLoader). kotlinc, ECJ and D8 all expose a callable entry point, so
 * one runner drives all three with no per-tool plumbing.
 *
 * Single-flight: stdout/stderr are swapped process-wide during the call, so only one build
 * runs at a time (the engine never starts a second).
 */
object ToolRunner {
    data class Result(val code: Int, val output: String)

    private val lock = Any()

    private class ExitCall(val code: Int) : SecurityException("exit:$code")

    /**
     * Compilers call exit() instead of returning — even on success — which would kill
     * the app (or the test VM) outright. The trap turns it into a catchable result. ART may
     * refuse a SecurityManager; callers that use returning entry points (see [ToolExec]) pass
     * allowWithoutTrap so a missing trap is not fatal for them.
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
        dexOptDir: File? = null,
    ): Result = runCall(jars, timeoutMs, dexOptDir, allowWithoutTrap = false) { loader ->
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

    /**
     * Runs [call] with [jars] loaded and stdout/stderr captured. [call] returns the exit
     * code. This is the shared body under both [runMain] and [ToolExec]'s returning entry
     * points.
     */
    fun runCall(
        jars: List<File>,
        timeoutMs: Long = 300_000,
        dexOptDir: File? = null,
        allowWithoutTrap: Boolean,
        call: (ClassLoader) -> Int,
    ): Result = synchronized(lock) {
        val missing = jars.filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            return Result(1, "Missing toolchain jars: ${missing.joinToString { it.name }}")
        }
        val loader = try {
            openLoader(jars, dexOptDir)
        } catch (error: Exception) {
            return Result(1, "Could not open the toolchain (${error.message ?: error.javaClass.simpleName}).")
        }
        val out = ByteArrayOutputStream()
        val previousOut = System.out
        val previousErr = System.err
        val previousManager = System.getSecurityManager()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "tool-runner").apply { isDaemon = true }
        }
        // Without the trap a main() that exits would kill the whole process. If the platform
        // refuses the trap, only calls that never exit may proceed.
        var trapInstalled = false
        try {
            System.setSecurityManager(ExitTrap())
            trapInstalled = true
        } catch (error: Exception) {
            if (!allowWithoutTrap) {
                closeQuietly(loader)
                return Result(1, "Exit trap unavailable (${error.message}): not running.")
            }
        }
        try {
            System.setOut(PrintStream(out, true))
            System.setErr(PrintStream(out, true))
            val future = executor.submit<Int> { call(loader) }
            return try {
                Result(future.get(timeoutMs, TimeUnit.MILLISECONDS), out.toString().takeLast(8000))
            } catch (error: TimeoutException) {
                future.cancel(true)
                Result(-1, (out.toString() + "\nTimed out.").takeLast(8000))
            } catch (error: java.util.concurrent.ExecutionException) {
                // A tool throwing (bad arguments, a broken project) is a failed run, not a crash.
                val cause = error.cause ?: error
                Result(1, (out.toString() + "\n" + (cause.message ?: cause.javaClass.simpleName)).takeLast(8000))
            }
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
            if (trapInstalled) {
                runCatching {
                    if (previousManager == null) {
                        // Clearing is denied once a manager exists; restoring null is best-effort.
                        System.setSecurityManager(null)
                    } else {
                        System.setSecurityManager(previousManager)
                    }
                }
            }
            executor.shutdownNow()
            closeQuietly(loader)
        }
    }

    /**
     * URLClassLoader (desktop tests) is Closeable; Android's DexClassLoader is not and
     * needs no close. Casting keeps this compiling against android.jar, which has no
     * ClassLoader.close().
     */
    private fun closeQuietly(loader: ClassLoader) {
        runCatching { (loader as? java.io.Closeable)?.close() }
    }

    /**
     * The pack script ships dex inside jars. A dexed jar is a zip whose root holds
     * classes.dex; ART runs it through DexClassLoader, and a plain URLClassLoader cannot.
     */
    private fun openLoader(jars: List<File>, dexOptDir: File?): ClassLoader {
        // The parent is the boot loader: the toolchain must not see the app's own classes,
        // and the app's classes must not shadow the toolchain's.
        val parent = ClassLoader.getSystemClassLoader().parent
        return if (jars.isNotEmpty() && containsDex(jars.first())) {
            val opt = (dexOptDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")).apply { mkdirs() }
            val ctor = Class.forName("dalvik.system.DexClassLoader").getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                ClassLoader::class.java,
            )
            ctor.newInstance(
                jars.joinToString(File.pathSeparator) { it.absolutePath },
                opt.absolutePath,
                null,
                parent,
            ) as ClassLoader
        } else {
            URLClassLoader(jars.map { it.toURI().toURL() }.toTypedArray(), parent)
        }
    }

    /** True when the file is a zip with classes.dex at its root. */
    private fun containsDex(file: File): Boolean = runCatching {
        java.util.zip.ZipFile(file).use { zip -> zip.getEntry("classes.dex") != null }
    }.getOrDefault(false)
}
