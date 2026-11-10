package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectSettings
import com.closecode.build.ProjectTypeDetector
import com.closecode.device.DeviceTools
import com.closecode.jobs.JobInfo
import com.closecode.jobs.JobManager
import com.closecode.ui.ide.openOpenCodeInBrowser
import com.closecode.ui.project.PreviewServer
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * The website equivalent of `closecode run`: build the static output, serve it on loopback,
 * hand the page to the browser, and optionally capture a screenshot or the served HTML.
 */
internal object PreviewCommand {

    data class Options(
        val port: Int = 0,
        val open: Boolean = true,
        val screenshot: Boolean = false,
        val dom: Boolean = false,
        val stop: Boolean = false,
        val status: Boolean = false,
    )

    fun parse(arguments: List<String>): Options {
        var options = Options()
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "stop" -> options = options.copy(stop = true)
                "status" -> options = options.copy(status = true)
                "--no-open" -> options = options.copy(open = false)
                "--open" -> options = options.copy(open = true)
                "--screenshot", "--shot" -> options = options.copy(screenshot = true)
                "--dom", "--html" -> options = options.copy(dom = true)
                "--port" -> {
                    arguments.getOrNull(index + 1)?.toIntOrNull()?.let {
                        options = options.copy(port = it)
                    }
                    index++
                }
                else -> when {
                    argument.startsWith("--port=") -> argument.substringAfter('=').toIntOrNull()
                        ?.let { options = options.copy(port = it) }
                }
            }
            index++
        }
        return options
    }

    fun run(
        context: Context,
        projectDir: File,
        settings: ProjectSettings,
        options: Options,
        onEvent: (JsonObject) -> Unit,
    ): JsonObject {
        if (options.status) {
            return JsonObject().apply {
                addProperty("ok", true)
                add("jobs", JsonArray().apply {
                    jobs(context, projectDir).forEach { add(it.json()) }
                })
            }
        }
        if (options.stop) {
            val job = jobs(context, projectDir).firstOrNull { it.status == "running" }
                ?: return CommandEnvelope.failed(
                    "No preview is running for this project.",
                    code = "not_running",
                )
            JobManager.stop(context, job.id)
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("stopped", job.id)
            }
        }
        val detection = ProjectTypeDetector.detect(projectDir, settings)
        if (detection.primary == ProjectTypeDetector.NPM) {
            val build = EcosystemCommand.run(
                context,
                projectDir,
                settings,
                "release",
                emptyList(),
                onEvent,
            )
            if (build.exitCode != 0) {
                return build.json.apply {
                    addProperty("stage", "build")
                }
            }
        } else if (detection.primary == ProjectTypeDetector.GRADLE_ANDROID ||
            detection.primary == ProjectTypeDetector.PYTHON
        ) {
            return CommandEnvelope.failed(
                "closecode preview serves a built static site; this is a ${detection.primary} project.",
                code = "not_static",
                remedy = "Use closecode run for Android or closecode serve for a server project.",
            )
        }
        val root = outputRoot(projectDir)
            ?: return CommandEnvelope.failed(
                "No built index.html was found.",
                code = "no_output",
                remedy = "Run the project's build first, or add index.html.",
            )
        val running = jobs(context, projectDir).firstOrNull { it.status == "running" }
        if (running != null) JobManager.stop(context, running.id)
        val server = PreviewServer(root, "127.0.0.1", options.port.coerceAtLeast(0))
        val port = runCatching { server.start() }.getOrElse { error ->
            return CommandEnvelope.failed(
                "Could not start the preview server: ${error.message}",
                code = "preview_failed",
                remedy = "Run closecode preview --port ${(options.port.coerceAtLeast(0) + 1).coerceAtLeast(1)}.",
            )
        }
        val url = "http://127.0.0.1:$port"
        val job = JobManager.startInternal(
            context = context,
            kind = "preview",
            command = "preview ${root.absolutePath} on $url",
            cwd = projectDir,
            port = port,
            url = url,
            stop = { server.stop() },
        )
        if (options.open) {
            runCatching { openOpenCodeInBrowser(context, url) }.onFailure {
                return JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("id", job.id)
                    addProperty("url", url)
                    addProperty("artifact", root.absolutePath)
                    addProperty("note", "The preview is serving, but no browser could be opened.")
                }
            }
        }
        val screenshot = if (options.screenshot) {
            runCatching {
                Thread.sleep(1_500)
                DeviceTools.screenshot(context, projectDir, null)
            }.getOrNull()
        } else {
            null
        }
        val dom = if (options.dom) fetchText(url) else null
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("id", job.id)
            addProperty("kind", job.kind)
            addProperty("url", url)
            addProperty("localUrl", url)
            addProperty("logPath", job.logPath)
            addProperty("artifact", root.absolutePath)
            add("artifacts", JsonArray().apply {
                add(root.absolutePath)
                (screenshot?.get("path")?.takeIf { it.isJsonPrimitive })?.asString?.let { add(it) }
            })
            add("errors", JsonArray())
            add("warnings", JsonArray())
            addProperty("exitCode", 0)
            addProperty("durationMs", 0)
            screenshot?.let { add("screenshot", it) }
            dom?.let { addProperty("dom", it) }
        }
    }

    private fun outputRoot(projectDir: File): File? = listOf("dist", "out", "build", "public", ".")
        .map { File(projectDir, it) }
        .firstOrNull { File(it, "index.html").isFile }

    private fun jobs(context: Context, projectDir: File): List<JobInfo> {
        val canonical = runCatching { projectDir.canonicalPath }.getOrDefault(projectDir.path)
        return JobManager.list(context).filter {
            it.kind == "preview" &&
                runCatching { File(it.cwd).canonicalPath }.getOrDefault(it.cwd) == canonical
        }
    }

    private fun fetchText(url: String): String? = runCatching {
        val port = url.substringAfterLast(':').toInt()
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.getOutputStream().write(
                "GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
                    .toByteArray(StandardCharsets.UTF_8),
            )
            socket.getOutputStream().flush()
            val bytes = socket.getInputStream().readBytes()
            String(bytes, StandardCharsets.UTF_8)
                .substringAfter("\r\n\r\n")
                .take(200_000)
        }
    }.getOrNull()
}
