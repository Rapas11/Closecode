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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

/** The two faces of one terminal: the conversation, or the saved-command shelf. */
private enum class TerminalSection { Shells, SavedCommands }

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
    val c = LocalPageColors.current
    val cc = LocalCcColors.current
    val context = LocalContext.current
    val settings = LocalSettings.current
    TerminalEnvironment.configure(context)
    val density = LocalDensity.current
    var ctrl by remember { mutableStateOf(ModifierLatch.OFF) }
    var alt by remember { mutableStateOf(ModifierLatch.OFF) }
    var completionCandidates by remember { mutableStateOf(emptyList<Completion>()) }
    var completionCycle by remember { mutableStateOf(0) }
    var completionStart by remember { mutableStateOf(0) }
    // The Commands sheet, the rename dialog's target shell, and the shelf behind the sheet.
    var renameShell by remember { mutableStateOf<Int?>(null) }
    var commandsPanelOpen by remember { mutableStateOf(false) }
    // The saved-command panel's own Add form, inline rather than a page of its own.
    var commandsPanelAdding by remember { mutableStateOf(false) }
    // The two top-level tabs share one content area: the chat, or the saved shelf.
    var terminalSection by remember { mutableStateOf(TerminalSection.Shells) }
    var shellMenuOpen by remember { mutableStateOf(false) }
    // The shells home (screen 22) is reachable from the shell selector, while the Shells tab
    // opens the active conversation directly.
    var shellsHome by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val promptFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val commandKv = remember { com.closecode.ui.settings.SharedPreferencesKeyValueStore(context) }
    // The keyboard height the commands panel must match so nothing jumps: exactly the last
    // keyboard height, defaulting to 216 dp. Tracked live through the platform insets and
    // persisted, clamped to sane keyboards — an unbounded capture once squeezed the output
    // to a single line. (Compose's own ime inset is unreadable from this file, so the View's.)
    var keyboardHeightDp by remember {
        mutableStateOf(commandKv.getInt(TERMINAL_KEYBOARD_HEIGHT_KEY, 216).coerceIn(150, 450))
    }
    val terminalView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(terminalView) {
        while (true) {
            val bottom = androidx.core.view.ViewCompat.getRootWindowInsets(terminalView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
                ?.bottom ?: 0
            if (bottom > 0) {
                val dp = (bottom / density.density).roundToInt().coerceIn(150, 450)
                if (dp != keyboardHeightDp) {
                    keyboardHeightDp = dp
                    commandKv.putInt(TERMINAL_KEYBOARD_HEIGHT_KEY, dp)
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }

    /** Swaps the keyboard for the commands panel at the same height. */
    fun openCommandsPanel() {
        commandsPanelOpen = true
        commandsPanelAdding = false
        terminalSection = TerminalSection.SavedCommands
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    /** Closes the commands panel and summons the keyboard back. */
    fun backToKeyboard() {
        commandsPanelOpen = false
        commandsPanelAdding = false
        terminalSection = TerminalSection.Shells
        promptFocus.requestFocus()
        keyboardController?.show()
    }

    /** The chip, the bolt key and the Keyboard button all toggle: open panel or back out. */
    fun toggleCommandsPanel() {
        if (commandsPanelOpen) backToKeyboard() else openCommandsPanel()
    }
    val fallbackCommands = rememberTerminalCommandsState(projectSlug)
    val commandsStore = commandsState ?: fallbackCommands
    val savedCommands = commandsStore.commands
    fun persistCommands(next: List<String>) {
        commandsStore.replace(next)
    }

    LaunchedEffect(newCommandRequest) {
        if (newCommandRequest > 0) {
            shellsHome = false
            terminalSection = TerminalSection.SavedCommands
            commandsPanelOpen = true
            commandsPanelAdding = true
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    // The list and the selection belong to the holder, so what this panel reads is a projection of
    // state that outlives the panel. Nothing below may reassign them.
    val sessionList = sessions.sessions
    val activeId = sessions.activeId
    val session = sessions.active()
    val shellName = session.customName?.takeIf { it.isNotBlank() } ?: "Shell ${session.number}"

    /** Types a saved command without running it — except clear and kill, which act at once.
     * Lives at page scope so the shells home can drop into a session with the command typed. */
    fun runSaved(command: String) {
        commandsPanelOpen = false
        commandsPanelAdding = false
        terminalSection = TerminalSection.Shells
        session.followOutput = true
        session.hasNewOutput = false
        when (command.trim()) {
            "clear" -> session.clearLines()
            "kill" -> {
                session.killRunning()
                session.appendLine("^C", TermTone.Warning)
            }
            // Typed at the prompt with the cursor at the end, panel closed and keyboard
            // back: the tap is the start of typing, not the whole of it.
            else -> {
                session.input = TextFieldValue(command, TextRange(command.length))
                promptFocus.requestFocus()
                keyboardController?.show()
            }
        }
    }
    val listState = rememberLazyListState()
    val historyStore = rememberTerminalHistory(projectSlug, settings.commandHistory)
    val pipeShell = usePipe || settings.shell == "System"
    val termScope = rememberCoroutineScope()
    // The output width in pixels, measured so the shell can be told its width in characters.
    // A monospace glyph advances about six tenths of its point size; the number is an estimate
    // the shell formats against, not a promise, and anything sane beats the bare 80.
    var termWidthPx by remember { mutableStateOf(0) }
    LaunchedEffect(termWidthPx, activeId) {
        if (termWidthPx > 0) {
            val charPx = with(density) { (11.5.sp * 0.6f).toPx() }
            sessions.active()?.setSize((termWidthPx / charPx).toInt().coerceIn(20, 512))
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
                    } else {
                        // Short, plain errors: shell noise is rewritten before drawing.
                        val clean = rewriteShellNoise(line)
                        val dropped = session.appendLine(
                            clean,
                            if (isErrorLine(clean)) TermTone.Error else TermTone.Output,
                        )
                        // Holding an unfollowed view still across the trim: the list loses rows
                        // at the top, so without this step-back the same content sits lower and
                        // the view jumps. Only the tab on screen owns the list position.
                        if (dropped > 0 && !session.followOutput && session === sessions.active()) {
                            val first =
                                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                            listState.scrollToItem((first - dropped).coerceAtLeast(0))
                        }
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


    // Whether new output should pull the view down to it. Follow mode is on by default: output
    // drags the view to the bottom unless the reader has scrolled up, and scrolling back to
    // within 24 dp of the bottom turns it on again. Watched rather than written from the
    // gesture: flings, the keyboard and the scrollbar all end at the same place. Resolved
    // against the live active tab on every event, because this watcher outlives tab switches.
    val followEdgePx = with(density) { 24.dp.toPx() }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            distanceToBottomPx(
                totalItems = info.totalItemsCount,
                lastVisibleIndex = last?.index ?: -1,
                lastVisibleEndPx = if (last == null) 0 else last.offset + last.size,
                viewportEndPx = info.viewportEndOffset,
            )
        }.collect { distance ->
            val live = sessions.active()
            val near = distance <= followEdgePx
            if (near && !live.followOutput) {
                live.followOutput = true
                live.hasNewOutput = false
            } else if (!near && live.followOutput) {
                live.followOutput = false
            }
        }
    }

    var lastFollowScrollMs by remember { mutableStateOf(0L) }
    // Leading edge, throttled: new output lands on the bottom without animation, at most once
    // every 33 ms, so a burst scrolls as one motion rather than a scroll per line. Output that
    // arrives unfollowed raises the pill's dot instead of moving the view.
    LaunchedEffect(session.lines.size) {
        val size = session.lines.size
        if (size == 0) return@LaunchedEffect
        if (!session.followOutput) {
            session.hasNewOutput = true
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        if (now - lastFollowScrollMs < 33) return@LaunchedEffect
        lastFollowScrollMs = now
        listState.scrollToItem(size - 1)
    }
    // Trailing ticker: whatever the throttle skipped lands within 33 ms of arriving. Idle while
    // the size stands still, so a quiet follower costs one comparison per tick, not a scroll.
    LaunchedEffect(session.followOutput) {
        if (!session.followOutput) return@LaunchedEffect
        var trailed = session.lines.size
        while (true) {
            kotlinx.coroutines.delay(33)
            val size = session.lines.size
            if (size != trailed) {
                trailed = size
                if (session.followOutput && size > 0) {
                    lastFollowScrollMs = System.currentTimeMillis()
                    listState.scrollToItem(size - 1)
                }
            }
        }
    }
    // Returning to a tab whose shell kept writing while it was in the background lands on the
    // bottom when following, instead of on wherever the list happened to be.
    LaunchedEffect(activeId) {
        val live = sessions.active()
        if (live.followOutput && live.lines.isNotEmpty()) {
            listState.scrollToItem(live.lines.lastIndex)
        }
    }

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

    // The key bar's measured height, so the Latest pill floats exactly above it.
    var keyBarHeightPx by remember { mutableStateOf(0) }
    val imeVisiblePanel = KeyboardVisibility.open
    // While a command runs, the last line is kept visible when the keyboard or key row
    // appears: the prompt is where the next command will be typed.
    LaunchedEffect(imeVisiblePanel) {
        if (imeVisiblePanel && session.followOutput && session.lines.isNotEmpty()) {
            listState.scrollToItem(session.lines.lastIndex)
        }
    }
    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(cc.content)
            // No imePadding here. The activity already declares adjustResize, so the window is
            // shrunk for the keyboard and this panel is laid out above it; adding padding as well
            // counted the same keyboard twice and pushed the output and the key bar off the top of
            // the screen. The editor works the same way — adjustResize alone, plus a reserved height
            // for its tool row — and the two now agree.
    ) {
        // The header button walks the same inner-first order as system Back — form, dialog,
        // panel, then the screen — so the two can never disagree about what Back means here.
        fun terminalHeaderBack() {
            when {
                renameShell != null -> {
                    com.closecode.ui.ide.BackLog.press("terminal-header", "branch=rename-shell")
                    renameShell = null
                }
                commandsPanelOpen -> {
                    com.closecode.ui.ide.BackLog.press("terminal-header", "branch=commands-panel")
                    commandsPanelOpen = false
                    commandsPanelAdding = false
                    terminalSection = TerminalSection.Shells
                }
                else -> onBack()
            }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader(
                origin = null,
                title = "Terminal",
                onBack = ::terminalHeaderBack,
                actions = {
                    Box {
                        Row(
                            Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cc.field)
                                .clickable { shellMenuOpen = true }
                                .padding(horizontal = 10.dp)
                                .semantics { contentDescription = "Select shell" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = shellName,
                                color = cc.text,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 88.dp),
                            )
                            com.closecode.ui.CcIcon("chevron", cc.muted, 14.dp, 1.8f)
                        }
                        DropdownMenu(
                            expanded = shellMenuOpen,
                            onDismissRequest = { shellMenuOpen = false },
                        ) {
                            sessionList.forEach { candidate ->
                                val name = candidate.customName?.takeIf { it.isNotBlank() }
                                    ?: "Shell ${candidate.number}"
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = name,
                                            color = if (candidate.number == activeId) cc.accent else cc.text,
                                            fontFamily = Sans,
                                            fontSize = 12.5.sp,
                                        )
                                    },
                                    onClick = {
                                        sessions.select(candidate.number)
                                        shellsHome = false
                                        terminalSection = TerminalSection.Shells
                                        shellMenuOpen = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("All shells", color = cc.text, fontFamily = Sans, fontSize = 12.5.sp) },
                                onClick = {
                                    shellsHome = true
                                    terminalSection = TerminalSection.Shells
                                    shellMenuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New shell", color = cc.accent, fontFamily = Sans, fontSize = 12.5.sp) },
                                onClick = {
                                    sessions.openNew()
                                    shellsHome = false
                                    terminalSection = TerminalSection.Shells
                                    shellMenuOpen = false
                                },
                            )
                        }
                    }
                    HeaderAction(
                        icon = "cog",
                        label = "Terminal settings",
                        onClick = onOpenTerminalSettings,
                    )
                },
            )
        }

        // While a command runs, a 2 dp line sweeps under the header so the eye knows work is
        // happening even when the output pauses; Stop sits in the header in the Commands
        // chip's place, and the prompt stays where it is throughout.
        if (session.commandRunning) {
            val sweep by rememberInfiniteTransition(label = "term-progress").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
                label = "sweep",
            )
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.34f)
                        .height(2.dp)
                        .offset {
                            IntOffset(
                                (maxWidth.toPx() * 0.66f * sweep).roundToInt(),
                                0,
                            )
                        }
                        .background(c.mock.accent),
                )
            }
        }

        TerminalSectionTabs(
            selected = if (commandsPanelOpen) TerminalSection.SavedCommands else terminalSection,
            onSelect = { section ->
                terminalSection = section
                when (section) {
                    TerminalSection.Shells -> {
                        shellsHome = false
                        commandsPanelOpen = false
                        commandsPanelAdding = false
                    }
                    TerminalSection.SavedCommands -> {
                        shellsHome = false
                        commandsPanelOpen = true
                        commandsPanelAdding = false
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    }
                }
            },
        )

        if (shellsHome) {
            ShellsHome(
                projectName = projectName,
                sessions = sessionList,
                activeId = activeId,
                commands = savedCommands,
                onOpenShell = { number ->
                    sessions.select(number)
                    shellsHome = false
                    terminalSection = TerminalSection.Shells
                },
                onAddShell = {
                    sessions.openNew()
                    shellsHome = false
                    terminalSection = TerminalSection.Shells
                },
                onRunCommand = { command ->
                    shellsHome = false
                    runSaved(command)
                },
                onAddCommand = {
                    shellsHome = false
                    terminalSection = TerminalSection.SavedCommands
                    commandsPanelOpen = true
                    commandsPanelAdding = true
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                },
            )
        } else {
        val messages = remember(session, session.lineVersion) {
            buildTermMessages(session.lines, session.lineMeta)
        }
        // One selection container over the whole output, so a long press starts a real selection
        // with handles and the system's own copy action rather than a bespoke one. It is the
        // only way to get at a stack trace or a URL word by word, and the only way to copy it at
        // all on a phone.
        //
        // A selection cannot reach past what is on screen, because the list is virtualised — a
        // copy of a thousand lines is not something this can assemble without holding all of it
        // in one string, which is the thing virtualisation exists to avoid. What is on screen can
        // be selected; what has scrolled away has to be scrolled back to.
        //
        // The weight lives on this Box, not on the list: weight only means anything to the
        // Column that holds it, and inside the selection container it was silently ignored —
        // which left the list wrapped around its content and the key row stranded mid-screen
        // with dead space below it.
        // The output keeps at least 3 lines visible, anchored so the latest lines and
        // the prompt stay in view above the form and keys.
        Box(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 57.dp)
                .fillMaxWidth()
                .background(cc.content)
                .onSizeChanged { termWidthPx = it.width },
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                // The empty state is one line, not a paragraph: the shell's folder, and then
                // the prompt below it, which is where the typing happens.
                if (session.lines.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Nothing here yet. Type a command below.",
                                color = cc.muted,
                                fontSize = 13.sp,
                                fontFamily = Sans,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                items(messages.size, key = { messages[it].index }) { position ->
                    val message = messages[position]
                    val bodyColor = when (message.tone) {
                        TermTone.Success -> cc.green
                        TermTone.Warning -> cc.amber
                        TermTone.Error -> cc.red
                        TermTone.Dim -> cc.muted
                        else -> cc.text2
                    }
                    val fromUser = message.who == TermWho.You
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        if (!fromUser) {
                            Box(Modifier.width(38.dp)) {
                                if (message.showHeader) TerminalAvatar(fromUser = false)
                            }
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
                        ) {
                            if (message.showHeader) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = if (fromUser) {
                                        Arrangement.End
                                    } else {
                                        Arrangement.Start
                                    },
                                ) {
                                    Text(
                                        text = if (fromUser) "You" else shellName,
                                        color = cc.text,
                                        fontSize = 13.5.sp,
                                        fontFamily = Sans,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    val clock = termClock(message.at)
                                    if (clock.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = clock,
                                            color = cc.muted,
                                            fontSize = 10.5.sp,
                                            fontFamily = Mono,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                            }
                            val bubbleShape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (fromUser) 16.dp else 4.dp,
                                bottomEnd = if (fromUser) 4.dp else 16.dp,
                            )
                            Box(
                                Modifier
                                    .widthIn(max = 320.dp)
                                    .clip(bubbleShape)
                                    .background(if (fromUser) cc.accent else cc.field)
                                    .border(
                                        width = 1.dp,
                                        color = if (fromUser) Color.Transparent else cc.border,
                                        shape = bubbleShape,
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Column(
                                    horizontalAlignment = if (fromUser) {
                                        Alignment.End
                                    } else {
                                        Alignment.Start
                                    },
                                ) {
                                    val prompt = message.prompt
                                    if (prompt != null) {
                                        Text(
                                            text = promptEcho(prompt, message.text),
                                            color = if (fromUser) Color.White else cc.text2,
                                            fontSize = 12.sp,
                                            fontFamily = Mono,
                                            lineHeight = 18.6.sp,
                                            textAlign = if (fromUser) {
                                                androidx.compose.ui.text.style.TextAlign.End
                                            } else {
                                                androidx.compose.ui.text.style.TextAlign.Start
                                            },
                                        )
                                    } else {
                                        // A line that names a file of this project becomes a tap on
                                        // that file; a plain failure line keeps its tint.
                                        val opens = pathsMentionedIn(message.text, tree)
                                        TerminalLine(
                                            line = message.text,
                                            color = if (fromUser) Color.White else bodyColor,
                                            fontSize = 12.sp,
                                            lineHeight = 18.6.sp,
                                            textAlign = if (fromUser) {
                                                androidx.compose.ui.text.style.TextAlign.End
                                            } else {
                                                androidx.compose.ui.text.style.TextAlign.Start
                                            },
                                            modifier = if (opens.isEmpty()) {
                                                Modifier
                                            } else {
                                                Modifier
                                                    .clickable { onOpenPath(opens.first()) }
                                                    .semantics {
                                                        contentDescription =
                                                            "Open ${opens.first().path}"
                                                    }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (fromUser) {
                            Box(Modifier.width(38.dp)) {
                                if (message.showHeader) TerminalAvatar(fromUser = true)
                            }
                        }
                    }
                }
                // Breathing room under the last line so underscores and descenders draw
                // inside the viewport instead of clipping at its edge.
                item(key = "end-pad") {
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        }

        if (savedCommands.isNotEmpty()) {
            SavedCommandsCard(
                commands = savedCommands,
                onRun = { runSaved(splitAlias(it).second) },
                onViewAll = ::openCommandsPanel,
            )
        }

        PromptRow(
            input = session.input,
            onInput = {
                session.input = it
                session.followOutput = true
                session.hasNewOutput = false
            },
            focusRequester = promptFocus,
            onSend = { send() },
            running = session.commandRunning,
            onStop = {
                session.killRunning()
                session.appendLine("^C", TermTone.Warning)
            },
        )

        // Whatever Tab was unsure about, shown rather than guessed at. It belongs to
        // typing, so it shows with the keyboard; the key row below it stays in every state.
        if (imeVisiblePanel && completionCandidates.isNotEmpty()) {
            TerminalCompletionPopup(
                candidates = completionCandidates,
                onPick = { pickCompletion(it) },
            )
        }

        // The commands panel swaps the keyboard at the same height, so nothing jumps.
        // It sits directly above the key row: output, then form, then keys, then keyboard.
        // Back closes the panel first: only with no panel does it leave the terminal.
        androidx.activity.compose.BackHandler(enabled = commandsPanelOpen) {
            com.closecode.ui.ide.BackLog.press("terminal", "branch=commands-panel")
            commandsPanelOpen = false
            commandsPanelAdding = false
            terminalSection = TerminalSection.Shells
        }
        androidx.activity.compose.BackHandler(enabled = renameShell != null) {
            com.closecode.ui.ide.BackLog.press("terminal", "branch=rename-shell")
            renameShell = null
        }
        // Deeper handlers (form, panel, rename) run first; the shells home is a leaf page, so
        // Back leaves the terminal from it.
        androidx.activity.compose.BackHandler(
            enabled = shellsHome && !commandsPanelOpen && renameShell == null,
        ) {
            com.closecode.ui.ide.BackLog.press("terminal", "branch=shells-home-exit")
            onBack()
        }
        if (commandsPanelOpen) {
            CommandsPanel(
                commands = savedCommands,
                heightDp = keyboardHeightDp,
                onRun = { runSaved(splitAlias(it).second) },
                adding = commandsPanelAdding,
                onAddingChange = { commandsPanelAdding = it },
                onAddCommand = { alias, cmd ->
                    val entry = if (alias.isNotBlank()) "$alias\u0000$cmd" else cmd
                    if (entry !in savedCommands) persistCommands(savedCommands + entry)
                },
                onRemove = { persistCommands(savedCommands - it) },
                onKeyboard = ::backToKeyboard,
            )
        }
        // The terminal key row belongs to typing: it rises with the IME and leaves with it.
        if (imeVisiblePanel) {
            TerminalKeyBar(
                modifier = Modifier.onSizeChanged { keyBarHeightPx = it.height },
                ctrl = ctrl,
                alt = alt,
                onCtrl = { ctrl = nextLatch(ctrl) },
                onAlt = { alt = nextLatch(alt) },
                onKey = { onKeyBar(it) },
                onHistory = { direction ->
                    if (direction > 0) onKeyBar("↑") else onKeyBar("↓")
                },
            )
        }

        renameShell?.let { number ->
            val target = sessionList.firstOrNull { it.number == number }
            IdeNameDialog(
                title = "Rename shell",
                hint = "server",
                initial = target?.customName.orEmpty(),
                onCreate = { name ->
                    sessions.rename(number, name)
                    renameShell = null
                },
                onDismiss = { renameShell = null },
            )
        }
        }
    }

    // Follow is off — and the commands panel is not covering the prompt area: a pill back
    // to the bottom, floating above the key row. The blue dot says output arrived while the
    // reader was away; tapping re-engages follow and jumps.
    if (!shellsHome && !session.followOutput && !commandsPanelOpen) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 12.dp,
                    bottom = with(density) { keyBarHeightPx.toDp() } + 8.dp,
                ),
        ) {
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cc.field)
                    .clickable {
                        session.followOutput = true
                        session.hasNewOutput = false
                        termScope.launch {
                            if (session.lines.isNotEmpty()) {
                                listState.scrollToItem(session.lines.lastIndex)
                            }
                        }
                    }
                    .padding(horizontal = 14.dp)
                    .semantics { contentDescription = "Back to latest output" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (session.hasNewOutput) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(cc.accent),
                    )
                }
                Text(
                    text = "↓ Latest",
                    color = cc.text2,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
        }
    }
    }
}

/**
 * The saved commands as a scrollable strip of pills, directly above the prompt. A tap types the
 * command at the prompt (clear and kill act at once, like the Commands panel does).
 */
@Composable
private fun QuickCommandChips(
    commands: List<String>,
    onRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (commands.isEmpty()) return
    val cc = LocalCcColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(cc.content)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        commands.forEach { entry ->
            val (alias, command) = splitAlias(entry)
            val label = (alias ?: command).lineSequence().first().take(18)
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cc.field)
                    .clickable { onRun(entry) }
                    .padding(horizontal = 13.dp)
                    .semantics { contentDescription = "Run $command" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = cc.text2,
                    fontSize = 12.5.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The candidates Tab found, as a strip of names.
 *
 * A strip rather than a list, because it belongs to a single line of input and a full-height panel
 * between the line and the keyboard would hide the very thing being completed. Folders are labelled
 * with a trailing slash so it is visible there is more inside, and one tap takes the name — which is
 * the point of showing it rather than only cycling through it.
 */
@Composable
private fun TerminalCompletionPopup(
    candidates: List<Completion>,
    onPick: (Completion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val settings = LocalSettings.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.panel2)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        candidates.forEach { candidate ->
            val label = if (candidate.isFolder) "${candidate.name}/" else candidate.name
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.mock.panel)
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, color = c.mock.text, fontSize = 12.sp, fontFamily = Mono)
            }
        }
    }
}

