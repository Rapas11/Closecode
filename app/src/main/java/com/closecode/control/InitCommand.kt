package com.closecode.control

import android.content.Context
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.defaultPackageName
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.project.Scaffold
import com.closecode.ui.project.ScaffoldFile
import com.closecode.tools.ShellEnvironment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * `closecode init` creates a project that the on-phone toolchains can build immediately.
 * Android/static web reuse the same scaffolds as the start screen; Node/Python templates are
 * plain files, so no network access happens until the user installs dependencies.
 */
internal object InitCommand {

    val templates = listOf(
        "android-compose",
        "web-vite",
        "web-static",
        "python-fastapi",
        "python-cli",
        "node-express",
    )

    data class Options(
        val template: String?,
        val name: String?,
        val packageName: String? = null,
        val install: Boolean = false,
    )

    fun parse(arguments: List<String>): Options {
        var template: String? = null
        var name: String? = null
        var packageName: String? = null
        var install = false
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "--template", "-t" -> {
                    template = arguments.getOrNull(index + 1)
                    index++
                }
                "--name", "-n" -> {
                    name = arguments.getOrNull(index + 1)
                    index++
                }
                "--package", "-p" -> {
                    packageName = arguments.getOrNull(index + 1)
                    index++
                }
                "--install", "--deps" -> install = true
                else -> when {
                    argument.startsWith("--template=") -> template = argument.substringAfter('=')
                    argument.startsWith("--name=") -> name = argument.substringAfter('=')
                    argument.startsWith("--package=") -> packageName = argument.substringAfter('=')
                    name == null && !argument.startsWith("-") -> name = argument
                }
            }
            index++
        }
        return Options(template, name, packageName, install)
    }

    fun run(context: Context, options: Options): JsonObject {
        val template = options.template?.lowercase()
        if (template == null) {
            return JsonObject().apply {
                addProperty("ok", true)
                add("templates", JsonArray().apply { templates.forEach { add(it) } })
                addProperty("usage", "closecode init --template <name> [project-name]")
            }
        }
        if (template !in templates) {
            return CommandEnvelope.failed(
                "Unknown template '$template'.",
                code = "unknown_template",
                remedy = "Use one of: ${templates.joinToString()}",
            )
        }
        val name = options.name?.takeIf { it.isNotBlank() }
            ?: "CloseCode ${template.replace('-', ' ')}"
        val repository = ProjectRepository(context)
        if (repository.nameTaken(name)) {
            return CommandEnvelope.failed(
                "A project named '$name' already exists.",
                code = "name_taken",
                remedy = "Choose another name: closecode init --template $template \"My App 2\"",
            )
        }
        return runCatching {
            val record = when (template) {
                "android-compose" -> repository.create(
                    name = name,
                    selection = SelectedTemplate(TemplateTab.App, 0),
                    config = ProjectConfig(packageName = options.packageName.orEmpty()),
                )
                "web-static" -> repository.create(
                    name = name,
                    selection = SelectedTemplate(TemplateTab.Web, 0),
                    config = ProjectConfig(packageName = ""),
                )
                else -> {
                    val files = filesFor(template, name)
                    repository.createFromFiles(
                        name = name,
                        tab = if (template.startsWith("web")) TemplateTab.Web else TemplateTab.App,
                        templateName = template,
                        config = ProjectConfig(packageName = options.packageName.orEmpty()),
                        files = files,
                    )
                }
            }
            val directory = repository.projectDir(record.slug)
            val install = if (options.install) installDependencies(context, directory, template) else null
            JsonObject().apply {
                addProperty("ok", true)
                addProperty("id", record.id)
                addProperty("slug", record.slug)
                addProperty("name", record.name)
                addProperty("template", template)
                addProperty("directory", directory.absolutePath)
                add("files", JsonArray().apply {
                    directory.walkTopDown()
                        .filter { it.isFile && it.name != ".project.json" }
                        .map { it.relativeTo(directory).path }
                        .sorted()
                        .forEach { add(it) }
                })
                install?.let { add("install", it) }
                add("next", JsonArray().apply {
                    when (template) {
                        "android-compose" -> add("closecode build --gradle")
                        "web-vite" -> {
                            add("npm install")
                            add("closecode serve")
                        }
                        "node-express" -> {
                            add("npm install")
                            add("closecode serve")
                        }
                        "python-fastapi" -> {
                            add("python3 -m pip install -r requirements.txt")
                            add("closecode serve")
                        }
                        "python-cli" -> add("python3 main.py --help")
                        else -> add("closecode serve")
                    }
                })
            }
        }.getOrElse { error ->
            CommandEnvelope.failed(
                error.message ?: "Could not create the project.",
                code = "init_failed",
                remedy = "Check free storage and try again.",
            )
        }
    }

    private fun installDependencies(context: Context, directory: File, template: String): JsonObject {
        val command = when (template) {
            "web-vite", "node-express" -> listOf("npm", "install")
            "python-fastapi" -> listOf("python3", "-m", "pip", "install", "-r", "requirements.txt")
            else -> return JsonObject().apply {
                addProperty("ok", true)
                addProperty("skipped", true)
                addProperty("reason", "This template has no package dependencies.")
            }
        }
        val builder = ProcessBuilder(command)
            .directory(directory)
            .redirectErrorStream(true)
        builder.environment().clear()
        builder.environment().putAll(ShellEnvironment.env(context, directory))
        return runCatching {
            val process = builder.start()
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(output) {
                                if (output.length < 40_000) output.appendLine(line)
                            }
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val finished = process.waitFor(900, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(3_000)
            val code = runCatching { process.exitValue() }.getOrDefault(124)
            JsonObject().apply {
                addProperty("ok", code == 0)
                addProperty("command", command.joinToString(" "))
                addProperty("exitCode", code)
                addProperty("output", output.toString().takeLast(4_000))
                if (code != 0) {
                    addProperty("remedy", "Run closecode setup --web, then npm install / pip install in the project.")
                }
            }
        }.getOrElse {
            JsonObject().apply {
                addProperty("ok", false)
                addProperty("command", command.joinToString(" "))
                addProperty("error", it.message ?: "Could not start the package manager.")
                addProperty("remedy", "Run closecode setup --web first.")
            }
        }
    }

    private fun filesFor(template: String, name: String): List<ScaffoldFile> = when (template) {
        "web-vite" -> listOf(
            ScaffoldFile(
                "package.json",
                """
                    {
                      "name": "${slugOf(name)}",
                      "private": true,
                      "version": "0.0.0",
                      "type": "module",
                      "scripts": {
                        "dev": "vite",
                        "build": "vite build",
                        "preview": "vite preview"
                      },
                      "devDependencies": {
                        "vite": "^6.0.0"
                      }
                    }
                """.trimIndent() + "\n",
            ),
            ScaffoldFile(
                "index.html",
                """
                    <!doctype html>
                    <html lang="en">
                      <head>
                        <meta charset="UTF-8" />
                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                        <title>$name</title>
                      </head>
                      <body>
                        <div id="app"></div>
                        <script type="module" src="/src/main.js"></script>
                      </body>
                    </html>
                """.trimIndent() + "\n",
            ),
            ScaffoldFile(
                "src/main.js",
                """
                    import './style.css'

                    document.querySelector('#app').innerHTML = `
                      <main>
                        <h1>$name</h1>
                        <p>Edit src/main.js and save. Vite reloads this page.</p>
                      </main>
                    `
                """.trimIndent() + "\n",
            ),
            ScaffoldFile(
                "src/style.css",
                """
                    :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
                    body { margin: 0; display: grid; min-height: 100vh; place-items: center; }
                    main { padding: 2rem; text-align: center; }
                """.trimIndent() + "\n",
            ),
            ScaffoldFile(".gitignore", "node_modules\ndist\n"),
        )
        "python-fastapi" -> listOf(
            ScaffoldFile(
                "main.py",
                """
                    from fastapi import FastAPI

                    app = FastAPI(title="$name")

                    @app.get("/")
                    def index():
                        return {"ok": True, "message": "$name"}

                    @app.get("/health")
                    def health():
                        return {"status": "ok"}
                """.trimIndent() + "\n",
            ),
            ScaffoldFile(
                "requirements.txt",
                "fastapi\nuvicorn[standard]\n",
            ),
            ScaffoldFile(
                "README.md",
                "# $name\n\nRun with `closecode serve` after installing requirements.\n",
            ),
            ScaffoldFile(".gitignore", "__pycache__/\n.venv/\n"),
        )
        "python-cli" -> listOf(
            ScaffoldFile(
                "main.py",
                """
                    #!/usr/bin/env python3
                    import argparse


                    def main() -> int:
                        parser = argparse.ArgumentParser(description="$name")
                        parser.add_argument("name", nargs="?", default="world")
                        args = parser.parse_args()
                        print(f"Hello, {args.name}!")
                        return 0


                    if __name__ == "__main__":
                        raise SystemExit(main())
                """.trimIndent() + "\n",
            ),
            ScaffoldFile("README.md", "# $name\n\nRun `python3 main.py`.\n"),
            ScaffoldFile(".gitignore", "__pycache__/\n.venv/\n"),
        )
        "node-express" -> listOf(
            ScaffoldFile(
                "package.json",
                """
                    {
                      "name": "${slugOf(name)}",
                      "private": true,
                      "version": "0.0.0",
                      "type": "module",
                      "scripts": {
                        "start": "node server.js",
                        "dev": "node --watch server.js"
                      },
                      "dependencies": {
                        "express": "^4.21.0"
                      }
                    }
                """.trimIndent() + "\n",
            ),
            ScaffoldFile(
                "server.js",
                """
                    import express from 'express'

                    const app = express()
                    const port = Number(process.env.PORT || 3000)

                    app.get('/', (_req, res) => res.json({ ok: true, message: '$name' }))
                    app.get('/health', (_req, res) => res.json({ status: 'ok' }))

                    app.listen(port, '0.0.0.0', () => {
                      console.log(`listening on http://localhost:${'$'}{port}`)
                    })
                """.trimIndent() + "\n",
            ),
            ScaffoldFile("README.md", "# $name\n\nRun `npm install`, then `closecode serve`.\n"),
            ScaffoldFile(".gitignore", "node_modules/\n"),
        )
        else -> emptyList()
    }

    private fun slugOf(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifBlank { "closecode-app" }
}
