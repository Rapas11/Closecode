package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectDetection
import com.closecode.build.ProjectSettings
import com.closecode.build.ProjectTypeDetector
import com.closecode.tools.ShellEnvironment
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `test`, `lint`, `format`, `package`, `release` and `clean` for every non-Gradle ecosystem.
 * Gradle projects are routed to the build engine, so this file never guesses at Gradle.
 */
internal object EcosystemCommand {

    data class Result(
        val json: JsonObject,
        val exitCode: Int,
    )

    fun run(
        context: Context,
        projectDir: File,
        settings: ProjectSettings,
        task: String,
        arguments: List<String>,
        onEvent: (JsonObject) -> Unit,
    ): Result {
        val detection = ProjectTypeDetector.detect(projectDir, settings)
        if (task == "clean" && detection.primary == ProjectTypeDetector.STATIC_HTML) {
            return cleanStatic(projectDir)
        }
        val command = when (detection.primary) {
            ProjectTypeDetector.NPM -> npmCommand(projectDir, task, arguments)
            ProjectTypeDetector.PYTHON -> pythonCommand(task, arguments)
            ProjectTypeDetector.CMAKE -> cmakeCommand(task, arguments)
            ProjectTypeDetector.STATIC_HTML -> staticCommand(projectDir, task, arguments)
            else -> return Result(
                failure(
                    "This project has no ${detection.primary} task runner for '$task'.",
                    "Add package.json, pyproject.toml or CMakeLists.txt, then retry.",
                ),
                1,
            )
        } ?: return Result(
            failure(
                "No '$task' command is configured for this project.",
                remedyFor(detection.primary, task),
            ),
            1,
        )
        return execute(context, projectDir, settings, task, command, onEvent)
    }

    private fun execute(
        context: Context,
        projectDir: File,
        settings: ProjectSettings,
        task: String,
        command: List<String>,
        onEvent: (JsonObject) -> Unit,
    ): Result {
        val logDir = File(projectDir, "build/logs")
        logDir.mkdirs()
        val log = File(logDir, "$task.log")
        log.writeText("# ${command.joinToString(" ")}\n")
        val builder = ProcessBuilder(command).directory(projectDir).redirectErrorStream(true)
        val env = ShellEnvironment.env(context, projectDir).apply { putAll(settings.env) }
        builder.environment().clear()
        builder.environment().putAll(env)
        val process = try {
            builder.start()
        } catch (error: Throwable) {
            return Result(
                failure(
                    "Could not start ${command.first()}: ${error.message}",
                    "Run closecode setup --web or --python, then retry.",
                    log.absolutePath,
                ),
                127,
            )
        }
        val started = System.currentTimeMillis()
        val output = StringBuilder()
        val writer = log.bufferedWriter()
        val cancelled = AtomicBoolean(false)
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            if (output.length < 400_000) output.appendLine(line)
                        }
                        runCatching { writer.appendLine(line); writer.flush() }
                        onEvent(JsonObject().apply {
                            addProperty("ok", true)
                            addProperty("event", "log")
                            addProperty("text", line)
                        })
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val timedOut = runCatching {
            process.waitFor(30, TimeUnit.MINUTES)
        }.getOrDefault(false).not()
        if (timedOut) {
            cancelled.set(true)
            com.closecode.build.ProcessTree.terminate(process)
        }
        reader.join(5_000)
        runCatching { writer.close() }
        val duration = System.currentTimeMillis() - started
        val code = runCatching { process.exitValue() }.getOrDefault(if (timedOut) 124 else -1)
        val text = synchronized(output) { output.toString() }
        val errors = failureLines(text).ifEmpty {
            if (code == 0) emptyList() else listOf("$task failed with exit code $code.")
        }
        val warnings = text.lineSequence()
            .filter { it.contains("warning", ignoreCase = true) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(20)
            .toList()
        val json = CommandEnvelope.of(
            ok = code == 0 && !timedOut,
            errors = if (code == 0 && !timedOut) emptyList() else errors,
            warnings = warnings,
            exitCode = code,
            durationMs = duration,
            logPath = log.absolutePath,
        ).apply {
            addProperty("task", task)
            addProperty("command", command.joinToString(" "))
            if (timedOut) {
                addProperty("error", "$task did not finish in 30 minutes.")
                addProperty("code", "timeout")
                addProperty("remedy", "Check the log and run a smaller task.")
            } else if (code != 0) {
                addProperty("error", errors.firstOrNull() ?: "$task failed.")
                addProperty("code", "task_failed")
            }
        }
        return Result(json, code)
    }

