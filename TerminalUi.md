Read the whole file TerminalUi.kt below, from the first line to the last, before you change anything. It is REFERENCE code for the Terminal screen: it shows exactly how the Terminal must look and behave (it matches the four pictures). It is not a file to drop in unchanged.
Your job: learn how it is built, then change OUR existing Terminal code so the result looks and behaves the same. Search the code for the word CONNECT. Each CONNECT comment tells you something that must be linked to our own code (package name, theme colours, icons, fonts, the terminal engine, the saved-commands storage, navigation). Replace the placeholders with what already exists in our project. Do not invent new features, do not change the engine, and do not touch other screens. When finished, remove the old Terminal UI pieces that the new code replaces, build, run, and compare a screenshot with each of the four pictures. Report what still differs.

/*
 * ============================================================================================
 *  TerminalUi.kt   —   REFERENCE CODE for the CloseCode Terminal screen (restyle only)
 * ============================================================================================
 *
 *  HOW TO USE THIS FILE (read this first, then read the whole file)
 *  ----------------------------------------------------------------
 *  1. This file shows how the new Terminal must be BUILT and how it must BEHAVE. It matches the four
 *     pictures:  1 closed   2 saved commands open   3 quick keys open   4 new command form.
 *  2. It is stateless UI: data comes in as parameters, actions go out as callbacks. All the real work
 *     (running commands, shells, storing saved commands) already exists in our app and MUST NOT change.
 *  3. Do not paste this file over our project. Learn from it, then put the correct parts into our
 *     existing Terminal screen: the layout, the sizes, the colours, the panel logic.
 *  4. Everything marked  // CONNECT:  must be linked to something that already exists in our app.
 *
 *  WHAT THE NEW TERMINAL LOOKS LIKE (differences from the old one, remove the old versions)
 *  ----------------------------------------------------------------------------------------
 *   - Header shows only "Terminal" (no shell name next to it).
 *   - Small shell tabs + a "+" tab, with the current folder at the right. No full-width "Shells" bar.
 *   - Chat: user messages on the RIGHT (purple bubble), shell replies on the LEFT in ONE card.
 *   - No "Latest" button. The list follows new output automatically.
 *   - One rounded input card at the bottom. It holds: "+" button, text field, "{ }" button, send button.
 *   - Saved commands, the new-command form and the quick keys are NOT always visible. They open as a
 *     panel INSIDE the same card, above the input row, and only one panel is open at a time.
 *   - No saved-command chips row, no always-visible key rows, no lightning icon, no "$" in the field.
 *
 *  CONNECT CHECKLIST (search this file for "CONNECT")
 *  -------------------------------------------------
 *   1. Package name           -> use our real package.
 *   2. Icons                  -> CcIcon(name, size, tint) is our icon function; keep its real signature.
 *   3. Colours                -> TC holds the dark values from the pictures; map them to our theme
 *                                tokens if equal values exist, otherwise keep these values.
 *   4. Monospace font         -> use our JetBrains Mono.
 *   5. Data                   -> map the engine's real shell history to TermMessage / ShellTab.
 *   6. Saved commands         -> use the existing storage (defaults first, then user-added).
 *   7. Special keys           -> Esc, Tab, Ctrl, arrows go through the code the terminal already uses.
 *   8. Callbacks              -> send, stop, copy, new shell, select shell, back, settings.
 *   9. Where it is called     -> replace the old Terminal composable in the navigation.
 * ============================================================================================
 */