/** An echo or the live prompt as drawn text: green path, gray dollar, plain command. */
@Composable
private fun promptEcho(dir: String, command: String): AnnotatedString {
    val cc = LocalCcColors.current
    return remember(dir, command, cc) {
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = cc.green))
            append(dir)
            pop()
            pushStyle(androidx.compose.ui.text.SpanStyle(color = cc.muted))
            append(" $ ")
            pop()
            append(command)
        }
    }
}

/**
 * The prompt as part of the output: path, dollar, and the line being typed, ending in a
 * blinking 7 dp block cursor. The field's own cursor is hidden — a thin line cannot be widened
 * — and the block is drawn at the caret the layout reports, so it tracks wrapping and edits.
 *
 * Prompt and command share one paragraph: the prompt is a visual prefix the caret can never
 * enter (it is not in the editing buffer, so it can neither be deleted nor typed into), which
 * is what lets a long command wrap back to the left edge instead of under the prompt's end —
 * exactly like the echoed lines above it.
 */
@Composable
private fun TerminalAvatar(fromUser: Boolean) {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (fromUser) cc.accent else cc.purple),
        contentAlignment = Alignment.Center,
    ) {
        com.closecode.ui.CcIcon(
            if (fromUser) "user" else "term",
            Color.White,
            19.dp,
            1.8f,
        )
    }
}

