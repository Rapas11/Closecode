package com.closecode.ui.ide

import java.io.File

/**
 * Part B terminal seam. The panel talks to this interface; the PTY is the default and the
 * old pipe stays behind the hidden Use pipe terminal switch as the fallback, with zero
 * panel changes. [isPty] lets the self-test tell a real terminal from the pipe so it never
 * reports a pass for the wrong engine.
 */
interface ShellBackend {
    val isRunning: Boolean
    val isPty: Boolean get() = false
    fun start()
    fun run(command: String)
    fun killRunning()
    fun setSize(cols: Int, rows: Int)
    fun close()
}

/** The pipe implementation, unchanged in behavior, behind the interface. */
internal class PipeShellBackend(
    directory: File?,
    onLine: (String) -> Unit,
) : ShellBackend {
    private val shell = ShellProcess(directory = directory, onLine = onLine)

    override val isRunning: Boolean get() = shell.isRunning
    override fun start() = shell.start()
    override fun run(command: String) = shell.run(command)
    override fun killRunning() = shell.killRunning()
    override fun setSize(cols: Int, rows: Int) = shell.setSize(cols, rows)
    override fun close() = shell.close()
}

/**
 * A real PTY unless the reader chose the pipe. A library that will not load is not a
 * reason to leave the terminal empty: the pipe still runs, and [isPty] stays false so the
 * self-test says which one answered.
 */
internal fun openShell(
    directory: File?,
    onLine: (String) -> Unit,
    usePipe: Boolean = false,
): ShellBackend =
    if (!usePipe && Pty.available) {
        PtyShellBackend(directory, onLine)
    } else {
        PipeShellBackend(directory, onLine)
    }
