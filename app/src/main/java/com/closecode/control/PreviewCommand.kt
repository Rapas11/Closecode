package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectSettings
import com.closecode.build.ProjectTypeDetector
import android.content.Intent
import com.closecode.device.WebPreviewActivity
import com.closecode.jobs.JobInfo
import com.closecode.jobs.JobManager
import com.closecode.ui.ide.openOpenCodeInBrowser
import com.closecode.ui.project.PreviewServer
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

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
        val test: String? = null,
        val eval: String? = null,
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
                "--test", "--script" -> {
                    options = options.copy(test = arguments.getOrNull(index + 1))
                    index++
                }
                "--eval" -> {
                    options = options.copy(eval = arguments.getOrNull(index + 1))
                    index++
                }
                "--port" -> {
                    arguments.getOrNull(index + 1)?.toIntOrNull()?.let {
                        options = options.copy(port = it)
                    }
                    index++
                }
                else -> when {
                    argument.startsWith("--port=") -> argument.substringAfter('=').toIntOrNull()
                        ?.let { options = options.copy(port = it) }
                    argument.startsWith("--test=") -> options = options.copy(test = argument.substringAfter('='))
                    argument.startsWith("--eval=") -> options = options.copy(eval = argument.substringAfter('='))
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
        val script = when {
            options.test != null -> runCatching {
                val file = File(options.test).takeIf { it.isAbsolute }
                    ?: File(projectDir, options.test)
                file.readText()
            }.getOrNull()
            options.eval != null -> options.eval
            options.dom -> "return document.documentElement.outerHTML;"
            else -> null
        }
        if (options.test != null && script == null) {
            JobManager.stop(context, job.id)
            return CommandEnvelope.failed(
                "Test script not found: ${options.test}",
                code = "not_found",
            )
        }
        val wantsHarness = script != null || options.screenshot || options.open
        val outputDir = File(File(context.filesDir, "preview"), UUID.randomUUID().toString())
        outputDir.mkdirs()
        if (wantsHarness) {
            val intent = Intent(context, WebPreviewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WebPreviewActivity.EXTRA_URL, url)
                .putExtra(WebPreviewActivity.EXTRA_OUTPUT, outputDir.absolutePath)
                .putExtra(WebPreviewActivity.EXTRA_SCREENSHOT, options.screenshot)
                .putExtra(WebPreviewActivity.EXTRA_FINISH, script != null || options.screenshot)
            script?.let { intent.putExtra(WebPreviewActivity.EXTRA_SCRIPT, it) }
            val opened = runCatching { context.startActivity(intent) }.isSuccess
            if (!opened) {
                openOpenCodeInBrowser(context, url)
            }
            if (!opened && (script != null || options.screenshot)) {
                JobManager.stop(context, job.id)
                return CommandEnvelope.failed(
                    "Could not open the in-app preview. Open CloseCode on screen and retry.",
                    code = "preview_failed",
                )
            }
        }
        val result = if (script != null || options.screenshot) {
            waitForHarness(outputDir, 30_000, options.screenshot)
        } else {
            null
        }
        val harnessError = result?.string("error")
        val testValue = result?.get("value")
        val failed = result?.boolean("ok") == false
        val dom = if (options.dom) {
            testValue?.let { runCatching { it.asString }.getOrNull() ?: it.toString() }
        } else {
            null
        }
        val testJson = if (options.test != null && testValue != null) {
            runCatching {
                if (testValue.isJsonPrimitive) {
                    com.google.gson.JsonParser.parseString(testValue.asString)
                } else {
                    testValue
                }
            }.getOrNull()
        } else {
            null
        }
        val screenshotFile = File(outputDir, "screenshot.png").takeIf { it.isFile }
        val envelope = if (failed || harnessError != null) {
            CommandEnvelope.failed(
                harnessError ?: "The browser test failed.",
                code = "preview_test_failed",
                remedy = "Read the test result, fix the page or script, then retry.",
            )
        } else {
            CommandEnvelope.of(ok = true)
        }
        return envelope.apply {
            addProperty("id", job.id)
            addProperty("kind", job.kind)
            addProperty("url", url)
            addProperty("localUrl", url)
            addProperty("logPath", job.logPath)
            addProperty("artifact", root.absolutePath)
            add("artifacts", JsonArray().apply {
                add(root.absolutePath)
                screenshotFile?.let { add(it.absolutePath) }
            })
            addProperty("durationMs", 0)
            testJson?.let { add("test", it) }
            dom?.let { addProperty("dom", it) }
            screenshotFile?.let {
                addProperty("screenshot", it.absolutePath)
                add("warnings", JsonArray())
            }
            addProperty("mode", "webview")
        }
    }

    private fun JsonObject.string(key: String): String? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull()

    private fun JsonObject.boolean(key: String): Boolean? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean
    }.getOrNull()

    private fun waitForHarness(directory: File, timeoutMs: Long, screenshot: Boolean): JsonObject? {
        val result = File(directory, "result.json")
        val shot = File(directory, "screenshot.png")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (result.isFile) return runCatching {
                com.google.gson.JsonParser.parseString(result.readText()).asJsonObject
            }.getOrNull()
            if (screenshot && shot.isFile) return JsonObject()
            Thread.sleep(200)
        }
        return null
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

}
