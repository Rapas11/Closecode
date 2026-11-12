package com.closecode.ui.ide

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.debug.DebugLog
import com.closecode.ui.components.HeaderAction
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.BufferedWriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The shell the terminal runs. Android ships one here and nowhere else. */
private const val SHELL_PATH = "/system/bin/sh"

/**
 * How much output the terminal keeps: the last 5,000 lines. Long enough that a build's worth of
 * output is still scrollable, small enough that a command that never stops cannot grow the panel
 * forever. Trimming drops from the top; the panel compensates the list position while unfollowed
 * so the view never jumps.
 */
const val TERMINAL_MAX_LINES = 5_000

/**
 * The line a command is followed by so the panel learns how it ended. The shell prints the status
 * on the same stream the command's own output arrives on, which is the only channel there is.
 */
internal const val EXIT_MARKER = "cc-exit:"

/**
 * The line actually written to the shell: the command, then the marker carrying its status.
 *
 * The marker is a comment-free echo rather than anything the command has to cooperate with, so
 * `ls` and a script that exits deep inside a subshell report the same way.
 */
internal fun shellLine(command: String): String = "$command ; echo \"$EXIT_MARKER${'$'}?\""

/**
 * The exit status out of a marker line, or null for any other line.
 *
 * Null is the common case — every line of ordinary output — so the panel's read loop can treat
 * "not a marker" as "show this" without a second test, and the marker itself is never shown.
 */
internal fun parseExitMarker(line: String): Int? {
    val trimmed = line.trim()
    if (!trimmed.startsWith(EXIT_MARKER)) return null
    return trimmed.removePrefix(EXIT_MARKER).toIntOrNull()
}

/**
 * One command, or nothing.
 *
 * A paste that carries newlines would otherwise run as several commands nobody has read, so only
 * the first line is ever sent. What is typed is never written to a log either: a terminal holds
 * whatever the user pasted, and a paste is exactly where a token shows up.
 */
internal fun sanitizeCommand(raw: String): String = raw.lineSequence().first().trim()

/**
 * Appends while enforcing [TERMINAL_MAX_LINES]. Same shape as the log's own cap: dropping the
 * oldest lines is one array copy, which is cheap enough per line and keeps the bound strict.
 * Returns how many old lines were dropped, so the panel can hold an unfollowed view still.
 */
internal fun <T> MutableList<T>.appendTerminalLine(line: T): Int {
    add(line)
    if (size > TERMINAL_MAX_LINES) {
        val dropped = size - TERMINAL_MAX_LINES
        subList(0, dropped).clear()
        return dropped
    }
    return 0
}

/** Which side of the chat-style log a row belongs to. */
internal enum class TermWho { You, Shell }

/**
 * One drawn row: an echo of a command (You) or output from the shell. Consecutive output lines
 * share one name/time header so a build log does not repeat the shell's avatar on every line.
 */
internal data class TermMessage(
    val index: Int,
    val who: TermWho,
    val text: String,
    val prompt: String?,
    val at: Long,
    val tone: TermTone,
    val showHeader: Boolean,
)

internal fun buildTermMessages(
    lines: List<String>,
    meta: List<TermMeta>,
): List<TermMessage> {
    val rows = ArrayList<TermMessage>(lines.size)
    var previous: TermWho? = null
    lines.forEachIndexed { index, line ->
        val echo = splitPrompt(line)
        val who = if (echo != null) TermWho.You else TermWho.Shell
        val m = meta.getOrNull(index)
        rows += TermMessage(
            index = index,
            who = who,
            text = echo?.second ?: line,
            prompt = echo?.first,
            at = m?.at ?: 0L,
            tone = m?.tone ?: TermTone.Output,
            showHeader = who != previous,
        )
        previous = who
    }
    return rows
}

internal fun termClock(at: Long): String =
    if (at <= 0L) "" else java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(at)

/**
 * One shell, kept alive for as long as the terminal is on screen.
 *
 * The process is real, so `cd` and exported variables survive between commands — which is the
 * whole reason a terminal exists rather than a run-once script box. Output arrives on a daemon
 * reader thread and is handed straight to [onLine]; the panel is what moves it onto the main
 * thread before it touches any state.
 */
