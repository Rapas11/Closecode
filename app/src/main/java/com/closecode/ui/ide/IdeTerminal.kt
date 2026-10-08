package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
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
    val listState = rememberLazyListState()
    val historyStore = rememberTerminalHistory(projectSlug)

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

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun send() {
        val command = sanitizeCommand(input.text)
        if (command.isEmpty()) return
        // The prompt is drawn by the app rather than printed by the shell, which never would: a
        // non-interactive shell does not draw one. It carries the exit status too, so the reader
        // learns how the last command went without looking for a number.
        lines.appendTerminalLine("$PROMPT$command")
        shell.run(command)
        history = addToHistory(history, command)
        if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
        historyIndex = history.size
        draft = ""
        input = TextFieldValue("")
    }

    /** A latched modifier gives up its hold after the one key it was armed for; a locked one holds. */
    fun finishModifiers() {
        if (modifierConsumes(ctrl)) ctrl = ModifierLatch.OFF
        if (modifierConsumes(alt)) alt = ModifierLatch.OFF
    }

    fun insertAtCaret(text: String) {
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
     * Applies one key-bar key.
     *
     * Every arrow and every CTRL letter is a line-editing act rather than a byte sent to the shell,
     * because the shell has no line editor to receive one. CTRL+C is the exception in spirit only: it
     * is the one that means "stop", and the only way to stop a process behind a pipe is to kill it.
     */
    fun onKeyBar(key: String) {
        when (key) {
            "↑" -> {
                previousHistoryEntry(history, historyIndex, draft)?.let {
                    if (historyIndex >= history.size) draft = input.text
                    historyIndex = it.index
                    input = TextFieldValue(it.text, TextRange(it.text.length))
                }
                return
            }
            "↓" -> {
                nextHistoryEntry(history, historyIndex, draft)?.let {
                    historyIndex = it.index
                    input = TextFieldValue(it.text, TextRange(it.text.length))
                }
                return
            }
            "←" -> {
                moveCaretTo(input.selection.start - 1)
                return
            }
            "→" -> {
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                items(lines.size) { index ->
                    val line = lines[index]
                    // A line the user typed is echoed back at them, and it is never coloured: a
                    // program cannot have coloured it. It is also the one line the reader recognises
                    // as theirs, so it keeps the prompt's own accent rather than the output's.
                    val echoed = line.startsWith(PROMPT)
                    TerminalLine(
                        line = line,
                        color = if (echoed) c.ac else null,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
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

        // The key bar sits below the input rather than above it, so it lands directly on top of the
        // soft keyboard with nothing between them — which is where a thumb already is.
        TerminalKeyBar(
            ctrl = ctrl,
            alt = alt,
            onCtrl = { ctrl = nextLatch(ctrl) },
            onAlt = { alt = nextLatch(alt) },
            onKey = { onKeyBar(it) },
        )
    }
}
