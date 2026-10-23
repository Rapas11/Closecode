package com.closecode.ai

import android.content.Context
import android.os.Build
import com.closecode.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The OpenCode server on the phone.
 *
 * One object, because there is only ever one server: it is launched on the phone, its documented
 * health endpoint is polled once a second, and when it stops answering the app starts it again
 * by itself.
 *
 * The server is unsecured, as OpenCode documents for local use: it is bound to 127.0.0.1 and
 * never to 0.0.0.0, so it is reachable only from this phone, and the page's own fetch and event
 * requests do not have to carry a Basic-auth answer the WebView may not attach. OpenCode's docs:
 * "If OPENCODE_SERVER_PASSWORD is not set, the server will be unsecured. This is fine for local
 * use but should be set for network access."
 *
 * The state is a plain Compose value, so the AI tab, the notification and the log read the same
 * truth.
 */
object OpenCode {

    /** What the screens show. */
    val state = androidx.compose.runtime.mutableStateOf(OpenCodeStatus())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var process: Process? = null

    /** The PID file belonging to [process], removed with it. */
    @Volatile
    private var pidFile: File? = null

    private val processLock = Any()

    /**
     * True from the moment [stop] is called until the next [start]. A launch that is already in
     * flight checks this before it adopts a process, so a quick Start/Stop cannot leave a server
     * running with nobody holding it.
     */
    @Volatile
    private var stopping: Boolean = false

    /** True while a server is being started or is running. */
    val isActive: Boolean get() = job?.isActive == true

    /**
     * Starts the server on [projectRoot].
     *
     * Safe to call twice: the second call is ignored while the first is still going.
     */
    fun start(context: Context, projectRoot: File, projectName: String) {
        if (isActive) return
        stopping = false
        val appContext = context.applicationContext
        job = scope.launch {
            runServer(appContext, projectRoot, projectName)
        }
    }

    /** Stops the server and clears the card. */
    fun stop() {
        stopping = true
        job?.cancel()
        job = null
        killProcess()
        lastAutoOpenedGeneration = -1
        state.value = OpenCodeStatus()
        DebugLog.crumb("AICO", "stopped")
    }

    /**
     * Puts a reason on the card without starting anything.
     *
     * Used when the app cannot even begin — no project folder, a CPU OpenCode does not run on.
     * The card shows the reason, so the user is never left looking at a button that does nothing.
     */
    fun reportFailure(message: String) {
        state.value = state.value.copy(
            phase = OpenCodePhase.Failed,
            error = message,
        )
        DebugLog.crumb("AICO", "not started: ${message.take(60)}")
    }

    private fun killProcess() {
        val running = synchronized(processLock) { process.also { process = null } }
        pidFile?.let { file -> runCatching { file.delete() } }
        if (running == null) return
        runCatching { running.destroy() }
        runCatching { running.waitFor(2, TimeUnit.SECONDS) }
        runCatching { running.destroyForcibly() }
    }

    /** The file that lets the next app run find a server a force-stop left behind. */
    private fun serverPidFile(context: Context): File =
        File(context.filesDir, "opencode/server.pid")

    /**
     * Kills a server the last app run left behind.
     *
     * `am force-stop` kills the app process but not its child. The child keeps listening on the
     * old port, and the next start would either take another port or talk to a server it does
     * not own. The PID file is written at launch and read here; the UID and cmdline checks make
     * sure a recycled PID cannot make the app kill an unrelated process.
     */
    private fun killOrphan(context: Context): Int? {
        val file = serverPidFile(context)
        val pid = runCatching { file.readText().trim().toInt() }.getOrNull()
        runCatching { file.delete() }
        if (pid == null || pid <= 0 || pid == android.os.Process.myPid()) return null
        val status = runCatching { File("/proc/$pid/status").readLines() }.getOrNull()
            ?: return null
        val uid = status.firstOrNull { it.startsWith("Uid:") }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (uid != android.os.Process.myUid()) return null
        val cmdline = runCatching { File("/proc/$pid/cmdline").readText() }.getOrNull()
            .orEmpty()
        if (!cmdline.contains("opencode")) return null
        val killed = runCatching {
            ProcessBuilder("/system/bin/kill", "-9", pid.toString())
                .start()
                .waitFor(2, TimeUnit.SECONDS)
        }.getOrDefault(false)
        if (!killed) return null
        DebugLog.crumb("AICO", "removed orphan server $pid")
        return pid
    }

    /**
     * True while the page the WebView kept from the last server run is out of date.
     *
     * A freshly started server wants a freshly started page: OpenCode's web frontend stores
     * state about the old server run in the WebView, and opening the new server with the old
     * state can bring up its error screen before anything is shown. So every new server
     * process marks the page data as out of date; the chat screen then clears it once — and
     * only once — before the page is loaded.
     */
    @Volatile
    private var webWipePending: Boolean = false

