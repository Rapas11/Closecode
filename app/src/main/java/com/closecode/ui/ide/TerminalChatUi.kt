package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.Mono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private object TC {
    val bg = Color(0xFF0F111A)
    val headerLine = Color(0xFF20243A)
    val tile = Color(0xFF181B2B)
    val card = Color(0xFF171A29)
    val cardLine = Color(0xFF262A40)
    val dockLine = Color(0xFF2C3050)
    val accent = Color(0xFF7B6BF0)
    val accentSoft = Color(0xFFB7ACFF)
    val shellAvatar = Color(0xFF8E7FF5)
    val text = Color(0xFFF2F3F7)
    val textOut = Color(0xFFE3E5F0)
    val dim = Color(0xFF9AA0BC)
    val muted = Color(0xFF7E839C)
    val tabText = Color(0xFFA4A9C2)
    val green = Color(0xFF4ADE80)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFE5484D)
    val promptInBubble = Color(0xFFB8F5CF)
    val keyBg = Color(0xFF232638)
    val savedTile = Color(0xFF26295A)
    val savedTileIcon = Color(0xFF9C90FF)
    val plusBorder = Color(0xFF6A6F8C)
    val bracesOn = Color(0xFF2A2D5E)
    val dashed = Color(0xFF4A4F8A)
}

internal enum class DockPanel { None, Saved, NewCommand, Keys }

internal data class TermChatLine(val text: String, val tone: TermTone)

internal sealed interface TermChat {
    val id: String
    val at: Long

    data class User(
        override val id: String,
        override val at: Long,
        val prompt: String,
        val command: String,
    ) : TermChat

    data class Shell(
        override val id: String,
        override val at: Long,
        val shellName: String,
        val lines: List<TermChatLine>,
        val run: CommandRun?,
        val running: Boolean,
    ) : TermChat
}

internal data class TermKey(
    val label: String,
    val insert: String? = null,
    val cursorBack: Int = 0,
    val special: String? = null,
)

internal val TermKeys = listOf(
    TermKey("Esc", special = "esc"),
    TermKey("Tab", special = "tab"),
    TermKey("Ctrl", special = "ctrl"),
    TermKey("↑", special = "up"),
    TermKey("↓", special = "down"),
    TermKey("←", special = "left"),
    TermKey("→", special = "right"),
    TermKey("{ }", "{}", 1),
    TermKey("( )", "()", 1),
    TermKey("[ ]", "[]", 1),
    TermKey("\" \"", "\"\"", 1),
    TermKey(";", ";"),
    TermKey("=", "="),
    TermKey("/", "/"),
    TermKey("|", "|"),
    TermKey("~", "~"),
    TermKey("-", "-"),
)

internal fun buildTermChat(
    lines: List<String>,
    meta: List<TermMeta>,
    runs: List<CommandRun>,
    shellName: String,
    commandRunning: Boolean,
): List<TermChat> {
    val out = ArrayList<TermChat>()
    val output = ArrayList<TermChatLine>()
    var groupStart = -1
    var groupAt = 0L

    fun flush() {
        if (groupStart < 0) return
        val run = runs.lastOrNull { it.echoIndex == groupStart }
        val running = run != null && run.exitCode == null && commandRunning
        if (output.isNotEmpty() || running) {
            out += TermChat.Shell(
                id = "s$groupStart",
                at = groupAt,
                shellName = shellName,
                lines = output.toList(),
                run = run,
                running = running,
            )
        }
        output.clear()
        groupStart = -1
    }

    lines.forEachIndexed { index, line ->
        val echo = splitPrompt(line)
        val m = meta.getOrNull(index)
        if (echo != null) {
            flush()
            out += TermChat.User(
                id = "u$index",
                at = m?.at ?: 0L,
                prompt = echo.first,
                command = echo.second,
            )
            groupStart = index
            groupAt = m?.at ?: 0L
        } else {
            if (groupStart < 0) {
                groupStart = index
                groupAt = m?.at ?: 0L
            }
            output += TermChatLine(line, m?.tone ?: TermTone.Output)
        }
    }
    flush()
    return out
}

private fun chatClock(at: Long): String {
    if (at <= 0L) return ""
    val locale = Locale.getDefault()
    return SimpleDateFormat("h:mm a", locale).format(Date(at)).lowercase(locale)
}

private fun durationText(startedAt: Long, endedAt: Long?): String {
    val ms = ((endedAt ?: startedAt) - startedAt).coerceAtLeast(0L)
    return String.format(Locale.US, "%.1f s", ms / 1000.0)
}

