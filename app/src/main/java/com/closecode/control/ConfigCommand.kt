package com.closecode.control

import com.closecode.build.ProjectSettings
import com.google.gson.JsonObject
import java.io.File

/**
 * `closecode config` reads and edits `.closecode.json`. This is the agent-safe way to pin an
 * engine, SDK, toolchain version or port without rewriting a build file.
 */
internal object ConfigCommand {

    fun run(projectDir: File, arguments: List<String>): JsonObject {
        // P1-2: flags are not positionals. `config show --json` used to look up a setting
        // literally named "--json"; now flags are removed before the action is read.
        val unknown = arguments.firstOrNull {
            it.startsWith("--") && it != "--json"
        }
        if (unknown != null) {
            return CommandEnvelope.failed(
                "unknown option '$unknown'",
                code = "unknown_option",
                remedy = "Run closecode config show --json to see the accepted fields.",
            )
        }
        val cleaned = arguments.filterNot { it == "--json" }
        return run(projectDir, cleaned, cleaned.firstOrNull()?.lowercase() ?: "show")
    }

    private fun run(projectDir: File, arguments: List<String>, action: String): JsonObject {
        return when (action) {
            "show", "get", "json" -> show(projectDir, arguments.drop(1).firstOrNull())
            "set", "pin" -> {
                val key = arguments.getOrNull(1)
                val value = arguments.getOrNull(2)
                if (key == null || value == null) {
                    return CommandEnvelope.failed(
                        "usage: closecode config set <key> <value>",
                        code = "usage",
                        remedy = "Example: closecode config set gradleVersion 8.13",
                    )
                }
                ProjectSettings.set(projectDir, key, value).fold(
                    onSuccess = { show(projectDir, null) },
                    onFailure = { error ->
                        CommandEnvelope.failed(
                            error.message ?: "Could not write .closecode.json",
                            code = "invalid_setting",
                            remedy = "Run closecode config show to see the current pins.",
                        )
                    },
                )
            }
            "unset", "remove" -> {
                val key = arguments.getOrNull(1)
                if (key == null) {
                    return CommandEnvelope.failed(
                        "usage: closecode config unset <key>",
                        code = "usage",
                    )
                }
                ProjectSettings.unset(projectDir, key).fold(
                    onSuccess = { show(projectDir, null) },
                    onFailure = { error ->
                        CommandEnvelope.failed(
                            error.message ?: "Could not write .closecode.json",
                            code = "invalid_setting",
                        )
                    },
                )
            }
            else -> CommandEnvelope.failed(
                "usage: closecode config show|set|unset",
                code = "usage",
            )
        }
    }

    private fun show(projectDir: File, key: String?): JsonObject {
        val settings = ProjectSettings.read(projectDir)
        val pins = settings.json()
        if (key != null) {
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("key", key)
                add("value", pins.get(key) ?: com.google.gson.JsonNull.INSTANCE)
                addProperty("path", File(projectDir, ProjectSettings.FILE).absolutePath)
            }
        }
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("path", File(projectDir, ProjectSettings.FILE).absolutePath)
            add("settings", pins)
        }
    }
}
