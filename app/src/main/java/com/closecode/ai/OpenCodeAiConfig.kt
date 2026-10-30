package com.closecode.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * The pieces of OpenCode's configuration the AI settings page can read and write.
 *
 * The file stays where the app grounds it: `$XDG_CONFIG_HOME/opencode/opencode.json`. Every
 * write keeps the keys the page does not understand — anything OpenCode itself or a user wrote
 * must survive one settings edit.
 *
 * Values live in the JSON exactly as OpenCode reads them: `"model": "provider/model"` and
 * `"permission": { <tool>: "allow" | "ask" | "deny" }`. OpenCode applies them on the next
 * server start, which the page says.
 */
object OpenCodeAiConfig {

    /** The tools the page offers a permission picker for, in the order it shows them. */
    val TOOLS = listOf(
        Tool("read", "Files read"),
        Tool("edit", "Files edited"),
        Tool("bash", "Shell commands"),
        Tool("webfetch", "Pages fetched"),
        Tool("websearch", "Web search"),
        Tool("skill", "Skills loaded"),
        Tool("task", "Subagents"),
        Tool("external_directory", "Project folders outside the app"),
        Tool("doom_loop", "Stuck-recovery prompts"),
    )

    data class Tool(val key: String, val label: String)

    /** The actions a picker cycles through, in key order; `allow` is OpenCode's own default. */
    val ACTIONS = listOf("allow", "ask", "deny")

    const val ALLOW = "allow"
    const val ASK = "ask"
    const val DENY = "deny"

    /** Reads the file as data, or null when there is nothing to read. */
    fun read(file: File): JsonObject? =
        runCatching {
            JsonParser.parseString(file.readText()).asJsonObject
        }.getOrNull()

    /** Writes the file. Returns false when the write failed; the caller keeps the old file. */
    fun save(file: File, json: JsonObject): Boolean =
        try {
            file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) return false }
            file.writeText(json.toString())
            true
        } catch (_: Throwable) {
            false
        }

    /** The model line as the page remembers it, or "" when the user set nothing. */
    fun model(json: JsonObject): String =
        json.getAsJsonPrimitive("model")?.asString.orEmpty()

    /** Remembers the model line, removing the key entirely when it is blank. */
    fun withModel(json: JsonObject, model: String): JsonObject = json.apply {
        if (model.isBlank()) remove("model")
        else addProperty("model", model)
    }

    /** The action the config has for [tool], or "" when the user has not touched it yet. */
    fun permission(json: JsonObject, tool: String): String {
        val permissions = json.getAsJsonObject("permission") ?: return ""
        return permissions.getAsJsonPrimitive(tool)?.asString?.takeIf { it in ACTIONS }.orEmpty()
    }

    /**
     * Steps the tool one [ACTIONS] key forward, writing the key once it leaves `allow`'s
     * default and removing it when the walk arrives back at the start.
     */
    fun withPermissionCycled(json: JsonObject, tool: String): JsonObject {
        val current = permission(json, tool)
        val next = when (current) {
            "" -> ASK
            ALLOW -> ASK
            ASK -> DENY
            else -> ""
        }
        return withPermission(json, tool, next)
    }

    /** Writes one tool's action, removing the key when [action] is not a real one. */
    fun withPermission(json: JsonObject, tool: String, action: String): JsonObject {
        val next = json.deepCopy()
        if (action !in ACTIONS) {
            next.getAsJsonObject("permission")?.remove(tool)
            return next
        }
        val permissions = next.getAsJsonObject("permission") ?: JsonObject().also {
            next.add("permission", it)
        }
        permissions.addProperty(tool, action)
        return next
    }

    /** True when nothing about [tool] is in the config, so OpenCode walks its own default. */
    fun untouched(json: JsonObject, tool: String) = permission(json, tool).isEmpty()
}
