package com.closecode.build

import java.util.concurrent.TimeUnit

/**
 * The built app's log, as far as Android allows this app to read it. Since Android 4.1
 * an ordinary app may only read its own log lines, so `logcat` run from CloseCode usually
 * returns CloseCode's lines and system lines, not the opened app's. That is a system
 * limit, not an installation failure, and the snapshot carries a plain note saying so.
 *
 * The parsing and filtering are pure so the panel is tested on the JVM.
 */
object LogcatReader {

    enum class Level { Error, Warning, Info, Other }

    data class Line(
        val index: Int,
        val level: Level,
        val text: String,
    )

    data class Snapshot(
        val lines: List<Line>,
        val note: String? = null,
    )

    fun read(packageName: String, limit: Int = 600): Snapshot {
        if (packageName.isBlank()) {
            return Snapshot(emptyList(), "Open an app first, then its log appears here.")
        }
        return runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "brief", "-t", "1500")
                .redirectErrorStream(true)
                .start()
            val raw = process.inputStream.bufferedReader().use { it.readLines() }
            if (!process.waitFor(4, TimeUnit.SECONDS)) process.destroy()
            val parsed = raw.mapIndexed { index, text -> line(index, text) }
            val matched = parsed.filter { entry ->
                entry.text.contains(packageName) ||
                    entry.text.contains("FATAL EXCEPTION") ||
                    entry.text.contains("AndroidRuntime")
            }.takeLast(limit)
            if (matched.isEmpty()) {
                Snapshot(
                    emptyList(),
                    "Android does not let CloseCode read another app's log lines. " +
                        "This is a system limit. CloseCode's own lines are still below.",
                )
            } else {
                Snapshot(matched)
            }
        }.getOrElse { error ->
            Snapshot(
                emptyList(),
                "Could not read the system log (${error.message ?: error.javaClass.simpleName}). " +
                    "Android limits what an app can see.",
            )
        }
    }

    /** A logcat brief line: `D/Tag( 1234): message`. */
    fun line(index: Int, text: String): Line {
        val level = when {
            text.startsWith("E/") -> Level.Error
            text.startsWith("W/") -> Level.Warning
            text.startsWith("I/") -> Level.Info
            else -> Level.Other
        }
        return Line(index, level, text)
    }

    fun visible(
        lines: List<Line>,
        level: Level?,
        query: String,
    ): List<Line> {
        val needle = query.trim()
        return lines.filter { entry ->
            (level == null || entry.level == level) &&
                (needle.isEmpty() || entry.text.contains(needle, ignoreCase = true))
        }
    }
}