    /**
     * Takes the due page wipe: answers whether one was pending and clears the mark, so the
     * wipe happens exactly once per server process and never during a running one.
     */
    fun consumeWebWipe(): Boolean {
        val due = webWipePending
        webWipePending = false
        return due
    }

    /**
     * The server generation whose page was opened automatically.
     *
     * The AI tab uses this to open a new server's page once, then leave the user alone: closing
     * the page and coming back to the tab must not pop it open again. [stop] resets it because
     * the next server starts its generation count over.
     */
    @Volatile
    var lastAutoOpenedGeneration: Int = -1

    /**
     * The whole life of the server: launch, wait for its health endpoint, answer, and come
     * back if it goes.
     */
    private suspend fun runServer(context: Context, projectRoot: File, projectName: String) {
        // A force-stop kills this app but not its child. The PID file is the only way the next
        // run can find that child; clean it up before choosing a port, or the old server would
        // keep 4098 and this run would start a second one beside it.
        val orphanPid = killOrphan(context)
        val port = freePort()
        val abis = Build.SUPPORTED_ABIS.toList()
        val arch = OpenCodeRuntime.alpineArch(abis)
        if (arch == null) {
            update {
                it.copy(
                    phase = OpenCodePhase.Failed,
                    projectName = projectName,
                    projectFolder = projectRoot.name,
                    error = unsupportedCpuMessage(abis),
                    log = listOf(OpenCodeLogLine("Starting")),
                )
            }
            OpenCodeService.stop(context)
            DebugLog.crumb("AICO", "no build for ${abis.joinToString()}")
            return
        }

        update {
            it.copy(
                phase = OpenCodePhase.Starting,
                projectName = projectName,
                projectFolder = projectRoot.name,
                url = "http://${OpenCodeDefaults.HOST}:$port",
                port = port,
                log = listOfNotNull(
                    OpenCodeLogLine("Starting OpenCode ${OpenCodeDefaults.VERSION}"),
                    orphanPid?.let {
                        OpenCodeLogLine("Removed a server left by the last run (PID $it)")
                    },
                ),
                error = null,
                downloading = false,
                downloadPercent = 0,
            )
        }

        // Step one: the program itself, and the libraries it is linked against. The first start
        // downloads both; later starts find them on the phone and skip the whole thing.
        val binary = try {
            update { it.copy(downloading = true, downloadPercent = 0) }
            val file = withContext(Dispatchers.IO) {
                OpenCodeBinary.ensure(context, abis) { percent ->
                    update { it.copy(downloadPercent = percent) }
                }
            }
            update { it.copy(downloading = false, downloadPercent = 0) }
            log("Downloaded OpenCode ${OpenCodeDefaults.VERSION}")

            update { it.copy(downloading = true, downloadPercent = 0) }
            withContext(Dispatchers.IO) {
                OpenCodeRuntime.ensure(context, arch) { percent ->
                    update { it.copy(downloadPercent = percent) }
                }
            }
            update { it.copy(downloading = false) }
            log("Runtime: musl, libstdc++ and libgcc from Alpine ${OpenCodeRuntime.ALPINE} ($arch)")
            file
        } catch (error: Throwable) {
            if (!currentCoroutineContext().isActive) return
            val message = error.message ?: "OpenCode could not be downloaded."
            update {
                it.copy(
                    phase = OpenCodePhase.Failed,
                    downloading = false,
                    error = message,
                    log = it.log + OpenCodeLogLine(message, warning = true),
                )
            }
            OpenCodeService.stop(context)
            DebugLog.crumb("AICO", "download failed: ${error.javaClass.simpleName}")
            return
        }

        log("Project: ${projectRoot.absolutePath}")
        log("Program: ${binary.absolutePath}")
        log("Loader: ${OpenCodeRuntime.loader(context, arch).absolutePath}")

        var attempt = 1
        while (currentCoroutineContext().isActive) {
            log(OpenCodeDefaults.launchLine(port))
            log("Server: local only, unsecured (OpenCode's documented local mode)")
            log("Waiting for http://${OpenCodeDefaults.HOST}:$port/global/health")
            val failure = launchProcess(context, arch, binary, projectRoot, port)
            if (failure != null) {
                if (!currentCoroutineContext().isActive) return
                val message = "OpenCode could not be started on this phone.\n$failure"
                update {
                    it.copy(
                        phase = OpenCodePhase.Failed,
                        error = message,
                        log = it.log + OpenCodeLogLine(message, warning = true),
                    )
                }
                OpenCodeService.stop(context)
                DebugLog.crumb("AICO", "launch failed: ${failure.take(120)}")
                return
            }

            val version = waitForAnswer(port)
            if (version != null) {
                val shown = version.ifBlank { readVersion(context, arch, binary) }
                val url = "http://${OpenCodeDefaults.HOST}:$port"
                update {
                    it.copy(
                        phase = OpenCodePhase.Running,
                        version = shown,
                        url = url,
                        generation = it.generation + 1,
                    )
                }
                log("OpenCode is answering at $url")
                OpenCodeService.update(context, url, projectName)
                DebugLog.crumb("AICO", "server answering")
                // Stay up until the server stops answering or the process ends.
                var misses = 0
                while (currentCoroutineContext().isActive) {
                    delay(1_000)
                    if (process?.isAlive != true) break
                    if (probe(port) != null) {
                        misses = 0
                    } else {
                        misses++
                        // One missed answer is a hiccup, two is a busy moment. A server that
                        // is truly gone does not come back within ten seconds of asking.
                        if (misses >= 3) break
                    }
                }
                if (!currentCoroutineContext().isActive) return
            }

            // The server is gone or stopped answering. The app brings it back by itself.
            attempt++
            log("Connection lost", warning = true)
            update {
                it.copy(
                    phase = OpenCodePhase.ConnectionLost,
                    retryInSeconds = RETRY_SECONDS,
                )
            }
            for (second in RETRY_SECONDS downTo 1) {
                if (!currentCoroutineContext().isActive) return
                update { it.copy(retryInSeconds = second) }
                delay(1_000)
            }
            log("Retry $attempt: starting again…")
            killProcess()
        }
    }

