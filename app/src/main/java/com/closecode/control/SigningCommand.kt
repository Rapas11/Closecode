package com.closecode.control

import android.content.Context
import com.closecode.build.SigningKeyInfo
import com.closecode.build.SigningStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/** `closecode signing status|generate|import|select|delete`: keys without exposing secrets. */
object SigningCommand {

    fun run(context: Context, arguments: List<String>, cwd: String? = null): JsonObject {
        val store = SigningStore(context)
        return when (val action = arguments.firstOrNull()?.lowercase()) {
            null, "status", "list" -> status(store)
            "generate", "new" -> generate(store, arguments.drop(1))
            "import", "add" -> import(store, arguments.drop(1), cwd)
            "select", "use" -> select(store, arguments.getOrNull(1))
            "delete", "remove" -> delete(store, arguments.getOrNull(1))
            else -> error("usage: closecode signing status|generate|import|select|delete")
        }
    }

    private fun status(store: SigningStore): JsonObject = JsonObject().apply {
        val selected = store.selectedId()
        addProperty("ok", true)
        addProperty("selected", selected)
        add("keys", JsonArray().apply {
            store.allKeys().forEach { add(key(it, selected)) }
        })
        addProperty(
            "note",
            "Generated and debug keys are bound to this phone and cannot be exported. " +
                "Import a PKCS#12/JKS key to publish on Play.",
        )
    }

    private fun generate(store: SigningStore, arguments: List<String>): JsonObject {
        val name = flag(arguments, "--name") ?: "CloseCode Release"
        val country = flag(arguments, "--country").orEmpty()
        val years = flag(arguments, "--years")?.toIntOrNull() ?: 30
        val alias = flag(arguments, "--alias") ?: "release"
        val info = store.generateRelease(name, country, years, alias).getOrThrow()
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("selected", store.selectedId())
            add("key", key(info, store.selectedId()))
        }
    }

    private fun import(
        store: SigningStore,
        arguments: List<String>,
        cwd: String?,
    ): JsonObject {
        val flagValues = flagsWithValue
        val fileArg = arguments.withIndex().firstOrNull { (index, argument) ->
            !argument.startsWith("--") && arguments.getOrNull(index - 1) !in flagValues
        }?.value ?: error(
            "usage: closecode signing import <file.keystore> --password <password>",
        )
        val file = File(fileArg).let {
            if (it.isAbsolute || cwd.isNullOrBlank()) it else File(cwd, fileArg)
        }
        if (!file.isFile) error("No keystore at ${file.path}.")
        val passwordText = flag(arguments, "--password")
            ?: flag(arguments, "--store-password")
            ?: error("Pass --password for the keystore.")
        val keyPasswordText = flag(arguments, "--key-password") ?: passwordText
        val password = passwordText.toCharArray()
        val keyPassword = keyPasswordText.toCharArray()
        try {
            val info = store.importKey(
                bytes = file.readBytes(),
                originalName = file.name,
                alias = flag(arguments, "--alias") ?: file.nameWithoutExtension,
                password = password,
                keyAlias = flag(arguments, "--key-alias"),
                keyPassword = keyPassword,
            ).getOrThrow()
            if (arguments.contains("--select")) store.select(info.id)
            return JsonObject().apply {
                addProperty("ok", true)
                addProperty("selected", store.selectedId())
                add("key", key(info, store.selectedId()))
            }
        } finally {
            password.fill('\u0000')
            keyPassword.fill('\u0000')
        }
    }

    private fun select(store: SigningStore, id: String?): JsonObject {
        val key = id ?: error("usage: closecode signing select <key-id>")
        if (store.allKeys().none { it.id == key }) error("No signing key named $key.")
        store.select(key)
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("selected", store.selectedId())
        }
    }

    private fun delete(store: SigningStore, id: String?): JsonObject {
        val key = id ?: error("usage: closecode signing delete <key-id>")
        val removed = when {
            key == SigningStore.GENERATED_ID -> store.deleteGenerated()
            key == SigningStore.DEBUG_ID -> false
            else -> store.deleteImported(key)
        }
        return JsonObject().apply {
            addProperty("ok", removed)
            if (!removed) addProperty("error", "That key cannot be deleted.")
            addProperty("selected", store.selectedId())
        }
    }

    private fun key(info: SigningKeyInfo, selected: String): JsonObject = JsonObject().apply {
        addProperty("id", info.id)
        addProperty("label", info.label)
        addProperty("source", info.source.name.lowercase())
        addProperty("subject", info.subject)
        addProperty("alias", info.alias)
        addProperty("sha256", info.sha256)
        addProperty("exportable", info.exportable)
        addProperty("selected", info.id == selected)
        info.expiresAt?.let { addProperty("expiresAt", it) }
        info.createdAt?.let { addProperty("createdAt", it) }
    }

    private fun flag(arguments: List<String>, name: String): String? {
        val index = arguments.indexOf(name)
        if (index < 0) return null
        return arguments.getOrNull(index + 1)?.takeUnless { it.startsWith("--") }
    }

    private val flagsWithValue = setOf(
        "--password",
        "--store-password",
        "--key-password",
        "--alias",
        "--key-alias",
        "--name",
        "--country",
        "--years",
    )
}
