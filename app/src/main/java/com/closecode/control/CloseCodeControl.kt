package com.closecode.control

import android.content.Context
import com.closecode.BuildConfig
import com.closecode.build.BuildCoordinator
import com.closecode.build.BuildOutcome
import com.closecode.build.BuildReport
import com.closecode.build.BuildSetup
import com.closecode.build.BuildSetupFactory
import com.closecode.build.BuildState
import com.closecode.build.CompileDriver
import com.closecode.build.GradleDiagnostics
import com.closecode.build.GradlePlan
import com.closecode.build.GradleRunner
import com.closecode.build.ProjectCheck
import com.closecode.build.ProjectSettings
import com.closecode.build.ProjectTypeDetector
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainManifest
import com.closecode.build.BuildCancel
import com.closecode.debug.BuildInfo
import com.closecode.debug.DebugLog
import com.closecode.device.CloseCodeAccessibilityService
import com.closecode.device.DeviceTools
import com.closecode.device.control.ConsentStore
import com.closecode.device.control.DeviceControl
import com.closecode.jobs.JobManager
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
                "status" -> send(output, status(context, request))
                "cancel" -> send(output, cancel())
                "install" -> send(output, install(context, request))
                "launch" -> send(output, launch(context, request))
                "logcat" -> send(output, logcat(context, request))
                "export" -> send(output, export(context, request))
                "screenshot" -> send(output, screenshot(context, request))
                "input" -> send(output, DeviceTools.input(args(request)))
                "ui" -> send(output, ui(context, request))
                "apps" -> send(output, apps(context, request))
                "intent" -> send(output, intent(context, request))
                "device" -> send(output, DeviceControl.device(context, args(request)))
                "files" -> send(output, DeviceControl.files(context, args(request)))
                "consent" -> send(output, DeviceControl.consent(context, args(request)))
                "audit" -> send(output, DeviceControl.audit(context, args(request)))
                "stop-all" -> send(output, DeviceControl.stopAll(context))
                "exec" -> {
                    it.soTimeout = 0
                    runBlocking { exec(context, request, output) }
                }
                "app" -> send(output, appStatus(context, request))
                "stop" -> send(output, stop(context, request))
                "restart" -> {
                    it.soTimeout = 0
                    runBlocking { restart(context, request, output) }
                }
                "accessibility" -> send(output, accessibility(context, args(request)))
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
                "setup" -> {
                    it.soTimeout = 0
                    setup(context, request, output)
                }
                "doctor" -> {
                    it.soTimeout = 0
                    doctor(context, request, output)
                }
                "toolchain" -> send(output, toolchain(context, request))
                "signing" -> send(
                    output,
                    runCatching {
                        SigningCommand.run(context, args(request), string(request, "cwd"))
                    }
                        .getOrElse { error(it.message ?: "Signing command failed.") },
                )
                "build" -> {
                    it.soTimeout = 0
                    runBlocking { build(context, request, output) }
                }
                "run" -> {
                    it.soTimeout = 0
                    runBlocking { run(context, request, output) }
                }
                "clean" -> send(output, clean(context, request))
                "serve" -> {
                    it.soTimeout = 0
                    runBlocking { serve(context, request, output) }
                }
                "preview" -> {
                    it.soTimeout = 0
                    runBlocking { preview(context, request, output) }
                }
                "jobs" -> send(output, jobs(context, request))
                "init" -> send(output, init(context, request))
                "config" -> send(output, config(context, request))
                "test", "lint", "format", "package", "release" -> {
                    it.soTimeout = 0
                    runBlocking {
                        ecosystem(context, request, output, string(request, "command").orEmpty())
                    }
                }
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

    private fun ui(context: Context, request: JsonObject): JsonObject {
        val arguments = args(request)
        val packageIndex = arguments.indexOf("--package")
        val packageName = if (packageIndex >= 0) arguments.getOrNull(packageIndex + 1) else {
            resolveProject(context, string(request, "cwd"), string(request, "project"))?.let {
                ProjectRepository(context).packageOf(
                    ProjectRepository(context).find(it.slug) ?: return@let null,
                )
            }
        }
        DeviceControl.checkUiConsent(context, packageName)?.let { return it }
        val cleaned = if (packageIndex >= 0) {
            arguments.filterIndexed { index, _ -> index != packageIndex && index != packageIndex + 1 }
        } else {
            arguments
        }
        if (packageName != null) {
            val foreground = com.closecode.device.CloseCodeAccessibilityService.current()
                ?.foregroundPackage()
            if (foreground != null && foreground != packageName) {
                return CommandEnvelope.failed(
                    "$packageName is not in the foreground (currently ${foreground.ifBlank { "unknown" }}).",
                    code = "wrong_package",
                    remedy = "Run closecode apps launch $packageName, wait, then retry.",
                )
            }
        }
        return DeviceTools.ui(cleaned)
    }

    private fun apps(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(context, string(request, "cwd"), string(request, "project"))
        return DeviceControl.apps(context, project?.directory, args(request))
    }

    private fun intent(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(context, string(request, "cwd"), string(request, "project"))
        return DeviceControl.intent(context, project?.directory, args(request))
    }

    private suspend fun exec(context: Context, request: JsonObject, output: OutputStream) {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: run {
            send(output, error("Run closecode exec from a registered project folder."))
            return
        }
        val settings = ProjectSettings.read(project.directory)
        val response = withContext(Dispatchers.IO) {
            DeviceControl.exec(
                context = context,
                projectDir = project.directory,
                settingsEnvironment = settings.env,
                arguments = args(request),
            ) { event -> send(output, event) }
        }
        send(output, response)
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
        val settings = ProjectSettings.read(project.directory)
        description.add("projectType", ProjectTypeDetector.detect(project.directory, settings).json())
        description.add("settings", settings.json())
        description.add("serve", JsonObject().apply {
            addProperty("running", JobManager.list(context).any {
                it.kind == "serve" && it.status == "running" &&
                    runCatching { File(it.cwd).canonicalPath }.getOrNull() == project.directory.canonicalPath
            })
        })
        val shellTools = ShellToolsStore(context, ShellToolsManifest.load(context))
        description.add("shellTools", JsonObject().apply {
            addProperty("ready", shellTools.isReady())
            addProperty("bytes", shellTools.installedBytes())
            addProperty("prefix", shellTools.directory.absolutePath)
        })
        return description
    }

    private fun doctor(context: Context, request: JsonObject, output: OutputStream) {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        send(output, DoctorCommand.run(context, project))
    }

    private fun toolchain(context: Context, request: JsonObject): JsonObject {
        val action = args(request).firstOrNull()?.lowercase() ?: "status"
        return when (action) {
            "status", "json" -> ToolchainStatusCommand.run(context)
            else -> error("usage: closecode toolchain status --json")
        }
    }

    private fun setup(context: Context, request: JsonObject, output: OutputStream) {
        val options = SetupCommand.parse(args(request))
        val response = SetupCommand.run(
            context,
            options,
            string(request, "cwd"),
        ) { event -> send(output, event) }
        send(output, response)
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

    private fun accessibility(context: Context, arguments: List<String>): JsonObject {
        val action = arguments.firstOrNull()?.lowercase() ?: "status"
        if (CloseCodeAccessibilityService.connected()) {
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("enabled", true)
                addProperty("action", action)
            }
        }
        if (action != "enable" && action != "on" && action != "open") {
            return JsonObject().apply {
                addProperty("ok", false)
                addProperty("enabled", false)
                addProperty("action", action)
                addProperty("needsUser", true)
                addProperty("remedy", "Run closecode accessibility enable, then enable CloseCode.")
            }
        }
        val component = android.content.ComponentName(
            context,
            CloseCodeAccessibilityService::class.java,
        ).flattenToString()
        val opened = runCatching {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
            )
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(":settings:fragment_args_key", component)
                .putExtra(
                    ":settings:show_fragment_args",
                    android.os.Bundle().apply {
                        putString(":settings:fragment_args_key", component)
                    },
                )
            context.startActivity(intent)
        }.isSuccess
        return JsonObject().apply {
            addProperty("ok", false)
            addProperty("enabled", false)
            addProperty("needsUser", true)
            addProperty("action", "enable")
            addProperty("url", "android.settings.ACCESSIBILITY_SETTINGS")
            addProperty("component", component)
            if (!opened) addProperty("error", "Android did not open accessibility settings.")
            else addProperty("note", "Enable CloseCode under Settings > Accessibility, then retry.")
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

    /**
     * Full Gradle is the default whenever it is installed: it is the only engine that
     * understands the project's own plugins and dependencies. The custom engine remains an
     * explicit fallback (`--engine closecode`) or the automatic choice when Gradle is not ready.
     */
    private fun chooseGradle(
        arguments: List<String>,
        gradleReady: Boolean,
        pinnedEngine: String? = null,
    ): Boolean {
        var custom = false
        var forced = false
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "--gradle", "--full-gradle", "--real", "--engine=gradle" -> forced = true
                "--no-gradle", "--custom", "--engine=closecode" -> custom = true
                "--engine" -> when (arguments.getOrNull(index + 1)?.lowercase()) {
                    "gradle" -> forced = true
                    "closecode" -> custom = true
                }
            }
            index++
        }
        return when {
            custom -> false
            forced -> true
            pinnedEngine == com.closecode.build.ProjectSettings.ENGINE_GRADLE -> true
            pinnedEngine == com.closecode.build.ProjectSettings.ENGINE_CLOSECODE -> false
            else -> gradleReady
        }
    }

    /**
     * True when the custom engine's own compatibility findings say it cannot build this
     * project (native code, a plugin or library it does not pin). Then the honest answer is
     * Gradle — and if Gradle is not installed, `needsSetup`, not a misleading custom build.
     */
    private fun gradleRequired(project: BuildSetup.Project, setup: BuildSetup): Boolean =
        runCatching {
            ProjectCheck.check(project.directory, project.id, setup.checkInput)
                .findings
                .any { it.engineOnly && it.severity == ProjectCheck.Severity.Error }
        }.getOrDefault(false)

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
        val projectSettings = com.closecode.build.ProjectSettings.read(resolved.directory)
        val gradleNeeded = setup.gradle == null && gradleRequired(resolved, setup)
        val useGradle = chooseGradle(
            arguments,
            setup.gradle != null || gradleNeeded,
            projectSettings.engine,
        )
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
            if (useGradle && setup.gradle == null) {
                addProperty("command", "closecode setup --gradle")
            } else if (!useGradle && setup.missing.isNotEmpty()) {
                addProperty("command", "closecode setup --all")
            }
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
        if (BuildCoordinator.active() != null) {
            return error("A build is running; cancel it before cleaning caches.")
        }
        val flags = args(request)
        val gradleCaches = flags.any { it == "--gradle-caches" || it == "--gradle" }
        val sdk = flags.any { it == "--sdk" }
        if (gradleCaches || sdk) {
            val targets = mutableListOf<File>()
            if (gradleCaches) {
                val gradleHome = com.closecode.tools.GradleToolsStore(context).gradleHome
                targets += listOf("caches", "daemon", "native", ".tmp")
                    .map { File(gradleHome, it) }
                targets += File(context.cacheDir, "build-cache")
            }
            if (sdk) {
                targets += com.closecode.build.PlatformStore(
                    context,
                    com.closecode.ui.settings.SharedPreferencesKeyValueStore(context),
                ).rootDir()
            }
            val existing = targets.filter { it.exists() }
            val before = existing.sumOf { sizeOf(it) }
            var removed = true
            existing.forEach { file ->
                if (!file.deleteRecursively()) removed = false
            }
            return JsonObject().apply {
                addProperty("ok", removed)
                addProperty("removedBytes", before)
                add("paths", com.google.gson.JsonArray().apply {
                    existing.forEach { add(it.absolutePath) }
                })
                if (!removed) addProperty("error", "Some cache files could not be removed.")
            }
        }
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        val settings = ProjectSettings.read(project.directory)
        val detection = ProjectTypeDetector.detect(project.directory, settings)
        if (detection.primary != ProjectTypeDetector.GRADLE_ANDROID) {
            return EcosystemCommand.run(
                context,
                project.directory,
                settings,
                "clean",
                emptyList(),
            ) { }.json
        }
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

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

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
                addProperty("command", "closecode setup --gradle")
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
            val diagnostics = GradleDiagnostics.parse(result.output)
            val failure = if (diagnostics.any { it.kind == CompileDriver.Diagnostic.Kind.Error }) {
                BuildOutcome.CompileFailed(
                    diagnostics,
                    result.code,
                    result.durationMs,
                    result.logPath,
                )
            } else {
                BuildOutcome.Failed(
                    GradleRunner.failureMessage(result),
                    diagnostics,
                    result.code,
                    result.durationMs,
                    result.logPath,
                )
            }
            send(output, BuildReport.outcome(failure).apply {
                addProperty("ok", false)
                addProperty("error", BuildReport.summary(failure))
                addProperty("task", task)
            })
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
            addProperty("durationMs", result.durationMs)
            result.logPath?.let { addProperty("logPath", it) }
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

    private fun serve(context: Context, request: JsonObject, output: OutputStream) {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (project == null) {
            send(output, error("Run closecode from a registered project folder."))
            return
        }
        val settings = ProjectSettings.read(project.directory)
        val options = ServeCommand.parse(args(request), settings)
        val response = runCatching {
            ServeCommand.run(context, project.directory, settings, options) { event ->
                send(output, event)
            }
        }.getOrElse { failure ->
            CommandEnvelope.failed(
                failure.message ?: "The server command failed.",
                code = "serve_failed",
                remedy = "Check the job log with closecode jobs logs <id>.",
            )
        }
        send(output, response)
    }

    private suspend fun preview(context: Context, request: JsonObject, output: OutputStream) {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        if (project == null) {
            send(output, error("Run closecode from a registered project folder."))
            return
        }
        val settings = ProjectSettings.read(project.directory)
        val options = PreviewCommand.parse(args(request))
        val response = runCatching {
            PreviewCommand.run(context, project.directory, settings, options) { event ->
                send(output, event)
            }
        }.getOrElse { failure ->
            CommandEnvelope.failed(
                failure.message ?: "The preview failed.",
                code = "preview_failed",
                remedy = "Check the build log, then retry closecode preview.",
            )
        }
        send(output, response)
    }

    private fun jobs(context: Context, request: JsonObject): JsonObject {
        val arguments = args(request)
        val action = arguments.firstOrNull()?.lowercase() ?: "list"
        return when (action) {
            "list", "ls", "status" -> JsonObject().apply {
                addProperty("ok", true)
                addProperty("count", JobManager.list(context).size)
                add("jobs", com.google.gson.JsonArray().apply {
                    JobManager.list(context).forEach { add(it.json()) }
                })
            }
            "stop", "kill" -> {
                val id = arguments.getOrNull(1) ?: return CommandEnvelope.failed(
                    "usage: closecode jobs stop <id>",
                    code = "usage",
                )
                val stopped = JobManager.stop(context, id)
                if (stopped == null) {
                    CommandEnvelope.failed(
                        "No job with id '$id'.",
                        code = "not_found",
                        remedy = "Run closecode jobs list.",
                    )
                } else {
                    JsonObject().apply {
                        addProperty("ok", true)
                        add("job", stopped.json())
                    }
                }
            }
            "stop-all", "stopall" -> JsonObject().apply {
                addProperty("ok", true)
                add("stopped", JobManager.stopAll(context))
            }
            "logs", "log" -> {
                val id = arguments.getOrNull(1) ?: return CommandEnvelope.failed(
                    "usage: closecode jobs logs <id> [lines]",
                    code = "usage",
                )
                val lines = JobManager.logs(context, id, arguments.getOrNull(2)?.toIntOrNull() ?: 80)
                    ?: return CommandEnvelope.failed(
                        "No job with id '$id'.",
                        code = "not_found",
                    )
                JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("id", id)
                    add("lines", com.google.gson.JsonArray().apply { lines.forEach { add(it) } })
                }
            }
            else -> CommandEnvelope.failed(
                "usage: closecode jobs list|stop <id>|logs <id>|stop-all",
                code = "usage",
            )
        }
    }

    private fun init(context: Context, request: JsonObject): JsonObject =
        InitCommand.run(context, InitCommand.parse(args(request)))

    private fun config(context: Context, request: JsonObject): JsonObject {
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        ) ?: return error("Run closecode from a registered project folder.")
        return ConfigCommand.run(project.directory, args(request))
    }

    private suspend fun ecosystem(
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
        val settings = ProjectSettings.read(project.directory)
        val detection = ProjectTypeDetector.detect(project.directory, settings)
        if (detection.primary == ProjectTypeDetector.GRADLE_ANDROID) {
            val gradleTask = when (task) {
                "test" -> "testDebugUnitTest"
                "lint" -> "lintDebug"
                "format" -> {
                    val buildText = listOf("build.gradle.kts", "build.gradle", "app/build.gradle.kts", "app/build.gradle")
                        .joinToString("\n") { name ->
                            runCatching { File(project.directory, name).readText() }.getOrDefault("")
                        }
                    when {
                        buildText.contains("spotless") -> "spotlessApply"
                        buildText.contains("ktlint") -> "ktlintFormat"
                        else -> {
                            send(output, CommandEnvelope.failed(
                                "This Gradle project has no formatter configured.",
                                code = "no_formatter",
                                remedy = "Add Spotless or ktlint to the build, then retry.",
                            ))
                            return
                        }
                    }
                }
                "package", "release" -> "assembleRelease"
                else -> task
            }
            fullGradle(context, request, output, gradleTask)
            return
        }
        if (task == "clean" && detection.primary == ProjectTypeDetector.UNKNOWN) {
            send(output, CommandEnvelope.failed(
                "This project type cannot be cleaned automatically.",
                code = "unknown_type",
                remedy = "Set a type with closecode config set type <type>, then retry.",
            ))
            return
        }
        val result = runCatching {
            EcosystemCommand.run(
                context,
                project.directory,
                settings,
                task,
                args(request).filterNot { it == "--json" || it == task },
            ) { event -> send(output, event) }
        }.getOrElse { failure ->
            EcosystemCommand.Result(
                CommandEnvelope.failed(
                    failure.message ?: "The command failed.",
                    code = "command_failed",
                ),
                1,
            )
        }
        send(output, result.json)
    }

    private fun status(context: Context, request: JsonObject): JsonObject {
        val active = BuildCoordinator.active()
        val last = BuildCoordinator.last()
        val project = resolveProject(
            context,
            cwd = string(request, "cwd"),
            slug = string(request, "project"),
        )
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("accessibility", CloseCodeAccessibilityService.connected())
            if (project != null) {
                val settings = ProjectSettings.read(project.directory)
                add("projectType", ProjectTypeDetector.detect(project.directory, settings).json())
                add("settings", settings.json())
            }
            add("jobs", com.google.gson.JsonArray().apply {
                JobManager.list(context).filter { it.status == "running" }.forEach { add(it.json()) }
            })
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
        addProperty("code", "failed")
    }

    /** Every response carries the same keys, so the agent never has to parse an adapter shape. */
    private fun normalized(json: JsonObject): JsonObject {
        if (!json.has("ok")) return json
        if (!json.has("artifacts")) json.add("artifacts", com.google.gson.JsonArray())
        if (!json.has("errors")) json.add("errors", com.google.gson.JsonArray())
        if (!json.has("warnings")) json.add("warnings", com.google.gson.JsonArray())
        if (!json.has("exitCode")) {
            json.addProperty("exitCode", if (json.get("ok")?.asBoolean == true) 0 else 1)
        }
        if (!json.has("durationMs")) json.addProperty("durationMs", 0)
        if (json.get("ok")?.asBoolean == false && !json.has("code")) {
            json.addProperty("code", "failed")
        }
        return json
    }

    private fun send(output: OutputStream, json: JsonObject): Boolean = synchronized(output) {
        runCatching {
            val envelope = normalized(json)
            output.write((envelope.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
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
    DATA=${'$'}(dirname "${'$'}(dirname "${'$'}BASE")")
    FILES="${'$'}DATA/files"
    SHELL_TOOLS="${'$'}DATA/file/u"
    GRADLE_PREFIX="${'$'}DATA/file/g"
    export TMPDIR="${'$'}FILES/tmp"
    mkdir -p "${'$'}TMPDIR" 2>/dev/null
    if [ -x "${'$'}SHELL_TOOLS/bin/sh" ]; then
      export PREFIX="${'$'}SHELL_TOOLS"
      export TERMUX_PREFIX="${'$'}SHELL_TOOLS"
      export PATH="${'$'}SHELL_TOOLS/bin:${'$'}HOME/.local/bin:${'$'}PATH"
      export LD_LIBRARY_PATH="${'$'}SHELL_TOOLS/lib${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"
      export TMPDIR="${'$'}SHELL_TOOLS/tmp"
      export npm_config_prefix="${'$'}SHELL_TOOLS"
      export NODE_PATH="${'$'}SHELL_TOOLS/lib/node_modules"
      export PIP_CACHE_DIR="${'$'}SHELL_TOOLS/cache/pip"
      export PYTHONUSERBASE="${'$'}HOME/.local"
      export SHELL="${'$'}SHELL_TOOLS/bin/sh"
      mkdir -p "${'$'}TMPDIR" 2>/dev/null
      if [ -f "${'$'}SHELL_TOOLS/etc/tls/cert.pem" ]; then
        export SSL_CERT_FILE="${'$'}SHELL_TOOLS/etc/tls/cert.pem"
        export CURL_CA_BUNDLE="${'$'}SHELL_TOOLS/etc/tls/cert.pem"
        export GIT_SSL_CAINFO="${'$'}SHELL_TOOLS/etc/tls/cert.pem"
        export NODE_EXTRA_CA_CERTS="${'$'}SHELL_TOOLS/etc/tls/cert.pem"
      fi
    fi
    JAVA_HOME="${'$'}GRADLE_PREFIX/lib/jvm/java-17-openjdk"
    if [ ! -x "${'$'}JAVA_HOME/bin/java" ]; then
      JAVA_HOME="${'$'}SHELL_TOOLS/lib/jvm/java-17-openjdk"
    fi
    if [ -x "${'$'}JAVA_HOME/bin/java" ]; then
      export JAVA_HOME
      export PATH="${'$'}JAVA_HOME/bin:${'$'}PATH"
    else
      unset JAVA_HOME
    fi
    for GRADLE_DIR in "${'$'}FILES"/gradle/gradle-*; do
      if [ -x "${'$'}GRADLE_DIR/bin/gradle" ]; then
        export PATH="${'$'}GRADLE_DIR/bin:${'$'}PATH"
        break
      fi
    done
    if [ -d "${'$'}FILES/sdk" ]; then
      export ANDROID_HOME="${'$'}FILES/sdk"
      export ANDROID_SDK_ROOT="${'$'}FILES/sdk"
    fi
    if [ -d "${'$'}FILES/gradle/home" ]; then
      export GRADLE_USER_HOME="${'$'}FILES/gradle/home"
    fi
    help() {
      case "${'$'}{1:-}" in
        build|b) echo "usage: closecode build [--gradle] [--release]" ;;
        run|r) echo "usage: closecode run [--gradle] [--release]" ;;
        clean) echo "usage: closecode clean [--gradle-caches|--sdk]" ;;
        serve) echo "usage: closecode serve [--port N] [--host H] [--json] | serve status|stop" ;;
        preview) echo "usage: closecode preview [--port N] [--no-open] [--screenshot] [--dom] | preview status|stop" ;;
        jobs) echo "usage: closecode jobs list|stop <id>|logs <id>|stop-all" ;;
        init) echo "usage: closecode init --template android-compose|web-vite|web-static|python-fastapi|python-cli|node-express [name]" ;;
        config) echo "usage: closecode config show|set <key> <value>|unset <key>" ;;
        test) echo "usage: closecode test — run the project's tests" ;;
        lint) echo "usage: closecode lint — run the project's linter" ;;
        format) echo "usage: closecode format — format the project's sources" ;;
        package) echo "usage: closecode package — build a distributable artifact" ;;
        release) echo "usage: closecode release — build a release artifact" ;;
        status|s) echo "usage: closecode status" ;;
        cancel|c) echo "usage: closecode cancel" ;;
        ping|p) echo "usage: closecode ping" ;;
        install|i) echo "usage: closecode install [apk-or-package]" ;;
        launch|l) echo "usage: closecode launch [package]" ;;
        logcat|log) echo "usage: closecode logcat [package] [lines]" ;;
        export|x) echo "usage: closecode export [name]" ;;
        screenshot|shot) echo "usage: closecode screenshot [name]" ;;
        input|in) echo "usage: closecode input tap|x|y|text|key|swipe|back|home|recents ..." ;;
        accessibility|a) echo "usage: closecode accessibility [status|enable]" ;;
        describe|d) echo "usage: closecode describe" ;;
        tool|t) echo "usage: closecode tool aapt2|d8|r8|kotlinc [args...]" ;;
        tools) echo "usage: closecode tools [status|install|remove]" ;;
        setup) echo "usage: closecode setup --gradle|--web|--python|--native|--all|--remove <component>" ;;
        doctor|doc) echo "usage: closecode doctor [--json]" ;;
        toolchain) echo "usage: closecode toolchain status [--json]" ;;
        signing|sign) echo "usage: closecode signing status|generate|import|select|delete" ;;
        gradle|g) echo "usage: closecode gradle <task> [--gradle]" ;;
        check|chk) echo "usage: closecode check" ;;
        github|gh) echo "usage: closecode github push|pull [--force]" ;;
        app|app-status) echo "usage: closecode app [package]" ;;
        apps) echo "usage: closecode apps list|launch <pkg>|stop <pkg>|info <pkg>|install <apk>|uninstall <pkg> --yes|clear <pkg>" ;;
        intent) echo "usage: closecode intent open <uri>|start <pkg/activity>|broadcast <action>|share <file>" ;;
        device) echo "usage: closecode device battery|network|storage|display|volume [n]|brightness [n]|dnd|clipboard [text]|sensors|orientation|all" ;;
        files) echo "usage: closecode files list|read|write|delete" ;;
        exec) echo "usage: closecode exec [--cwd DIR] [--timeout MS] [--approve] <command...>" ;;
        consent) echo "usage: closecode consent status|grant|revoke <capability>" ;;
        audit) echo "usage: closecode audit tail [lines]" ;;
        stop-all) echo "usage: closecode stop-all" ;;
        stop) echo "usage: closecode stop [package]" ;;
        restart) echo "usage: closecode restart [package]" ;;
        ui) echo "usage: closecode ui [dump|tap|text|...]" ;;
        *)
          echo "usage: closecode <command> [args...]"
          echo "commands: build status cancel ping install launch logcat export screenshot input accessibility describe tool tools setup doctor toolchain signing gradle check github run app stop restart ui apps intent device files exec consent audit stop-all clean serve preview jobs init config test lint format package release"
          echo "Run 'closecode help <command>' for details."
          ;;
      esac
    }
    case "${'$'}{1:-}" in
      help) help "${'$'}{2:-}"; exit 0 ;;
      --help|-h) help; exit 0 ;;
    esac
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
      apps) CMD=apps ;;
      intent|int) CMD=intent ;;
      device|dev) CMD=device ;;
      files|fs) CMD=files ;;
      exec|sh) CMD=exec ;;
      consent|grants) CMD=consent ;;
      audit) CMD=audit ;;
      stop-all|stopall) CMD=stop-all ;;
      log|logcat) CMD=logcat ;;
      x|export) CMD=export ;;
      shot|screenshot) CMD=screenshot ;;
      in|input) CMD=input ;;
      a|accessibility) CMD=accessibility ;;
      d|describe) CMD=describe ;;
      t|tool) CMD=tool ;;
      tools) CMD=tools ;;
      setup) CMD=setup ;;
      doctor|doc) CMD=doctor ;;
      toolchain) CMD=toolchain ;;
      signing|sign) CMD=signing ;;
      g|gradle) CMD=gradle ;;
      chk|check) CMD=check ;;
      gh|github) CMD=github ;;
      sv|serve) CMD=serve ;;
      pv|preview) CMD=preview ;;
      job|jobs) CMD=jobs ;;
      init) CMD=init ;;
      cfg|config) CMD=config ;;
      fmt|format) CMD=format ;;
      pkg|package) CMD=package ;;
      rel|release) CMD=release ;;
    esac
    case "${'$'}CMD" in
      build|status|cancel|ping|install|launch|logcat|export|screenshot|input|accessibility|describe|tool|tools|setup|doctor|doc|toolchain|signing|sign|gradle|check|github|run|app|stop|restart|ui|apps|intent|device|files|exec|consent|audit|stop-all|clean|serve|preview|jobs|init|config|test|lint|format|package|release) ;;
      *) help >&2; exit 2 ;;
    esac
    case "${'$'}{2:-}" in
      help|--help|-h) help "${'$'}CMD"; exit 0 ;;
    esac
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
