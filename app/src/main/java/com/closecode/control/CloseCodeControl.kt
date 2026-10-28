package com.closecode.control

import android.content.Context
import com.closecode.BuildConfig
import com.closecode.build.BuildCoordinator
import com.closecode.build.BuildReport
import com.closecode.build.BuildSetup
import com.closecode.build.BuildSetupFactory
import com.closecode.build.BuildState
import com.closecode.debug.BuildInfo
import com.closecode.debug.DebugLog
import com.closecode.ui.project.ProjectRepository
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
import kotlinx.coroutines.runBlocking

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
            val supplied = request.get("token")?.asString.orEmpty()
            if (!sameToken(supplied, token)) {
                send(output, error("Unauthorized."))
                return
            }
            when (request.get("command")?.asString.orEmpty()) {
                "ping" -> send(output, ping())
                "status" -> send(output, status(context))
                "cancel" -> send(output, cancel())
                "build" -> {
                    it.soTimeout = 0
                    runBlocking { build(context, request, output) }
                }
                else -> send(output, error("Unknown command."))
            }
        }
    }

    private suspend fun build(
        context: Context,
        request: JsonObject,
        output: OutputStream,
    ) {
        val resolved = resolveProject(
            context,
            cwd = request.get("cwd")?.asString,
            slug = request.get("project")?.asString,
        )
        if (resolved == null) {
            send(output, error("Run closecode from a registered project folder."))
            return
        }
        if (BuildCoordinator.active() != null) {
            send(output, error("A build is already running."))
            return
        }
        val setup = try {
            BuildSetupFactory.create(context, resolved)
        } catch (error: Throwable) {
            send(output, error(error.message ?: error.javaClass.simpleName))
            return
        }
        send(output, JsonObject().apply {
            addProperty("ok", true)
            addProperty("event", "start")
            addProperty("slug", resolved.slug)
            addProperty("project", resolved.name)
            addProperty("build", BuildConfig.VERSION_CODE)
            add("missing", com.google.gson.JsonArray().apply {
                setup.missing.forEach { add(it) }
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
        try {
            val outcome = BuildCoordinator.build(context, resolved, setup, onEvent, onLog)
            send(output, JsonObject().apply {
                addProperty("ok", true)
                addProperty("event", "done")
                addProperty("summary", BuildReport.summary(outcome))
                add("result", BuildReport.outcome(outcome))
                add("logTail", com.google.gson.JsonArray().apply {
                    BuildReport.tail(resolved.directory, 20).forEach { add(it) }
                })
            })
        } catch (error: Throwable) {
            send(output, error(error.message ?: error.javaClass.simpleName))
        }
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
            savedPackage = configured?.packageName?.takeIf { it.isNotBlank() },
            savedMinSdk = configured?.minSdk?.toIntOrNull(),
            savedTargetSdk = configured?.targetSdk?.toIntOrNull(),
            savedCompileSdk = configured?.compileSdk?.toIntOrNull(),
            otherPackages = records
                .filter { it.slug != record.slug }
                .mapNotNull { runCatching { repository.packageOf(it) }.getOrNull() }
                .toSet(),
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
      build|status|cancel|ping) ;;
      *) echo "usage: closecode build|status|cancel|ping" >&2; exit 2 ;;
    esac
    CWD=${'$'}(pwd -P 2>/dev/null || pwd)
    escape() { printf '%s' "${'$'}1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
    REQUEST="{\"token\":\"${'$'}(escape "${'$'}TOKEN")\",\"command\":\"${'$'}CMD\",\"cwd\":\"${'$'}(escape "${'$'}CWD")\"}"
    OUT="${'$'}BASE/control.out.${'$'}${'$'}"
    if printf '%s\n' "${'$'}REQUEST" | /system/bin/nc 127.0.0.1 "${'$'}PORT" > "${'$'}OUT" 2>/dev/null; then
      cat "${'$'}OUT"
      rm -f "${'$'}OUT"
    else
      rm -f "${'$'}OUT"
      echo "closecode: the app did not answer." >&2
      exit 1
    fi
""".trimIndent() + "\n"
