package com.closecode.control

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The one result shape every adapter returns. Agents should not have to know whether a task
 * ran through Gradle, npm, pytest or CMake.
 */
internal object CommandEnvelope {

    fun of(
        ok: Boolean,
        artifacts: List<String> = emptyList(),
        errors: List<String> = emptyList(),
        warnings: List<String> = emptyList(),
        exitCode: Int = if (ok) 0 else 1,
        durationMs: Long = 0,
        logPath: String? = null,
    ): JsonObject = JsonObject().apply {
        addProperty("ok", ok)
        add("artifacts", JsonArray().apply { artifacts.forEach { add(it) } })
        add("errors", JsonArray().apply { errors.forEach { add(it) } })
        add("warnings", JsonArray().apply { warnings.forEach { add(it) } })
        addProperty("exitCode", exitCode)
        addProperty("durationMs", durationMs)
        logPath?.let { addProperty("logPath", it) }
    }

    fun failed(
        message: String,
        code: String = "command_failed",
        remedy: String? = null,
        installCommand: String? = null,
        logPath: String? = null,
    ): JsonObject = of(
        ok = false,
        errors = listOf(message),
        logPath = logPath,
    ).apply {
        addProperty("error", message)
        addProperty("code", code)
        remedy?.let { addProperty("remedy", it) }
        installCommand?.let { addProperty("installCommand", it) }
    }
}