@Composable
internal fun TerminalChatScreen(
    sessions: List<TerminalSession>,
    selectedShellId: Int,
    cwd: String,
    messages: List<TermChat>,
    savedCommands: List<String>,
    input: TextFieldValue,
    listState: LazyListState,
    followOutput: Boolean,
    onFollowChange: (Boolean) -> Unit,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSelectShell: (Int) -> Unit,
    onNewShell: () -> Unit,
    onCloseShell: (Int) -> Unit,
    onRenameShell: (Int, String) -> Unit,
    onClearShell: (Int) -> Unit,
    onSaveCommand: (String, String?) -> Unit,
    onPickSaved: (String) -> Unit,
    onSpecialKey: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onWidthChange: (Int) -> Unit,
    newCommandRequest: Int,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    modifier: Modifier = Modifier,
) {
    var panel by rememberSaveable { mutableStateOf(DockPanel.None) }

    LaunchedEffect(newCommandRequest) {
        if (newCommandRequest > 0) panel = DockPanel.NewCommand
    }
    BackHandler(enabled = panel != DockPanel.None) { panel = DockPanel.None }

    fun headerBack() {
        if (panel != DockPanel.None) panel = DockPanel.None else onBack()
    }

    Column(modifier.fillMaxSize().background(TC.bg).statusBarsPadding()) {
        TermHeader(onBack = ::headerBack, onSettings = onOpenSettings)
        ShellTabs(
            sessions = sessions,
            selectedId = selectedShellId,
            cwd = cwd,
            onSelect = onSelectShell,
            onNew = onNewShell,
            onClose = onCloseShell,
            onRename = onRenameShell,
            onClear = onClearShell,
        )
        MessageList(
            messages = messages,
            listState = listState,
            followOutput = followOutput,
            onFollowChange = onFollowChange,
            onStop = onStop,
            onWidthChange = onWidthChange,
            modifier = Modifier.weight(1f),
        )
        CommandDock(
            panel = panel,
            onPanel = { panel = it },
            input = input,
            onInput = onInputChange,
            onSend = onSend,
            saved = savedCommands,
            onPickSaved = {
                onPickSaved(it)
                panel = DockPanel.None
            },
            onSaveNew = onSaveCommand,
            onSpecialKey = onSpecialKey,
            onInputChange = onInputChange,
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun TermHeader(onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                drawLine(TC.headerLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(onBack) { CcIcon("back", TC.text, 20.dp) }
        Text(
            text = "Terminal",
            color = TC.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconTile(onSettings) { CcIcon("cog", TC.text, 19.dp) }
    }
}

@Composable
private fun IconTile(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TC.tile)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShellTabs(
    sessions: List<TerminalSession>,
    selectedId: Int,
    cwd: String,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onClose: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onClear: (Int) -> Unit,
) {
    var cardFor by remember { mutableStateOf<Int?>(null) }
    var cardAnchor by remember { mutableStateOf(IntOffset.Zero) }
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sessions.forEach { session ->
            val selected = session.number == selectedId
            val label = session.customName?.takeIf { it.isNotBlank() } ?: "Shell ${session.number}"
            var tabOrigin by remember(session.number) { mutableStateOf(IntOffset.Zero) }
            Box {
                Row(
                    Modifier
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            tabOrigin = IntOffset(
                                position.x.roundToInt(),
                                (position.y + coordinates.size.height).roundToInt(),
                            )
                        }
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) TC.accent else TC.tile)
                        .combinedClickable(
                            onClick = { onSelect(session.number) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                cardAnchor = tabOrigin
                                cardFor = session.number
                            },
                        )
                        .padding(horizontal = 14.dp)
                        .semantics { contentDescription = label },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (session.commandRunning) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(TC.green))
                    }
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else TC.tabText,
                        maxLines = 1,
                    )
                }
                if (cardFor == session.number) {
                    ShellTabCard(
                        anchor = cardAnchor,
                        shellName = label,
                        isRunning = session.commandRunning,
                        onRename = { onRename(session.number, it) },
                        onClear = { onClear(session.number) },
                        onClose = { onClose(session.number) },
                        onDismiss = { cardFor = null },
                    )
                }
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(TC.tile)
                .clickable(onClick = onNew)
                .semantics { contentDescription = "New shell" },
            contentAlignment = Alignment.Center,
        ) { CcIcon("plus", TC.tabText, 16.dp) }
        Spacer(Modifier.weight(1f))
        Text(cwd, fontFamily = Mono, fontSize = 11.5.sp, color = TC.muted, maxLines = 1)
    }
}

