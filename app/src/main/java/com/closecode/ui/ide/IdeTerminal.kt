package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import java.io.BufferedWriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

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
@Composable
fun IdeTerminal(
    projectRoot: File?,
    projectSlug: String = "",
    tree: List<ProjectFile> = emptyList(),
    onOpenPath: (ProjectFile) -> Unit = {},
    onOpenSourceControl: () -> Unit = {},
    onFilesChanged: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val output = remember(projectRoot) { Channel<String>(Channel.UNLIMITED) }
    val lines = remember(projectRoot) { mutableStateListOf<String>() }
    var input by remember(projectRoot) { mutableStateOf(TextFieldValue("")) }
    var lastExit by remember(projectRoot) { mutableStateOf<Int?>(null) }
    var ctrl by remember { mutableStateOf(ModifierLatch.OFF) }
    var alt by remember { mutableStateOf(ModifierLatch.OFF) }
    var history by remember { mutableStateOf(emptyList<String>()) }
    var historyIndex by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    // The shell's working directory is invisible from out here, so the app follows `cd` itself.
    // Project-relative, and "" for the root, because that is how completion addresses the tree.
    var cwd by remember(projectSlug) { mutableStateOf("") }
    var completionCandidates by remember { mutableStateOf(emptyList<Completion>()) }
    var completionCycle by remember { mutableStateOf(0) }
    var completionStart by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val historyStore = rememberTerminalHistory(projectSlug)

    fun dismissCompletion() {
        completionCandidates = emptyList()
        completionCycle = 0
    }

    // Commands run before this screen is opened are still reachable, so the list is read once on
    // arrival rather than starting empty every time the panel is opened.
    LaunchedEffect(projectSlug) {
        if (projectSlug.isNotEmpty()) history = historyStore.read(projectSlug)
    }

    val shell = remember(projectRoot) {
        ShellProcess(directory = projectRoot, onLine = { line -> output.trySend(line) })
    }

    LaunchedEffect(projectRoot) {
        if (projectRoot != null) {
            withContext(Dispatchers.IO) { shell.start() }
        }
        if (!shell.isRunning) {
            lines.appendTerminalLine(
                if (projectRoot == null) {
                    "Open a project first: the shell starts in its folder."
                } else {
                    "This device has no shell to run."
                },
            )
        }
        // Drained here rather than on the reader thread: compose state is written on the thread
        // that owns the composition, and the reader is not it.
        for (line in output) {
            val exit = parseExitMarker(line)
            if (exit != null) {
                lastExit = exit
            } else {
                lines.appendTerminalLine(line)
            }
        }
    }

    DisposableEffect(projectRoot) {
        onDispose { shell.close() }
    }

    // Whether new output should pull the view down to it. A terminal that stops following output is a
    // terminal you have to chase; one that yanks you away while you are reading is worse. So it
    // follows until the reader scrolls back to look at something, and resumes the moment they return
    // to the bottom — which is where a real terminal's behaviour settles too.
    var followOutput by remember(projectRoot) { mutableStateOf(true) }

    // Watched rather than written from the gesture: the reader can scroll with a fling, a keyboard,
    // or the scrollbar, and every one of those ends at the same place.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex >= listState.layoutInfo.totalItemsCount - 1
        }.collect { atBottom ->
            followOutput = atBottom
        }
    }

    LaunchedEffect(lines.size, followOutput) {
        if (!followOutput) return@LaunchedEffect
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    /** Remembers, runs and clears the line, which a chip and the send button both need. */
    fun submit(text: String) {
        val command = sanitizeCommand(text)
        if (command.isEmpty()) return
        // The prompt is drawn by the app rather than printed by the shell, which never would: a
        // non-interactive shell does not draw one. It carries the exit status too, so the reader
        // learns how the last command went without looking for a number.
        lines.appendTerminalLine("$PROMPT$command")

        // `code` is not a program and never runs. It is the app's own way of saying "show me this
        // file", and it is handled before the shell sees it so that `code` followed by a name opens
        // the editor rather than reporting a command that does not exist.
        val target = codeTarget(command)
        if (target != null) {
            val file = codeTargetFile(tree, cwd, target)
            if (file != null) {
                onOpenPath(file)
                lines.appendTerminalLine("Opened ${file.path} in the editor")
            } else {
                lines.appendTerminalLine("No file called $target in this project")
            }
            history = addToHistory(history, command)
            if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
            historyIndex = history.size
            draft = ""
            input = TextFieldValue("")
            return
        }

        shell.run(command)
        // The drawer follows the terminal: a file made or removed here is a file the reader expects
        // to see listed, and the alternative is a list that is quietly wrong until something else
        // happens to refresh it.
        if (refreshesFiles(command)) onFilesChanged()
        // The app tracks the working directory because the shell will not tell it. Anything that
        // changes directory behind the app's back desyncs completion, and there is no way to ask
        // without running a command in it.
        cwdAfterCommand(cwd, "", command)?.let { cwd = it }
        history = addToHistory(history, command)
        if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
        historyIndex = history.size
        draft = ""
        input = TextFieldValue("")
    }

    fun send() = submit(input.text)

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
        val at = input.selection.start.coerceIn(0, input.text.length)
        input = TextFieldValue(
            input.text.substring(0, at) + text + input.text.substring(at),
            TextRange(at + text.length),
        )
    }

    /** Sets the caret, as every arrow and every "go to the edge" shortcut amounts to. */
    fun moveCaretTo(at: Int) {
        input = input.copy(selection = TextRange(at.coerceIn(0, input.text.length)))
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
        val (start, token) = wordAt(input.text, input.selection.start)
        if (completionCandidates.isEmpty()) {
            val (dirPart, namePart) = splitPath(token)
            completionCandidates = completionCandidates(
                dir = resolvePath(cwd, dirPart.ifEmpty { "" }),
                namePart = namePart,
                tree = tree,
            )
            completionStart = start
            completionCycle = 0
        }
        val (dirPart, namePart) = splitPath(wordAt(input.text, input.selection.start).second)
        val chosen = nextCompletion(namePart, completionCandidates, completionCycle)
        if (chosen == null) {
            // Nothing to add and nothing chosen: show the list and wait for another press.
            if (completionCandidates.isNotEmpty()) completionCycle = 1
            return
        }
        completionCycle += 1
        val replacement = dirPart + chosen
        input = TextFieldValue(
            input.text.substring(0, completionStart) + replacement + input.text.substring(input.selection.start),
            TextRange(completionStart + replacement.length),
        )
    }

    /** Takes one of the candidates from the popup, rather than pressing Tab to reach it. */
    fun pickCompletion(candidate: Completion) {
        val (dirPart, _) = splitPath(wordAt(input.text, input.selection.start).second)
        val replacement = dirPart + if (candidate.isFolder) "${candidate.name}/" else candidate.name
        input = TextFieldValue(
            input.text.substring(0, completionStart) + replacement +
                input.text.substring(input.selection.start),
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
                previousHistoryEntry(history, historyIndex, draft)?.let {
                    if (historyIndex >= history.size) draft = input.text
                    historyIndex = it.index
                    input = TextFieldValue(it.text, TextRange(it.text.length))
                }
                return
            }
            "↓" -> {
                dismissCompletion()
                nextHistoryEntry(history, historyIndex, draft)?.let {
                    historyIndex = it.index
                    input = TextFieldValue(it.text, TextRange(it.text.length))
                }
                return
            }
            "←" -> {
                dismissCompletion()
                moveCaretTo(input.selection.start - 1)
                return
            }
            "→" -> {
                dismissCompletion()
                moveCaretTo(input.selection.start + 1)
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
                        input = input.copy(selection = TextRange(0))
                        finishModifiers()
                        return
                    }
                    ControlAction.LINE_END -> {
                        input = input.copy(selection = TextRange(input.text.length))
                        finishModifiers()
                        return
                    }
                    ControlAction.CLEAR_LINE -> {
                        val (text, caret) = deleteToCaret(input.text, input.selection.start)
                        input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.CLEAR_AFTER -> {
                        val (text, caret) = deleteToEnd(input.text, input.selection.start)
                        input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.DELETE_WORD -> {
                        val (text, caret) = deleteWordBefore(input.text, input.selection.start)
                        input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.CLEAR_SCREEN -> {
                        lines.clear()
                        finishModifiers()
                        return
                    }
                    ControlAction.DELETE_FORWARD -> {
                        val (text, caret) = deleteForward(input.text, input.selection.start)
                        input = TextFieldValue(text, TextRange(caret))
                        finishModifiers()
                        return
                    }
                    ControlAction.NONE -> {
                        // ETX: on a real terminal this raises an interrupt. A pipe cannot deliver a
                        // signal, so the process is killed instead — which is the same thing to the
                        // person at the keyboard, minus the exit status.
                        if (controlChar(character) == '') {
                            shell.killRunning()
                            lines.appendTerminalLine("^C")
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
            .imePadding(),
    ) {
        PanelBackBar("Terminal", onBack)

        if (lines.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Android's shell, in the project's folder. Type a command and send it.",
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                )
            }
        } else {
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
                        .padding(horizontal = 12.dp),
            ) {
                        items(lines.size) { index ->
                            val line = lines[index]
                            // A line the user typed is echoed back at them, and it is never
                            // coloured: a program cannot have coloured it. It is also the one line
                            // the reader recognises as theirs, so it keeps the prompt's accent
                            // rather than the output's.
                            val echoed = line.startsWith(PROMPT)
                            // A line that names a file of this project becomes a tap on that file.
                            // The match is made against the tree, so this is the file and not a
                            // guess at something that looks like one.
                            val opens = pathsMentionedIn(line, tree)
                            TerminalLine(
                                line = line,
                                color = if (echoed) c.ac else null,
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

// The prompt, then the line the reader is typing. Green once a command has succeeded, red
        // once one has failed, which is the one thing about the previous command a phone keyboard
        // cannot tell you.
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.panel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = PROMPT,
                color = if (lastExit == null) c.ac else if (lastExit == 0) c.success else c.red,
                fontSize = 13.sp,
                fontFamily = Mono,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(color = c.tx, fontFamily = Mono, fontSize = 13.sp),
                cursorBrush = SolidColor(c.ac),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.ac)
                    .clickable { send() }
                    .semantics { contentDescription = "Run" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Chevron, Color.White, 16.dp)
            }
        }

        // The chips sit between the input and the key bar: they are about the line being typed, and that
        // is the thing just above them.
        TerminalChipsRow(
            onChip = { action ->
                when (action) {
                    is ChipAction.Command -> {
                        submit(action.command)
                        Unit
                    }
                    ChipAction.SourceControl -> onOpenSourceControl()
                }
            },
        )

        // The key bar sits below the input rather than above it, so it lands directly on top of the
        // soft keyboard with nothing between them — which is where a thumb already is.
        TerminalKeyBar(
            ctrl = ctrl,
            alt = alt,
            onCtrl = { ctrl = nextLatch(ctrl) },
            onAlt = { alt = nextLatch(alt) },
            onKey = { onKeyBar(it) },
            onAction = { action ->
                when (action) {
                    TerminalAction.CLEAR -> lines.clear()
                    TerminalAction.CANCEL -> {
                        shell.killRunning()
                        lines.appendTerminalLine("^C")
                    }
                    // Not a real end-of-file: a button that closed the shell would leave the terminal
                    // dead until it was left and reopened, and the key it stands in for is far more
                    // useful here as the one thing a phone keyboard has no other way to do.
                    TerminalAction.EOF -> {
                        val (text, caret) = deleteForward(input.text, input.selection.start)
                        input = TextFieldValue(text, TextRange(caret))
                    }
                }
            },
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