/** The two top-level tabs: one shared terminal, two faces. */
@Composable
private fun TerminalSectionTabs(
    selected: TerminalSection,
    onSelect: (TerminalSection) -> Unit,
) {
    val cc = LocalCcColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(cc.content),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TerminalSection.entries.forEach { section ->
                val isSelected = section == selected
                Column(
                    Modifier
                        .clickable { onSelect(section) }
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = when (section) {
                                TerminalSection.Shells -> "Shells"
                                TerminalSection.SavedCommands -> "Saved Commands"
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when (section) {
                            TerminalSection.Shells -> "Shells"
                            TerminalSection.SavedCommands -> "Saved Commands"
                        },
                        color = if (isSelected) cc.text else cc.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isSelected) cc.accent else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cc.divider))
    }
}

/**
 * The shells home (screen 22): every shell as a card with a running dot, then the saved
 * commands in two columns and the dashed Add. Tapping a card drops into that session, a
 * command drops into its prompt, and Add opens the same New-command form the panel uses.
 */
@Composable
private fun ShellsHome(
    projectName: String,
    sessions: List<TerminalSession>,
    activeId: Int,
    commands: List<String>,
    onOpenShell: (Int) -> Unit,
    onAddShell: () -> Unit,
    onRunCommand: (String) -> Unit,
    onAddCommand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cc = LocalCcColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        SectionLabel("SHELLS")
        if (sessions.isEmpty()) {
            // There is always one shell in practice; the dashed card covers the rest.
            DashedAction("+ New shell", onClick = onAddShell)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                sessions.forEach { session ->
                    val active = session.number == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) cc.field else cc.content)
                            .clickable(onClick = { onOpenShell(session.number) })
                            .padding(10.dp)
                            .semantics { contentDescription = "Open ${session.customName ?: "Shell ${session.number}"}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cc.accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ">_",
                                    color = cc.accent,
                                    fontSize = 13.sp,
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            // The running dot rides the tile's bottom-right corner, the way
                            // the mock shows working shells at a glance.
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (session.commandRunning) cc.green else cc.toggleOff,
                                    ),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = session.customName ?: "Shell ${session.number}",
                                color = cc.text,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayDir(projectName, session.cwd),
                                    color = cc.muted,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (session.commandRunning) "· running" else "· idle",
                                    color = if (session.commandRunning) cc.green else cc.muted,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("SAVED COMMANDS")
        Spacer(Modifier.height(8.dp))
        // A plain chunked grid rather than a lazy one: a vertical-scroll column cannot host
        // a child with the same axis, and the list is a handful of pills — composition is cheap.
        commands.chunked(2).forEach { rowCommands ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rowCommands.forEach { command ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cc.field)
                            .clickable(onClick = { onRunCommand(command) })
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "›",
                            color = cc.accent,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = command,
                            color = cc.text,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                if (rowCommands.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(4.dp))
        DashedAction("+ Add command", onClick = onAddCommand)
        Spacer(Modifier.height(16.dp))
    }
}