internal class ShellProcess(
    private val directory: File?,
    private val onLine: (String) -> Unit,
    private val shellPath: String = SHELL_PATH,
) {
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    // The size the shell believes it has. Pipes carry no terminal size, so the panel measures
    // its own width, tells the shell in characters (COLUMNS now), and keeps the numbers for the
    // day a PTY replaces the pipe (its size call then takes these instead of an export).
    var columns: Int = 80
        private set
    var rows: Int = 24
        private set

    val isRunning: Boolean get() = process?.isAlive == true

    fun start() {
        if (process?.isAlive == true) return
        // A dead shell restarts instead of staying dead. Killing it is the only Ctrl+C there is,
        // and `exit` typed at the prompt ends it too — neither must brick the tab, leaving every
        // later command silently swallowed with no output and no error.
        runCatching { stdin?.close() }
        runCatching { process?.destroy() }
        process = null
        stdin = null
        val started = runCatching {
            val builder = ProcessBuilder(shellPath)
            directory?.let { builder.directory(it) }
            // Programs decide their colors from TERM: xterm-256color is the honest answer for
            // a panel that parses 256 colors and truecolor. (There is no PTY behind this
            // shell — piping through one needs native code this app does not ship — so
            // programs that color only on a terminal get their color forced below instead.)
            // The same environment as the PTY puts the app's terminal commands on PATH.
            TerminalEnvironment.applyTo(builder.environment(), directory ?: File("/"))
            // Standard error is folded into standard output rather than given its own pipe.
            //
            // It used to have one, and nothing ever read it. A pipe holds about 64 KB and the shell
            // blocks the moment it fills, so any command that wrote more than that to stderr — a
            // build's warnings, a `grep` that had something to say, a download — stopped dead with
            // no output, no prompt and no error: the terminal simply went quiet and stayed quiet.
            // That is the worst possible failure for a shell, and it was reachable from one line
            // of ordinary use.
            //
            // Merging also gets the ordering right, which two reader threads could not: the panel
            // then shows exactly the sequence the shell wrote, rather than two streams interleaved
            // by whichever thread the scheduler ran first.
            builder.redirectErrorStream(true)
            builder.start()
        }.getOrNull() ?: return
        process = started
        stdin = started.outputStream.bufferedWriter()
        DebugLog.line("TERMINAL") { "shell started in ${directory?.absolutePath ?: "no folder"}" }
        Thread {
            runCatching {
                // Chunked reader, not line-blocked: a line is drawn after its newline
                // arrives, or after 50 ms without more data for the final partial line.
                // Reading runs until the stream ends and the last chunk is flushed, so
                // output is never cut — the missing tail of ls -la was the reader
                // stopping early, and a stuck reader is measured against the raw bytes.
                val reader = started.inputStream.bufferedReader()
                val pending = StringBuilder()
                val chunk = CharArray(4096)
                var lastDataAt = System.currentTimeMillis()
                fun emitLine(raw: String) {
                    val line = if (raw.endsWith("\r")) raw.dropLast(1) else raw
                    onLine(line)
                }
                try {
                    while (true) {
                        if (reader.ready()) {
                            val count = reader.read(chunk)
                            if (count < 0) break
                            pending.append(chunk, 0, count)
                            lastDataAt = System.currentTimeMillis()
                            var cut = pending.indexOf("\n")
                            while (cut >= 0) {
                                emitLine(pending.substring(0, cut))
                                pending.delete(0, cut + 1)
                                cut = pending.indexOf("\n")
                            }
                        } else {
                            if (pending.isNotEmpty() &&
                                System.currentTimeMillis() - lastDataAt >= 50
                            ) {
                                emitLine(pending.toString())
                                pending.clear()
                            }
                            Thread.sleep(10)
                        }
                    }
                } finally {
                    if (pending.isNotEmpty()) {
                        emitLine(pending.toString())
                        pending.clear()
                    }
                    runCatching { reader.close() }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        // Folders draw blue and git diffs green: plain `ls` and `git` ask for color on
        // every invocation, and the panel already parses ANSI, so the escapes turn into the
        // palette's colors. Shell functions rather than aliases, because a piped shell does
        // not expand aliases. Always rather than auto: auto means "only on a terminal", and
        // this shell is a pipe, so auto would color nothing. Silenced: a shell without the
        // flags says so once into the void, not into the transcript.
        runCatching {
            stdin?.write("ls() { command ls --color=always \"$@\"; } 2>/dev/null\n")
            stdin?.write("git() { command git -c color.ui=always \"$@\"; } 2>/dev/null\n")
            stdin?.write("export COLUMNS=$columns LINES=$rows\n")
            stdin?.flush()
        }
    }

    /** Sends one command, restarting a dead shell first. Nothing typed is ever handed to a logger. */
    fun run(command: String) {
        if (process?.isAlive != true) start()
        val writer = stdin ?: return
        runCatching {
            writer.write(shellLine(command))
            writer.write("\n")
            writer.flush()
        }
    }

    /**
     * Tells a running shell how wide the panel is, in characters, so `ls` and Gradle format to
     * fit instead of the 80 they assume behind a pipe. A restart re-exports on its own via
     * [start], so a resize is never lost — only delayed until the shell is alive to hear it.
     */
    fun setSize(cols: Int, lines: Int = 24) {
        val newCols = cols.coerceIn(20, 512)
        val newRows = lines.coerceIn(10, 512)
        val changed = newCols != columns || newRows != rows
        columns = newCols
        rows = newRows
        if (changed) DebugLog.line("TERMINAL") { "size ${columns}x$rows" }
        val writer = stdin
        if (process?.isAlive == true && writer != null) {
            runCatching {
                writer.write("export COLUMNS=$columns LINES=$rows\n")
                writer.flush()
            }
        }
    }

    /**
     * Kills the shell's current process, standing in for an interrupt.
     *
     * CTRL+C on a real terminal raises SIGINT against whatever is in the foreground. Here the shell
     * is fed through a pipe, which cannot deliver a signal to the process behind it, and there is no
     * foreground process to deliver one to anyway — the shell reads one line, runs it to completion
     * and comes back for more. Killing it is the closest honest equivalent, and it is what makes
     * CTRL+C stop a runaway command instead of sitting there looking busy.
     */
    fun killRunning() {
        runCatching { process?.destroy() }
        stdin?.close()
        stdin = null
        process = null
    }

    fun close() {
        runCatching { stdin?.close() }
        runCatching { process?.destroy() }
        stdin = null
        process = null
        DebugLog.line("TERMINAL") { "shell closed" }
    }
}

/**
 * The saved commands belong to the project, not the terminal panel: the sidebar shows the same
 * list and can add to it, so the state lives above both surfaces.
 */
internal class TerminalCommandsState(
    initial: List<String>,
    private val persist: (List<String>) -> Unit,
) {
    var commands by mutableStateOf(initial)
        private set

    fun replace(next: List<String>) {
        commands = next
        persist(next)
    }
}

@Composable
internal fun rememberTerminalCommandsState(projectSlug: String): TerminalCommandsState {
    val context = LocalContext.current
    val kv = remember { com.closecode.ui.settings.SharedPreferencesKeyValueStore(context) }
    return remember(projectSlug) {
        TerminalCommandsState(
            decodeCommands(kv.getString(TERMINAL_COMMANDS_KEY, null)).ifEmpty {
                DefaultTerminalCommands
            },
        ) { next ->
            kv.putString(TERMINAL_COMMANDS_KEY, encodeCommands(next))
        }
    }
}

/**
 * A shell in the project's folder.
 *
 * Real, not simulated: the process is the device's own shell, started when the panel opens and
 * closed when it leaves. There is no git, no Gradle and no build tool in here on purpose — the
 * terminal runs what Android itself ships and nothing else, so it never offers a tool the app
 * cannot deliver.
 *
 * Because the process is fed through a pipe rather than a terminal, the shell has no line editor of
 * its own: history, the caret, CTRL+A and the arrows are all provided here instead. That is not a
 * substitute for a real terminal — job control and a program's own cursor handling still do not
 * work — but it is what makes the shell usable on a phone rather than write-only.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun IdeTerminal(
    projectRoot: File?,
    projectSlug: String = "",
    projectName: String = "",
    // Held outside this panel on purpose — see TerminalSessions.
    sessions: TerminalSessions = TerminalSessions(),
    commandsState: TerminalCommandsState? = null,
    // Bumped by the sidebar when its Add command action must open the full-page form.
    newCommandRequest: Int = 0,
    tree: List<ProjectFile> = emptyList(),
    // The hidden Developer switch. New shells honour it; running ones keep their engine.
    usePipe: Boolean = false,
    onOpenPath: (ProjectFile) -> Unit = {},
    onFilesChanged: () -> Unit = {},
    onOpenTerminalSettings: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    TerminalEnvironment.configure(context)
    val density = LocalDensity.current

    // The Python runtime's own ensurepip bundle is checked once per app run, so a runtime
    // that was upgraded under the app does not leave `python3 -m venv` broken. The check is
    // a file test; only a missing wheel costs a download.
    suspend fun repairPythonOnce() {
        withContext(Dispatchers.IO) {
            runCatching {
                com.closecode.tools.ShellToolsStore(
                    context,
                    com.closecode.tools.ShellToolsManifest.load(context),
                ).repairPythonOnce()
            }
        }
    }
    var ctrl by remember { mutableStateOf(ModifierLatch.OFF) }
    var alt by remember { mutableStateOf(ModifierLatch.OFF) }
    var completionCandidates by remember { mutableStateOf(emptyList<Completion>()) }
    var completionCycle by remember { mutableStateOf(0) }
    var completionStart by remember { mutableStateOf(0) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val promptFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val fallbackCommands = rememberTerminalCommandsState(projectSlug)
    val commandsStore = commandsState ?: fallbackCommands
    val savedCommands = commandsStore.commands
    fun persistCommands(next: List<String>) {
        commandsStore.replace(next)
    }

    // The list and the selection belong to the holder, so what this panel reads is a projection of
    // state that outlives the panel. Nothing below may reassign them.
    val sessionList = sessions.sessions
    val activeId = sessions.activeId
    val session = sessions.active()
    val shellName = session.customName?.takeIf { it.isNotBlank() } ?: "Shell ${session.number}"
    val listState = rememberLazyListState()
    val historyStore = rememberTerminalHistory(projectSlug, settings.commandHistory)
    val pipeShell = usePipe || settings.shell == "System"
    // The output width in pixels, measured so the shell can be told its width in characters.
    // A monospace glyph advances about six tenths of its point size; the number is an estimate
    // the shell formats against, not a promise, and anything sane beats the bare 80.
    var termWidthPx by remember { mutableStateOf(0) }
    LaunchedEffect(termWidthPx, activeId) {
        if (termWidthPx > 0) {
            val charPx = with(density) { (11.5.sp * 0.6f).toPx() }
            sessions.active().setSize((termWidthPx / charPx).toInt().coerceIn(20, 512))
        }
    }

    // Commands run before this screen is opened are still reachable, so the list is read once on
    // arrival rather than starting empty every time the panel is opened. Every tab starts with it:
    // the commands are the reader's, not one tab's.
    LaunchedEffect(projectSlug, settings.commandHistory) {
        val remembered = if (projectSlug.isEmpty()) emptyList() else historyStore.read(projectSlug)
        sessions.applyHistory(remembered)
    }

    // Every session is started and drained for as long as the panel is open, whether or not it is
    // the one on screen. A shell whose output stopped being read while it sat in another tab would
    // fill its pipe and wedge — the same hang that folding standard error into standard output was
    // there to prevent, arriving through a different door.
    LaunchedEffect(sessions) {
        sessions.sessions.forEach { session ->
            launch {
                withContext(Dispatchers.IO) { session.start(projectRoot, pipeShell) }
                // A Python runtime that was upgraded under the app can leave the pip wheel its
                // own ensurepip asks for missing, and `python3 -m venv` then fails. The repair
                // runs once per app run, in the background, so a shell is never the reason a
                // Python project cannot start.
                withContext(Dispatchers.IO) { repairPythonOnce() }
                if (!session.isRunning) {
                    session.appendLine(
                        if (projectRoot == null) {
                            "Open a project first: the shell starts in its folder."
                        } else {
                            "This device has no shell to run."
                        },
                        TermTone.Warning,
                    )
                } else {
                    // A command handed over before this panel was composed: the AI tab's
                    // Authenticate action uses this to start OpenCode's login in a fresh PTY.
                    session.pendingCommand?.let { command ->
                        session.pendingCommand = null
                        val prompt = promptPrefix(displayDir(projectName, session.cwd))
                        if (session.submit(command, projectSlug, historyStore, prompt)) {
                            onFilesChanged()
                        }
                    }
                }
                // Drained here rather than on the reader thread: compose state is written on the
                // thread that owns the composition, and the reader is not it.
                for (line in session.output) {
                    val exit = parseExitMarker(line)
                    if (exit != null) {
                        session.lastExit = exit
                        session.commandRunning = false
                        session.finishRun(exit)
                    } else {
                        // Short, plain errors: shell noise is rewritten before drawing.
                        val clean = rewriteShellNoise(line)
                        session.appendLine(
                            clean,
                            if (isErrorLine(clean)) TermTone.Error else TermTone.Output,
                        )
                    }
                }
            }
        }
    }

    // The session on screen is read from the holder above, because it outlives this panel: two tabs
    // are two shells rather than two readings of one.

    // Keyed on the unit, deliberately: a DisposableEffect keyed on a value is disposed and recreated
    // every time that value changes, and this one's cleanup closed *every* session. Adding or closing
    // a shell tab therefore changed the key, ran the cleanup against the new list, and killed the
    // server the reader had running in the first tab — without ever leaving the panel. Its own
    // comment even said so, and the tests pinned the geometry rather than the lifetime.
    // Nothing is torn down when this panel goes away, and that is the point: the shells belong to
    // the holder now, so a screen swap finds the same processes and the same output.
    //
    // What this does still own is the reader thread. Draining stopped when the panel is unmounted,
    // so the holder has to drain instead — see MainActivity.


    /** The prompt as drawn for the active shell, and echoed back in front of its commands. */
    fun livePrompt(): String =
        promptPrefix(displayDir(projectName, session.cwd))

    /** Remembers, runs and clears the line: Enter, the Commands sheet and Tab all end here. */
    fun submit(text: String) {
        val command = sanitizeCommand(text)
        if (command.isEmpty()) return
        // Typing, Enter and every started command re-engage follow: the reader asked for new
        // output, so the view goes back to the bottom to watch it arrive.
        session.followOutput = true
        session.hasNewOutput = false
        // `code` is not a program and never runs. It is the app's own way of saying "show me this
        // file", and it is handled before the shell sees it so that `code` followed by a name opens
        // the editor rather than reporting a command that does not exist.
        val target = codeTarget(command)
        if (target != null) {
            // Echoed once, in the same shape as a run command: the scrollback reads as one
            // transcript whether the line ran or opened a file.
            session.appendLine(livePrompt() + command, TermTone.User)
            val file = codeTargetFile(tree, session.cwd, target)
            if (file != null) onOpenPath(file)
            session.recordAppCommand(command, tree, projectSlug, historyStore, opened = file)
            return
        }

        // The drawer follows the terminal: a file made or removed here is a file the reader expects
        // to see listed, and the alternative is a list that is quietly wrong until something else
        // happens to refresh it.
        if (session.submit(command, projectSlug, historyStore, livePrompt())) onFilesChanged()
    }

    fun send() = submit(session.input.text)

    fun dismissCompletion() {
        completionCandidates = emptyList()
        completionCycle = 0
    }

    /** A latched modifier gives up its hold after the one key it was armed for; a locked one holds. */
    fun finishModifiers() {
        if (modifierConsumes(ctrl)) ctrl = ModifierLatch.OFF
        if (modifierConsumes(alt)) alt = ModifierLatch.OFF
    }

    fun insertAtCaret(text: String) {
        // Anything typed by hand closes the list: the candidates were worked out against a word
        // that no longer exists, and leaving them up would offer names that do not fit it.
        dismissCompletion()
        if (text.isEmpty()) return
        val at = session.input.selection.start.coerceIn(0, session.input.text.length)
        session.input = TextFieldValue(
            session.input.text.substring(0, at) + text + session.input.text.substring(at),
            TextRange(at + text.length),
        )
    }

    /** Sets the caret, as every arrow and every "go to the edge" shortcut amounts to. */
    fun moveCaretTo(at: Int) {
        session.input = session.input.copy(selection = TextRange(at.coerceIn(0, session.input.text.length)))
    }

    /**
     * One press of Tab.
     *
     * The first press adds whatever the candidates have in common, or puts the list up when there is
     * nothing more to add; a later press cycles through them. The candidates are worked out once and
     * kept, because recomputing them on every press would make the second Tab cycle through a
     * freshly-ordered list and appear to pick a different name each time.
     */
    fun complete() {
        val text = session.input.text
        val cursor = session.input.selection.start.coerceIn(0, text.length)
        // A completion round belongs to the line it was computed for. Typing through the
        // system keyboard, deleting, or moving the cursor after it started leaves a start that
        // points into an older line — slicing that crashed on an empty prompt — so the round
        // is dropped and begun again from the word under the cursor now.
        if (completionCandidates.isNotEmpty() && wordAt(text, cursor).first != completionStart) {
            dismissCompletion()
        }
        val (start, token) = wordAt(text, cursor)
        if (completionCandidates.isEmpty()) {
            val (dirPart, namePart) = splitPath(token)
            completionCandidates = completionCandidates(
                dir = resolvePath(session.cwd, dirPart.ifEmpty { "" }),
                namePart = namePart,
                tree = tree,
            )
            completionStart = start
            completionCycle = 0
        }
        val (dirPart, namePart) = splitPath(wordAt(text, cursor).second)
        val chosen = nextCompletion(namePart, completionCandidates, completionCycle)
        if (chosen == null) {
            // Nothing to add and nothing chosen: show the list and wait for another press.
            if (completionCandidates.isNotEmpty()) completionCycle = 1
            return
        }
        completionCycle += 1
        val replacement = dirPart + chosen
        session.input = applyCompletion(text, cursor, completionStart, replacement)
    }

    /** Takes one of the candidates from the popup, rather than pressing Tab to reach it. */
    fun pickCompletion(candidate: Completion) {
        val text = session.input.text
        val cursor = session.input.selection.start.coerceIn(0, text.length)
        // The popup outlives the line it was computed for when the reader keeps typing under
        // it: a start past the end belongs to that older line, so there is nothing to apply to.
        if (completionStart > text.length) {
            dismissCompletion()
            return
        }
        val (dirPart, _) = splitPath(wordAt(text, cursor).second)
        val replacement = dirPart + if (candidate.isFolder) "${candidate.name}/" else candidate.name
        session.input = applyCompletion(text, cursor, completionStart, replacement)
        dismissCompletion()
    }

    /**
     * Applies one key-bar key.
     *
     * Every arrow and every CTRL letter is a line-editing act rather than a byte sent to the shell,
     * because the shell has no line editor to receive one. CTRL+C is the exception in spirit only: it
     * is the one that means "stop", and the only way to stop a process behind a pipe is to kill it.
     */
    fun onKeyBar(key: String) {
        when (key) {
            "\t" -> {
                complete()
                return
            }
            "↑" -> {
                dismissCompletion()
                previousHistoryEntry(session.history, session.historyIndex, session.draft)?.let {
                    if (session.historyIndex >= session.history.size) session.draft = session.input.text
                    session.historyIndex = it.index
                    session.input = TextFieldValue(it.text, TextRange(it.text.length))
                }
                return
            }
            "↓" -> {
                dismissCompletion()
                nextHistoryEntry(session.history, session.historyIndex, session.draft)?.let {
                    session.historyIndex = it.index
                    session.input = TextFieldValue(it.text, TextRange(it.text.length))
                }
                return
            }
            "←" -> {
                dismissCompletion()
                moveCaretTo(session.input.selection.start - 1)
                return
            }
            "→" -> {
                dismissCompletion()
                moveCaretTo(session.input.selection.start + 1)
                return
            }
        }
        if (modifierApplies(ctrl) || modifierApplies(alt)) {
            val character = key.firstOrNull() ?: return
            val ctrlHeld = modifierApplies(ctrl)
            val altHeld = modifierApplies(alt)
            if (ctrlHeld) {
                when (controlActionFor(character)) {
                    ControlAction.LINE_START -> {
                        session.input = session.input.copy(selection = TextRange(0))
                        finishModifiers()
                        return
                    }
                    ControlAction.LINE_END -> {
                        session.input = session.input.copy(selection = TextRange(session.input.text.length))
                        finishModifiers()
                        return
                    }
                    ControlAction.CLEAR_LINE -> {
                        val (text, caret) = deleteToCaret(session.input.text, session.input.selection.start)
                        session.input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.CLEAR_AFTER -> {
                        val (text, caret) = deleteToEnd(session.input.text, session.input.selection.start)
                        session.input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.DELETE_WORD -> {
                        val (text, caret) = deleteWordBefore(session.input.text, session.input.selection.start)
                        session.input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.CLEAR_SCREEN -> {
                        session.clearLines()
                        finishModifiers()
                        return
                    }
                    ControlAction.DELETE_FORWARD -> {
                        val (text, caret) = deleteForward(session.input.text, session.input.selection.start)
                        session.input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.NONE -> {
                        // ETX: on a real terminal this raises an interrupt. A pipe cannot deliver a
                        // signal, so the process is killed instead — which is the same thing to the
                        // person at the keyboard, minus the exit status.
                        if (controlChar(character) == '') {
                            session.killRunning()
                            session.appendLine("^C", TermTone.Warning)
                            finishModifiers()
                        }
                        return
                    }
                }
            }
            insertAtCaret(keyBarCharacter(character, ctrlHeld, altHeld))
            finishModifiers()
            return
        }
        insertAtCaret(key)
    }

    val messages = remember(session, session.lineVersion, session.commandRunning) {
        buildTermChat(
            lines = session.lines,
            meta = session.lineMeta,
            runs = session.commandRuns,
            shellName = shellName,
            commandRunning = session.commandRunning,
        )
    }
    Box(modifier.fillMaxSize()) {
        TerminalChatScreen(
            sessions = sessionList,
            selectedShellId = activeId,
            cwd = displayDir(projectName, session.cwd),
            messages = messages,
            savedCommands = savedCommands,
            input = session.input,
            listState = listState,
            followOutput = session.followOutput,
            onFollowChange = {
                session.followOutput = it
                if (it) session.hasNewOutput = false
            },
            onInputChange = {
                session.input = it
                session.followOutput = true
                session.hasNewOutput = false
                dismissCompletion()
            },
            onSend = { send() },
            onSelectShell = { sessions.select(it) },
            onNewShell = { sessions.openNew() },
            onCloseShell = { sessions.close(it) },
            onRenameShell = { number, name -> sessions.rename(number, name) },
            onClearShell = { number ->
                sessionList.firstOrNull { it.number == number }?.clearLines()
            },
            onSaveCommand = { command, name ->
                val entry = if (name.isNullOrBlank()) command else "$name\u0000$command"
                if (entry !in savedCommands) persistCommands(savedCommands + entry)
            },
            onPickSaved = { command ->
                session.input = TextFieldValue(command, TextRange(command.length))
                session.followOutput = true
                session.hasNewOutput = false
                promptFocus.requestFocus()
                keyboardController?.show()
            },
            onSpecialKey = { key ->
                when (key) {
                    "esc" -> onKeyBar("\u001B")
                    "tab" -> onKeyBar("\t")
                    "ctrl" -> ctrl = nextLatch(ctrl)
                    "up" -> onKeyBar("↑")
                    "down" -> onKeyBar("↓")
                    "left" -> onKeyBar("←")
                    "right" -> onKeyBar("→")
                }
            },
            onStop = {
                session.killRunning()
                session.finishRun(130)
                session.appendLine("^C", TermTone.Warning)
            },
            onBack = onBack,
            onOpenSettings = onOpenTerminalSettings,
            onWidthChange = { termWidthPx = it },
            newCommandRequest = newCommandRequest,
            focusRequester = promptFocus,
        )

    }
}
