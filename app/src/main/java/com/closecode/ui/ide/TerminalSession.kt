package com.closecode.ui.ide

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.closecode.ui.model.ProjectFile
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import kotlinx.coroutines.channels.Channel

/**
 * One shell, with everything that belongs to it.
 *
 * Tabs are not views over a shared shell: each one is a separate process with its own working
 * directory, its own scrollback and its own half-typed line. That is the point of having more than
 * one — a server left running in the first while Git work goes on in the second — and it cannot be
 * faked by swapping which text is shown.
 *
 * The output channel is per session and is drained for every session for as long as the panel is
 * open, not only for the one on screen. A session that stopped being read while it was in the
 * background would fill its pipe and wedge its shell, which is precisely the hang that folding
 * standard error into standard output was there to prevent.
 */
internal class TerminalSession(val number: Int) {
    val output = Channel<String>(Channel.UNLIMITED)
    val lines = mutableStateListOf<String>()
    var input by mutableStateOf(TextFieldValue(""))
    var lastExit by mutableStateOf<Int?>(null)
    var cwd by mutableStateOf("")
    var history by mutableStateOf(emptyList<String>())
    var historyIndex by mutableStateOf(0)
    var draft by mutableStateOf("")
    var followOutput by mutableStateOf(true)

    private var shell: ShellProcess? = null

    val isRunning: Boolean get() = shell?.isRunning == true

    fun start(directory: File?) {
        if (shell != null) return
        val started = ShellProcess(
            directory = directory,
            onLine = { line -> output.trySend(line) },
        )
        started.start()
        shell = started
    }

    fun run(command: String) {
        shell?.run(command)
    }

    fun killRunning() {
        shell?.killRunning()
    }

    fun close() {
        shell?.close()
        shell = null
    }

    /** Gives a session the reader's remembered commands, at the moment it is opened. */
    fun copyHistory(remembered: List<String>) {
        history = remembered
        historyIndex = remembered.size
    }

    /**
     * Runs one line, recording it and remembering it, and reports whether it changed anything on
     * disk so the drawer can follow.
     *
     * The caller deals with `code`, which never reaches here because it is not a program; everything
     * else goes to the shell.
     */
    fun submit(command: String, projectSlug: String, historyStore: TerminalHistoryStore): Boolean {
        val text = sanitizeCommand(command)
        if (text.isEmpty()) return false
        lines.appendTerminalLine("$PROMPT$text")
        run(text)
        cwdAfterCommand(cwd, "", text)?.let { cwd = it }
        history = addToHistory(history, text)
        if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
        historyIndex = history.size
        draft = ""
        input = TextFieldValue("")
        return refreshesFiles(text)
    }

    /** Records a `code` command without running it, reporting whether it opened something. */
    fun recordAppCommand(
        command: String,
        tree: List<ProjectFile>,
        projectSlug: String,
        historyStore: TerminalHistoryStore,
        opened: ProjectFile?,
    ) {
        val text = sanitizeCommand(command)
        val target = codeTarget(text)
        lines.appendTerminalLine(
            when {
                opened != null -> "Opened ${opened.path} in the editor"
                target != null -> "No file called $target in this project"
                else -> "code needs a file name"
            }
        )
        history = addToHistory(history, text)
        if (projectSlug.isNotEmpty()) historyStore.write(projectSlug, history)
        historyIndex = history.size
        draft = ""
        input = TextFieldValue("")
    }
}

/**
 * What a tab is called.
 *
 * The number is the identity — it is what makes one tab distinguishable from another at a glance —
 * and the directory is appended only when the shell has left the project root, because that is the
 * one thing about a tab that is not obvious from a list of numbered labels.
 */
internal fun sessionTitle(number: Int, cwd: String): String {
    if (cwd.isEmpty()) return "Shell $number"
    return "Shell $number · ${cwd.substringAfterLast('/').ifBlank { cwd }}"
}

/** The most a tab label may be, so a long path cannot push the other tabs off the strip. */
internal const val TAB_TITLE_MAX_CHARS = 22