    private fun npmCommand(projectDir: File, task: String, arguments: List<String>): List<String>? {
        val packageJson = runCatching {
            JsonParser.parseString(File(projectDir, "package.json").readText()).asJsonObject
        }.getOrNull() ?: return null
        val scripts = runCatching { packageJson.getAsJsonObject("scripts") }.getOrNull()
        val has = { name: String -> scripts?.has(name) == true }
        val manager = when {
            File(projectDir, "pnpm-lock.yaml").isFile -> "pnpm"
            File(projectDir, "yarn.lock").isFile -> "yarn"
            else -> "npm"
        }
        val script = when (task) {
            "test" -> listOf("test").firstOrNull(has)
            "lint" -> listOf("lint", "eslint").firstOrNull(has)
            "format" -> listOf("format", "prettier").firstOrNull(has)
            "package" -> listOf("package", "pack", "build").firstOrNull(has)
            "release" -> listOf("release", "build").firstOrNull(has)
            "clean" -> listOf("clean").firstOrNull(has)
            else -> null
        }
        if (script == null) {
            val base = when (task) {
                "test" -> listOf(manager, "test")
                "lint" -> listOf("npx", "eslint", ".")
                "format" -> listOf("npx", "prettier", "--write", ".")
                "package" -> listOf(manager, "pack")
                "release" -> listOf(manager, "run", "build")
                else -> null
            } ?: return null
            return base + arguments
        }
        return when (manager) {
            "pnpm" -> listOf("pnpm", "run", script)
            "yarn" -> listOf("yarn", script) + arguments
            else -> if (script == "test") {
                listOf("npm", "test", "--") + arguments
            } else {
                listOf("npm", "run", script, "--") + arguments
            }
        }
    }

    private fun pythonCommand(task: String, arguments: List<String>): List<String>? = when (task) {
        "test" -> listOf("python3", "-m", "pytest") + arguments
        "lint" -> listOf("python3", "-m", "ruff", "check", ".") + arguments
        "format" -> listOf("python3", "-m", "ruff", "format", ".") + arguments
        "package", "release" -> listOf("python3", "-m", "build") + arguments
        else -> null
    }

    private fun cmakeCommand(task: String, arguments: List<String>): List<String>? = when (task) {
        "test" -> listOf("ctest", "--test-dir", "build", "--output-on-failure") + arguments
        "format" -> listOf("sh", "-c", "find . -name '*.c' -o -name '*.cc' -o -name '*.cpp' | xargs -r clang-format -i")
        "package" -> listOf("cmake", "--build", "build", "--target", "package") + arguments
        "release" -> listOf("cmake", "--build", "build", "--config", "Release") + arguments
        "clean" -> listOf("cmake", "--build", "build", "--target", "clean")
        else -> null
    }

    private fun staticCommand(
        projectDir: File,
        task: String,
        arguments: List<String>,
    ): List<String>? = when (task) {
        "package" -> listOf("python3", "-c", zipScript(projectDir, arguments.firstOrNull()))
        else -> null
    }

    private fun zipScript(projectDir: File, name: String?): String {
        val output = File(projectDir, "dist/${name ?: "site.zip"}")
        val root = projectDir.absolutePath.replace("'", "'\\''")
        val target = output.absolutePath.replace("'", "'\\''")
        return "import os,zipfile;root='$root';target='$target';" +
            "os.makedirs(os.path.dirname(target),exist_ok=True);" +
            "z=zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED);" +
            "[z.write(os.path.join(d,f),os.path.relpath(os.path.join(d,f),root)) " +
            "for d,dirs,files in os.walk(root) if not any(p in d for p in ('/node_modules','/.git','/build','/dist')) " +
            "for f in files if f!='.closecode.json'];z.close();print(target)"
    }

    private fun cleanStatic(projectDir: File): Result {
        val removed = mutableListOf<String>()
        listOf("dist", "build", ".next", "out").forEach { name ->
            val file = File(projectDir, name)
            if (file.exists() && file.deleteRecursively()) removed += file.absolutePath
        }
        return Result(
            CommandEnvelope.of(ok = true).apply {
                addProperty("task", "clean")
                add("artifacts", com.google.gson.JsonArray().apply { removed.forEach { add(it) } })
            },
            0,
        )
    }

    private fun failure(message: String, remedy: String, logPath: String? = null): JsonObject =
        CommandEnvelope.failed(
            message = message,
            code = "tool_missing",
            remedy = remedy,
            installCommand = "closecode setup --web --python",
            logPath = logPath,
        )

    private fun remedyFor(type: String, task: String): String = when (type) {
        ProjectTypeDetector.NPM ->
            "Add a \"$task\" script to package.json, or install the suggested tool."
        ProjectTypeDetector.PYTHON -> "Run closecode setup --python and install pytest/ruff."
        ProjectTypeDetector.CMAKE -> "Run closecode setup --native, then retry."
        ProjectTypeDetector.STATIC_HTML -> "Static sites only support package and clean."
        else -> "Run closecode init to create a supported project."
    }

    private fun failureLines(text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .filter {
            it.contains("error", ignoreCase = true) ||
                it.contains("failed", ignoreCase = true) ||
                it.startsWith("E ") ||
                it.startsWith("FAILED")
        }
        .take(30)
        .toList()
}