/** The dashed indigo action bar: Add command, New shell. */
@Composable
private fun DashedAction(text: String, onClick: () -> Unit) {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cc.field)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = cc.accent,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = cc.accent,
            fontSize = 13.sp,
            fontFamily = Mono,
        )
    }
}

@Composable
private fun SavedCommandsCard(
    commands: List<String>,
    onRun: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val cc = LocalCcColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cc.field)
            .border(1.dp, cc.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Saved commands",
                color = cc.text,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "View all",
                color = cc.accent,
                fontSize = 11.5.sp,
                fontFamily = Sans,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .semantics { contentDescription = "View all saved commands" },
            )
        }
        Spacer(Modifier.height(4.dp))
        commands.take(3).forEachIndexed { index, entry ->
            val (alias, command) = splitAlias(entry)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRun(entry) }
                    .padding(vertical = 7.dp)
                    .semantics { contentDescription = "Run ${alias ?: command}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cc.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    com.closecode.ui.CcIcon("term", cc.accent, 14.dp, 1.9f)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = alias ?: command.lineSequence().first(),
                        color = cc.text,
                        fontSize = 12.5.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (alias != null) {
                        Text(
                            text = command,
                            color = cc.muted,
                            fontSize = 10.5.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (index < minOf(commands.size, 3) - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(cc.divider))
            }
        }
    }
}

