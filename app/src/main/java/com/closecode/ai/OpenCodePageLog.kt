package com.closecode.ai

import androidx.compose.runtime.mutableStateOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * What OpenCode's own web page says while it runs.
 *
 * The AI tab shows these lines beside the server log, and its "Copy page log" button copies
 * them. They are the page's console messages plus the network failures the app's small fetch,
 * XHR and event-source hooks report: a page that loads but whose buttons do nothing usually
 * says why here, as a 401 or a JavaScript error, instead of leaving the reason a guess.
 *
 * The log is deliberately small and never contains a password or a token. It is bounded so a
 * page that logs every animation frame cannot grow the app's memory forever.
 */
object OpenCodePageLog {
    private const val MAX_LINES = 200
    private const val MAX_LINE_LENGTH = 500
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val state = mutableStateOf<List<String>>(emptyList())

    /** The newest lines, oldest first. */
    val lines: List<String> get() = state.value

    /** Adds one line, stamped with the time, and drops the oldest lines past the cap. */
    fun add(text: String) {
        val line = text.trim()
        if (line.isEmpty()) return
        val stamped = "${LocalTime.now().format(clock)} ${line.take(MAX_LINE_LENGTH)}"
        state.value = (state.value + stamped).takeLast(MAX_LINES)
    }

    /** Empties the log. */
    fun clear() {
        state.value = emptyList()
    }
}
