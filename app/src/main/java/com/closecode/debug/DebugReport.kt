package com.closecode.debug

import android.content.Context
import android.content.Intent
import com.closecode.CrashReport

/**
 * The report a person can copy and send: the version line first, then the last self-test
 * result, the last 200 log lines and the last crash. Every part is whatever the phone
 * measured — nothing is guessed, and no secret (token, typed command) is ever a part of it.
 */
object DebugReport {

    /** Pure text shape, so the wording is pinned by a unit test. */
    fun build(
        header: String,
        selfTest: String?,
        logLines: List<String>,
        crash: String?,
    ): String = buildString {
        appendLine(header)
        appendLine()
        appendLine("--- last self-test ---")
        appendLine(selfTest?.takeIf { it.isNotBlank() } ?: "(no self-test has run yet)")
        appendLine()
        val recent = logLines.takeLast(200)
        appendLine("--- last ${recent.size} log lines ---")
        if (recent.isEmpty()) {
            appendLine("(the debug log is off — no lines)")
        } else {
            recent.forEach { appendLine(it) }
        }
        appendLine()
        appendLine("--- last crash ---")
        appendLine(crash?.takeIf { it.isNotBlank() } ?: "(none)")
    }

    /** The same report for the phone this runs on. */
    fun compose(context: Context): String = build(
        header = BuildInfo.label,
        selfTest = SelfTest.lastReport,
        logLines = DebugLog.snapshot(),
        crash = CrashReport.readLast(context.filesDir),
    )
}

/**
 * Hands a report to whatever the phone uses to send a plain text — mail, a chat app, notes.
 * Returns false when no app accepts it, so the caller can say so instead of failing silently.
 */
fun sharePlainText(context: Context, subject: String, text: String): Boolean = runCatching {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, subject)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
    true
}.getOrDefault(false)
