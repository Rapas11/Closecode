package com.closecode.ai

import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One `provider/model` pair the AI settings page can choose as the page's default. */
data class OpenCodeModelChoice(val provider: String, val model: String) {

    /** The string OpenCode's configuration file stores for this pair. */
    val qualified: String get() = "$provider/$model"
}

/**
 * The models the AI settings page can offer.
 *
 * OpenCode writes every model it saw into `HOME/cache/opencode/models.json`, so the page
 * prefers the cache and only asks a running server for something fresh. A silent server is
 * never out of the way: the page claims what the cache says, and the settings page asks the
 * user to start the AI or try again when there is neither.
 */
object OpenCodeModelChoices {

    data class Loaded(
        val choices: List<OpenCodeModelChoice>,
        /** Where the list came from: `cache` or `server`. Empty when nothing was found. */
        val source: String = "",
    )

    fun load(context: android.content.Context, baseUrl: String): Loaded {
        val cached = fromCache(OpenCode.home(context))
            ?: OpenCodeModelChoices.Loaded(emptyList(), "")
        if (cached.choices.isNotEmpty()) return cached
        val served = fromServer(baseUrl)
        return when {
            served.choices.isNotEmpty() -> served
            else -> cached
        }
    }

    /** Reads OpenCode's own durable cache. Returns null when the file cannot be a list. */
    internal fun fromCache(home: File): Loaded? {
        val file = File(home, "cache/opencode/models.json")
        if (!file.isFile) return null
        val body = runCatching { file.readText() }.getOrNull() ?: return null
        val choices = parse(body)
        return Loaded(choices, if (choices.isEmpty()) "" else "cache")
    }

    /** Asks a running server its model list. */
    internal fun fromServer(baseUrl: String): Loaded {
        if (baseUrl.isBlank()) return Loaded(emptyList())
        val body = request(baseUrl.trimEnd('/') + "/model")
        val choices = parse(body)
        return Loaded(choices, if (choices.isEmpty()) "" else "server")
    }

    /**
     * Accepts the shapes OpenCode has written: a bare array of model records, an object with a
     * `models`/`data` array, or an object of `provider` → models. Unknown fields are ignored.
     */
    internal fun parse(body: String?): List<OpenCodeModelChoice> {
        if (body.isNullOrBlank()) return emptyList()
        val json = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
        val choices = mutableSetOf<OpenCodeModelChoice>()

        fun addRecord(
            element: com.google.gson.JsonElement?,
            defaultProvider: String = "",
            defaultModel: String = "",
        ) {
            if (element == null || !element.isJsonObject) return
            val obj = element.asJsonObject
            val provider = obj.getAsJsonPrimitive("providerID")?.asString
                ?: obj.getAsJsonPrimitive("provider")?.asString
                ?: defaultProvider
            val model = obj.getAsJsonPrimitive("modelID")?.asString
                ?: obj.getAsJsonPrimitive("model")?.asString
                ?: obj.getAsJsonPrimitive("id")?.asString
                ?: defaultModel
            if (provider.isNullOrBlank() || model.isNullOrBlank()) return
            choices += OpenCodeModelChoice(provider, model)
        }

        fun readArray(array: com.google.gson.JsonArray?, defaultProvider: String = "") {
            array?.forEach { addRecord(it, defaultProvider) }
        }

        when {
            json.isJsonArray -> readArray(json.asJsonArray)
            json.isJsonObject -> {
                val root = json.asJsonObject
                root.get("models")?.takeIf { it.isJsonArray }?.let { readArray(it.asJsonArray) }
                root.get("data")?.takeIf { it.isJsonArray }?.let { readArray(it.asJsonArray) }
                root.entrySet().forEach { entry ->
                    when {
                        entry.value.isJsonArray -> readArray(entry.value.asJsonArray, entry.key)
                        entry.value.isJsonObject -> entry.value.asJsonObject.entrySet()
                            .forEach { addRecord(it.value, entry.key, it.key) }
                        else -> Unit
                    }
                }
            }
            else -> Unit
        }
        return choices.sortedWith(compareBy({ it.provider }, { it.model }))
    }

    private fun request(urlText: String): String? {
        val connection = runCatching {
            (URL(urlText).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
        }.getOrNull() ?: return null
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