/**
 * A tab label that fits the strip.
 *
 * The number is never trimmed away, whatever the path looks like: two tabs both called "…going"
 * are worse than two tabs both called "Shell 1", because the first loses the only thing that
 * distinguishes them. So the path is shortened and the head is kept.
 */
internal fun tabLabel(number: Int, cwd: String): String {
    val head = "Shell $number"
    if (cwd.isEmpty()) return head
    val leaf = cwd.substringAfterLast('/').ifBlank { cwd }
    val full = "$head · $leaf"
    if (full.length <= TAB_TITLE_MAX_CHARS) return full
    // " · " plus the ellipsis, taken off the end of the label before the path starts.
    val room = TAB_TITLE_MAX_CHARS - head.length - 4
    if (room <= 1) return head
    return "$head · …${leaf.takeLast(room)}"
}
/**
 * The row of terminal tabs.
 *
 * A tab is labelled by its number, with the directory appended only once the shell has left the
 * project root — the one thing about a tab that a list of numbered labels would hide. The last tab
 * cannot be closed, for the same reason the last editor tab cannot: there has to be somewhere to
 * type.
 */
@Composable
internal fun TerminalTabStrip(
    sessions: List<TerminalSession>,
    activeId: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        sessions.forEach { session ->
            val active = session.number == activeId
            Row(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) c.panel2 else Color.Transparent)
                    .border(1.dp, if (active) c.ac else c.ln, RoundedCornerShape(7.dp))
                    .clickable { onSelect(session.number) }
                    .padding(start = 9.dp, end = if (sessions.size > 1) 4.dp else 9.dp, top = 5.dp, bottom = 5.dp)
                    .semantics { contentDescription = tabLabel(session.number, session.cwd) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = tabLabel(session.number, session.cwd),
                    color = if (active) c.tx else c.mu,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                if (sessions.size > 1) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onClose(session.number) }
                            .semantics { contentDescription = "Close this shell" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = c.mu, fontSize = 12.sp)
                    }
                }
            }
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, c.ln, RoundedCornerShape(7.dp))
                .clickable { onAdd() }
                .semantics { contentDescription = "New shell" },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = c.tx, fontSize = 15.sp, fontFamily = Mono)
        }
    }
}

/**
 * The shells, held above every screen that can be replaced.
 *
 * This exists because the sessions used to live in a `remember` inside the terminal panel, and the
 * terminal panel is unmounted by anything that takes the editor's place — including the settings
 * screen, which is a sibling branch of the IDE and not a dialog over it. So changing the terminal's
 * colour killed the shell before the new colour was in force, and there was nothing left to recolour
 * when the terminal came back: a fresh, empty shell. The reader saw a theme that appeared not to
 * work, and the cause was a lifetime, not a colour.
 *
 * Holding the list here means the processes outlive every panel swap and the settings screen, and a
 * theme change reaches a terminal that is already on screen with output in it.
 */
@Stable
internal class TerminalSessions {
    internal var sessions by mutableStateOf(listOf(TerminalSession(1)))
        private set

    var activeId by mutableIntStateOf(1)
        private set

    /** The shell on screen. Never null while there is at least one, which there always is. */
    fun active(): TerminalSession =
        sessions.firstOrNull { it.number == activeId } ?: sessions.first()

    fun select(number: Int) {
        if (sessions.any { it.number == number }) activeId = number
    }

    /** Opens another shell and switches to it. The new one starts empty; the others keep running. */
    fun openNew(): Int {
        val next = (sessions.maxOfOrNull { it.number } ?: 0) + 1
        sessions = sessions + TerminalSession(next)
        activeId = next
        return next
    }

    /**
     * Closes one shell, and only that one.
     *
     * The last one is never closed: there would be nothing left to type into, and a terminal with no
     * tabs is not a state a reader can get out of.
     */
    fun close(number: Int) {
        if (sessions.size <= 1) return
        val target = sessions.firstOrNull { it.number == number } ?: return
        target.close()
        val remaining = sessions.filterNot { it.number == number }
        sessions = remaining
        if (activeId == number) activeId = remaining.first().number
    }

    /** Replaces the remembered commands on every shell, on arrival. */
    fun applyHistory(commands: List<String>) {
        sessions = sessions.map { it.apply { copyHistory(commands) } }
    }
}
