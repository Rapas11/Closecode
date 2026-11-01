package com.closecode.control

import android.content.Context
import com.closecode.BuildConfig
import com.closecode.build.BuildCoordinator
import com.closecode.build.BuildOutcome
import com.closecode.build.BuildReport
import com.closecode.build.BuildSetup
import com.closecode.build.BuildSetupFactory
import com.closecode.build.BuildState
import com.closecode.build.GradlePlan
import com.closecode.build.GradleRunner
import com.closecode.build.ProjectCheck
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainManifest
import com.closecode.build.BuildCancel
import com.closecode.debug.BuildInfo
import com.closecode.debug.DebugLog
import com.closecode.device.CloseCodeAccessibilityService
import com.closecode.device.DeviceTools
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.ui.git.GitService
import com.closecode.ui.github.GitHubRemote
import com.closecode.ui.github.TokenStore
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.settings.SettingsStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object CloseCodeControl {

    private const val HOST = "127.0.0.1"
    private const val MAX_REQUEST = 64 * 1024

    private val lock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    private var workers: ExecutorService? = null

    @Volatile
    private var token = ""

    @Volatile
    private var appContext: Context? = null

    fun start(context: Context) {
        synchronized(lock) {
            if (running) return
            val app = context.applicationContext
            base(app).mkdirs()
            val newToken = ByteArray(32)
                .also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            val socket = try {
                ServerSocket(0, 50, InetAddress.getByName(HOST))
            } catch (error: Throwable) {
                DebugLog.crumb("CONTROL", "bind failed: ${error.javaClass.simpleName}")
                return
            }
            val pool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "closecode-control").apply { isDaemon = true }
            }
            try {
                tokenFile(app).writeText(newToken)
                tokenFile(app).setReadable(true, false)
                portFile(app).writeText(socket.localPort.toString())
                portFile(app).setReadable(true, false)
                installCommand(app)
                ToolWrappers.install(app)
            } catch (error: Throwable) {
                runCatching { socket.close() }
                pool.shutdownNow()
                DebugLog.crumb("CONTROL", "files failed: ${error.javaClass.simpleName}")
                return
            }
            server = socket
            token = newToken
            appContext = app
            workers = pool
            running = true
            acceptThread = Thread({ acceptLoop(socket) }, "closecode-control-accept").apply {
                isDaemon = true
                start()
            }
            DebugLog.crumb("CONTROL", "listening on $HOST:${socket.localPort}")
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            runCatching { server?.close() }
            server = null
            acceptThread?.interrupt()
            acceptThread = null
            workers?.shutdownNow()
            workers = null
            appContext = null
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: Throwable) {
                break
            }
            val pool = workers
            if (pool == null) {
                runCatching { client.close() }
                break
            }
            runCatching { pool.execute { handle(client) } }
                .onFailure { runCatching { client.close() } }
        }
    }

    private fun readRequest(reader: BufferedReader): String? {
        val line = StringBuilder()
        while (line.length <= MAX_REQUEST) {
            val c = reader.read()
            if (c < 0) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
        return line.toString()
    }

    private fun handle(client: Socket) {
        client.use {
            val context = appContext ?: return
            it.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
            val line = runCatching { readRequest(reader) }.getOrNull() ?: return
            val output = BufferedOutputStream(it.getOutputStream())
            if (line.length > MAX_REQUEST) {
                send(output, error("The request is too large."))
                return
            }
            val request = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
            if (request == null) {
                send(output, error("The request is not JSON."))
                return
            }
            val supplied = string(request, "token").orEmpty()
            if (!sameToken(supplied, token)) {
                send(output, error("Unauthorized."))
                return
            }
            when (string(request, "command").orEmpty()) {
                "ping" -> send(output, ping())
                "status" -> send(output, status(context))
                "cancel" -> send(output, cancel())
                "install" -> send(output, install(context, request))
                "launch" -> send(output, launch(context, request))
                "logcat" -> send(output, logcat(context, request))
                "export" -> send(output, export(context, request))
                "screenshot" -> send(output, screenshot(context, request))
                "input" -> send(output, DeviceTools.input(args(request)))
                "ui" -> send(output, DeviceTools.ui(args(request)))
                "app" -> send(output, appStatus(context, request))
                "stop" -> send(output, stop(context, request))
                "restart" -> {
                    it.soTimeout = 0
                    runBlocking { restart(context, request, output) }
                }
                "accessibility" -> send(output, accessibility(context))
                "describe" -> {
                    it.soTimeout = 0
                    runBlocking { send(output, describe(context, request)) }
                }
                "tool" -> {
                    it.soTimeout = 0
                    tool(context, request, output)
                }
                "tools" -> {
                    it.soTimeout = 0
                    tools(context, request, output)
                }
                "build" -> {
                    it.soTimeout = 0
                    runBlocking { build(context, request, output) }
                }
                "run" -> {
                    it.soTimeout = 0
                    runBlocking { run(context, request, output) }
                }
                "clean" -> send(output, clean(context, request))
                "gradle" -> {
                    it.soTimeout = 0
                    runBlocking { gradle(context, request, output) }
                }
                "check" -> {
                    it.soTimeout = 0
                    runBlocking { send(output, staticCheck(context, request, "check")) }
                }
                "github" -> {
                    it.soTimeout = 0
                    runBlocking { send(output, github(context, request)) }
                }
                else -> send(output, error("Unknown command."))
            }
        }
    }

    private fun install(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        return DeviceTools.install(context, project.directory, args(request).firstOrNull())
    }

    private fun launch(context: Context, request: JsonObject): JsonObject {
        val argument = args(request).firstOrNull()
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (project == null && argument.isNullOrBlank()) {
            return error("Run closecode from a registered project folder or pass a package name.")
        }
        return DeviceTools.launch(context, project?.directory, argument)
    }

    private fun logcat(context: Context, request: JsonObject): JsonObject {
        val arguments = args(request)
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (project == null && arguments.firstOrNull().isNullOrBlank()) {
            return error("Run closecode from a registered project folder or pass a package name.")
        }
        val limit = int(request, "limit")
            ?: arguments.getOrNull(1)?.toIntOrNull()
            ?: 600
        return DeviceTools.logcat(project?.directory, arguments.firstOrNull(), limit)
    }

    private fun export(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        return DeviceTools.export(context, project.directory, args(request).firstOrNull())
    }

    private fun screenshot(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        return DeviceTools.screenshot(context, project.directory, args(request).firstOrNull())
    }

    private suspend fun describe(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        val setup = try {
            BuildSetupFactory.create(context, project)
        } catch (error: Throwable) {
            return error(error.message ?: error.javaClass.simpleName)
        }
        val store = ToolchainHolder.store(context)
        val last = BuildCoordinator.last()?.takeIf { it.id == project.id }?.let { build ->
            com.google.gson.JsonObject().apply {
                addProperty("summary", build.summary)
                add("result", BuildReport.outcome(build.outcome))
            }
        }
        val description = ProjectDescription.json(
            project = project,
            facts = ProjectDescription.read(project),
            setup = setup,
            toolchainVersion = ToolchainManifest.load(context).version,
            toolchainReady = store.isReady(),
            toolchainMissing = store.missing().map { it.fileName },
            toolchainBytes = store.sizeOnPhone(),
            gradleReady = setup.gradle != null,
            gradleMissing = setup.gradleMissing,
            last = last,
        )
        val shellTools = ShellToolsStore(context, ShellToolsManifest.load(context))
        description.add("shellTools", JsonObject().apply {
            addProperty("ready", shellTools.isReady())
            addProperty("bytes", shellTools.installedBytes())
            addProperty("prefix", shellTools.directory.absolutePath)
        })
        return description
    }

    private fun tool(context: Context, request: JsonObject, output: OutputStream) {
        val arguments = args(request)
        val name = arguments.firstOrNull()
        if (name == null) {
            sendRaw(output, "usage: closecode tool aapt2|d8|r8|kotlinc [args...]\n", 2)
            return
        }
        if (name !in ToolBridge.names) {
            sendRaw(output, "closecode: unknown tool '$name'.\n", 2)
            return
        }
        if (BuildCoordinator.active() != null) {
            sendRaw(output, "closecode: a build is running; try again when it finishes.\n", 1)
            return
        }
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (project == null) {
            sendRaw(output, "closecode: run this from a registered project folder.\n", 1)
            return
        }
        val result = runCatching {
            ToolBridge.run(context, name, arguments.drop(1), project.directory)
        }.getOrElse { error ->
            ToolBridge.Result(1, error.message ?: error.javaClass.simpleName)
        }
        val text = result.output + if (result.output.endsWith("\n")) "" else "\n"
        sendRaw(output, text, result.code)
    }

    private fun sendRaw(output: OutputStream, text: String, code: Int): Boolean =
        synchronized(output) {
            runCatching {
                output.write(text.toByteArray(StandardCharsets.UTF_8))
                output.write("__CLOSECODE_TOOL_EXIT__${code}__\n".toByteArray(StandardCharsets.UTF_8))
                output.flush()
                true
            }.getOrDefault(false)
        }

    private fun tools(context: Context, request: JsonObject, output: OutputStream) {
        val store = ShellToolsStore(context, ShellToolsManifest.load(context))
        when (args(request).firstOrNull() ?: "status") {
            "status" -> send(output, toolsStatus(context, store))
            "remove" -> {
                val removed = store.remove()
                send(output, JsonObject().apply {
                    addProperty("ok", removed)
                    if (!removed) addProperty("error", "Could not remove the shell tools.")
                    else addProperty("removed", true)
                })
            }
            "install" -> {
                val result = store.install { progress ->
                    send(output, JsonObject().apply {
                        addProperty("ok", true)
                        addProperty("event", "progress")
                        addProperty("stage", progress.stage)
                        addProperty("doneBytes", progress.doneBytes)
                        addProperty("totalBytes", progress.totalBytes)
                        progress.packageName?.let { addProperty("package", it) }
                        addProperty("message", progress.message)
                    })
                }
                if (result.ok) {
                    send(output, JsonObject().apply {
                        addProperty("ok", true)
                        addProperty("event", "done")
                        addProperty("bytes", store.installedBytes())
                        addProperty("prefix", store.directory.absolutePath)
                    })
                } else {
                    send(output, error(result.error ?: "The shell tools did not install."))
                }
            }
            else -> send(output, error("usage: closecode tools status|install|remove"))
        }
    }

    private fun toolsStatus(context: Context, store: ShellToolsStore): JsonObject = JsonObject().apply {
        val manifest = ShellToolsManifest.load(context)
        addProperty("ok", true)
        addProperty("ready", store.isReady())
        addProperty("prefix", store.directory.absolutePath)
        addProperty("bytes", store.installedBytes())
        addProperty("version", manifest.version)
        add("packages", com.google.gson.JsonArray().apply {
            store.installedPackages().forEach { add(it) }
        })
        add("required", com.google.gson.JsonArray().apply {
            manifest.required.forEach { add(it) }
        })
    }

    private fun accessibility(context: Context): JsonObject {
        if (CloseCodeAccessibilityService.connected()) {
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("enabled", true)
            }
        }
        val opened = runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        return JsonObject().apply {
            addProperty("ok", opened)
            addProperty("enabled", false)
            if (!opened) addProperty("error", "Android did not open accessibility settings.")
            else addProperty("note", "Enable CloseCode in the accessibility settings, then try input again.")
        }
    }

    private fun args(request: JsonObject): List<String> =
        request.getAsJsonArray("args")
            ?.mapNotNull { runCatching { it.asString }.getOrNull() }
            .orEmpty()

    private fun string(request: JsonObject, key: String): String? =
        runCatching { request.get(key)?.asString }.getOrNull()

    private fun int(request: JsonObject, key: String): Int? =
        runCatching { request.get(key)?.asInt }.getOrNull()

    private suspend fun build(
        context: Context,
        request: JsonObject,
        output: OutputStream,
    ): BuildRun? {
        val resolved = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (resolved == null) {
            send(output, error("Run closecode from a registered project folder."))
            return null
        }
        if (BuildCoordinator.active() != null) {
            send(output, error("A build is already running."))
            return null
        }
        val setup = try {
            BuildSetupFactory.create(context, resolved)
        } catch (error: Throwable) {
            send(output, error(error.message ?: error.javaClass.simpleName))
            return null
        }
        val arguments = args(request)
        val useGradle = arguments.any {
            it == "--gradle" || it == "--full-gradle" || it == "--real"
        }
        send(output, JsonObject().apply {
            addProperty("ok", true)
            addProperty("event", "start")
            addProperty("id", resolved.id)
            addProperty("slug", resolved.slug)
            addProperty("project", resolved.name)
            addProperty("build", BuildConfig.VERSION_CODE)
            addProperty("engine", if (useGradle) "gradle" else "closecode")
            add("missing", com.google.gson.JsonArray().apply {
                (if (useGradle) setup.gradleMissing else setup.missing).forEach { add(it) }
            })
        })
        val onEvent: (JsonObject) -> Unit = { event -> send(output, event) }
        val onLog: (String) -> Unit = { text ->
            send(output, JsonObject().apply {
                addProperty("ok", true)
                addProperty("event", "log")
                addProperty("text", text)
            })
        }
        val buildType = if (args(request).any {
                it == "release" || it == "--release" ||
                    it.endsWith("Release", ignoreCase = true)
            }
        ) {
            "release"
        } else {
            "debug"
        }
        try {
            val settings = SettingsStore(SharedPreferencesKeyValueStore(context)).read()
            val outcome = BuildCoordinator.build(
                context,
                resolved,
                setup,
                onEvent,
                onLog,
                cacheEnabled = settings.buildCache,
                buildType = buildType,
                useGradle = useGradle,
            )
            send(output, JsonObject().apply {
                addProperty("ok", true)
                addProperty("event", "done")
                addProperty("summary", BuildReport.summary(outcome))
                add("result", BuildReport.outcome(outcome))
                add("logTail", com.google.gson.JsonArray().apply {
                    BuildReport.tail(resolved.directory, 20).forEach { add(it) }
                })
            })
            return BuildRun(resolved, outcome)
        } catch (error: Throwable) {
            send(output, error(error.message ?: error.javaClass.simpleName))
            return null
        }
    }

    private data class BuildRun(val project: BuildSetup.Project, val outcome: BuildOutcome)

    private fun clean(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        val build = File(project.directory, "build")
        val existed = build.exists()
        val removed = !existed || build.deleteRecursively()
        return JsonObject().apply {
            addProperty("ok", removed)
            addProperty("removed", existed)
            addProperty("path", build.absolutePath)
            if (!removed) addProperty("error", "Could not remove the build folder.")
        }
    }

    private fun appStatus(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        return DeviceTools.appStatus(context, project?.directory, args(request).firstOrNull())
    }

    private fun stop(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        val argument = args(request).firstOrNull()
        if (project == null && argument.isNullOrBlank()) {
            return error("Run closecode from a registered project folder or pass a package name.")
        }
        return DeviceTools.stop(context, project?.directory, argument)
    }

    private suspend fun restart(context: Context, request: JsonObject, output: OutputStream) {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        val argument = args(request).firstOrNull()
        if (project == null && argument.isNullOrBlank()) {
            send(output, error("Run closecode from a registered project folder or pass a package name."))
            return
        }
        val stopped = DeviceTools.stop(context, project?.directory, argument)
        send(output, stopped)
        val launched = DeviceTools.launch(context, project?.directory, argument)
        send(output, launched)
        if (launched.get("ok")?.asBoolean != true) return
        send(output, DeviceTools.waitForForeground(project?.directory, argument, 15_000))
    }

    private suspend fun run(context: Context, request: JsonObject, output: OutputStream) {
        val run = build(context, request, output) ?: return
        val built = run.outcome as? BuildOutcome.Built
        if (built == null) {
            send(output, error("The build did not succeed, so nothing was installed."))
            return
        }
        val install = DeviceTools.install(
            context,
            run.project.directory,
            built.apk.absolutePath,
            waitMs = 60_000,
        )
        send(output, install)
        if (install.get("ok")?.asBoolean != true) return
        if (install.get("needsUser")?.asBoolean == true) return
        val launch = DeviceTools.launch(context, run.project.directory, null)
        send(output, launch)
        if (launch.get("ok")?.asBoolean != true) return
        send(output, DeviceTools.waitForForeground(run.project.directory, null, 15_000))
    }

    /**
     * CloseCode builds with its own engine by default; an optional downloaded Gradle is used
     * only when --gradle is passed. This translates the Gradle task names an agent knows into
     * the same power, and answers the rest with the list of what the phone can run — so the
     * agent never guesses twice.
     */
    private suspend fun gradle(
        context: Context,
        request: JsonObject,
        output: OutputStream,
    ) {
        val arguments = args(request)
        val useGradle = arguments.any {
            it == "--gradle" || it == "--full-gradle" || it == "--real"
        }
        val task = arguments.firstOrNull {
            it != "--gradle" && it != "--full-gradle" && it != "--real"
        }
        if (task == null) {
            send(output, error(gradleTaskMessage("")))
            return
        }
        if (!isSupportedTask(task)) {
            send(output, error(gradleTaskMessage(task)))
            return
        }
        if (task == "assembleDebug" || task == "assembleRelease") {
            build(context, request, output)
            return
        }
        if (task in setOf("test", "testDebugUnitTest", "lint", "lintDebug", "check")) {
            if (useGradle) {
                fullGradle(context, request, output, task)
            } else {
                send(output, staticCheck(context, request, task))
            }
            return
        }
        if (task in setOf("bundleDebug", "bundleRelease", "tasks") ||
            task.startsWith("assemble") || task.startsWith("bundle")
        ) {
            if (useGradle) {
                fullGradle(context, request, output, task)
            } else {
                send(output, error(
                    "closecode: gradle '$task' needs the full Gradle engine. " +
                        "Add --gradle to use it.",
                ))
            }
            return
        }
        val run = build(context, request, output) ?: return
        val built = run.outcome as? BuildOutcome.Built
        if (built == null) {
            send(output, error("The build did not succeed, so nothing was installed."))
            return
        }
        if (task == "installDebug" || task == "installRelease") {
            send(
                output,
                DeviceTools.install(context, run.project.directory, built.apk.absolutePath),
            )
            return
        }
        val installed = DeviceTools.install(
            context,
            run.project.directory,
            built.apk.absolutePath,
        )
        if (installed.get("ok")?.asBoolean != true) {
            send(output, installed)
            return
        }
        send(output, DeviceTools.launch(context, run.project.directory, null))
    }

    /**
     * Full Gradle tasks that do not produce an APK: unit tests, lint, check, bundles and the
     * task listing. The output is streamed as log events; the last event names the artifact
     * (or says the task passed) and the exit code.
     */
    private suspend fun fullGradle(
        context: Context,
        request: JsonObject,
        output: OutputStream,
        task: String,
    ) {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (project == null) {
            send(output, error("Run closecode from a registered project folder."))
            return
        }
        if (BuildCoordinator.active() != null) {
            send(output, error("A build is already running."))
            return
        }
        val setup = try {
            BuildSetupFactory.create(context, project)
        } catch (error: Throwable) {
            send(output, error(error.message ?: error.javaClass.simpleName))
            return
        }
        val gradle = setup.gradle
        if (gradle == null) {
            send(output, JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", "Full Gradle is not ready.")
                add("missing", com.google.gson.JsonArray().apply {
                    setup.gradleMissing.forEach { add(it) }
                })
            })
            return
        }
        send(output, JsonObject().apply {
            addProperty("ok", true)
            addProperty("event", "start")
            addProperty("id", project.id)
            addProperty("slug", project.slug)
            addProperty("project", project.name)
            addProperty("engine", "gradle")
            addProperty("task", task)
        })
        val cancelled = AtomicBoolean(false)
        BuildCancel.register { cancelled.set(true) }
        val result = try {
            withContext(Dispatchers.IO) {
                GradleRunner.runTask(
                    projectDir = project.directory,
                    task = task,
                    toolchain = gradle,
                    onLog = { line ->
                        send(output, JsonObject().apply {
                            addProperty("ok", true)
                            addProperty("event", "log")
                            addProperty("text", line)
                        })
                    },
                    cancelled = { cancelled.get() },
                )
            }
        } finally {
            BuildCancel.clear()
        }
        if (result.cancelled) {
            send(output, error("Gradle task cancelled."))
            return
        }
        if (result.timedOut || result.code != 0) {
            send(output, error(GradleRunner.failureMessage(result)))
            return
        }
        val bundle = if (task.startsWith("bundle")) {
            GradlePlan.findBundle(project.directory)
        } else {
            null
        }
        val apk = if (task.startsWith("assemble")) {
            GradlePlan.findAnyApk(project.directory)
        } else {
            null
        }
        send(output, JsonObject().apply {
            addProperty("ok", true)
            addProperty("task", task)
            addProperty("exit", result.code)
            addProperty(
                "result",
                when {
                    bundle != null || apk != null -> "built"
                    else -> "passed"
                },
            )
            if (bundle != null) {
                addProperty("bundle", bundle.absolutePath)
                addProperty("bytes", bundle.length())
            }
            if (apk != null) {
                addProperty("apk", apk.absolutePath)
                addProperty("bytes", apk.length())
            }
        })
    }

    /** `closecode check`: the project's static checks, without building. */
    private suspend fun staticCheck(context: Context, request: JsonObject, task: String): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        val setup = try {
            BuildSetupFactory.create(context, project)
        } catch (error: Throwable) {
            return error(error.message ?: error.javaClass.simpleName)
        }
        val report = ProjectCheck.check(project.directory, project.slug, setup.checkInput)
        return JsonObject().apply {
            addProperty("ok", report.ok)
            addProperty("task", task)
            addProperty("checks", "Static project checks. Real unit tests are not run on the phone.")
            addProperty("errors", report.errors.size)
            addProperty("warnings", report.warnings.size)
            addProperty("notes", report.notes.size)
            add("findings", com.google.gson.JsonArray().apply {
                report.findings.forEach { finding ->
                    add(JsonObject().apply {
                        addProperty("severity", finding.severity.name.lowercase())
                        addProperty("message", finding.message)
                        if (finding.file != null) addProperty("file", finding.file)
                        if (finding.line != null) addProperty("line", finding.line)
                    })
                }
            })
        }
    }

    internal fun gradleTaskMessage(task: String): String =
        "closecode: gradle '$task' has no phone equivalent. CloseCode builds with its own " +
            "engine by default; add --gradle to use the downloaded JDK and Gradle distribution. " +
            "Supported tasks: assembleDebug or assembleRelease (build an APK), " +
            "installDebug or installRelease (build and install), launch or launchRelease " +
            "(build, install and launch), test, testDebugUnitTest, lint, lintDebug, check, " +
            "bundleDebug, bundleRelease, tasks, or a flavour task such as assembleFreeDebug " +
            "or bundleFreeRelease. Without --gradle, test, lint and check run the phone's " +
            "static checks and bundles are refused."

    internal fun isSupportedTask(task: String): Boolean =
        task in GRADLE_TASKS ||
            Regex("^assemble[A-Z][A-Za-z0-9]*$").matches(task) ||
            Regex("^bundle[A-Z][A-Za-z0-9]*$").matches(task)

    /** The Gradle names `closecode gradle` accepts; anything else is answered with the list. */
    internal val GRADLE_TASKS = setOf(
        "assembleDebug",
        "assembleRelease",
        "bundleDebug",
        "bundleRelease",
        "tasks",
        "installDebug",
        "installRelease",
        "launch",
        "launchRelease",
        "test",
        "testDebugUnitTest",
        "lint",
        "lintDebug",
        "check",
        "staticCheck",
    )

    /** The GitHub verbs `closecode github` accepts; anything else is answered with the usage. */
    internal val GITHUB_ACTIONS = setOf("push", "pull")

    internal const val GITHUB_USAGE = "usage: closecode github push|pull [--force]"

    /**
     * Publishes a project to GitHub, or takes what came back.
     *
     * The token is read through [TokenStore] inside the app process and handed straight to
     * JGit's credential provider — it never becomes a command line argument, a log line or part
     * of the shell's environment, so a shell history or `ps` cannot leak it.
     */
    private suspend fun github(context: Context, request: JsonObject): JsonObject {
        val arguments = args(request)
        val action = arguments.firstOrNull().orEmpty()
        if (action !in GITHUB_ACTIONS) return error(GITHUB_USAGE)
        val force = arguments.drop(1).any { it.matches(Regex("(--)?force", RegexOption.IGNORE_CASE)) }
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        val token = TokenStore(context).read()
            ?: return error("GitHub is not signed in. Sign in from CloseCode's GitHub screen, then try again.")
        val remote = GitHubRemote(project.directory)
        if (remote.originUrl() == null) {
            return error("This project is not linked to GitHub yet. Link it in the GitHub panel first.")
        }
        if (action == "pull") {
            val clean = runCatching {
                GitService(project.directory).snapshot().getOrNull()
            }.getOrNull()?.isClean ?: false
            if (!clean && !force) {
                return error(
                    "Your changes would be overwritten by the pull. Commit or stage them first, " +
                        "or run closecode github pull --force.",
                )
            }
        }
        val answer = when (action) {
            "push" -> remote.push(token)
            else -> remote.pull(token)
        }
        return answer.fold(
            onSuccess = { message ->
                JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("action", action)
                    addProperty("message", message)
                }
            },
            onFailure = { failure -> error(failure.message ?: failure.javaClass.simpleName) },
        )
    }

    private fun ping(): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("app", "closecode")
        addProperty("label", BuildInfo.label)
        addProperty("version", BuildConfig.VERSION_NAME)
        addProperty("build", BuildConfig.VERSION_CODE)
    }

    private fun status(context: Context): JsonObject {
        val active = BuildCoordinator.active()
        val last = BuildCoordinator.last()
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("running", active != null)
            if (active != null) {
                addProperty("id", active.id)
                addProperty("slug", active.slug)
                addProperty("project", active.name)
                add("state", BuildReport.state(active.engine.state.value))
                val directory = runCatching {
                    ProjectRepository(context).projectDir(active.slug)
                }.getOrNull()
                if (directory != null) {
                    add("logTail", com.google.gson.JsonArray().apply {
                        BuildReport.tail(directory, 20).forEach { add(it) }
                    })
                }
            } else {
                add("state", BuildReport.state(BuildState.Idle))
                if (last != null) {
                    add("last", JsonObject().apply {
                        addProperty("id", last.id)
                        addProperty("slug", last.slug)
                        addProperty("project", last.name)
                        addProperty("summary", last.summary)
                        add("result", BuildReport.outcome(last.outcome))
                    })
                }
            }
        }
    }

    private fun cancel(): JsonObject = JsonObject().apply {
        val cancelled = BuildCoordinator.cancel()
        addProperty("ok", true)
        addProperty("cancelling", cancelled)
    }

    private fun resolveProject(
        context: Context,
        cwd: String?,
        slug: String?,
    ): BuildSetup.Project? {
        val repository = ProjectRepository(context)
        val records = repository.listProjects()
        val record = if (!slug.isNullOrBlank()) {
            records.firstOrNull { it.slug == slug }
        } else {
            val folder = runCatching {
                cwd?.let { File(it).canonicalFile }
            }.getOrNull() ?: return null
            records
                .filter { record ->
                    val project = runCatching {
                        repository.projectDir(record.slug).canonicalFile
                    }.getOrNull() ?: return@filter false
                    folder.path == project.path ||
                        folder.path.startsWith(project.path + File.separator)
                }
                .maxByOrNull { record ->
                    runCatching {
                        repository.projectDir(record.slug).canonicalPath.length
                    }.getOrDefault(0)
                }
        } ?: return null
        val directory = runCatching {
            repository.projectDir(record.slug).canonicalFile
        }.getOrNull() ?: return null
        if (!directory.isDirectory) return null
        val configured = record.takeIf { !it.imported }?.config
        return BuildSetup.Project(
            slug = record.slug,
            name = record.name,
            directory = directory,
            id = record.id,
            savedPackage = configured?.packageName?.takeIf { it.isNotBlank() },
            savedMinSdk = configured?.minSdk?.toIntOrNull(),
            savedTargetSdk = configured?.targetSdk?.toIntOrNull(),
            savedCompileSdk = configured?.compileSdk?.toIntOrNull(),
            otherPackages = repository.reservedPackages(record.id),
        )
    }

    private fun error(message: String): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", message)
    }

    private fun send(output: OutputStream, json: JsonObject): Boolean = synchronized(output) {
        runCatching {
            output.write((json.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            output.flush()
            true
        }.getOrDefault(false)
    }

    private fun sameToken(supplied: String, expected: String): Boolean =
        supplied.isNotEmpty() &&
            expected.isNotEmpty() &&
            MessageDigest.isEqual(
                supplied.toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8),
            )

    private fun base(context: Context): File = File(context.filesDir, "opencode")

    private fun tokenFile(context: Context): File = File(base(context), "control.token")

    private fun portFile(context: Context): File = File(base(context), "control.port")

    private fun commandFile(context: Context): File =
        File(base(context), "bin/closecode")

    private fun installCommand(context: Context) {
        val file = commandFile(context)
        val content = controlCommandScript(
            base(context).absolutePath.replace("'", "'\\''"),
        )
        file.parentFile?.mkdirs()
        if (!file.isFile || file.readText() != content) file.writeText(content)
        if (!file.setExecutable(true, false)) {
            DebugLog.crumb("CONTROL", "could not make closecode executable")
        }
    }
}

