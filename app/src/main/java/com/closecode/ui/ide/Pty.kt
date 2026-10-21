package com.closecode.ui.ide

import android.os.ParcelFileDescriptor
import com.closecode.debug.DebugLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * The JNI bridge to libclosepty.so. The library is built by scripts/build-pty.ps1 with the
 * NDK and shipped in jniLibs; it is arm64 only, like the app.
 *
 * A failed load is not fatal: [available] stays false and the terminal keeps its pipe
 * fallback, which is a working shell with fewer terminal features, not a broken screen.
 */
internal object Pty {
    private val loaded: Boolean = runCatching { System.loadLibrary("closepty") }.isSuccess

    val available: Boolean get() = loaded

    /** Returns the master fd, or -1. The pid lands in [pidOut]. */
    external fun createSubprocess(
        command: String,
        workingDirectory: String,
        argv: Array<String>,
        env: Array<String>,
        pidOut: IntArray,
        rows: Int,
        cols: Int,
    ): Int

    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)
    external fun isAlive(pid: Int): Boolean
    external fun waitFor(pid: Int): Int
    external fun closeFd(fd: Int)
    external fun hangup(pid: Int)
}

/**
 * Turns the PTY's byte stream into transcript lines.
 *
 * A terminal writes progress with a carriage return and no newline ("50 %\r75 %\r100 %\n").
 * Drawing each of those as its own line would bury real output, so a bare CR rewrites the
 * line being built and only a newline commits it. CRLF is still an ordinary line ending.
 * Backspace edits the line, as the terminal would. Escape sequences are kept for
 * [TerminalLine], which draws their colors and hides the codes.
 *
 * Pure Kotlin so a JVM test can pin all of it without a native library.
 */
internal class PtyOutputBuffer(private val emit: (String) -> Unit) {
    private val line = StringBuilder()
    private var pendingCr = false
    private var lastDataAt = 0L

    @Synchronized
    fun feed(text: String) {
        for (character in text) {
            if (pendingCr) {
                pendingCr = false
                if (character == '\n') {
                    commit()
                    continue
                }
                line.clear()
            }
            when (character) {
                '\r' -> pendingCr = true
                '\n' -> commit()
                '\b' -> if (line.isNotEmpty()) line.deleteCharAt(line.length - 1)
                else -> line.append(character)
            }
        }
        if (text.isNotEmpty()) lastDataAt = System.currentTimeMillis()
    }

    /** Emits the unfinished line after a quiet moment, like the pipe reader does. */
    @Synchronized
    fun flushIfIdle(now: Long = System.currentTimeMillis(), idleMs: Long = 50) {
        if (line.isEmpty()) return
        if (now - lastDataAt < idleMs) return
        pendingCr = false
        commit()
    }

    /** Emits whatever is left when the stream ends. */
    @Synchronized
    fun flushAll() {
        if (pendingCr) {
            pendingCr = false
            commit()
        } else if (line.isNotEmpty()) {
            commit()
        }
    }

    private fun commit() {
        emit(line.toString())
        line.clear()
        lastDataAt = System.currentTimeMillis()
    }
}

/**
 * A shell on a real PTY. Programs see a terminal: colors, line wrapping by the kernel's
 * window size, and SIGINT from Ctrl+C. The transcript still draws its own prompt and the
 * typed command, and the kernel echo is off, so nothing appears twice.
 *
 * Only the output side is line-based. That is enough for `ls`, `git`, `ping`, `ps` and the
 * other things the phone actually has; a full-screen program that needs a character grid
 * is not supported, and the terminal does not pretend otherwise.
 */