    /**
     * Waits for the documented health endpoint to say the server is healthy.
     *
     * Returns the version it reported (possibly empty), or null when the process died or the
     * server did not answer within [START_TIMEOUT_MS].
     */
    private suspend fun waitForAnswer(port: Int): String? {
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (currentCoroutineContext().isActive) {
            if (process?.isAlive != true) return null
            probe(port)?.let { return it.version }
            if (System.currentTimeMillis() >= deadline) {
                log("No answer after ${START_TIMEOUT_MS / 1000} s", warning = true)
                return null
            }
            delay(1_000)
        }
        return null
    }

    private data class ServerHealth(val version: String)

    /**
     * One request to `GET /global/health`, the endpoint OpenCode documents for "is the server
     * up, and which version is it". The server is unsecured on loopback, so no Authorization
     * header is needed.
     */
    private fun probe(port: Int): ServerHealth? {
        val connection = try {
            URL("http://${OpenCodeDefaults.HOST}:$port/global/health").openConnection()
                as HttpURLConnection
        } catch (error: Throwable) {
            return null
        }
        return try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.useCaches = false
            connection.requestMethod = "GET"
            if (connection.responseCode != 200) {
                null
            } else {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                healthyVersion(text)?.let { ServerHealth(it) }
            }
        } catch (error: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Starts the program and reads its output into the log.
     *
     * The program is started *through the musl loader*, with the runtime's directories on its
     * search path: it is linked against libraries Android does not have, and this is how they
     * are handed to it. Everything after the program's own path is the command the log line
     * above names.
     *
     * Returns null when the process started, or the exact failure text when it did not. The
     * text goes onto the card: "could not be started" without the reason is a dead end.
     */
    private fun launchProcess(
        context: Context,
        arch: String,
        binary: File,
        projectRoot: File,
        port: Int,
    ): String? {
        return try {
            val loader = OpenCodeRuntime.loader(context, arch).absolutePath
            val libraryPath = OpenCodeRuntime.libraryPath(context)
            val pid = serverPidFile(context)
            runCatching { pid.parentFile?.mkdirs() }
            val command = listOf(
                loader,
                "--library-path",
                libraryPath,
                binary.absolutePath,
                "web",
                "--hostname",
                OpenCodeDefaults.HOST,
                "--port",
                port.toString(),
            )
            // The shell writes its own PID, then becomes the loader. `exec` keeps the PID, so
            // the file names the server itself, and no Android-only Process.pid() is needed.
            val launch = "echo \$\$ > ${shellQuote(pid.absolutePath)}; exec " +
                command.joinToString(" ") { shellQuote(it) }
            val builder = ProcessBuilder("/system/bin/sh", "-c", launch)
            builder.directory(projectRoot)
            ground(context, builder.environment())
            // No OPENCODE_SERVER_PASSWORD and no OPENCODE_SERVER_USERNAME: the server is bound
            // to loopback, and OpenCode documents unsecured local use as fine. This is also
            // what makes the page's own fetch/SSE calls work without the WebView having to
            // attach a Basic-auth answer it may not carry.
            //
            // The server must not try to replace itself, and must not download LSP servers it
            // would then have to run: both are desktop habits that do not belong in this app's
            // storage. Print logs to stderr so the AI tab's log shows what the server itself
            // says when it refuses to start.
            builder.environment()["OPENCODE_DISABLE_AUTOUPDATE"] = "true"
            builder.environment()["OPENCODE_DISABLE_LSP_DOWNLOAD"] = "true"
            builder.environment()["OPENCODE_PRINT_LOGS"] = "1"
            builder.environment()["OPENCODE_LOG_LEVEL"] = "INFO"
            builder.redirectErrorStream(true)
            pidFile = pid
            val started = builder.start()
            synchronized(processLock) {
                if (stopping) {
                    runCatching { started.destroyForcibly() }
                    runCatching { pid.delete() }
                    return "The start was cancelled."
                }
                process = started
            }
            webWipePending = true
            scope.launch {
                // Destroying the process closes this stream under the reader's feet: the read
                // in progress answers InterruptedIOException. The reader must then stop
                // reading quietly — its process is being replaced — not take the app with it.
                runCatching {
                    started.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> log(line) }
                    }
                }
            }
            null
        } catch (error: Throwable) {
            buildString {
                append(error.javaClass.simpleName)
                error.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
        }
    }

