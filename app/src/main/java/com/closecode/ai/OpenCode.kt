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

        // Step one: the program itself. The first start downloads it; later starts skip that.
        val binary = try {
            update { it.copy(downloading = true, downloadPercent = 0) }
            val file = withContext(Dispatchers.IO) {
                OpenCodeBinary.ensure(context, abis) { percent ->
                    update { it.copy(downloadPercent = percent) }
                }
            }
            update { it.copy(downloading = false) }
            log("Downloaded OpenCode ${OpenCodeDefaults.VERSION}")
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
            val started = launchProcess(binary, projectRoot, port, password)
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
                val version = readVersion(binary)
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

    /** Starts the program and reads its output into Build Info. */
    private fun launchProcess(
        binary: File,
        projectRoot: File,
        port: Int,
        password: String,
    ): Boolean {
        return try {
            val builder = ProcessBuilder(
                binary.absolutePath,
                "web",
                "--hostname",
                OpenCodeDefaults.HOST,
                "--port",
                port.toString(),
            )
            builder.directory(projectRoot)
            builder.environment()["OPENCODE_SERVER_PASSWORD"] = password
            builder.redirectErrorStream(true)
            val started = builder.start()
            process = started
            scope.launch {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> log(line) }
                }
            }
            true
        } catch (error: Throwable) {
            DebugLog.crumb("AICO", "launch failed: ${error.javaClass.simpleName}")
            false
        }
    }

    /** The version OpenCode reports about itself. */
    private fun readVersion(binary: File): String {
        return try {
            val builder = ProcessBuilder(binary.absolutePath, "--version")
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
