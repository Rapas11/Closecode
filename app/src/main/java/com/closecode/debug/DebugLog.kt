package com.closecode.debug

import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * The debug log (spec, Debug tools, item 2): a switch, off by default and off again at every
 * app start, that keeps the last 500 lines in memory and in `debug/debug.log` (never past
 * 1 MB — it is rewritten from the lines still in memory when it would grow further).
 *
 * Each line is `HH:mm:ss.SSS AREA message`. The areas are a fixed vocabulary so a report can
 * be filtered for one question. Facts only: what happened, with what numbers — never secrets
 * (no token, no typed command text; the terminal logs its size, nothing else).
 *
 * Off costs nothing: [line] checks the switch before its lambda runs, so a caller can describe
 * an event knowing the description is never built while the switch is off. The breadcrumb ring
 * ([crumb]) is always on and deliberately tiny — it is what the crash report attaches — but it
 * only reaches the file while the switch is on.
 */
object DebugLog {

    /** The fixed area tags. A typo here would break the reader's filter, so they live once. */
    val areas = listOf(
        "BACK",
        "KEYBOARD",
        "SCROLL",
        "BUILD",
        "TOOLS",
        "INSTALL",
        "PREVIEW",
        "TERMINAL",
        "GITHUB",
        "CRASH",
    )

    const val MAX_LINES = 500
    const val MAX_CRUMBS = 100
    const val MAX_FILE_BYTES = 1_000_000L

    @Volatile
    var enabled: Boolean = false
        private set

    private var file: File? = null
    private var header: String = ""
    private val lines = ArrayDeque<String>(MAX_LINES)
    private val crumbs = ArrayDeque<String>(MAX_CRUMBS)
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Points the log at `<root>/debug/debug.log` and remembers the version line. Once per start. */
    @Synchronized
    fun attach(root: File, versionLabel: String) {
        file = File(File(root, "debug"), "debug.log")
        header = versionLabel
    }

    @Synchronized
    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (on) writeHeaderLine()
    }

    /** Detailed lines while the switch is on. The lambda only runs when it is. */
    fun line(area: String, message: () -> String) {
        if (!enabled) return
        record(stamped(area, message()))
    }

    /**
     * A short always-recorded event line — the crash report's last [MAX_CRUMBS] events. Written
     * to the log file too while the switch is on; the string is short on purpose, because this
     * runs on ordinary event paths rather than only under the switch.
     */
    @Synchronized
    fun crumb(area: String, text: String) {
        val entry = stamped(area, text)
        if (crumbs.size >= MAX_CRUMBS) crumbs.removeFirst()
        crumbs.addLast(entry)
        if (enabled) record(entry)
    }

    /** The last [MAX_LINES] lines, oldest first — what Copy log and reports take. */
    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    /** The breadcrumb ring, oldest first — what the crash report attaches. */
    @Synchronized
    fun crumbs(): List<String> = crumbs.toList()

    /** Clears the memory ring and the file, keeping the version line at its top. */
    @Synchronized
    fun clear() {
        lines.clear()
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            target.writeText(header + "\n")
        }
    }

    @Synchronized
    internal fun resetForTests() {
        enabled = false
        file = null
        header = ""
        lines.clear()
        crumbs.clear()
    }

    @Synchronized
    private fun record(entry: String) {
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast(entry)
        appendToFile(entry)
    }

    private fun stamped(area: String, text: String): String {
        val now = synchronized(clock) { clock.format(Date()) }
        return "$now $area $text"
    }

    private fun appendToFile(entry: String) {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            if (!target.isFile) target.writeText(header + "\n")
            target.appendText(entry + "\n")
            if (target.length() > MAX_FILE_BYTES) rotate(target)
        }
    }

    /** Never past ~1 MB: rewritten from the 500 lines still in memory, newest kept. */
    private fun rotate(target: File) {
        val kept = ArrayList<String>(lines.size + 1)
        kept.add(header)
        kept.addAll(lines)
        target.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun writeHeaderLine() {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            if (target.isFile && target.length() > 0L) target.appendText("--- new session ---\n")
            target.appendText(header + "\n")
        }
    }
}