@Composable
private fun PromptRow(
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onSend: () -> Unit,
    running: Boolean = false,
    onStop: () -> Unit = {},
) {
    val cc = LocalCcColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(cc.content)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(cc.field)
                .border(1.dp, cc.border, CircleShape)
                .padding(start = 8.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cc.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                com.closecode.ui.CcIcon("term", cc.accent, 17.dp, 1.9f)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    textStyle = TextStyle(
                        color = cc.text,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                    ),
                    // A command is one line. singleLine also makes a hardware Enter trigger the
                    // Send action instead of inserting a newline, so a Bluetooth keyboard works.
                    singleLine = true,
                    cursorBrush = SolidColor(cc.accent),
                    // Commands must arrive as typed: the keyboard once turned ls into Ls and
                    // ping into Ping, so capitalization, auto-correct and suggestions stay off.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (input.text.isEmpty()) {
                    Text(
                        text = "Type a command…",
                        color = cc.faint,
                        fontFamily = Mono,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (running) cc.red else cc.accent)
                    .clickable { if (running) onStop() else onSend() }
                    .semantics {
                        contentDescription = if (running) "Stop the running command" else "Send"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (running) {
                    com.closecode.ui.CcIcon("x", Color.White, 17.dp, 2.2f)
                } else {
                    com.closecode.ui.CcIcon("send", Color.White, 17.dp, 1.9f)
                }
            }
        }
    }
}

