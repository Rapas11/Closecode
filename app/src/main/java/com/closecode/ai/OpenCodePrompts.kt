package com.closecode.ai

import com.google.gson.JsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The bridge from a build error to the agent: the user taps Ask AI on one diagnostic, and
 * this turns it into the message the agent receives — hands-free, as the AGENTS.md rules
 * promise. The turn continues in the chat page the app opens afterwards.
 */
object OpenCodePrompts {

    /** The one-shot rule for the agent when a diagnostic goes to it from an error row. */
    const val FIX_LINE =
        "A build error needs you, not me. Fix the sources and re-run closecode gradle " +
            "assembleDebug (closecode check first when the wording is unclear) until it passes"

    /** The server path that starts one turn in a V2 session. */
    internal const val MESSAGE_PATH = "/api/session/%s/prompt"

    /** A slow turn is normal; the server answers after the agent's reply is in. */
    internal const val CONNECT_TIMEOUT_MS = 5_000
    internal const val READ_TIMEOUT_MS = 600_000

    /** The message text for one diagnostic, so tests can hold the wording still. */
    fun fixPrompt(fileName: String, line: Int, message: String): String {
        val where = if (line > 0) "$fileName:$line" else fileName
        return "$FIX_LINE in $where:\n$message"
    }

    /** The same, straight from a problem row: what a row knows is file, line and message. */
    fun diagnosticPrompt(fileName: String, line: Int, message: String): String =
        fixPrompt(fileName, line.coerceAtLeast(0), message)

    /** The request body V2's `POST /api/session/:id/prompt` accepts. */
    internal fun bodyJson(prompt: String): String =
        JsonObject().apply { addProperty("text", prompt) }.toString()

    /** The URL one message goes to. */
    internal fun endpoint(baseUrl: String, sessionId: String): String =
        baseUrl.trimEnd('/') + MESSAGE_PATH.format(sessionId)

    /**
     * Sends the prompt into [sessionId] on the running server. True when the server accepted
     * the message (the turn runs on; the chat page shows it as it works).
     */
    fun send(
        baseUrl: String,
        sessionId: String,
        prompt: String,
        basicAuth: String = "",
    ): Boolean {
        if (baseUrl.isBlank() || sessionId.isBlank()) return false
        val body = bodyJson(prompt)
        return try {
            val connection = URL(endpoint(baseUrl, sessionId)).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                if (basicAuth.isNotBlank()) {
                    connection.setRequestProperty("Authorization", "Basic $basicAuth")
                }
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        } catch (_: Throwable) {
            false
        }
    }
}
