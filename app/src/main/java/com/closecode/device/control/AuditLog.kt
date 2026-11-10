package com.closecode.device.control

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Append-only record of what the agent asked the phone to do: time, action, package,
 * arguments and result. Every device-control command writes one line.
 */
class AuditLog(private val context: Context) {

    private val file: File get() = File(context.filesDir, "audit/audit.jsonl")

    @Synchronized
    fun record(action: String, packageName: String?, arguments: List<String>, ok: Boolean, result: String?) {
        runCatching {
            file.parentFile?.mkdirs()
            val line = JsonObject().apply {
                addProperty("ts", System.currentTimeMillis())
                addProperty("action", action)
                addProperty("package", packageName.orEmpty())
                add("args", JsonArray().apply { arguments.forEach { add(it) } })
                addProperty("ok", ok)
                result?.let { addProperty("result", it.take(2000)) }
            }
            file.appendText(line.toString() + "\n")
        }
    }

    fun tail(lines: Int): JsonArray {
        val wanted = lines.coerceIn(1, 1000)
        val rows = runCatching {
            if (!file.isFile) emptyList() else file.readLines().takeLast(wanted)
        }.getOrDefault(emptyList())
        return JsonArray().apply {
            rows.forEach { row ->
                runCatching { add(JsonParser.parseString(row)) }
            }
        }
    }
}
