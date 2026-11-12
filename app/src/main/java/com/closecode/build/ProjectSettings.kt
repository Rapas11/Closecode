package com.closecode.build

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Per-project pins from `.closecode.json`. Nothing here changes a build file: the project's
 * own Gradle/Node/Python files stay authoritative unless the user explicitly pins a version.
 */
data class ProjectSettings(
    val type: String? = null,
    val engine: String? = null,
    val sdk: Int? = null,
    val buildTools: String? = null,
    val gradleVersion: String? = null,
    val nodeVersion: String? = null,
    val pythonVersion: String? = null,
    val env: Map<String, String> = emptyMap(),
    val ports: Map<String, Int> = emptyMap(),
) {
    fun json(): JsonObject = JsonObject().apply {
        type?.let { addProperty("type", it) }
        engine?.let { addProperty("engine", it) }
        sdk?.let { addProperty("sdk", it) }
        buildTools?.let { addProperty("buildTools", it) }
        gradleVersion?.let { addProperty("gradleVersion", it) }
        nodeVersion?.let { addProperty("nodeVersion", it) }
        pythonVersion?.let { addProperty("pythonVersion", it) }
        add("env", JsonObject().apply { env.forEach { (key, value) -> addProperty(key, value) } })
        add("ports", JsonObject().apply { ports.forEach { (key, value) -> addProperty(key, value) } })
    }

    companion object {
        const val FILE = ".closecode.json"
        const val ENGINE_GRADLE = "gradle"
        const val ENGINE_CLOSECODE = "closecode"
        val TYPES = setOf(
            "gradle-android",
            "npm",
            "static-html",
            "python",
            "cmake",
            "mixed",
        )

        fun read(directory: File): ProjectSettings = runCatching {
            val file = File(directory, FILE)
            if (!file.isFile) return ProjectSettings()
            val json = JsonParser.parseString(file.readText()).asJsonObject
            ProjectSettings(
                type = json.string("type")?.takeIf { it in TYPES },
                engine = json.string("engine")?.takeIf {
                    it == ENGINE_GRADLE || it == ENGINE_CLOSECODE
                },
                sdk = json.int("sdk"),
                buildTools = json.string("buildTools"),
                gradleVersion = json.string("gradleVersion"),
                nodeVersion = json.string("nodeVersion"),
                pythonVersion = json.string("pythonVersion"),
                env = json.objectStrings("env"),
                ports = json.objectInts("ports"),
            )
        }.getOrDefault(ProjectSettings())

        /** Sets one whitelisted key, preserving every key this app does not know. */
        fun set(directory: File, key: String, value: String): Result<ProjectSettings> = runCatching {
            val clean = key.trim()
            val text = value.trim()
            when {
                clean == "type" && text !in TYPES ->
                    throw IllegalArgumentException("type must be one of ${TYPES.joinToString()}")
                clean == "engine" && text != ENGINE_GRADLE && text != ENGINE_CLOSECODE ->
                    throw IllegalArgumentException("engine must be gradle or closecode")
                clean == "sdk" && text.toIntOrNull() == null ->
                    throw IllegalArgumentException("sdk must be a number")
                clean == "ports" ->
                    throw IllegalArgumentException("use ports.<name>=<number>")
                clean == "env" ->
                    throw IllegalArgumentException("use env.<NAME>=<value>")
                clean.startsWith("ports.") && (text.toIntOrNull() ?: 0) !in 1..65535 ->
                    throw IllegalArgumentException("a port must be 1-65535")
                clean.startsWith("env.") && clean.length <= 4 && text.isEmpty() ->
                    throw IllegalArgumentException("use env.<NAME>=<value>")
                !allowed(clean) ->
                    throw IllegalArgumentException(
                        "unknown key; use type, engine, jdk, sdk, buildTools, gradleVersion, " +
                            "nodeVersion, pythonVersion, env.<NAME> or ports.<name>",
                    )
            }
            update(directory) { json ->
                if (clean.startsWith("ports.")) {
                    val ports = json.getAsJsonObject("ports") ?: JsonObject().also {
                        json.add("ports", it)
                    }
                    ports.addProperty(clean.removePrefix("ports."), text.toInt())
                } else if (clean.startsWith("env.")) {
                    val env = json.getAsJsonObject("env") ?: JsonObject().also {
                        json.add("env", it)
                    }
                    env.addProperty(clean.removePrefix("env."), text)
                } else {
                    when (clean) {
                        "sdk" -> json.addProperty(clean, text.toInt())
                        else -> json.addProperty(clean, text)
                    }
                }
            }
            read(directory)
        }

        fun unset(directory: File, key: String): Result<ProjectSettings> = runCatching {
            val clean = key.trim()
            if (!allowed(clean)) throw IllegalArgumentException("unknown key")
            update(directory) { json ->
                if (clean.startsWith("ports.")) {
                    json.getAsJsonObject("ports")?.remove(clean.removePrefix("ports."))
                } else if (clean.startsWith("env.")) {
                    json.getAsJsonObject("env")?.remove(clean.removePrefix("env."))
                } else {
                    json.remove(clean)
                }
            }
            read(directory)
        }

        private fun allowed(key: String): Boolean =
            key in setOf(
                "type", "engine", "sdk", "buildTools", "gradleVersion",
                "nodeVersion", "pythonVersion",
            ) || (key.startsWith("env.") && key.length > 4) ||
                (key.startsWith("ports.") && key.length > 6)

        private fun update(directory: File, block: (JsonObject) -> Unit) {
            directory.mkdirs()
            val file = File(directory, FILE)
            val json = runCatching {
                if (file.isFile) JsonParser.parseString(file.readText()).asJsonObject
                else JsonObject()
            }.getOrElse { JsonObject() }
            block(json)
            val temp = File(directory, "$FILE.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) {
                file.writeText(json.toString())
                temp.delete()
            }
        }
    }
}

fun ProjectSettings.port(name: String): Int? = ports[name]

private fun JsonObject.string(key: String): String? =
    runCatching { get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
        .getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

private fun JsonObject.objectStrings(key: String): Map<String, String> {
    val objectValue = runCatching { getAsJsonObject(key) }.getOrNull() ?: return emptyMap()
    return objectValue.entrySet().mapNotNull { (name, value: JsonElement) ->
        runCatching { name to value.asString }.getOrNull()
    }.toMap()
}

private fun JsonObject.objectInts(key: String): Map<String, Int> {
    val objectValue = runCatching { getAsJsonObject(key) }.getOrNull() ?: return emptyMap()
    return objectValue.entrySet().mapNotNull { (name, value: JsonElement) ->
        runCatching { name to value.asInt }.getOrNull()
    }.toMap()
}