internal class PtyShellBackend(
    private val directory: File?,
    private val onLine: (String) -> Unit,
) : ShellBackend {
    override val isPty: Boolean get() = true

    private var fd = -1
    private var pid = 0
    private var descriptor: ParcelFileDescriptor? = null
    private var output: OutputStream? = null
    private val writeLock = Any()
    private val buffer = PtyOutputBuffer(onLine)

    @Volatile
    private var alive = false

    private var columns = 80
    private var rows = 24

    override val isRunning: Boolean
        get() = alive && pid > 0 && Pty.available && Pty.isAlive(pid)

    override fun start() {
        if (alive) return
        closeResources()
        if (!Pty.available) {
            onLine("This phone could not load the terminal library.")
            return
        }
        val folder = directory ?: File("/")
        val env = TerminalEnvironment.env(folder)
        val argv = arrayOf("/system/bin/sh", "-i")
        val pidOut = IntArray(1)
        val master = runCatching {
            Pty.createSubprocess(
                command = "/system/bin/sh",
                workingDirectory = folder.absolutePath,
                argv = argv,
                env = env,
                pidOut = pidOut,
                rows = rows,
                cols = columns,
            )
        }.getOrElse { error ->
            DebugLog.crumb("TERMINAL", "pty start failed: ${error.javaClass.simpleName}")
            -1
        }
        if (master < 0 || pidOut[0] <= 0) {
            onLine("This phone could not start a terminal.")
            if (master >= 0) runCatching { Pty.closeFd(master) }
            return
        }
        fd = master
        pid = pidOut[0]
        val adopted = ParcelFileDescriptor.adoptFd(master)
        descriptor = adopted
        output = FileOutputStream(adopted.fileDescriptor)
        val stream = FileInputStream(adopted.fileDescriptor)
        alive = true
        DebugLog.line("TERMINAL") {
            "pty started pid=$pid size=${columns}x$rows in ${folder.absolutePath}"
        }
        // A PTY master reports no bytes as "available" even when the shell has written
        // output, so polling ready() never drains it. Block in read(); the flush thread
        // keeps an unfinished line (progress reported with a bare carriage return)
        // appearing without waiting for a newline.
        Thread {
            val input = InputStreamReader(stream, StandardCharsets.UTF_8)
            val chunk = CharArray(4096)
            Thread {
                while (alive) {
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        break
                    }
                    buffer.flushIfIdle()
                }
            }.apply {
                isDaemon = true
                name = "closepty-flush-$pid"
                start()
            }
            runCatching {
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (count > 0) buffer.feed(String(chunk, 0, count))
                }
            }
            buffer.flushAll()
            alive = false
        }.apply {
            isDaemon = true
            name = "closepty-$pid"
            start()
        }
    }

    override fun run(command: String) {
        if (!alive) start()
        val stream = output ?: return
        synchronized(writeLock) {
            runCatching {
                stream.write((shellLine(command) + "\n").toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            }
        }
    }

    override fun killRunning() {
        // The terminal driver turns this into SIGINT for the foreground process group.
        val stream = output ?: return
        synchronized(writeLock) {
            runCatching {
                stream.write(byteArrayOf(3))
                stream.flush()
            }
        }
        DebugLog.crumb("TERMINAL", "ctrl-c written to the pty")
    }

    override fun setSize(cols: Int, rows: Int) {
        columns = cols.coerceIn(20, 512)
        this.rows = rows.coerceIn(10, 512)
        if (fd >= 0 && Pty.available) Pty.setPtyWindowSize(fd, this.rows, columns)
    }

    override fun close() {
        alive = false
        if (pid > 0 && Pty.available) runCatching { Pty.hangup(pid) }
        closeResources()
        DebugLog.crumb("TERMINAL", "pty closed")
    }

    private fun closeResources() {
        // One descriptor backs both streams; closing it once is the whole cleanup.
        runCatching { descriptor?.close() }
        output = null
        descriptor = null
        fd = -1
        pid = 0
    }
}

/**
 * The environment every PTY shell gets, and the ENV file the interactive shell reads for
 * its aliases. Written once under the app's files folder; no project file is touched.
 */
internal object TerminalEnvironment {
    @Volatile
    private var base: File? = null

    fun configure(context: android.content.Context) {
        base = context.filesDir
    }

    fun env(folder: File): Array<String> {
        val envFile = base?.let { File(it, "terminal/env.sh") }
        if (envFile != null) {
            runCatching {
                envFile.parentFile?.mkdirs()
                envFile.writeText(
                    """
                    PS1=''
                    PS2=''
                    alias ls='ls --color=auto'
                    alias grep='grep --color=auto'
                    alias egrep='egrep --color=auto'
                    """.trimIndent() + "\n",
                )
            }
        }
        return buildList {
            add("TERM=xterm-256color")
            add("HOME=${folder.absolutePath}")
            add("PATH=/system/bin:/system/xbin")
            add("LANG=en_US.UTF-8")
            envFile?.let { add("ENV=${it.absolutePath}") }
        }.toTypedArray()
    }
}
