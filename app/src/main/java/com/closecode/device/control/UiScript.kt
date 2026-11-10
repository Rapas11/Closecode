package com.closecode.device.control

import android.content.Context
import com.closecode.control.CommandEnvelope
import com.closecode.device.DeviceTools
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Multi-app UI scripts: a small JSON step list shared by `closecode test-ui file.json`.
 * Each step maps to exactly one existing device command, so there is no second automation
 * implementation to drift from `ui tap`, `ui wait` and friends.
 */
internal object UiScript {

    fun run(
        context: Context,
        projectDir: File?,
        arguments: List<String>,
    ): JsonObject {
        val path = arguments.firstOrNull { !it.startsWith("--") }
            ?: return CommandEnvelope.failed(
                "usage: closecode test-ui <script.json>",
                code = "usage",
            )
        val keepGoing = arguments.contains("--keep-going")
        val file = if (File(path).isAbsolute) File(path) else File(projectDir, path)
        if (!file.isFile) {
            return CommandEnvelope.failed("Script not found: ${file.path}", code = "not_found")
        }
        val steps = runCatching {
            val root = JsonParser.parseString(file.readText())
            if (root.isJsonArray) root.asJsonArray else root.asJsonObject.getAsJsonArray("steps")
        }.getOrNull() ?: return CommandEnvelope.failed(
            "The script must be a JSON array or {\"steps\": [...]}.",
            code = "invalid_script",
        )
        val defaultPackage = projectDir?.let {
            DeviceTools.packageName(it, null)
        }
        val results = JsonArray()
        var failed = false
        var index = 0
        for (element in steps) {
            val step = runCatching { element.asJsonObject }.getOrNull()
            if (step == null) {
                failed = true
                results.add(failure(index, "invalid", "A step must be a JSON object."))
                if (!keepGoing) break
                index++
                continue
            }
            val action = step.string("action") ?: step.string("do") ?: ""
            val packageName = step.string("package") ?: defaultPackage
            val gate = DeviceControl.checkUiConsent(context, packageName)
            val result = gate ?: execute(context, projectDir, action, step, packageName)
            val ok = result.get("ok")?.asBoolean == true
            results.add(JsonObject().apply {
                addProperty("index", index)
                addProperty("action", action)
                addProperty("ok", ok)
                add("result", result)
            })
            if (!ok) {
                failed = true
                if (!keepGoing) break
            }
            index++
        }
        val envelope = CommandEnvelope.of(
            ok = !failed,
            errors = if (failed) listOf("A UI step failed.") else emptyList(),
        )
        envelope.add("steps", results)
        if (failed) {
            envelope.addProperty("error", "A UI step failed.")
            envelope.addProperty("code", "ui_script_failed")
        }
        envelope.addProperty("script", file.absolutePath)
        return envelope
    }

    private fun execute(
        context: Context,
        projectDir: File?,
        action: String,
        step: JsonObject,
        packageName: String?,
    ): JsonObject = when (action.lowercase()) {
        "launch", "open" -> DeviceTools.launch(context, projectDir, packageName)
        "stop", "kill" -> DeviceTools.stop(context, projectDir, packageName)
        "tap", "click" -> DeviceTools.ui(listOf("tap") + selector(step))
        "wait" -> DeviceTools.ui(listOf("wait") + selector(step) + timeout(step) +
            if (step.boolean("gone") == true) listOf("--gone") else emptyList())
        "assert" -> DeviceTools.ui(listOf("assert") + selector(step))
        "type", "text" -> DeviceTools.ui(listOf("type", step.string("text").orEmpty()))
        "scroll" -> DeviceTools.ui(listOf("scroll", step.string("direction") ?: "down") + selector(step))
        "dump" -> DeviceTools.ui(listOf("dump"))
        "key" -> DeviceTools.input(listOf("key", step.string("key") ?: "back"))
        "sleep", "wait-ms" -> {
            Thread.sleep(step.long("ms")?.coerceIn(0, 60_000) ?: 500)
            CommandEnvelope.of(ok = true)
        }
        else -> CommandEnvelope.failed("Unknown step action '$action'.", code = "unknown_step")
    }

    private fun selector(step: JsonObject): List<String> {
        val out = mutableListOf<String>()
        step.string("text")?.let { out += listOf("--text", it) }
        step.string("id")?.let { out += listOf("--id", it) }
        step.string("desc")?.let { out += listOf("--desc", it) }
        if (out.isEmpty()) {
            step.string("selector")?.let { out += listOf("--text", it) }
        }
        step.int("index")?.let { out += listOf("--index", it.toString()) }
        return out
    }

    private fun timeout(step: JsonObject): List<String> =
        step.long("timeout")?.let { listOf("--timeout", it.toString()) } ?: emptyList()

    private fun failure(index: Int, action: String, message: String): JsonObject =
        JsonObject().apply {
            addProperty("index", index)
            addProperty("action", action)
            addProperty("ok", false)
            add("result", CommandEnvelope.failed(message, code = "invalid_step"))
        }

    private fun JsonObject.string(key: String): String? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

    private fun JsonObject.boolean(key: String): Boolean? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
}
