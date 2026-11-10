package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectSettings
import com.closecode.build.ProjectTypeDetector
import com.closecode.jobs.JobInfo
import com.closecode.jobs.JobManager
import com.closecode.ui.project.PreviewServer
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * `closecode serve` starts the project's own dev server (Vite/Next/Astro/Flask/FastAPI) or a
 * dependency-free static server, owns it as a [JobManager] job, and reports both URLs.
 */
internal object ServeCommand {

    data class Options(
        val action: String = "start",
        val port: Int = 3000,
        val host: String = "127.0.0.1",
        val explicitPort: Boolean = false,
        val explicitHost: Boolean = false,
        val id: String? = null,
    )

    fun parse(arguments: List<String>, settings: ProjectSettings): Options {
        var action = "start"
        var port = settings.ports["serve"] ?: settings.ports["web"] ?: 3000
        var host = "127.0.0.1"
        var explicitPort = false
        var explicitHost = false
        var id: String? = null
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "status" -> action = "status"
                "stop" -> action = "stop"
                "start", "run" -> action = "start"
                "--json" -> Unit
                "--port" -> {
                    arguments.getOrNull(index + 1)?.toIntOrNull()?.let {
                        port = it
                        explicitPort = true
                    }
                    index++
                }
                "--host" -> {
                    arguments.getOrNull(index + 1)?.takeIf { it.isNotBlank() }?.let {
                        host = it
                        explicitHost = true
                    }
                    index++
                }
                "--id" -> {
                    id = arguments.getOrNull(index + 1)
                    index++
                }
                else -> when {
                    argument.startsWith("--port=") -> {
                        argument.substringAfter('=').toIntOrNull()?.let {
                            port = it
                            explicitPort = true
                        }
                    }
                    argument.startsWith("--host=") -> {
                        argument.substringAfter('=').takeIf { it.isNotBlank() }?.let {
                            host = it
                            explicitHost = true
                        }
                    }
                    argument.startsWith("--id=") -> id = argument.substringAfter('=')
                }
            }
            index++
        }
        return Options(action, port, host, explicitPort, explicitHost, id)
    }

    fun run(
        context: Context,
        projectDir: File,
        settings: ProjectSettings,
        options: Options,
        onEvent: (JsonObject) -> Unit,
    ): JsonObject {
        when (options.action) {
            "status" -> return status(context, projectDir)
            "stop" -> return stop(context, projectDir, options.id)
        }
        val running = jobs(context, projectDir).firstOrNull { it.status == "running" }
        if (running != null) {
            return JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", "A server is already running for this project.")
                addProperty("id", running.id)
                running.url?.let { addProperty("url", it) }
                addProperty("remedy", "Run closecode serve stop or closecode jobs stop ${running.id}.")
            }
        }
        if (options.port !in 1..65535) {
            return failure("The port must be between 1 and 65535.", "closecode serve --port 3000")
        }
        val detection = ProjectTypeDetector.detect(projectDir, settings)
        if (options.explicitPort && portInUse(options.port)) {
            return failure(
                "Port ${options.port} is already in use.",
                "closecode serve --port ${options.port + 1}",
            )
        }
        return when (detection.primary) {
            ProjectTypeDetector.STATIC_HTML -> startStatic(context, projectDir, detection, options)
            ProjectTypeDetector.NPM -> startNode(context, projectDir, detection, options, onEvent)
            ProjectTypeDetector.PYTHON -> startPython(context, projectDir, detection, options, onEvent)
            ProjectTypeDetector.GRADLE_ANDROID, ProjectTypeDetector.CMAKE ->
                failure(
                    "This project is not a web or server project.",
                    "closecode build && closecode run",
                )
            else -> {
                if (File(projectDir, "index.html").isFile) {
                    startStatic(context, projectDir, detection, options)
                } else {
                    failure(
                        "CloseCode could not tell what to serve.",
                        "Add index.html or a package.json with a dev script, then retry.",
                    )
                }
            }
        }
    }

    private fun startStatic(
        context: Context,
        projectDir: File,
        detection: com.closecode.build.ProjectDetection,
        options: Options,
    ): JsonObject {
        val root = listOf("dist", "public", ".").map { File(projectDir, it) }
            .firstOrNull { File(it, "index.html").isFile }
            ?: return failure(
                "No index.html found to serve.",
                "Create index.html, or run closecode preview after building the site.",
            )
        val port = if (options.explicitPort) options.port else freePort(options.port)
        val server = PreviewServer(root, options.host, port)
        val bound = runCatching { server.start() }.getOrElse {
            return failure(
                "Could not start the static server: ${it.message}",
                "closecode serve --port ${port + 1}",
            )
        }
        val localUrl = "http://127.0.0.1:$bound"
        val lanUrls = lanUrls(options.host, bound)
        val job = JobManager.startInternal(
            context = context,
            kind = "serve",
            command = "static server ${root.absolutePath} on ${options.host}:$bound",
            cwd = projectDir,
            port = bound,
            url = localUrl,
            stop = { server.stop() },
        )
        return success(job, localUrl, lanUrls, root.absolutePath, detection)
    }

    private fun startNode(
        context: Context,
        projectDir: File,
        detection: com.closecode.build.ProjectDetection,
        options: Options,
        onEvent: (JsonObject) -> Unit,
    ): JsonObject {
        val packageJson = runCatching {
            JsonParser.parseString(File(projectDir, "package.json").readText()).asJsonObject
        }.getOrNull() ?: return failure(
            "package.json is not valid JSON.",
            "Fix package.json, then retry.",
        )
        val scripts = runCatching { packageJson.getAsJsonObject("scripts") }.getOrNull()
        val scriptNames = scripts?.keySet().orEmpty()
        val dependencies = linkedSetOf<String>()
        listOf("dependencies", "devDependencies").forEach { name ->
            runCatching {
                dependencies += packageJson.getAsJsonObject(name)?.keySet().orEmpty()
            }
        }
        val manager = when {
            File(projectDir, "pnpm-lock.yaml").isFile -> "pnpm"
            File(projectDir, "yarn.lock").isFile -> "yarn"
            else -> "npm"
        }
        val script = when {
            "dev" in scriptNames -> "dev"
            "start" in scriptNames -> "start"
            "serve" in scriptNames -> "serve"
            else -> null
        }
        if (script == null) {
            if (File(projectDir, "index.html").isFile) {
                return startStatic(context, projectDir, detection, options)
            }
            return failure(
                "package.json has no dev, start or serve script.",
                "Add \"dev\" to scripts, or run closecode init --template node-express.",
            )
        }
        val isNext = dependencies.any { it == "next" }
        val args = if (isNext) {
            listOf("-H", options.host, "-p", options.port.toString())
        } else {
            listOf("--host", options.host, "--port", options.port.toString())
        }
        val command = when (manager) {
            "pnpm" -> listOf("pnpm", "run", script, "--") + args
            "yarn" -> listOf("yarn", script) + args
            else -> if (script == "start") {
                listOf("npm", "start", "--") + args
            } else {
                listOf("npm", "run", script, "--") + args
            }
        }
        val settings = ProjectSettings.read(projectDir)
        return startJob(
            context = context,
            projectDir = projectDir,
            command = command,
            env = settings.env,
            port = options.port,
            host = options.host,
            kind = "serve",
            detection = detection,
            onEvent = onEvent,
            remedy = if (manager == "npm") {
                "Run closecode setup --web, then npm install in the project and retry."
            } else {
                "Install $manager, run $manager install in the project and retry."
            },
        )
    }

    private fun startPython(
        context: Context,
        projectDir: File,
        detection: com.closecode.build.ProjectDetection,
        options: Options,
        onEvent: (JsonObject) -> Unit,
    ): JsonObject {
        val requirements = listOf("requirements.txt", "pyproject.toml", "Pipfile")
            .joinToString("\n") { name ->
                runCatching { File(projectDir, name).readText() }.getOrDefault("")
            }
        val sources = projectDir.listFiles()
            ?.filter { it.isFile && it.extension == "py" }
            ?.joinToString("\n") { it.readText() }
            .orEmpty()
        val hasFastApi = requirements.contains("fastapi", ignoreCase = true) ||
            sources.contains("FastAPI", ignoreCase = true)
        val entry = listOf("main.py", "app.py", "asgi.py", "wsgi.py")
            .map { File(projectDir, it) }
            .firstOrNull { it.isFile }
        val command = when {
            hasFastApi && entry != null -> {
                val module = entry.nameWithoutExtension
                val attribute = if (entry.name == "app.py") "app" else "app"
                listOf(
                    "python3", "-m", "uvicorn", "$module:$attribute",
                    "--host", options.host, "--port", options.port.toString(),
                )
            }
            sources.contains("Flask", ignoreCase = true) -> {
                val module = entry?.nameWithoutExtension ?: "app"
                listOf(
                    "python3", "-m", "flask", "--app", module, "run",
                    "--host", options.host, "--port", options.port.toString(),
                )
            }
            File(projectDir, "index.html").isFile -> {
                listOf(
                    "python3", "-m", "http.server", options.port.toString(),
                    "--bind", options.host,
                )
            }
            else -> return failure(
                "No Python web entry point was found.",
                "Add app.py with a FastAPI app, or run closecode init --template python-fastapi.",
            )
        }
        val settings = ProjectSettings.read(projectDir)
        return startJob(
            context = context,
            projectDir = projectDir,
            command = command,
            env = settings.env,
            port = options.port,
            host = options.host,
            kind = "serve",
            detection = detection,
            onEvent = onEvent,
            remedy = "Run closecode setup --python, then install the app's requirements and retry.",
        )
    }

    private fun startJob(
        context: Context,
        projectDir: File,
        command: List<String>,
        env: Map<String, String>,
        port: Int,
        host: String,
        kind: String,
        detection: com.closecode.build.ProjectDetection,
        onEvent: (JsonObject) -> Unit,
        remedy: String,
    ): JsonObject {
        val job = try {
            JobManager.startProcess(
                context = context,
                kind = kind,
                command = command,
                cwd = projectDir,
                environment = env,
                port = port,
            )
        } catch (error: Throwable) {
            return failure(
                error.message ?: "Could not start the server.",
                remedy,
            )
        }
        onEvent(JsonObject().apply {
            addProperty("ok", true)
            addProperty("event", "started")
            addProperty("id", job.id)
            addProperty("command", job.command)
            addProperty("logPath", job.logPath)
        })
        val listening = waitForPort(port, 25_000) { JobManager.find(context, job.id)?.status }
        val localUrl = "http://127.0.0.1:$port"
        if (!listening) {
            val tail = JobManager.logs(context, job.id, 25).orEmpty()
            val status = JobManager.find(context, job.id)?.status
            JobManager.stop(context, job.id)
            return JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", "The server did not start on port $port.")
                if (status != null) addProperty("status", status)
                add("errors", JsonArray().apply { tail.forEach { add(it) } })
                add("warnings", JsonArray())
                add("artifacts", JsonArray())
                addProperty("code", "serve_failed")
                addProperty("remedy", remedy)
                addProperty("logPath", job.logPath)
            }
        }
        val lanUrls = lanUrls(host, port)
        return success(job, localUrl, lanUrls, job.logPath, detection)
    }

    private fun success(
        job: JobInfo,
        localUrl: String,
        lanUrls: List<String>,
        artifact: String,
        detection: com.closecode.build.ProjectDetection,
    ): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("id", job.id)
        addProperty("kind", job.kind)
        addProperty("command", job.command)
        addProperty("status", job.status)
        addProperty("pid", job.pid)
        addProperty("startedAt", job.startedAt)
        addProperty("logPath", job.logPath)
        addProperty("localUrl", localUrl)
        addProperty("url", localUrl)
        if (lanUrls.isNotEmpty()) addProperty("lanUrl", lanUrls.first())
        add("lanUrls", JsonArray().apply { lanUrls.forEach { add(it) } })
        add("artifacts", JsonArray().apply { add(artifact) })
        add("errors", JsonArray())
        add("warnings", JsonArray())
        addProperty("exitCode", 0)
        addProperty("durationMs", 0)
        add("projectType", detection.json())
    }

    private fun status(context: Context, projectDir: File): JsonObject {
        val active = jobs(context, projectDir)
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("running", active.any { it.status == "running" })
            add("jobs", JsonArray().apply { active.forEach { add(it.json()) } })
        }
    }

    private fun stop(context: Context, projectDir: File, id: String?): JsonObject {
        val active = jobs(context, projectDir)
        val target = if (id != null) active.firstOrNull { it.id == id } else {
            active.firstOrNull { it.status == "running" }
        } ?: return JsonObject().apply {
            addProperty("ok", false)
            addProperty("error", "No server is running for this project.")
            addProperty("code", "not_running")
        }
        val stopped = JobManager.stop(context, target.id)
        return JsonObject().apply {
            addProperty("ok", stopped != null)
            stopped?.let { add("job", it.json()) }
        }
    }

    private fun jobs(context: Context, projectDir: File): List<JobInfo> {
        val canonical = runCatching { projectDir.canonicalPath }.getOrDefault(projectDir.path)
        return JobManager.list(context).filter {
            it.kind == "serve" && runCatching { File(it.cwd).canonicalPath }.getOrDefault(it.cwd) == canonical
        }
    }

    private fun failure(message: String, remedy: String): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", message)
        addProperty("code", "serve_failed")
        addProperty("remedy", remedy)
    }

    private fun portInUse(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
        true
    }.getOrDefault(false)

    private fun waitForPort(port: Int, timeoutMs: Long, status: () -> String?): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (portInUse(port)) return true
            if (status() != "running") return false
            Thread.sleep(250)
        }
        return portInUse(port)
    }

    private fun freePort(preferred: Int): Int {
        if (!portInUse(preferred)) return preferred
        return runCatching { java.net.ServerSocket(0).use { it.localPort } }.getOrDefault(preferred)
    }

    private fun lanUrls(host: String, port: Int): List<String> {
        if (host == "127.0.0.1" || host == "localhost" || host == "::1") return emptyList()
        val addresses = if (host == "0.0.0.0" || host == "::") {
            runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { it.hostAddress }
            }.getOrDefault(emptyList())
        } else {
            listOf(host)
        }
        return addresses.filterNotNull().distinct().map { "http://$it:$port" }
    }
}
