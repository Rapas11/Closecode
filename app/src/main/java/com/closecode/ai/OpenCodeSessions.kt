package com.closecode.ai

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * The one OpenCode session that belongs to a project.
 *
 * OpenCode's server is global: its session list contains every project it has opened. The page's
 * own "new session" flow can therefore land in another project's history. CloseCode gives each
 * project one root session and opens that session directly, so a project switch cannot show the
 * previous project's chat.
 *
 * The session ID is remembered by project UUID, but the directory in the server's answer is
 * checked before the ID is saved or used. A project that was removed and recreated must never
 * inherit an old session.
 */
object OpenCodeSessions {

    private const val PREFS = "closecode_open_code_sessions"
    private const val PREFIX = "session:"
    private const val TIMEOUT_MS = 10_000

    internal fun sessionsPath(directory: String): String =
        "/session?directory=${URLEncoder.encode(directory, "UTF-8")}&roots=true&limit=100"

    internal fun createSessionPath(directory: String): String =
        "/session?directory=${URLEncoder.encode(directory, "UTF-8")}"

    /** The direct route that opens [sessionId] instead of OpenCode's new-session prompt. */
    internal fun sessionPageUrl(baseUrl: String, sessionId: String): String =
        baseUrl.trimEnd('/') + "/server/" + openCodeServerKey(baseUrl) +
            "/session/" + sessionId

    /**
     * Picks the project's existing root session, preferring [preferred] when it still belongs to
     * [directory]. Returns null when the list has no usable session, which tells the caller to
     * create one.
     */
    internal fun chooseSessionId(
        json: String?,
        directory: String,
        preferred: String? = null,
    ): String? {
        val sessions = runCatching { JSONArray(json ?: return null) }.getOrNull() ?: return null
        val matches = (0 until sessions.length()).mapNotNull { index ->
            val item = sessions.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("parentID").isNotBlank()) return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val folder = item.optString("directory").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (!sameProjectPath(folder, directory)) return@mapNotNull null
            val time = item.optJSONObject("time")
            val updated = time?.optLong("updated", 0L) ?: 0L
            val created = time?.optLong("created", 0L) ?: 0L
            Session(id, maxOf(updated, created))
        }
        if (matches.isEmpty()) return null
        preferred?.let { wanted -> matches.firstOrNull { it.id == wanted }?.let { return it.id } }
        return matches.maxByOrNull { it.time }?.id
    }

    /** Reads the ID from a create answer, but only when the answer names the same directory. */
    internal fun createdSessionId(json: String?, directory: String): String? {
        val root = runCatching { JSONObject(json ?: return null) }.getOrNull() ?: return null
        val item = root.optJSONObject("session") ?: root
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val folder = item.optString("directory").takeIf { it.isNotBlank() } ?: return null
        return id.takeIf { sameProjectPath(folder, directory) }
    }

    /**
     * Returns the project's session, creating one when the server has none. Null means the
     * server could not answer; the caller falls back to the project page instead of blocking
     * the server.
     */
    fun ensure(
        context: Context,
        baseUrl: String,
        projectId: String,
        directory: String,
    ): String? {
        if (baseUrl.isBlank() || directory.isBlank()) return null
        val key = key(projectId, directory)
        val preferred = prefs(context).getString(key, null)
        val list = request("GET", baseUrl.trimEnd('/') + sessionsPath(directory), null)
        chooseSessionId(list, directory, preferred)?.let { id ->
            remember(context, projectId, directory, id)
            return id
        }
        val created = request("POST", baseUrl.trimEnd('/') + createSessionPath(directory), "{}")
        val id = createdSessionId(created, directory) ?: return null
        remember(context, projectId, directory, id)
        return id
    }

    fun remember(context: Context, projectId: String, directory: String, sessionId: String) {
        if (sessionId.isBlank()) return
        prefs(context).edit().putString(key(projectId, directory), sessionId).apply()
    }

    fun forget(context: Context, projectId: String, directory: String) {
        prefs(context).edit().remove(key(projectId, directory)).apply()
    }

    private fun key(projectId: String, directory: String): String =
        PREFIX + projectId.ifBlank { directory }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun request(method: String, url: String, body: String?): String? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
        }.getOrNull() ?: return null
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private data class Session(val id: String, val time: Long)
}
