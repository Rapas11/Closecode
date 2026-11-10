package com.closecode.control

import android.content.Context
import com.closecode.device.control.AuditLog
import com.closecode.device.control.ConsentStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/** The `closecode secrets` command. Values are written, never printed. */
internal object SecretsCommand {

    fun run(context: Context, arguments: List<String>): JsonObject {
        if (!ConsentStore(context).isGranted("secrets")) {
            return CommandEnvelope.failed(
                "The secrets capability is not granted.",
                code = "needs_consent",
                remedy = "Run closecode consent request secrets, then allow it in Settings > Agent permissions.",
            )
        }
        val store = SecretStore(context)
        val action = arguments.firstOrNull()?.lowercase() ?: "list"
        val rest = arguments.drop(1)
        val result = when (action) {
            "list", "ls" -> CommandEnvelope.of(ok = true).apply {
                add("secrets", JsonArray().apply { store.list().forEach { add(it) } })
                addProperty("durable", store.durable())
                if (!store.durable()) {
                    add("warnings", JsonArray().apply {
                        add("This phone cannot encrypt secrets; values last for this session only.")
                    })
                }
            }
            "set", "put" -> set(context, store, rest)
            "delete", "remove", "rm" -> {
                val name = rest.firstOrNull()
                    ?: return CommandEnvelope.failed("usage: closecode secrets delete <name>", code = "usage")
                val done = store.remove(name)
                CommandEnvelope.of(ok = done).apply {
                    addProperty("name", name)
                }
            }
            "get" -> CommandEnvelope.failed(
                "Secret values are never printed.",
                code = "masked",
                remedy = "Use closecode secrets list to see names; deploy reads values itself.",
            )
            else -> CommandEnvelope.failed(
                "usage: closecode secrets list|set <name> <value>|set <name> --file FILE|delete <name>",
                code = "usage",
            )
        }
        AuditLog(context).record("secrets $action", rest.firstOrNull(), emptyList(), result.get("ok")?.asBoolean == true, result.get("error")?.asString)
        return result
    }

    private fun set(context: Context, store: SecretStore, arguments: List<String>): JsonObject {
        val name = arguments.firstOrNull()
            ?: return CommandEnvelope.failed("usage: closecode secrets set <name> <value>|--file FILE", code = "usage")
        val fileIndex = arguments.indexOf("--file")
        val value = if (fileIndex >= 0) {
            val path = arguments.getOrNull(fileIndex + 1)
                ?: return CommandEnvelope.failed("--file needs a path", code = "usage")
            runCatching { File(path).readText() }.getOrElse {
                return CommandEnvelope.failed("Could not read $path: ${it.message}", code = "file_failed")
            }
        } else {
            arguments.drop(1).joinToString(" ")
        }
        if (value.isEmpty()) {
            return CommandEnvelope.failed("Secret value is empty.", code = "usage")
        }
        val durable = store.put(name, value)
        return CommandEnvelope.of(ok = true).apply {
            addProperty("name", name)
            addProperty("bytes", value.length)
            addProperty("durable", durable)
            if (!durable) {
                addProperty("warning", "Encrypted storage is unavailable; the secret lasts for this session only.")
            }
        }
    }
}
