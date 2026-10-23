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
 * One object, because there is only ever one server: it is launched on the phone, it is polled
 * once a second, and when it stops answering the app starts it again by itself. The user never
 * presses "try again" — the spec is explicit about that, and the countdown on the Connection
 * lost screen is the promise that it happens.
 *
 * The state is a plain Compose value, so the AI tab reads the same truth the notification and
 * the log show.
 */
object OpenCode {

    /** What the screens show. */
    val state = androidx.compose.runtime.mutableStateOf(OpenCodeStatus())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var process: Process? = null

    /** True while a server is being started or is running. */
    val isActive: Boolean get() = job?.isActive == true

    /**
     * Starts the server on [projectRoot].
     *
     * Safe to call twice: the second call is ignored while the first is still going.
     */
    fun start(context: Context, projectRoot: File, projectName: String) {
        if (isActive) return
        val appContext = context.applicationContext
        job = scope.launch {
            runServer(appContext, projectRoot, projectName)
        }
    }

    /** Stops the server and clears the card. */
    fun stop() {
        job?.cancel()
        job = null
        killProcess()
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
            steps = openCodeSteps(failed = true),
        )
        DebugLog.crumb("AICO", "not started: ${message.take(60)}")
    }

    private fun killProcess() {
        val running = process ?: return
        process = null
        runCatching { running.destroy() }
        runCatching { running.waitFor(2, TimeUnit.SECONDS) }
        runCatching { running.destroyForcibly() }
    }

    /**
     * The whole life of the server: launch, wait for the port, answer, and come back if it goes.
     */
    private suspend fun runServer(context: Context, projectRoot: File, projectName: String) {
        val port = freePort()
        val abis = Build.SUPPORTED_ABIS.toList()
        val arch = OpenCodeRuntime.alpineArch(abis)
        if (arch == null) {
            update {
                it.copy(
                    phase = OpenCodePhase.Failed,
                    projectName = projectName,
                    projectFolder = projectRoot.name,
                    error = "This phone's CPU is not one OpenCode runs on. It needs ARM64 or x86_64.",
                    steps = openCodeSteps(failed = true),
                    log = listOf(OpenCodeLogLine("Starting")),
                )
            }
            DebugLog.crumb("AICO", "no build for ${abis.firstOrNull()}")
            return
        }

        update {
            it.copy(
                phase = OpenCodePhase.Starting,
                projectName = projectName,
                projectFolder = projectRoot.name,
                url = "http://${OpenCodeDefaults.HOST}:$port",
                port = port,
                steps = openCodeSteps(),
                log = listOf(OpenCodeLogLine("Starting")),
                error = null,
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
            update {
                it.copy(
                    phase = OpenCodePhase.Failed,
                    downloading = false,
                    error = error.message ?: "OpenCode could not be downloaded.",
                    steps = openCodeSteps(failed = true),
                )
            }
            DebugLog.crumb("AICO", "download failed: ${error.javaClass.simpleName}")
            return
        }

        update { it.copy(steps = openCodeSteps(filesChecked = true)) }

        // Step two: the project. OpenCode works on the folder that is open in Closecode.
        log("Project: ${projectRoot.absolutePath}")
        update { it.copy(steps = openCodeSteps(filesChecked = true, projectConnected = true)) }

        val password = OpenCodeSecret(context).read()
        var attempt = 1
        while (currentCoroutineContext().isActive) {
            log(OpenCodeDefaults.launchLine(port))
            log("Waiting for port $port")
            val started = launchProcess(context, arch, binary, projectRoot, port, password)
            if (!started) {
                update {
                    it.copy(
                        phase = OpenCodePhase.Failed,
                        error = "OpenCode could not be started on this phone.",
                        steps = openCodeSteps(failed = true),
                    )
                }
                return
            }
            update {
                it.copy(
                    steps = openCodeSteps(
                        filesChecked = true,
                        projectConnected = true,
                        serverStarted = true,
                    ),
                )
            }

            if (waitForAnswer(port)) {
                val version = readVersion(context, arch, binary)
                update {
                    it.copy(
                        phase = OpenCodePhase.Running,
                        version = version,
                        steps = openCodeSteps(
                            filesChecked = true,
                            projectConnected = true,
                            serverStarted = true,
                            answered = true,
                        ),
                    )
                }
                log("OpenCode is answering at http://${OpenCodeDefaults.HOST}:$port")
                DebugLog.crumb("AICO", "server answering")
                // Stay up until the server stops answering or the process ends.
                var misses = 0
                while (currentCoroutineContext().isActive) {
                    delay(1_000)
                    if (process?.isAlive != true) break
                    if (isAnswering(port)) {
                        misses = 0
                    } else {
                        misses++
                        // One missed answer is a hiccup; two in a row is a dead server.
                        if (misses >= 2) break
                    }
                }
                if (!currentCoroutineContext().isActive) return
            }

            // The server is gone or stopped answering. The app brings it back by itself.
            attempt++
            log("Connection lost")
            update {
                it.copy(
                    phase = OpenCodePhase.ConnectionLost,
                    retryInSeconds = RETRY_SECONDS,
                    steps = openCodeSteps(
                        filesChecked = true,
                        projectConnected = true,
                        serverStarted = true,
                        answered = false,
                    ),
                )
            }
            for (second in RETRY_SECONDS downTo 1) {
                if (!currentCoroutineContext().isActive) return
                update { it.copy(retryInSeconds = second) }
                log("Checking again in 1 second", warning = true)
                delay(1_000)
            }
            log("Retry $attempt: starting again…")
            killProcess()
        }
    }

    /**
     * Waits for the server to answer on its port.
     *
     * Returns true when it answered. Returns false when the process died on the way there.
     */
    private suspend fun waitForAnswer(port: Int): Boolean {
        while (currentCoroutineContext().isActive) {
            if (process?.isAlive != true) return false
            if (isAnswering(port)) return true
            delay(1_000)
        }
        return false
    }

    /** One request to the local server. Anything that answers counts as up. */
    private fun isAnswering(port: Int): Boolean {
        val connection = (URL("http://${OpenCodeDefaults.HOST}:$port/").openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 1_500
            readTimeout = 1_500
            instanceFollowRedirects = false
            useCaches = false
        }
        return try {
            connection.requestMethod = "GET"
            connection.connect()
            // Any answer at all means the server is up: a login page is still an answer.
            connection.responseCode in 100..599
        } catch (error: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Starts the program and reads its output into Build Info.
     *
     * The program is started *through the musl loader*, with the runtime's directories on its
     * search path: it is linked against libraries Android does not have, and this is how they
     * are handed to it. Everything after the program's own path is the command the log line
     * above names.
     */
    private fun launchProcess(
        context: Context,
        arch: String,
        binary: File,
        projectRoot: File,
        port: Int,
        password: String,
    ): Boolean {
        return try {
            val builder = ProcessBuilder(
                OpenCodeRuntime.loader(context, arch).absolutePath,
                "--library-path",
                OpenCodeRuntime.libraryPath(context),
                binary.absolutePath,
                "web",
                "--hostname",
                OpenCodeDefaults.HOST,
                "--port",
                port.toString(),
            )
            builder.directory(projectRoot)
            ground(context, builder.environment())
            builder.environment()["OPENCODE_SERVER_PASSWORD"] = password
            builder.redirectErrorStream(true)
            val started = builder.start()
            process = started
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
            true
        } catch (error: Throwable) {
            DebugLog.crumb("AICO", "launch failed: ${error.javaClass.simpleName}")
            false
        }
    }

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

    /** Appends a line to Build Info. */
    private fun log(text: String, warning: Boolean = false) {
        if (text.isBlank()) return
        update { status -> status.copy(log = status.log + OpenCodeLogLine(text, warning)) }
    }

    private fun update(block: (OpenCodeStatus) -> OpenCodeStatus) {
        state.value = block(state.value)
    }

    private const val RETRY_SECONDS = 3
}
