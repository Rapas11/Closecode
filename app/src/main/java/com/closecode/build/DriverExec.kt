package com.closecode.build

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs toolchain pieces as subprocesses (JVM harness and the aapt2 binary alike).
 * Combined stdout+stderr, a hard timeout that kills stragglers, and no secret logging —
 * commands may carry keystore paths, so output is returned, never printed.
 */
object DriverExec {
    data class Result(val code: Int, val output: String, val timedOut: Boolean)

    fun runJava(
        javaBin: String,
        classpath: List<File>,
        mainClass: String,
        args: List<String>,
        workDir: File,
        timeoutMs: Long = 300_000,
    ): Result = run(
        listOf(javaBin, "-cp", classpath.joinToString(File.pathSeparator) { it.absolutePath }, mainClass) + args,
        workDir,
        timeoutMs,
    )

    fun runNative(
        bin: File,
        args: List<String>,
        workDir: File,
        timeoutMs: Long = 120_000,
        env: Map<String, String> = emptyMap(),
    ): Result = run(listOf(bin.absolutePath) + args, workDir, timeoutMs, env)

    private fun run(
        command: List<String>,
        workDir: File,
        timeoutMs: Long,
        env: Map<String, String> = emptyMap(),
    ): Result {
        val builder = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
        builder.environment().putAll(env)
        val process = builder.start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { output.appendLine(it) }
                }
            }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.join(5_000)
            return Result(code = -1, output = output.toString().takeLast(4000), timedOut = true)
        }
        reader.join(30_000)
        return Result(process.exitValue(), output.toString().takeLast(8000), timedOut = false)
    }
}