    /** A single-quoted shell argument, safe for a path with spaces in it. */
    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    /** The version the program reports about itself, asked of it the same way it is started. */
    private fun readVersion(context: Context, arch: String, binary: File): String {
        return try {
            val builder = ProcessBuilder(
                OpenCodeRuntime.loader(context, arch).absolutePath,
                "--library-path",
                OpenCodeRuntime.libraryPath(context),
                binary.absolutePath,
                "--version",
            )
            ground(context, builder.environment())
            builder.redirectErrorStream(true)
            val started = builder.start()
            val text = started.inputStream.bufferedReader().readText().trim()
            started.waitFor(10, TimeUnit.SECONDS)
            Regex("v?\\d+\\.\\d+\\.\\d+").find(text)?.value ?: ""
        } catch (error: Throwable) {
            ""
        }
    }

    /**
     * The home directory the program is given: its own corner of the app's files, not the
     * phone's.
     */
    private fun home(context: Context): File = File(context.filesDir, "opencode/home")

    /** The place for files a program is meant to throw away again. */
    private fun tmp(context: Context): File =
        File(context.cacheDir, "opencode").apply { mkdirs() }

    /**
     * Puts into [environment] the directories the program is allowed to write: Android is not a
     * desktop Linux — there is no `/tmp` an app may write and no `$HOME` a program may rely
     * on — and OpenCode makes `/tmp/opencode` for itself the moment it starts, so without
     * these it dies with EACCES before the server is ever up, even at `--version`.
     */
    private fun ground(context: Context, environment: MutableMap<String, String>) {
        val home = home(context)
        environment["TMPDIR"] = tmp(context).absolutePath
        environment["HOME"] = home.absolutePath
        environment["XDG_DATA_HOME"] = File(home, "data").apply { mkdirs() }.absolutePath
        environment["XDG_CONFIG_HOME"] = File(home, "config").apply { mkdirs() }.absolutePath
        environment["XDG_CACHE_HOME"] = File(home, "cache").apply { mkdirs() }.absolutePath
    }

    /**
     * The port the server gets.
     *
     * The spec names 4098. When that port is already taken the app takes the next free one and
     * says so in the log, rather than failing over a port it cannot use.
     */
    private fun freePort(): Int {
        for (candidate in OpenCodeDefaults.PORT..(OpenCodeDefaults.PORT + 20)) {
            if (!isPortBusy(candidate)) return candidate
        }
        return OpenCodeDefaults.PORT
    }

    private fun isPortBusy(port: Int): Boolean {
        return try {
            ServerSocket(port).use { false }
        } catch (error: Throwable) {
            true
        }
    }

    /** Appends a line to the log, keeping the newest [MAX_LOG_LINES] so it cannot grow forever. */
    private fun log(text: String, warning: Boolean = false) {
        if (text.isBlank()) return
        update { status ->
            val lines = status.log + OpenCodeLogLine(text, warning)
            status.copy(
                log = if (lines.size > MAX_LOG_LINES) lines.takeLast(MAX_LOG_LINES) else lines,
            )
        }
    }

    private fun update(block: (OpenCodeStatus) -> OpenCodeStatus) {
        if (stopping) return
        state.value = block(state.value)
    }

    private const val RETRY_SECONDS = 3
    private const val START_TIMEOUT_MS = 120_000L
    private const val MAX_LOG_LINES = 400
}
