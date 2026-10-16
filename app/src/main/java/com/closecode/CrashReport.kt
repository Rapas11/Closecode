package com.closecode

import com.closecode.debug.DebugLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last thing a crash leaves behind: a report written to a file before the process dies,
 * so the next launch can show it instead of swallowing it. There is no logcat on a phone,
 * so without this a crash report is "it closed" and nothing else.
 *
 * The file starts with the version label — which APK it was — and ends with the debug log's
 * last hundred breadcrumbs, the short event lines that are always recorded, so the report
 * says what the app was doing when it died.
 */
internal object CrashReport {
    private const val DIR = "crash"
    private const val FILE_NAME = "last-crash.txt"

    /** The pre-debug-tools location, still read so a crash from an older APK is not lost. */
    private const val LEGACY_NAME = "last_crash.txt"

    private const val MAX_CHARS = 16_000

    /**
     * Records the uncaught exception and then lets the app die exactly as it would have: the
     * previous handler still runs, so crash behaviour never changes, only its memory does.
     * [label] is called at crash time so the report carries the running APK's version line.
     */
    fun install(filesDir: File, label: () -> String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                write(filesDir, thread.name, throwable, label(), DebugLog.crumbs())
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The report's text shape. Pure, so the wording is pinned by a unit test. */
    internal fun compose(
        label: String,
        time: String,
        threadName: String,
        throwable: Throwable,
        crumbs: List<String>,
    ): String = buildString {
        appendLine(label)
        appendLine("crashed at $time on thread $threadName")
        appendLine()
        appendLine(throwable.stackTraceToString().take(MAX_CHARS))
        if (crumbs.isNotEmpty()) {
            appendLine()
            appendLine("--- the last ${crumbs.size} events ---")
            crumbs.forEach { appendLine(it) }
        }
    }

    internal fun write(
        filesDir: File,
        threadName: String,
        throwable: Throwable,
        label: String,
        crumbs: List<String>,
    ) {
        val dir = File(filesDir, DIR)
        dir.mkdirs()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        File(dir, FILE_NAME).writeText(
            compose(label, time, threadName, throwable, crumbs),
        )
    }

    /** The report left by the previous run, or null when it exited cleanly. */
    fun readLast(filesDir: File): String? {
        val file = File(File(filesDir, DIR), FILE_NAME)
        if (file.isFile) return file.readText().takeIf { it.isNotBlank() }
        val legacy = File(filesDir, LEGACY_NAME)
        if (legacy.isFile) return legacy.readText().takeIf { it.isNotBlank() }
        return null
    }

    /** Forgets the recorded crash, so it is shown once and never again. */
    fun clear(filesDir: File) {
        runCatching { File(File(filesDir, DIR), FILE_NAME).delete() }
        runCatching { File(filesDir, LEGACY_NAME).delete() }
    }
}
