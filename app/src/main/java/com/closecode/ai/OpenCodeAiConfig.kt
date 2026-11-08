package com.closecode.ai

import com.google.gson.JsonArray
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
 * V2 stores permissions as an ordered ruleset:
 * `"permissions": [{ "action": ..., "resource": ..., "effect": ... }]`. The page edits the
 * wildcard rule for each action; rules for particular files (the default `.env` asks) are left
 * alone. OpenCode applies the file on the next server start, which the page says.
 */
object OpenCodeAiConfig {

    /** The tools the page offers a permission picker for, in the order it shows them. */
    val TOOLS = listOf(
        Tool("read", "Files read"),
        Tool("edit", "Files edited"),
        Tool("shell", "Shell commands"),
        Tool("webfetch", "Pages fetched"),
        Tool("websearch", "Web search"),
        Tool("external_directory", "Project folders outside the app"),
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

    /** The wildcard action the config has for [tool], or "" when the user has not touched it. */
    fun permission(json: JsonObject, tool: String): String {
        val rules = json.getAsJsonArray("permissions") ?: return ""
        for (index in 0 until rules.size()) {
            val rule = rules.get(index) as? JsonObject ?: continue
            if (stringField(rule, "action") != tool) continue
            if (stringField(rule, "resource") != "*") continue
            return stringField(rule, "effect").takeIf { it in ACTIONS }.orEmpty()
        }
        return ""
    }

    /**
     * Steps the tool one [ACTIONS] key forward, writing the rule once it leaves `allow`'s
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

    /** Writes one tool's wildcard rule, removing it when [action] is not a real one. */
    fun withPermission(json: JsonObject, tool: String, action: String): JsonObject {
        val next = json.deepCopy()
        val rules = next.getAsJsonArray("permissions") ?: JsonArray().also {
            next.add("permissions", it)
        }
        for (index in rules.size() - 1 downTo 0) {
            val rule = rules.get(index) as? JsonObject ?: continue
            if (stringField(rule, "action") == tool &&
                stringField(rule, "resource") == "*"
            ) {
                rules.remove(index)
            }
        }
        if (action in ACTIONS) {
            rules.add(JsonObject().apply {
                addProperty("action", tool)
                addProperty("resource", "*")
                addProperty("effect", action)
            })
        }
        return next
    }

    /** True when the ruleset has the wildcard allow that makes the agent hands-free. */
    fun hasWildcardAllow(json: JsonObject): Boolean {
        val rules = json.getAsJsonArray("permissions") ?: return false
        for (index in 0 until rules.size()) {
            val rule = rules.get(index) as? JsonObject ?: continue
            if (stringField(rule, "action") == "*" &&
                stringField(rule, "resource") == "*" &&
                stringField(rule, "effect") == ALLOW
            ) {
                return true
            }
        }
        return false
    }

    /** True when nothing about [tool] is in the config, so OpenCode walks its own default. */
    fun untouched(json: JsonObject, tool: String) = permission(json, tool).isEmpty()

    private fun stringField(json: JsonObject, name: String): String =
        runCatching { json.get(name)?.asString }.getOrNull().orEmpty()
}
