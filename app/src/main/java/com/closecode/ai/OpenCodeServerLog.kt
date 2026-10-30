package com.closecode.ai

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The server's own log file on disk.
 *
 * AndroidIDE appends the whole OpenCode output to a log file beside the server
 * (`OPENCODE_LOG_FILE`), so a failure is still explained after the app run that started the
 * server is long gone. CloseCode keeps the same record at `files/opencode/logs/server.log`,
 * with the previous run kept beside it in `server.1.log` — two files, never bigger than a
 * megabyte all told. The on-screen log is capped at a few hundred lines; this file carries
 * everything, so a black page or a silent restart never leaves nothing behind to read.
 */
class OpenCodeServerLog(
    private val dir: File,
    private val maxBytes: Int = ROTATE_BYTES,
) {

    private val lock = Any()

    /**
     * Starts a run: the previous replacement moves aside (if there is one), and a header names
     * the run that begins. Called once per server start.
     */
    fun runStarted(header: String) {
        synchronized(lock) {
            ensureDir()
            if (current().isFile) {
                moveAside()
            }
            runCatching { current().appendText("[${stampNow()}] $header\n") }
        }
    }

    /**
     * One line from the app, stamped; [warning] marks it with a `!` so the eye finds the one
     * bad line in a wall of routine ones.
     */
    fun appLine(text: String, warning: Boolean = false) {
        if (text.isBlank()) return
        synchronized(lock) {
            ensureDir()
            rotateIfNeeded()
            runCatching {
                current().appendText("[${stampNow()}]" + (if (warning) " !" else " ") + text + "\n")
            }
        }
    }

    /** The file this run writes to. */
    fun file(): File = current()

    /**
     * Moves the current file to its previous slot. `Files.move` first — an atomic rename never
     * leaves the two files half-written — and a copy as the fallback for a file system that
     * refuses an atomic move across its own details.
     */
    private fun moveAside() {
        val file = current()
        if (!file.isFile) return
        val target = previous()
        val moved = runCatching {
            Files.move(
                file.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.isSuccess
        if (moved) return
        runCatching {
            file.copyTo(target, overwrite = true)
            file.delete()
        }
    }

    /** Where a rotating file goes when the current one grows past the cap. */
    private fun rotateIfNeeded() {
        if (current().isFile && current().length() > maxBytes) {
            moveAside()
        }
    }

    private fun ensureDir() {
        if (!dir.isDirectory) dir.mkdirs()
    }

    private fun current(): File = File(dir, "server.log")

    private fun previous(): File = File(dir, "server.1.log")

    companion object {

        /** A lone file stays under this many bytes before it moves aside. */
        internal const val ROTATE_BYTES = 512_000

        /** The directory the log is kept in. */
        fun dir(context: Context): File = File(context.filesDir, "opencode/logs")

        /** A timestamp for a log line. A new formatter per call: SimpleDateFormat is not thread-safe. */
        internal fun stampNow(): String {
            return SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        }
    }
}
