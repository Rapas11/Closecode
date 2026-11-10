package com.closecode.device.control

import android.content.Context
import android.content.Intent
import com.closecode.control.CommandEnvelope
import com.closecode.device.ScreenRecordActivity
import com.closecode.device.ScreenRecordService
import com.google.gson.JsonObject
import java.io.File

internal object ScreenControl {

    private fun statusFile(context: Context): File =
        File(File(context.filesDir, "screen"), "status.json")

    fun run(context: Context, arguments: List<String>): JsonObject {
        val consent = ConsentStore(context)
        if (!consent.isGranted("screen")) {
            return CommandEnvelope.failed(
                "The screen capability is not granted.",
                code = "needs_consent",
                remedy = "Run closecode consent request screen, then allow it in Settings > Agent permissions.",
            )
        }
        val action = arguments.firstOrNull()?.lowercase() ?: "status"
        return when (action) {
            "start", "record" -> start(context, arguments.drop(1))
            "stop" -> stop(context)
            "status", "ls" -> status(context)
            else -> CommandEnvelope.failed(
                "usage: closecode screen start [--audio] [--path FILE]|stop|status",
                code = "usage",
            )
        }
    }

    private fun start(context: Context, arguments: List<String>): JsonObject {
        val existing = readStatus(context)
        if (existing.boolean("running")) {
            return CommandEnvelope.failed("Screen recording is already running.", code = "already_running")
        }
        val path = valueAfter(arguments, "--path")?.let(::File)
            ?: File(File(context.filesDir, "screen"), "screen-${System.currentTimeMillis()}.mp4")
        val status = statusFile(context)
        status.delete()
        val intent = Intent(context, ScreenRecordActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ScreenRecordActivity.EXTRA_OUTPUT, path.absolutePath)
            .putExtra(ScreenRecordActivity.EXTRA_STATUS_DIR, status.absolutePath)
            .putExtra(ScreenRecordActivity.EXTRA_AUDIO, arguments.contains("--audio"))
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            return CommandEnvelope.failed(
                "Could not open the screen-capture consent dialog. Bring CloseCode to the foreground.",
                code = "needs_user",
            )
        }
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val current = readStatus(context)
            if (current.has("error")) {
                return CommandEnvelope.failed(
                    current.get("error").asString,
                    code = "screen_failed",
                ).apply {
                    addProperty("needsUser", true)
                }
            }
            if (current.boolean("running")) {
                return CommandEnvelope.of(ok = true).apply {
                    addProperty("running", true)
                    addProperty("path", current.get("path")?.asString ?: path.absolutePath)
                    addProperty("audio", current.get("audio")?.asBoolean ?: false)
                    addProperty("statusPath", status.absolutePath)
                }
            }
            Thread.sleep(200)
        }
        return CommandEnvelope.failed(
            "Screen recording did not start. Approve the system screen-capture dialog.",
            code = "needs_user",
            remedy = "Bring CloseCode to the foreground and run screen start again.",
        ).apply {
            addProperty("needsUser", true)
        }
    }

    private fun stop(context: Context): JsonObject {
        ScreenRecordService.stop(context)
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            val current = readStatus(context)
            if (current.has("running") && !current.boolean("running")) {
                return CommandEnvelope.of(ok = true).apply {
                    addProperty("running", false)
                    current.get("path")?.takeIf { it.isJsonPrimitive }?.asString?.let { addProperty("path", it) }
                    current.get("bytes")?.takeIf { it.isJsonPrimitive }?.asLong?.let { addProperty("bytes", it) }
                }
            }
            Thread.sleep(150)
        }
        return CommandEnvelope.of(ok = true).apply {
            addProperty("running", false)
            addProperty("note", "Stop was requested; the service may still be finishing the file.")
        }
    }

    private fun status(context: Context): JsonObject {
        val current = readStatus(context)
        if (current.isEmpty) {
            return CommandEnvelope.of(ok = true).apply {
                addProperty("running", false)
                addProperty("path", statusFile(context).absolutePath)
            }
        }
        return CommandEnvelope.of(ok = true).apply {
            add("recording", current)
            addProperty("running", current.boolean("running"))
            current.get("path")?.takeIf { it.isJsonPrimitive }?.asString?.let { addProperty("path", it) }
        }
    }

    private fun JsonObject.boolean(key: String): Boolean = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    }.getOrDefault(false)

    private fun readStatus(context: Context): JsonObject = runCatching {
        val file = statusFile(context)
        if (!file.isFile) return@runCatching JsonObject()
        com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
    }.getOrDefault(JsonObject())

    private fun valueAfter(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        return if (index >= 0) arguments.getOrNull(index + 1) else null
    }
}