// CONNECT 1: replace with our real package name.
package app.closecode.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text            // Text is used only as plumbing; every style below is explicit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================================
//  1. COLOURS AND FONT
// =============================================================================================
// CONNECT 3: these are the DARK values taken from the pictures. If our theme already has a token with
// the same value, use the token instead. If the app has a light theme, define the light equivalents
// in the theme (same roles, same names) and read them here. Do not change the roles.
private object TC {
    val bg = Color(0xFF0F111A)            // screen background
    val headerLine = Color(0xFF20243A)    // 1 dp line under the header
    val tile = Color(0xFF181B2B)          // back/gear tiles and inactive shell tabs
    val card = Color(0xFF171A29)          // shell output card and the input dock card
    val cardLine = Color(0xFF262A40)      // border of the output card, dividers inside cards
    val dockLine = Color(0xFF2C3050)      // border of the dock card, form fields
    val accent = Color(0xFF7B6BF0)        // purple: user bubble, active tab, send button, Save
    val accentSoft = Color(0xFFB7ACFF)    // light purple: cancel X, active { }, links, cursor block
    val shellAvatar = Color(0xFF8E7FF5)
    val text = Color(0xFFF2F3F7)
    val textOut = Color(0xFFE3E5F0)       // normal output text
    val dim = Color(0xFF9AA0BC)           // dim output text (task lines)
    val muted = Color(0xFF7E839C)         // times, folder, placeholders
    val tabText = Color(0xFFA4A9C2)
    val green = Color(0xFF4ADE80)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFE5484D)
    val promptInBubble = Color(0xFFB8F5CF)   // the "~/folder $" part inside the user bubble
    val keyBg = Color(0xFF232638)
    val savedTile = Color(0xFF26295A)
    val savedTileIcon = Color(0xFF9C90FF)
    val plusBorder = Color(0xFF6A6F8C)
    val bracesOn = Color(0xFF2A2D5E)
    val dashed = Color(0xFF4A4F8A)
}

// CONNECT 4: use our JetBrains Mono here (whatever the rest of the app already uses for code).
private val Mono = FontFamily.Monospace

// CONNECT 2: ICONS. Everywhere below, CcIcon("name", size, tint) draws one of our 24x24 line icons.
// Use our real icon function and its real signature. Names used in this file:
//   back, gear, plus, x2 (close/cancel), send, term, user, check, copy, stop, braces
// If x2, stop or braces do not exist yet, add them to the icon table (24x24, stroke 1.9, round caps):
//   x2     = "M18 6L6 18M6 6l12 12"
//   stop   = "M7 7h10v10H7z"            (filled square)
//   braces = "M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1"

// =============================================================================================
//  2. UI MODELS  (what the screen needs; map our real data into these)
// =============================================================================================
// CONNECT 5: our terminal engine already has a history per shell. Convert it to these classes in the
// ViewModel (or wherever the screen gets its data). The UI never talks to the engine directly.

/** Which panel is open in the input card. Only ONE at a time. */
enum class DockPanel { None, Saved, NewCommand, Keys }

enum class LineKind { Normal, Dim, Warn, Error }
data class OutLine(val text: String, val kind: LineKind = LineKind.Normal)

sealed interface RunState {
    data class Running(val seconds: Int) : RunState
    data class Done(val exitCode: Int, val durationText: String) : RunState   // e.g. "0.2 s"
}

sealed interface TermMessage {
    val id: String
    val time: String                                    // already formatted, e.g. "1:13 pm"
    /** What the user typed. [prompt] is the text before the command, e.g. "~/gjb $". */
    data class User(override val id: String, override val time: String,
                    val prompt: String, val command: String) : TermMessage
    /** The reply to ONE command. All its output lines belong to ONE card. */
    data class Shell(override val id: String, override val time: String, val shellName: String,
                     val lines: List<OutLine>, val state: RunState) : TermMessage
}

data class ShellTab(val id: String, val name: String, val running: Boolean)

// CONNECT 6: the existing saved-commands storage. Order = default commands first, then the ones the
// user added. There are NO "default" / "yours" headings in the UI, so the UI does not need to know which is which.
data class SavedCommand(val id: String, val command: String, val name: String? = null)

/**
 * One key in the quick-keys panel.
 *  - [insert]: text typed into the input at the cursor. [cursorBack] puts the cursor back inside pairs.
 *  - [special]: key sent to the shell (esc, tab, ctrl, up, down, left, right).
 */
data class TermKey(val label: String, val insert: String? = null, val cursorBack: Int = 0, val special: String? = null)

/** The keys, in the order shown in picture 3. */
val TermKeys = listOf(
    TermKey("Esc", special = "esc"), TermKey("Tab", special = "tab"), TermKey("Ctrl", special = "ctrl"),
    TermKey("↑", special = "up"), TermKey("↓", special = "down"), TermKey("←", special = "left"), TermKey("→", special = "right"),
    TermKey("{ }", "{}", 1), TermKey("( )", "()", 1), TermKey("[ ]", "[]", 1), TermKey("\" \"", "\"\"", 1),
    TermKey(";", ";"), TermKey("=", "="), TermKey("/", "/"), TermKey("|", "|"), TermKey("~", "~"), TermKey("-", "-"),
)

