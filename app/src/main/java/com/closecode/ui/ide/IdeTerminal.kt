package com.closecode.ui.ide

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
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
 * How much output the terminal keeps. Long enough that a listing or a compile's worth of output is
 * still scrollable, small enough that a command that never stops cannot grow the panel forever.
 */
const val TERMINAL_MAX_LINES = 2_000

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
 * The prompt the app draws at the head of the input line and in front of every echoed command.
 *
 * Not printed by the shell, because a shell fed through a pipe does not print one: prompts belong to
 * interactive shells, and this one is deliberately not interactive. Drawing it here is also what lets
 * it carry the previous command's exit status, which is the one thing about a command a phone
 * keyboard cannot otherwise report.
 */
internal const val PROMPT = "closecode $ "

/**
 * Appends while enforcing [TERMINAL_MAX_LINES]. Same shape as the log's own cap: dropping the
 * oldest lines is one array copy, which is cheap enough per line and keeps the bound strict.
 */
internal fun <T> MutableList<T>.appendTerminalLine(line: T) {
    add(line)
    if (size > TERMINAL_MAX_LINES) {
        subList(0, size - TERMINAL_MAX_LINES).clear()
    }
}

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

    val isRunning: Boolean get() = process?.isAlive == true

    fun start() {
        if (process != null) return
        val started = runCatching {
            val builder = ProcessBuilder(shellPath)
            directory?.let { builder.directory(it) }
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
        Thread {
            runCatching {
                started.inputStream.bufferedReader().useLines { sequence -> sequence.forEach(onLine) }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /** Sends one command. Nothing typed is ever handed to a logger. */
    fun run(command: String) {
        val writer = stdin ?: return
        runCatching {
            writer.write(shellLine(command))
            writer.write("\n")
            writer.flush()
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
    tree: List<ProjectFile> = emptyList(),
    onOpenPath: (ProjectFile) -> Unit = {},
    onFilesChanged: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var ctrl by remember { mutableStateOf(ModifierLatch.OFF) }
    var alt by remember { mutableStateOf(ModifierLatch.OFF) }
    var completionCandidates by remember { mutableStateOf(emptyList<Completion>()) }
    var completionCycle by remember { mutableStateOf(0) }
    var completionStart by remember { mutableStateOf(0) }
    // The Commands sheet, the rename dialog's target shell, and the shelf behind the sheet.
    var commandsOpen by remember { mutableStateOf(false) }
    var renameShell by remember { mutableStateOf<Int?>(null) }
    var namingCommand by remember { mutableStateOf(false) }
    var fieldLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val commandKv = remember { com.closecode.ui.settings.SharedPreferencesKeyValueStore(context) }
    var savedCommands by remember(projectSlug) {
        mutableStateOf(
            decodeCommands(commandKv.getString(TERMINAL_COMMANDS_KEY, null))
                .ifEmpty { DefaultTerminalCommands },
        )
    }
    fun persistCommands(next: List<String>) {
        savedCommands = next
        commandKv.putString(TERMINAL_COMMANDS_KEY, encodeCommands(next))
    }
    // The list and the selection belong to the holder, so what this panel reads is a projection of
    // state that outlives the panel. Nothing below may reassign them.
    val sessionList = sessions.sessions
    val activeId = sessions.activeId
    val session = sessions.active()
    val listState = rememberLazyListState()
    val historyStore = rememberTerminalHistory(projectSlug)

    // Commands run before this screen is opened are still reachable, so the list is read once on
    // arrival rather than starting empty every time the panel is opened. Every tab starts with it:
    // the commands are the reader's, not one tab's.
    LaunchedEffect(projectSlug) {
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
                withContext(Dispatchers.IO) { session.start(projectRoot) }
                if (!session.isRunning) {
                    session.lines.appendTerminalLine(
                        if (projectRoot == null) {
                            "Open a project first: the shell starts in its folder."
                        } else {
                            "This device has no shell to run."
                        }
                    )
                }
                // Drained here rather than on the reader thread: compose state is written on the
                // thread that owns the composition, and the reader is not it.
                for (line in session.output) {
                    val exit = parseExitMarker(line)
                    if (exit != null) {
                        session.lastExit = exit
                        session.commandRunning = false
                    } else {
                        session.lines.appendTerminalLine(line)
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


    // Whether new output should pull the view down to it. A terminal that stops following output is a
    // terminal you have to chase; one that yanks you away while you are reading is worse. So it
    // follows until the reader scrolls back to look at something, and resumes the moment they return
    // to the bottom — which is where a real terminal's behaviour settles too.

    // Watched rather than written from the gesture: the reader can scroll with a fling, a keyboard,
    // or the scrollbar, and every one of those ends at the same place.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex >= listState.layoutInfo.totalItemsCount - 1
        }.collect { atBottom ->
            session.followOutput = atBottom
        }
    }

    LaunchedEffect(session.lines.size, session.followOutput) {
        if (!session.followOutput) return@LaunchedEffect
        if (session.lines.isNotEmpty()) listState.animateScrollToItem(session.lines.lastIndex)
    }

    /** The prompt as drawn for the active shell, and echoed back in front of its commands. */
    fun livePrompt(): String =
        promptPrefix(displayDir(projectName, session.cwd))

    /** Remembers, runs and clears the line: Enter, the Commands sheet and Tab all end here. */
    fun submit(text: String) {
        val command = sanitizeCommand(text)
        if (command.isEmpty()) return
        // The prompt is drawn by the app rather than printed by the shell, which never would: a
        // non-interactive shell does not draw one. It carries the exit status too, so the reader
        // learns how the last command went without looking for a number.
        session.lines.appendTerminalLine("$PROMPT$command")

        // `code` is not a program and never runs. It is the app's own way of saying "show me this
        // file", and it is handled before the shell sees it so that `code` followed by a name opens
        // the editor rather than reporting a command that does not exist.
        val target = codeTarget(command)
        if (target != null) {
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
        val (start, token) = wordAt(session.input.text, session.input.selection.start)
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
        val (dirPart, namePart) = splitPath(wordAt(session.input.text, session.input.selection.start).second)
        val chosen = nextCompletion(namePart, completionCandidates, completionCycle)
        if (chosen == null) {
            // Nothing to add and nothing chosen: show the list and wait for another press.
            if (completionCandidates.isNotEmpty()) completionCycle = 1
            return
        }
        completionCycle += 1
        val replacement = dirPart + chosen
        session.input = TextFieldValue(
            session.input.text.substring(0, completionStart) + replacement + session.input.text.substring(session.input.selection.start),
            TextRange(completionStart + replacement.length),
        )
    }

    /** Takes one of the candidates from the popup, rather than pressing Tab to reach it. */
    fun pickCompletion(candidate: Completion) {
        val (dirPart, _) = splitPath(wordAt(session.input.text, session.input.selection.start).second)
        val replacement = dirPart + if (candidate.isFolder) "${candidate.name}/" else candidate.name
        session.input = TextFieldValue(
            session.input.text.substring(0, completionStart) + replacement +
                session.input.text.substring(session.input.selection.start),
            TextRange(completionStart + replacement.length),
        )
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
                        session.lines.clear()
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
                            session.lines.appendTerminalLine("^C")
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

    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            // No imePadding here. The activity already declares adjustResize, so the window is
            // shrunk for the keyboard and this panel is laid out above it; adding padding as well
            // counted the same keyboard twice and pushed the output and the key bar off the top of
            // the screen. The editor works the same way — adjustResize alone, plus a reserved height
            // for its tool row — and the two now agree.
    ) {
        // 52 dp header: the title with the shell's folder under it, and Commands — or Stop
        // while a command runs, which sends the same kill the old CANCEL key did.
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The way back out. The spec draws no back button here, but the panel replaces the
            // editor when open, so without one there is no door back to it.
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(
                    CcIcons.Chevron,
                    c.textMuted,
                    18.dp,
                    Modifier.graphicsLayer { rotationZ = 180f },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Terminal",
                    color = c.text,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = displayDir(projectName, session.cwd),
                    color = c.textFaint,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (session.commandRunning) {
                Box(
                    Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.chipBg)
                        .clickable {
                            session.killRunning()
                            session.lines.appendTerminalLine("^C")
                        }
                        .padding(horizontal = 14.dp)
                        .semantics { contentDescription = "Stop the running command" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Stop",
                        color = c.error,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
            } else {
                Box(
                    Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.chipBg)
                        .clickable { commandsOpen = true }
                        .padding(horizontal = 12.dp)
                        .semantics { contentDescription = "Saved commands" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CcIcon(CcIcons.Zap, c.textMuted, 13.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Commands",
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                        )
                    }
                }
            }
        }

        // The tab strip, so two shells can exist at once — a server left running in one while Git
        // work goes on in the other. Above the output rather than below it, because which shell you
        // are typing into is the more important of the two facts.
        TerminalTabStrip(
            sessions = sessionList,
            activeId = activeId,
            onSelect = { sessions.select(it) },
            // One shell closes, and only that one. The old code reassigned the list, which changed
            // the key of the DisposableEffect that was responsible for killing every shell, so
            // closing a tab killed the server in the next one along.
            onClose = { sessions.close(it) },
            // Adding a tab used to reassign the list, which disposed the effect whose cleanup
            // closed every shell and then started them all again — so opening a second tab killed
            // the first one's server without the reader ever leaving the panel.
            onAdd = { sessions.openNew() },
            onRename = { renameShell = it },
        )

        val fontSp = terminalFontSize(settings).sp
        // One selection container over the whole output, so a long press starts a real selection
        // with handles and the system's own copy action rather than a bespoke one. It is the
        // only way to get at a stack trace or a URL word by word, and the only way to copy it at
        // all on a phone.
        //
        // A selection cannot reach past what is on screen, because the list is virtualised — a
        // copy of a thousand lines is not something this can assemble without holding all of it
        // in one string, which is the thing virtualisation exists to avoid. What is on screen can
        // be selected; what has scrolled away has to be scrolled back to.
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(c.fieldBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // The empty state is one line, not a paragraph: the shell's folder, and then
                // the prompt below it, which is where the typing happens.
                if (session.lines.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "Android shell · ${displayDir(projectName, session.cwd)}",
                            color = c.textMuted,
                            fontSize = fontSp,
                            fontFamily = Mono,
                        )
                    }
                }
                items(session.lines.size) { index ->
                    val line = session.lines[index]
                    // A line the user typed is echoed back as prompt plus command, in the same
                    // colours as the live prompt below — green path, gray dollar — so the
                    // scrollback reads as one transcript. Anything else is program output.
                    val echo = splitPrompt(line)
                    if (echo != null) {
                        Text(
                            text = promptEcho(echo.first, echo.second),
                            color = c.text,
                            fontSize = fontSp,
                            fontFamily = Mono,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    } else {
                        // A line that names a file of this project becomes a tap on that file.
                        // The match is made against the tree, so this is the file and not a
                        // guess at something that looks like one.
                        val opens = pathsMentionedIn(line, tree)
                        TerminalLine(
                            line = line,
                            modifier = if (opens.isEmpty()) {
                                Modifier.padding(vertical = 1.dp)
                            } else {
                                Modifier
                                    .padding(vertical = 1.dp)
                                    .clickable { onOpenPath(opens.first()) }
                                    .semantics {
                                        contentDescription = "Open ${opens.first().path}"
                                    }
                            },
                        )
                    }
                }
            }
        }

        // The prompt is part of the output: path, dollar, and the line being typed, ending in
        // a blinking block cursor. Typing happens here with the system keyboard; Enter runs.
        // No send button: the keyboard's own Enter is the whole submit path.
        PromptRow(
            dir = displayDir(projectName, session.cwd),
            input = session.input,
            onInput = { session.input = it },
            fieldLayout = fieldLayout,
            onLayout = { fieldLayout = it },
            fieldFocused = fieldFocused,
            onFocus = { fieldFocused = it },
            fontSp = fontSp,
            onSend = { send() },
        )

        // The key bar sits below the prompt rather than above anything else, so it lands
        // directly on top of the soft keyboard with nothing between them — which is where a
        // thumb already is.
        //
        // Only while the keyboard is open. Without it the bar is a dead strip eating a third
        // of the screen; tapping the prompt opens the keyboard, and the bar comes with it.
        // The completion list belongs to typing too, so it hides with the bar.
        val imeVisible = WindowInsets.isImeVisible
        if (imeVisible) {
            TerminalKeyBar(
                ctrl = ctrl,
                alt = alt,
                onCtrl = { ctrl = nextLatch(ctrl) },
                onAlt = { alt = nextLatch(alt) },
                onKey = { onKeyBar(it) },
                onHistory = { direction ->
                    if (direction > 0) onKeyBar("↑") else onKeyBar("↓")
                },
            )

            // Whatever Tab was unsure about, shown rather than guessed at. Above the key bar because it
            // is about the line being typed, and the line is the thing just above it.
            if (completionCandidates.isNotEmpty()) {
                TerminalCompletionPopup(
                    candidates = completionCandidates,
                    onPick = { pickCompletion(it) },
                )
            }
        }

        /** Types a saved command without running it — except clear and kill, which act at once. */
        fun runSaved(command: String) {
            commandsOpen = false
            when (command.trim()) {
                "clear" -> session.lines.clear()
                "kill" -> {
                    session.killRunning()
                    session.lines.appendTerminalLine("^C")
                }
                else -> session.input = TextFieldValue(command, TextRange(command.length))
            }
        }

        if (commandsOpen) {
            CommandsSheet(
                commands = savedCommands,
                onRun = ::runSaved,
                onAdd = {
                    commandsOpen = false
                    namingCommand = true
                },
                onRemove = { persistCommands(savedCommands - it) },
                onDismiss = { commandsOpen = false },
            )
        }

        if (namingCommand) {
            IdeNameDialog(
                title = "New command",
                hint = "git log --oneline",
                onCreate = { name ->
                    namingCommand = false
                    if (name.isNotBlank() && name !in savedCommands) {
                        persistCommands(savedCommands + name)
                    }
                },
                onDismiss = { namingCommand = false },
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
                    .background(c.panel)
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, color = c.tx, fontSize = 12.sp, fontFamily = Mono)
            }
        }
    }
}

/** An echo or the live prompt as drawn text: green path, gray dollar, plain command. */
@Composable
private fun promptEcho(dir: String, command: String): AnnotatedString {
    val c = LocalPageColors.current
    return remember(dir, command, c) {
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.success))
            append(dir)
            pop()
            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.textMuted))
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
 */
@Composable
private fun PromptRow(
    dir: String,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    fieldLayout: androidx.compose.ui.text.TextLayoutResult?,
    onLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit,
    fieldFocused: Boolean,
    onFocus: (Boolean) -> Unit,
    fontSp: androidx.compose.ui.unit.TextUnit,
    onSend: () -> Unit,
) {
    val c = LocalPageColors.current
    val density = LocalDensity.current
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "blink",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.fieldBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = promptEcho(dir, ""),
            color = c.text,
            fontSize = fontSp,
            fontFamily = Mono,
        )
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = input,
                onValueChange = onInput,
                textStyle = TextStyle(
                    color = c.popupText,
                    fontFamily = Mono,
                    fontSize = fontSp,
                ),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                onTextLayout = onLayout,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocus(it.isFocused) },
            )
            val caret = input.selection.start.coerceIn(0, input.text.length)
            val rect = fieldLayout?.let { layout ->
                runCatching { layout.getCursorRect(caret) }.getOrNull()
            }
            if (rect != null && fieldFocused) {
                Box(
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(7.dp, with(density) { rect.height.toDp() })
                        .background(c.accent.copy(alpha = blink)),
                )
            }
        }
    }
}

/**
 * The Commands sheet: the saved shelf as two columns of cards, plus an Add card. Tapping a
 * command types it at the prompt without running it, so it can be edited first — except clear
 * and kill, which act at once because there is nothing to edit about them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandsSheet(
    commands: List<String>,
    onRun: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    IdeBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "Commands",
            color = c.text,
            fontSize = 15.sp,
            fontFamily = Mono,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        commands.chunked(2).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { command ->
                    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.chipBg)
                            .combinedClickable(
                                onClick = { onRun(command) },
                                onLongClick = { onRemove(command) },
                            )
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = command,
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            com.closecode.ui.components.OutlineButton(
                label = "Add command",
                onClick = onAdd,
            )
        }
        Text(
            text = "Tapping types without running. Long press a card to remove it.",
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}
