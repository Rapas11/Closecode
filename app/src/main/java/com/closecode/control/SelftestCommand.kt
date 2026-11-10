package com.closecode.control

import android.content.Context
import com.closecode.build.ProjectTypeDetector
import com.closecode.tools.ShellEnvironment
import com.closecode.ui.project.ProjectRepository
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `closecode selftest` scaffolds every template and checks the files and detection signals
 * a build depends on. It works offline and removes the test projects afterwards.
 */
internal object SelftestCommand {

    private data class Case(val template: String, val expected: List<String>, val type: String)

    private val cases = listOf(
        Case("android-compose", listOf("app/src/main/AndroidManifest.xml", "build.gradle.kts", "app/build.gradle.kts"), ProjectTypeDetector.GRADLE_ANDROID),
        Case("web-vite", listOf("package.json", "index.html", "src/main.js"), ProjectTypeDetector.NPM),
        Case("web-static", listOf("index.html"), ProjectTypeDetector.STATIC_HTML),
        Case("python-fastapi", listOf("main.py", "requirements.txt"), ProjectTypeDetector.PYTHON),
        Case("python-cli", listOf("main.py"), ProjectTypeDetector.PYTHON),
        Case("node-express", listOf("package.json", "server.js"), ProjectTypeDetector.NPM),
    )

    fun run(context: Context, arguments: List<String>): JsonObject {
        val repository = ProjectRepository(context)
        val keep = arguments.contains("--keep")
        val build = arguments.contains("--build") || arguments.contains("--full")
        val checks = JsonArray()
        val created = mutableListOf<String>()
        try {
            cases.forEach { case ->
                val name = "selftest-${case.template}-${System.nanoTime()}"
                val createdProject = InitCommand.run(
                    context,
                    InitCommand.Options(
                        template = case.template,
                        name = name,
                        install = build && (case.template == "web-vite" || case.template == "node-express"),
                    ),
                )
                if (createdProject.get("ok")?.asBoolean != true) {
                    checks.add(check(case.template, false, createdProject.get("error")?.asString ?: "scaffold failed"))
                    return@forEach
                }
                val slug = createdProject.get("slug")?.asString.orEmpty()
                created += slug
                val directory = repository.projectDir(slug)
                val missing = case.expected.filterNot { File(directory, it).isFile }
                val detection = ProjectTypeDetector.detect(directory)
                val typeOk = detection.types.contains(case.type)
                val ok = missing.isEmpty() && typeOk
                checks.add(check(
                    case.template,
                    ok,
                    when {
                        missing.isNotEmpty() -> "missing: ${missing.joinToString()}"
                        !typeOk -> "detected ${detection.primary} instead of ${case.type}"
                        else -> "scaffold and detection agree"
                    },
                ).apply {
                    addProperty("directory", directory.absolutePath)
                    add("detection", detection.json())
                })
                if (build && ok) checks.add(buildCheck(context, directory, case.template))
            }
            checks.add(structuralChecks(context))
            val ok = checks.all { it.asJsonObject.get("ok")?.asBoolean == true }
            return CommandEnvelope.of(ok = ok).apply {
                add("checks", checks)
                addProperty("fullBuild", build)
                if (!ok) {
                    addProperty("remedy", "Read the failing check; closecode setup installs the missing toolchain.")
                }
            }
        } finally {
            if (!keep) created.forEach { runCatching { repository.delete(it) } }
        }
    }

    private fun buildCheck(context: Context, directory: File, template: String): JsonObject {
        val command = when (template) {
            "web-vite" -> listOf("npm", "run", "build")
            "node-express" -> listOf("node", "--check", "server.js")
            "python-fastapi", "python-cli" -> listOf("python3", "-m", "compileall", "-q", ".")
            "android-compose" -> null
            else -> null
        }
        if (command == null) {
            return check("$template-build", true, "no build step for this template").apply {
                addProperty("skipped", true)
                if (template == "android-compose") {
                    addProperty("note", "Build Android templates with closecode build on the phone.")
                }
            }
        }
        val builder = ProcessBuilder(command)
            .directory(directory)
            .redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(ShellEnvironment.env(context, directory))
        return runCatching {
            val process = builder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(15, TimeUnit.MINUTES)
            if (!finished) process.destroyForcibly()
            val code = runCatching { process.exitValue() }.getOrDefault(124)
            check(
                "$template-build",
                code == 0,
                if (code == 0) "built successfully" else output.takeLast(1_500),
            ).apply { addProperty("exitCode", code) }
        }.getOrElse {
            check("$template-build", false, "could not start ${command.first()}: ${it.message}")
        }
    }

    private fun structuralChecks(context: Context): JsonObject {
        val root = File(context.cacheDir, "selftest-structure-${System.nanoTime()}").apply { mkdirs() }
        return try {
            File(root, "CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.22)\nproject(selftest CXX)\n")
            File(root, "main.cpp").writeText("int main() { return 0; }\n")
            File(root, "libs/arm64-v8a").mkdirs()
            File(root, "libs/arm64-v8a/libselftest.so").writeBytes(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
            File(root, "build.gradle.kts").writeText(
                "plugins { id(\"com.android.application\") }\nandroid { }\n",
            )
            val cmake = ProjectTypeDetector.detect(root)
            val prebuilt = File(root, "libs/arm64-v8a/libselftest.so").isFile
            val ok = cmake.types.contains(ProjectTypeDetector.CMAKE) && prebuilt
            check("cmake-prebuilt-so", ok, if (ok) "CMake and a prebuilt .so are detected" else "CMake or prebuilt .so missing")
        } finally {
            root.deleteRecursively()
        }
    }

    private fun check(name: String, ok: Boolean, detail: String): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("ok", ok)
        addProperty("detail", detail)
    }
}