// =============================================================================================
//  3. THE SCREEN
// =============================================================================================
// CONNECT 9: this replaces the old Terminal composable in our navigation. The route, the ViewModel and
// the callbacks stay ours; only the UI inside changes.
@Composable
fun TerminalScreen(
    shells: List<ShellTab>, selectedShellId: String, cwd: String,
    messages: List<TermMessage>, savedCommands: List<SavedCommand>,
    input: TextFieldValue, onInputChange: (TextFieldValue) -> Unit,
    // CONNECT 8: all of these call code that already exists today.
    onSend: () -> Unit,                         // run the text in the input (existing behaviour)
    onSelectShell: (String) -> Unit,            // switch shell
    onNewShell: () -> Unit,                     // the "+" tab: create a shell
    onSaveCommand: (command: String, name: String?) -> Unit,   // add to saved-commands storage
    onSpecialKey: (String) -> Unit,             // CONNECT 7: esc/tab/ctrl/up/down/left/right, same as today
    onStop: () -> Unit,                         // stop the running command (same as Ctrl+C today)
    onCopy: (String) -> Unit,                   // copy output text
    onBack: () -> Unit,                         // leave the Terminal (back arrow)
    onOpenSettings: () -> Unit,                 // gear
) {
    // The only UI state of this screen: which panel is open. It survives rotation.
    var panel by rememberSaveable { mutableStateOf(DockPanel.None) }

    // System Back closes an open panel FIRST. Only when no panel is open does Back leave the Terminal.
    // (If the app has its own back handling, keep its order: panel first, then the normal back.)
    BackHandler(enabled = panel != DockPanel.None) { panel = DockPanel.None }

    Column(Modifier.fillMaxSize().background(TC.bg).statusBarsPadding()) {
        TermHeader(onBack, onOpenSettings)
        ShellTabs(shells, selectedShellId, cwd, onSelectShell, onNewShell)
        // weight(1f): the message area takes all the space the dock does not use. When a panel opens,
        // the dock grows upward and this area simply becomes shorter. Nothing overlaps.
        MessageList(messages, onStop, onCopy, Modifier.weight(1f))
        CommandDock(
            panel = panel, onPanel = { panel = it },
            input = input, onInput = onInputChange, onSend = onSend,
            saved = savedCommands,
            onPickSaved = { command ->
                // Tapping a saved command only PUTS it in the input. It does not run it. The panel closes.
                onInputChange(TextFieldValue(command, TextRange(command.length)))
                panel = DockPanel.None
            },
            onSaveNew = onSaveCommand,
            onKey = { key ->
                when {
                    key.special != null -> onSpecialKey(key.special)
                    key.insert != null -> onInputChange(insertAtCursor(input, key.insert, key.cursorBack))
                }
            },
        )
    }
}

/** Inserts [text] at the cursor (replacing any selection) and moves the cursor back by [cursorBack]. */
private fun insertAtCursor(tv: TextFieldValue, text: String, cursorBack: Int): TextFieldValue {
    val start = tv.selection.min
    val end = tv.selection.max
    val newText = tv.text.replaceRange(start, end, text)
    return TextFieldValue(newText, TextRange(start + text.length - cursorBack))
}

// =============================================================================================
//  4. HEADER  (picture 1)  height 56, back tile 38 radius 12, title 19 sp bold, gear tile 38
// =============================================================================================
@Composable
private fun TermHeader(onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).drawBottomLine(TC.headerLine).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(onBack) { CcIcon("back", 20.dp, TC.text) }
        // Only the word "Terminal". The shell name is NOT shown here any more.
        Text("Terminal", color = TC.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconTile(onSettings) { CcIcon("gear", 19.dp, TC.text) }
    }
}

@Composable
private fun IconTile(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(TC.tile).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}

private fun Modifier.drawBottomLine(color: Color) = this.drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