/**
 * The commands panel: it swaps the keyboard at the same height, so nothing jumps. Saved
 * commands scroll in two columns (an entry may carry an optional alias, shown as the card
 * label), Add turns the panel into an inline form, and Keyboard summons the keyboard back.
 * Tapping a card types its command at the prompt without running it, except clear and kill,
 * which act at once because there is nothing to edit about them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandsPanel(
    commands: List<String>,
    heightDp: Int,
    onRun: (String) -> Unit,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    onAddCommand: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onKeyboard: () -> Unit,
) {
    val c = LocalPageColors.current
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    val duplicate = name.trim().isNotEmpty() &&
        commands.any { it.substringBefore('\u0000').equals(name.trim(), ignoreCase = true) }
    val ready = name.isNotBlank() && command.isNotBlank() && !duplicate
    LaunchedEffect(adding) {
        if (adding) {
            name = ""
            command = ""
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(c.fieldBg)
            .drawBehind {
                drawLine(color = c.divider, start = Offset(0f, 0.5f), end = Offset(size.width, 0.5f), strokeWidth = 1f)
            }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Saved commands",
                color = c.mock.text2,
                fontSize = 12.sp,
                fontFamily = Mono,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAddingChange(!adding) }
                    .padding(horizontal = 10.dp)
                    .semantics { contentDescription = if (adding) "Cancel add command" else "Add command" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!adding) {
                    com.closecode.ui.CcIcon("plus", c.mock.accent, 14.dp, 2f)
                }
                Text(
                    text = if (adding) "Cancel" else "Add Command",
                    color = c.mock.accent,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
            Box(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.mock.field)
                    .clickable(onClick = onKeyboard)
                    .padding(horizontal = 10.dp)
                    .semantics { contentDescription = "Back to keyboard" },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(CcIcons.Keyboard, c.mock.muted, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Keyboard", color = c.mock.text, fontSize = 12.sp, fontFamily = Mono)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (adding) {
            NewCommandField(label = "Name", value = name, onValue = { name = it }, hint = "Name")
            Spacer(Modifier.height(7.dp))
            NewCommandField(
                label = "Command",
                value = command,
                onValue = { command = it },
                hint = "Command to run",
                mono = true,
            )
            if (duplicate) {
                Text(
                    text = "A command with this name already exists.",
                    color = c.mock.danger,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (ready) c.mock.accent else c.mock.field)
                    .clickable(enabled = ready) {
                        onAddCommand(name.trim(), command.trim())
                        onAddingChange(false)
                    }
                    .semantics { contentDescription = "Save command" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Save command",
                    color = if (ready) c.mock.onAccent else c.mock.faint,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(commands, key = { it }) { command ->
                    // An entry may carry an optional alias before a NUL: the card shows the name,
                    // the prompt still receives the command behind it.
                    val (alias, cmd) = splitAlias(command)
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.mock.field)
                            .combinedClickable(onClick = { onRun(cmd) }, onLongClick = { onRemove(command) })
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = alias ?: cmd,
                            color = c.mock.text2,
                            fontSize = 11.5.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Dashed primary outline on a field fill, pinned under the scrolling list.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.mock.field)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            color = c.mock.accent,
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(10.dp.toPx()),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                            ),
                        )
                    }
                    .clickable { onAddingChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+ Add command",
                    color = c.mock.accent,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                )
            }
        }
    }
}

/** One field of the inline New-command form: a small label, then the value box. */
@Composable
private fun NewCommandField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    mono: Boolean = false,
    readOnly: Boolean = false,
) {
    val c = LocalPageColors.current
    Column {
        Text(
            text = label,
            color = c.mock.textSoft,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.mock.field)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (readOnly || value.isEmpty() && hint.isNotEmpty()) {
                Text(
                    text = if (readOnly) value else hint,
                    color = if (readOnly) c.mock.text else c.mock.faint,
                    fontSize = 12.5.sp,
                    fontFamily = if (mono) Mono else Sans,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.mock.text,
                        fontSize = 12.5.sp,
                        fontFamily = if (mono) Mono else Sans,
                    ),
                    cursorBrush = SolidColor(c.mock.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