@Composable
private fun MessageList(
    messages: List<TermChat>,
    listState: LazyListState,
    followOutput: Boolean,
    onFollowChange: (Boolean) -> Unit,
    onStop: () -> Unit,
    onWidthChange: (Int) -> Unit,
    modifier: Modifier,
) {
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            info.totalItemsCount == 0 ||
                (last != null && last.index == info.totalItemsCount - 1 &&
                    last.offset + last.size <= info.viewportEndOffset + 2)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { atBottom }.collect { onFollowChange(it) }
    }

    val streamingLines = (messages.lastOrNull() as? TermChat.Shell)?.lines?.size ?: 0
    LaunchedEffect(messages.size, streamingLines, followOutput) {
        if (followOutput && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            listState.scrollBy(1_000_000f)
        }
    }

    if (messages.isEmpty()) {
        Box(modifier.fillMaxWidth().onSizeChanged { onWidthChange(it.width) }, contentAlignment = Alignment.Center) {
            Text("Nothing here yet. Type a command below.", color = TC.muted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        modifier.fillMaxWidth().onSizeChanged { onWidthChange(it.width) },
        state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        items(messages, key = { it.id }) { message ->
            when (message) {
                is TermChat.User -> UserBubble(message)
                is TermChat.Shell -> ShellCard(message, onStop)
            }
        }
    }
}

@Composable
private fun UserBubble(message: TermChat.User) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.widthIn(max = 290.dp), horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                Text(chatClock(message.at), fontSize = 11.sp, color = TC.muted)
                Text("You", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TC.text)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TC.promptInBubble)) { append(message.prompt + " ") }
                    append(message.command)
                },
                fontFamily = Mono,
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp))
                    .background(TC.accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Avatar(TC.accent) { CcIcon("user", Color.White, 17.dp) }
    }
}

@Composable
private fun ShellCard(message: TermChat.Shell, onStop: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(TC.shellAvatar) { CcIcon("term", Color.White, 17.dp) }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                Text(message.shellName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TC.text)
                Text(chatClock(message.at), fontSize = 11.sp, color = TC.muted)
            }
            Spacer(Modifier.height(5.dp))
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(TC.card)
                    .border(1.dp, TC.cardLine, shape)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            ) {
                if (message.lines.isNotEmpty()) {
                    Text(
                        buildAnnotatedString {
                            message.lines.forEachIndexed { index, line ->
                                val color = when (line.tone) {
                                    TermTone.Success -> TC.green
                                    TermTone.Warning -> TC.amber
                                    TermTone.Error -> TC.red
                                    TermTone.Dim -> TC.dim
                                    else -> TC.textOut
                                }
                                withStyle(SpanStyle(color = color)) { append(line.text) }
                                if (index < message.lines.lastIndex) append("\n")
                            }
                        },
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        lineHeight = 19.2.sp,
                    )
                    if (message.running) {
                        val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
                            1f,
                            0f,
                            infiniteRepeatable(tween(600), RepeatMode.Reverse),
                            label = "cursorAlpha",
                        )
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .size(7.dp, 13.dp)
                                .alpha(blink)
                                .background(TC.accentSoft),
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TC.cardLine))
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (message.running) {
                        val started = message.run?.startedAt ?: System.currentTimeMillis()
                        RunningFooter(started, onStop, Modifier.weight(1f))
                    } else {
                        val exit = message.run?.exitCode ?: 0
                        val ok = exit == 0
                        val color = if (ok) TC.green else TC.red
                        CcIcon(if (ok) "check" else "x2", color, 13.dp)
                        Text(
                            "exit $exit · ${durationText(message.run?.startedAt ?: 0L, message.run?.endedAt)}",
                            fontFamily = Mono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.clickable {
                            clipboard.setText(AnnotatedString(message.lines.joinToString("\n") { it.text }))
                        }) {
                            CcIcon("copy", TC.muted, 15.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningFooter(startedAt: Long, onStop: () -> Unit, modifier: Modifier = Modifier) {
    var seconds by remember(startedAt) {
        mutableStateOf(((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L).toInt())
    }
    LaunchedEffect(startedAt) {
        while (true) {
            delay(1000)
            seconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L).toInt()
        }
    }
    Text(
        "running… $seconds s",
        fontFamily = Mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = TC.amber,
        modifier = modifier,
    )
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TC.red)
            .clickable(onClick = onStop)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CcIcon("stop", Color.White, 11.dp)
        Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun Avatar(background: Color, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun CommandDock(
    panel: DockPanel,
    onPanel: (DockPanel) -> Unit,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    saved: List<String>,
    onPickSaved: (String) -> Unit,
    onSaveNew: (String, String?) -> Unit,
    onSpecialKey: (String) -> Unit,
    onInputChange: (TextFieldValue) -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
) {
    val shape = RoundedCornerShape(26.dp)
    val open = panel != DockPanel.None
    val listLike = panel == DockPanel.Saved || panel == DockPanel.NewCommand

    Box(
        Modifier
            .fillMaxWidth()
            .background(TC.bg)
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (open) Modifier.shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    else Modifier,
                )
                .clip(shape)
                .background(TC.card)
                .border(1.dp, TC.dockLine, shape),
        ) {
            AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    when (panel) {
                        DockPanel.Saved -> SavedPanel(
                            saved = saved,
                            onPick = onPickSaved,
                            onAdd = { onPanel(DockPanel.NewCommand) },
                        )
                        DockPanel.NewCommand -> NewCommandPanel(
                            onSave = { command, name ->
                                onSaveNew(command, name)
                                onPanel(DockPanel.Saved)
                            },
                            onCancel = { onPanel(DockPanel.Saved) },
                        )
                        DockPanel.Keys -> KeysPanel(
                            onKey = { key ->
                                when {
                                    key.special != null -> onSpecialKey(key.special)
                                    key.insert != null -> onInputChange(
                                        insertAtCursor(input, key.insert, key.cursorBack),
                                    )
                                }
                            },
                        )
                        DockPanel.None -> Unit
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TC.cardLine))
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.8.dp, if (listLike) TC.accentSoft else TC.plusBorder, CircleShape)
                        .clickable { onPanel(if (listLike) DockPanel.None else DockPanel.Saved) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (listLike) CcIcon("x2", TC.accentSoft, 18.dp) else CcIcon("plus", TC.text, 19.dp)
                }

                Box(Modifier.weight(1f)) {
                    if (input.text.isEmpty()) {
                        Text("Type a command…", fontFamily = Mono, fontSize = 13.5.sp, color = TC.muted)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInput,
                        maxLines = 4,
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 13.5.sp, color = TC.text),
                        cursorBrush = SolidColor(TC.accentSoft),
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
                }

                val keysOn = panel == DockPanel.Keys
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (keysOn) TC.bracesOn else Color.Transparent)
                        .clickable { onPanel(if (keysOn) DockPanel.None else DockPanel.Keys) },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon("braces", if (keysOn) TC.accentSoft else TC.tabText, 20.dp)
                }

                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TC.accent)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) { CcIcon("send", Color.White, 18.dp) }
            }
        }
    }
}