// =============================================================================================
//  5. SHELL TABS  height 50. SMALL pills (not a full-width bar), then "+", folder at the far right.
// =============================================================================================
@Composable
private fun ShellTabs(shells: List<ShellTab>, selectedId: String, cwd: String,
                      onSelect: (String) -> Unit, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shells.forEach { s ->
            val selected = s.id == selectedId
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) TC.accent else TC.tile)
                    .clickable { onSelect(s.id) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // Green dot = a command is running in this shell.
                if (s.running) Box(Modifier.size(7.dp).clip(CircleShape).background(TC.green))
                Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else TC.tabText)
            }
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(TC.tile).clickable(onClick = onNew),
            contentAlignment = Alignment.Center) { CcIcon("plus", 16.dp, TC.tabText) }
        Spacer(Modifier.weight(1f))
        Text(cwd, fontFamily = Mono, fontSize = 11.5.sp, color = TC.muted, maxLines = 1)   // current folder, e.g. ~/gjb
    }
}

// =============================================================================================
//  6. MESSAGES  chat style. User = RIGHT, shell = LEFT. Content sits at the BOTTOM of the area.
//     Auto-scroll while following. NO "Latest" button.
// =============================================================================================
@Composable
private fun MessageList(messages: List<TermMessage>, onStop: () -> Unit, onCopy: (String) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()

    // "follow" = the list sticks to the newest output. It turns OFF when the user scrolls up and turns
    // ON again when they scroll back to the bottom. There is no button for it.
    var follow by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            info.totalItemsCount == 0 ||
                (last != null && last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + 2)
        }
    }
    LaunchedEffect(listState.isScrollInProgress) { if (!listState.isScrollInProgress) follow = atBottom }

    // Re-scroll whenever a message is added OR the last reply gets more lines (streaming output).
    val streamingLines = (messages.lastOrNull() as? TermMessage.Shell)?.lines?.size ?: 0
    LaunchedEffect(messages.size, streamingLines) {
        if (follow && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            listState.scrollBy(1_000_000f)          // go to the very end of the last (possibly tall) card
        }
    }

    if (messages.isEmpty()) {
        // Empty state (no pictured version): short hint, centred. Real shells start empty.
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Nothing here yet. Type a command below.", color = TC.muted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        modifier.fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),   // 14 dp gap above the dock
        // Alignment.Bottom: with few messages the content hugs the bottom, like a chat.
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        items(messages, key = { it.id }) { m ->
            when (m) {
                is TermMessage.User -> UserBubble(m)
                is TermMessage.Shell -> ShellCard(m, onStop, onCopy)
            }
        }
    }
}

/** User message: purple bubble on the RIGHT, avatar 34 at the far right, "You" + time above the bubble. */
@Composable
private fun UserBubble(m: TermMessage.User) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
        verticalAlignment = Alignment.Top) {
        Column(Modifier.widthIn(max = 290.dp), horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                Text(m.time, fontSize = 11.sp, color = TC.muted)
                Text("You", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TC.text)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TC.promptInBubble)) { append(m.prompt + " ") }   // "~/gjb $" in light green
                    append(m.command)                                                            // command in white
                },
                fontFamily = Mono, fontSize = 13.sp, color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp))
                    .background(TC.accent).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Avatar(TC.accent) { CcIcon("user", 17.dp, Color.White) }
    }
}

/**
 * Shell reply on the LEFT. The WHOLE output of one command is ONE card:
 *   - never one card per line, never an empty card,
 *   - footer inside the same card: finished = tick + "exit 0 · 0.2 s" + copy icon,
 *                                  running  = "running… 12 s" + red Stop pill + blinking cursor block.
 */