internal fun controlCommandScript(basePath: String): String = """
    #!/system/bin/sh
    BASE='$basePath'
    TOKEN=${'$'}(cat "${'$'}BASE/control.token" 2>/dev/null)
    PORT=${'$'}(cat "${'$'}BASE/control.port" 2>/dev/null)
    if [ -z "${'$'}TOKEN" ] || [ -z "${'$'}PORT" ]; then
      echo "closecode: open CloseCode once, then try again." >&2
      exit 1
    fi
    if [ ! -x /system/bin/nc ]; then
      echo "closecode: this phone has no nc." >&2
      exit 1
    fi
    CMD="${'$'}{1:-status}"
    case "${'$'}CMD" in
      b|build) CMD=build ;;
      clean) CMD=clean ;;
      s|status) CMD=status ;;
      c|cancel) CMD=cancel ;;
      p|ping) CMD=ping ;;
      i|install) CMD=install ;;
      l|launch) CMD=launch ;;
      r|run) CMD=run ;;
      app|app-status) CMD=app ;;
      stop) CMD=stop ;;
      restart) CMD=restart ;;
      ui) CMD=ui ;;
      log|logcat) CMD=logcat ;;
      x|export) CMD=export ;;
      shot|screenshot) CMD=screenshot ;;
      in|input) CMD=input ;;
      a|accessibility) CMD=accessibility ;;
      d|describe) CMD=describe ;;
      t|tool) CMD=tool ;;
      tools) CMD=tools ;;
      g|gradle) CMD=gradle ;;
      chk|check) CMD=check ;;
      gh|github) CMD=github ;;
    esac
    case "${'$'}CMD" in
      build|status|cancel|ping|install|launch|logcat|export|screenshot|input|accessibility|describe|tool|tools|gradle|check|github|run|app|stop|restart|ui|clean) ;;
      *) echo "usage: closecode build|status|cancel|ping|install|launch|logcat|export|screenshot|input|accessibility|describe|tool|tools|gradle|check|github|run|app|stop|restart|ui|clean" >&2; exit 2 ;;
    esac
    [ ${'$'}# -gt 0 ] && shift
    CWD=${'$'}(pwd -P 2>/dev/null || pwd)
    escape() { printf '%s' "${'$'}1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
    ARGS=""
    for arg in "${'$'}@"; do
      item=${'$'}(escape "${'$'}arg")
      if [ -z "${'$'}ARGS" ]; then ARGS="\"${'$'}item\""; else ARGS="${'$'}ARGS,\"${'$'}item\""; fi
    done
    REQUEST="{\"token\":\"${'$'}(escape "${'$'}TOKEN")\",\"command\":\"${'$'}CMD\",\"cwd\":\"${'$'}(escape "${'$'}CWD")\",\"args\":[${'$'}ARGS]}"
    OUT="${'$'}BASE/control.out.${'$'}${'$'}"
    if printf '%s\n' "${'$'}REQUEST" | /system/bin/nc 127.0.0.1 "${'$'}PORT" > "${'$'}OUT" 2>/dev/null; then
      if [ "${'$'}CMD" = "tool" ]; then
        CODE=${'$'}(tail -n 1 "${'$'}OUT" 2>/dev/null)
        case "${'$'}CODE" in
          __CLOSECODE_TOOL_EXIT__*__)
            sed '${'$'}d' "${'$'}OUT"
            CODE=${'$'}(printf '%s\n' "${'$'}CODE" | sed 's/^__CLOSECODE_TOOL_EXIT__//; s/__${'$'}//')
            rm -f "${'$'}OUT"
            exit "${'$'}CODE"
            ;;
          *)
            cat "${'$'}OUT"
            rm -f "${'$'}OUT"
            exit 1
            ;;
        esac
      fi
      cat "${'$'}OUT"
      rm -f "${'$'}OUT"
    else
      rm -f "${'$'}OUT"
      echo "closecode: the app did not answer." >&2
      exit 1
    fi
""".trimIndent() + "\n"
