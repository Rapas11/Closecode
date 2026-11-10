package com.closecode.jobs

import android.content.Context
import com.closecode.build.ProcessTree
import com.closecode.debug.DebugLog
import com.closecode.tools.ShellEnvironment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

data class JobInfo(
    val id: String,
    val kind: String,
    val command: String,
    val status: String,
    val pid: Long,
    val startedAt: Long,
    val logPath: String,
    val cwd: String,
    val port: Int? = null,
    val url: String? = null,
    val exitCode: Int? = null,
    val endedAt: Long? = null,
) {
    fun json(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("kind", kind)
        addProperty("command", command)
        addProperty("status", status)
        addProperty("pid", pid)
        addProperty("startedAt", startedAt)
        addProperty("logPath", logPath)
        addProperty("cwd", cwd)
        port?.let { addProperty("port", it) }
        url?.let { addProperty("url", it) }
        exitCode?.let { addProperty("exitCode", it) }
        endedAt?.let { addProperty("endedAt", it) }
    }
}

/**
 * Servers, tests and other long-running commands are owned here rather than by one socket.
 * A job keeps writing to `filesDir/jobs/<id>.log`, and [JobKeepAliveService] keeps the app
 * process alive while at least one job is still running.
 */
object JobManager {

    private val lock = Any()
    private val counter = AtomicInteger(1)
    private val jobs = linkedMapOf<String, JobInfo>()
    private val processes = mutableMapOf<String, Process>()
    private val stopActions = mutableMapOf<String, () -> Unit>()
    private val writers = mutableMapOf<String, java.io.Writer>()
    private var loaded = false