@Composable
private fun ShellCard(m: TermMessage.Shell, onStop: () -> Unit, onCopy: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Avatar(TC.shellAvatar) { CcIcon("term", 17.dp, Color.White) }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                Text(m.shellName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TC.text)
                Text(m.time, fontSize = 11.sp, color = TC.muted)
            }
            Spacer(Modifier.height(5.dp))
            val cardShape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().clip(cardShape).background(TC.card).border(1.dp, TC.cardLine, cardShape)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            ) {
                if (m.lines.isNotEmpty()) {
                    Text(
                        buildAnnotatedString {
                            m.lines.forEachIndexed { i, line ->
                                val c = when (line.kind) {
                                    LineKind.Normal -> TC.textOut; LineKind.Dim -> TC.dim
                                    LineKind.Warn -> TC.amber;     LineKind.Error -> TC.red
                                }
                                withStyle(SpanStyle(color = c)) { append(line.text) }
                                if (i < m.lines.lastIndex) append("\n")
                            }
                        },
                        fontFamily = Mono, fontSize = 12.sp, lineHeight = 19.2.sp,      // 12 sp x 1.6
                    )
                    if (m.state is RunState.Running) {
                        // Blinking cursor block 7 x 13 at the end of the output.
                        val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
                            1f, 0f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "cursorAlpha")
                        Box(Modifier.padding(top = 2.dp).size(7.dp, 13.dp).alpha(blink).background(TC.accentSoft))
                    }
                }
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TC.cardLine))     // footer divider
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    when (val s = m.state) {
                        is RunState.Done -> {
                            val ok = s.exitCode == 0
                            val c = if (ok) TC.green else TC.red                          // failed = red cross + real exit code
                            CcIcon(if (ok) "check" else "x2", 13.dp, c)
                            Text("exit ${s.exitCode} · ${s.durationText}", fontFamily = Mono, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = c, modifier = Modifier.weight(1f))
                            Box(Modifier.clickable { onCopy(m.lines.joinToString("\n") { it.text }) }) {
                                CcIcon("copy", 15.dp, TC.muted)                           // copies this reply's output
                            }
                        }
                        is RunState.Running -> {
                            Text("running… ${s.seconds} s", fontFamily = Mono, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = TC.amber, modifier = Modifier.weight(1f))
                            Row(
                                Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(TC.red)
                                    .clickable(onClick = onStop).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CcIcon("stop", 11.dp, Color.White)
                                Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(background: Color, icon: @Composable () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) { icon() }
}

// =============================================================================================
//  7. THE DOCK  (pictures 1 to 4)
//     ONE rounded card (radius 26). Inside it, from top to bottom:
//         [ panel: only when open ]   <- Saved list, New-command form, or Quick keys
//         [ divider 1 dp ]            <- only when a panel is open
//         [ input row: + | text field | { } | send ]
//     The card sits above the keyboard (imePadding). When a panel opens the card grows UPWARD.
// =============================================================================================
@Composable
private fun CommandDock(
    panel: DockPanel, onPanel: (DockPanel) -> Unit,
    input: TextFieldValue, onInput: (TextFieldValue) -> Unit, onSend: () -> Unit,
    saved: List<SavedCommand>, onPickSaved: (String) -> Unit,
    onSaveNew: (String, String?) -> Unit, onKey: (TermKey) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val open = panel != DockPanel.None
    // "listLike" = Saved or NewCommand. While one of these is open the "+" turns into a cancel "X".
    val listLike = panel == DockPanel.Saved || panel == DockPanel.NewCommand

    Box(
        Modifier.fillMaxWidth().background(TC.bg).navigationBarsPadding().imePadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                // A soft shadow only while a panel is open (picture 2, 3, 4). Shadow goes BEFORE clip.
                .then(if (open) Modifier.shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                .clip(shape).background(TC.card).border(1.dp, TC.dockLine, shape),
        ) {
            AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    when (panel) {
                        DockPanel.Saved -> SavedPanel(saved, onPickSaved, onAdd = { onPanel(DockPanel.NewCommand) })
                        DockPanel.NewCommand -> NewCommandPanel(
                            // Save: store it, then go back to the list so the user sees the new command.
                            onSave = { command, name -> onSaveNew(command, name); onPanel(DockPanel.Saved) },
                            onCancel = { onPanel(DockPanel.Saved) },        // the "Cancel" button returns to the list
                        )
                        DockPanel.Keys -> KeysPanel(onKey)
                        DockPanel.None -> Unit
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TC.cardLine))   // divider between panel and input row
                }
            }

            // ----- input row: height 56, side padding 9, gap 8 -----
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "+" opens the saved commands. While Saved/NewCommand is open it shows an "X";
                // tapping the X closes EVERYTHING (panel = None).
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .border(1.8.dp, if (listLike) TC.accentSoft else TC.plusBorder, CircleShape)
                        .clickable { onPanel(if (listLike) DockPanel.None else DockPanel.Saved) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (listLike) CcIcon("x2", 18.dp, TC.accentSoft) else CcIcon("plus", 19.dp, TC.text)
                }

                // Text field. No icon, no "$". Placeholder is drawn by us so we control its colour.
                Box(Modifier.weight(1f)) {
                    if (input.text.isEmpty()) Text("Type a command…", fontFamily = Mono, fontSize = 13.5.sp, color = TC.muted)
                    BasicTextField(
                        value = input, onValueChange = onInput, maxLines = 4,
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.5.sp, color = TC.text),
                        cursorBrush = SolidColor(TC.accentSoft),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // "{ }" opens the quick keys. Highlighted ONLY while the Keys panel is open.
                // Tapping it while Saved/NewCommand is open switches straight to Keys.
                val keysOn = panel == DockPanel.Keys
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (keysOn) TC.bracesOn else Color.Transparent)
                        .clickable { onPanel(if (keysOn) DockPanel.None else DockPanel.Keys) },
                    contentAlignment = Alignment.Center,
                ) { CcIcon("braces", 20.dp, if (keysOn) TC.accentSoft else TC.tabText) }

                Box(Modifier.size(40.dp).clip(CircleShape).background(TC.accent).clickable(onClick = onSend),
                    contentAlignment = Alignment.Center) { CcIcon("send", 18.dp, Color.White) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  PANEL A: saved commands (picture 2).  ONE plain list. No "default" / "yours" headings.
// ---------------------------------------------------------------------------------------------
@Composable
private fun SavedPanel(saved: List<SavedCommand>, onPick: (String) -> Unit, onAdd: () -> Unit) {
    Column {
        // Max height 280: a long list scrolls INSIDE the card; the card never takes the whole screen.
        LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(saved, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clickable { onPick(c.command) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(TC.savedTile), contentAlignment = Alignment.Center) {
                        CcIcon("term", 15.dp, TC.savedTileIcon)
                    }
                    Text(c.command, fontFamily = Mono, fontSize = 13.5.sp, color = TC.text, maxLines = 1)
                }
            }
        }
        if (saved.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(TC.cardLine))
        // Last row: "Add command" (dashed tile). Opens the new-command form.
        Row(
            Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onAdd).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).border(1.5.dp, TC.dashed, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) { CcIcon("plus", 14.dp, TC.accentSoft) }
            Text("Add command", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TC.accentSoft)
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  PANEL B: new command form (picture 4). Same card, same cancel X as the saved list.
// ---------------------------------------------------------------------------------------------
@Composable
private fun NewCommandPanel(onSave: (String, String?) -> Unit, onCancel: () -> Unit) {
    var command by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Column(Modifier.padding(bottom = 14.dp)) {
        Text("New command", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TC.text,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
        FormField("Command", "e.g. git status", command) { command = it }          // required
        FormField("Name (optional)", "e.g. Status", name) { name = it }
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Cancel: clear the fields and go back to the saved list.
            Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(TC.keyBg)
                .clickable { command = ""; name = ""; onCancel() }, contentAlignment = Alignment.Center) {
                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCDD0E2))
            }
            // Save: disabled (50 % opacity) until "Command" has text.
            val canSave = command.isNotBlank()
            Box(Modifier.weight(1f).height(42.dp).alpha(if (canSave) 1f else 0.5f).clip(RoundedCornerShape(21.dp))
                .background(TC.accent)
                .clickable(enabled = canSave) { onSave(command.trim(), name.trim().ifEmpty { null }); command = ""; name = "" },
                contentAlignment = Alignment.Center) {
                Text("Save command", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun FormField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TC.tabText,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        Box(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(TC.bg)
                .border(1.dp, TC.dockLine, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(hint, fontFamily = Mono, fontSize = 13.sp, color = TC.muted)
            BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(TC.accentSoft),
                textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = TC.text), modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  PANEL C: quick keys (picture 3). ONE horizontal row that scrolls, with a fade on the right edge.
//  The panel stays open while the user taps several keys.
// ---------------------------------------------------------------------------------------------
@Composable
private fun KeysPanel(onKey: (TermKey) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(TermKeys) { k ->
                // 3-letter keys (Esc, Tab, Ctrl) are a little wider than the others.
                Box(
                    Modifier.height(36.dp).widthIn(min = if (k.label.length >= 3) 50.dp else 40.dp)
                        .clip(RoundedCornerShape(11.dp)).background(TC.keyBg)
                        .clickable { onKey(k) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(k.label, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TC.textOut) }
            }
        }
        // Fade that hints "scroll for more". Same colour as the card.
        Box(Modifier.align(Alignment.CenterEnd).width(36.dp).height(56.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, TC.card))))
    }
}