private fun insertAtCursor(tv: TextFieldValue, text: String, cursorBack: Int): TextFieldValue {
    val start = tv.selection.min
    val end = tv.selection.max
    val newText = tv.text.replaceRange(start, end, text)
    return TextFieldValue(newText, TextRange((start + text.length - cursorBack).coerceIn(0, newText.length)))
}

@Composable
private fun SavedPanel(saved: List<String>, onPick: (String) -> Unit, onAdd: () -> Unit) {
    Column {
        LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(saved, key = { it }) { entry ->
                val command = splitAlias(entry).second
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clickable { onPick(command) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(TC.savedTile),
                        contentAlignment = Alignment.Center,
                    ) { CcIcon("term", TC.savedTileIcon, 15.dp) }
                    Text(command, fontFamily = Mono, fontSize = 13.5.sp, color = TC.text, maxLines = 1)
                }
            }
        }
        if (saved.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(TC.cardLine))
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.5.dp, TC.dashed, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) { CcIcon("plus", TC.accentSoft, 14.dp) }
            Text("Add command", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TC.accentSoft)
        }
    }
}

@Composable
private fun NewCommandPanel(onSave: (String, String?) -> Unit, onCancel: () -> Unit) {
    var command by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            "New command",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TC.text,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        FormField("Command", "e.g. git status", command) { command = it }
        FormField("Name (optional)", "e.g. Status", name) { name = it }
        Row(
            Modifier.padding(horizontal = 16.dp).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(TC.keyBg)
                    .clickable {
                        command = ""
                        name = ""
                        onCancel()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCDD0E2))
            }
            val canSave = command.isNotBlank()
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .alpha(if (canSave) 1f else 0.5f)
                    .clip(RoundedCornerShape(21.dp))
                    .background(TC.accent)
                    .clickable(enabled = canSave) {
                        onSave(command.trim(), name.trim().ifEmpty { null })
                        command = ""
                        name = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Save command", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun FormField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TC.tabText,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TC.bg)
                .border(1.dp, TC.dockLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(hint, fontFamily = Mono, fontSize = 13.sp, color = TC.muted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                cursorBrush = SolidColor(TC.accentSoft),
                textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = TC.text),
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

@Composable
private fun KeysPanel(onKey: (TermKey) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(TermKeys) { key ->
                Box(
                    Modifier
                        .height(36.dp)
                        .widthIn(min = if (key.label.length >= 3) 50.dp else 40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(TC.keyBg)
                        .clickable { onKey(key) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        key.label,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TC.textOut,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(36.dp)
                .height(56.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, TC.card))),
        )
    }
}