    fun startProcess(
        context: Context,
        kind: String,
        command: List<String>,
        cwd: File,
        environment: Map<String, String> = emptyMap(),
        port: Int? = null,
        url: String? = null,
    ): JobInfo {
        ensureLoaded(context)
        val id = newId(kind)
        val log = logFile(context, id)
        log.parentFile?.mkdirs()
        val writer = log.bufferedWriter()
        writer.appendLine("# ${command.joinToString(" ")}")
        writer.flush()
        val builder = ProcessBuilder(command)
            .directory(cwd)
            .redirectErrorStream(true)
        val env = ShellEnvironment.env(context, cwd).apply {
            putAll(environment)
        }
        builder.environment().clear()
        builder.environment().putAll(env)
        val process = try {
            builder.start()
        } catch (error: IOException) {
            runCatching { writer.close() }
            throw IllegalStateException(
                "Could not start ${command.firstOrNull() ?: kind}: ${error.message}",
            )
        }
        val info = JobInfo(
            id = id,
            kind = kind,
            command = command.joinToString(" "),
            status = "running",
            pid = pidOf(process),
            startedAt = System.currentTimeMillis(),
            logPath = log.absolutePath,
            cwd = cwd.absolutePath,
            port = port,
            url = url,
        )
        synchronized(lock) {
            jobs[id] = info
            processes[id] = process
            writers[id] = writer
        }
        Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        runCatching {
                            writer.appendLine(line)
                            writer.flush()
                        }
                    }
                }
            }
        }, "closecode-job-$id").apply { isDaemon = true; start() }
        Thread({
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            synchronized(lock) {
                val current = jobs[id] ?: return@synchronized
                val stopped = current.status == "stopped" || current.status == "cancelled"
                jobs[id] = current.copy(
                    status = if (stopped) current.status else if (code == 0) "exited" else "failed",
                    exitCode = code,
                    endedAt = System.currentTimeMillis(),
                )
                processes.remove(id)
                writers.remove(id)?.let { runCatching { it.close() } }
                persist(context)
                if (runningCountLocked() == 0) JobKeepAliveService.stop(context)
            }
        }, "closecode-job-wait-$id").apply { isDaemon = true; start() }
        persist(context)
        JobKeepAliveService.start(context)
        return info
    }

    fun startInternal(
        context: Context,
        kind: String,
        command: String,
        cwd: File,
        port: Int?,
        url: String?,
        stop: () -> Unit,
    ): JobInfo {
        ensureLoaded(context)
        val id = newId(kind)
        val log = logFile(context, id)
        log.parentFile?.mkdirs()
        runCatching { log.writeText("# $command\n") }
        val info = JobInfo(
            id = id,
            kind = kind,
            command = command,
            status = "running",
            pid = -1,
            startedAt = System.currentTimeMillis(),
            logPath = log.absolutePath,
            cwd = cwd.absolutePath,
            port = port,
            url = url,
        )
        synchronized(lock) {
            jobs[id] = info
            stopActions[id] = stop
        }
        persist(context)
        JobKeepAliveService.start(context)
        return info
    }

    fun stop(context: Context, id: String): JobInfo? {
        ensureLoaded(context)
        val process: Process?
        val action: (() -> Unit)?
        val current: JobInfo?
        synchronized(lock) {
            current = jobs[id]
            process = processes.remove(id)
            action = stopActions.remove(id)
            if (current != null) {
                jobs[id] = current.copy(
                    status = "stopped",
                    endedAt = System.currentTimeMillis(),
                )
            }
        }
        process?.let { ProcessTree.terminate(it) }
        action?.let { runCatching { it() } }
        synchronized(lock) {
            writers.remove(id)?.let { runCatching { it.close() } }
            persist(context)
            if (runningCountLocked() == 0) JobKeepAliveService.stop(context)
            return jobs[id]
        }
    }

    fun stopAll(context: Context): JsonArray {
        ensureLoaded(context)
        val running = synchronized(lock) {
            jobs.values.filter { it.status == "running" }.map { it.id }
        }
        return JsonArray().apply {
            running.forEach { id -> stop(context, id)?.let { add(it.json()) } }
        }
    }

    fun list(context: Context): List<JobInfo> {
        ensureLoaded(context)
        synchronized(lock) { return jobs.values.toList().sortedByDescending { it.startedAt } }
    }

    fun find(context: Context, id: String): JobInfo? {
        ensureLoaded(context)
        synchronized(lock) { return jobs[id] }
    }

    fun logs(context: Context, id: String, lines: Int = 80): List<String>? {
        val job = find(context, id) ?: return null
        return runCatching {
            File(job.logPath).readLines().takeLast(lines.coerceIn(1, 1000))
        }.getOrDefault(emptyList())
    }

    fun runningCount(context: Context): Int {
        ensureLoaded(context)
        synchronized(lock) { return runningCountLocked() }
    }

    private fun runningCountLocked(): Int = jobs.values.count { it.status == "running" }

    private fun ensureLoaded(context: Context) {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val file = stateFile(context)
            if (!file.isFile) return
            runCatching {
                val array = JsonParser.parseString(file.readText()).asJsonArray
                array.forEach { element ->
                    val json = element.asJsonObject
                    val job = JobInfo(
                        id = json.string("id") ?: return@forEach,
                        kind = json.string("kind").orEmpty(),
                        command = json.string("command").orEmpty(),
                        status = json.string("status") ?: "lost",
                        pid = json.long("pid") ?: -1,
                        startedAt = json.long("startedAt") ?: 0,
                        logPath = json.string("logPath").orEmpty(),
                        cwd = json.string("cwd").orEmpty(),
                        port = json.int("port"),
                        url = json.string("url"),
                        exitCode = json.int("exitCode"),
                        endedAt = json.long("endedAt"),
                    )
                    jobs[job.id] = if (job.status == "running") job.copy(status = "lost") else job
                }
            }.onFailure { DebugLog.crumb("JOBS", "state read failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun persist(context: Context) {
        runCatching {
            val file = stateFile(context)
            file.parentFile?.mkdirs()
            val array = JsonArray().apply { jobs.values.forEach { add(it.json()) } }
            file.writeText(array.toString())
        }
    }

    private fun newId(kind: String): String =
        "$kind-${System.currentTimeMillis().toString(36)}-${counter.getAndIncrement()}"

    private fun logFile(context: Context, id: String) =
        File(File(context.filesDir, "jobs"), "$id.log")

    private fun stateFile(context: Context) = File(File(context.filesDir, "jobs"), "jobs.json")

    private fun pidOf(process: Process): Long =
        runCatching { ProcessTree.pid(process) }.getOrDefault(-1L)

    private fun JsonObject.string(key: String): String? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
}
