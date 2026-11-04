package com.closecode.ui.ide

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcShape
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType
import com.closecode.ui.components.ChangeKind
import com.closecode.ui.components.changeColor
import com.closecode.ui.components.OneLine
import com.closecode.ui.components.EmptyState
import com.closecode.ui.components.ErrorCard
import com.closecode.ui.components.LoadingList
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.HeaderAction
import com.closecode.ui.components.SmallAction
import com.closecode.ui.components.MenuOverlay
import com.closecode.ui.components.MenuEntry
import com.closecode.ui.model.FileKind

/** Screen 10 — Files panel inside SidePanel. */
@Composable
fun FilesPanel(
    projectName: String?,
    openFiles: List<String>,
    rows: List<TreeRowUi>,
    filter: String,
    onFilter: (String) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenTab: (String) -> Unit,
    onRowClick: (TreeRowUi) -> Unit,
    onRowLongClick: (TreeRowUi) -> Unit,
    runCard: (@Composable () -> Unit)?,
) {
    val c = CcTheme.colors

    SidePanel(
        title = "Files",
        subtitle = projectName,
        actions = {
            HeaderAction("plus", "New file", onClick = onNewFile)
            HeaderAction("folder", "New folder", onClick = onNewFolder)
        },
        runCard = runCard,
    ) {
        PanelFilter(filter, onFilter, "Filter files")
        if (openFiles.isNotEmpty()) {
            SecLabel("Open · ${openFiles.size}")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 0.dp, end = 14.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                openFiles.forEach { name ->
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(c.field)
                            .clickable { onOpenTab(name) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        OneLine(
                            name,
                            CcType.caption.copy(fontWeight = FontWeight.SemiBold),
                            c.text2,
                        )
                    }
                }
            }
        }
        SecLabel("Files")
        when {
            rows.isEmpty() && filter.isNotEmpty() ->
                EmptyState("search", "No files match", "Try another name.")
            rows.isEmpty() ->
                EmptyState("folder", "This folder is empty", "Create a file or a folder.", "New file", onNewFile)
            else -> {
                rows.forEach { row ->
                    TreeRow(row, { onRowClick(row) }, { onRowLongClick(row) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Screen 10 — Filter pill used in the Files panel. */
@Composable
fun PanelFilter(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
) {
    val c = CcTheme.colors
    Box(
        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.field)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcIcon("search", c.muted, 16.dp, 1.9f)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        hint,
                        style = CcType.small.copy(fontSize = 13.5.sp),
                        color = c.muted,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    cursorBrush = SolidColor(c.accent),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.5.sp,
                        color = c.text,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Screen 10 — Tree row. */
@Composable
fun TreeRow(
    n: TreeRowUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = CcTheme.colors
    val rot by androidx.compose.animation.core.animateFloatAsState(
        if (n.expanded) 90f else 0f,
        tween(120),
        label = "chev",
    )
    val ext = n.name.substringAfterLast('.', "").lowercase()
    val (icon, tint) = when {
        n.isDir -> "folder" to c.yellow
        ext in setOf("kt", "java", "ts", "js", "py", "html", "css", "xml", "json", "gradle", "kts") ->
            "code" to c.purple
        ext in setOf("md", "txt") -> "file" to c.muted
        else -> "file" to c.blue
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (n.selected) c.selectedFile else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = (6 + 14 * n.depth).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (n.isDir) {
            CcIcon("cr", c.faint, 13.dp, 2.2f)
        } else {
            Spacer(Modifier.width(13.dp))
        }
        Spacer(Modifier.width(8.dp))
        CcIcon(icon, tint, 17.dp, 1.7f)
        Spacer(Modifier.width(8.dp))
        OneLine(
            n.name,
            CcType.small.copy(fontSize = 13.5.sp),
            c.text2,
            Modifier.weight(1f),
        )
        n.gitLetter?.let { letter ->
            Text(
                letter,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp,
                ),
                color = when (letter) {
                    "M" -> c.amber
                    "A" -> c.green
                    else -> c.red
                },
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

/** Screen 11 — FileMenuOverlay. */
@Composable
fun FileMenuOverlay(
    anchorTop: Dp,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuOverlay(
        top = anchorTop,
        end = 24.dp,
        width = 200.dp,
        onDismiss = onDismiss,
        entries = listOf(
            MenuEntry.Item("plus", "New file", onClick = onNewFile),
            MenuEntry.Item("folder", "New folder", onClick = onNewFolder),
            MenuEntry.Item("edit", "Rename", onClick = onRename),
            MenuEntry.Item("copy", "Duplicate", onClick = onDuplicate),
            MenuEntry.Divider,
            MenuEntry.Item("trash", "Delete", destructive = true, onClick = onDelete),
        ),
    )
}

/** Screen 12 — Search panel inside SidePanel. */
@Composable
fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    mode: SearchMode,
    onMode: (SearchMode) -> Unit,
    state: SearchUi,
    onOpenOptions: () -> Unit,
    onRetry: () -> Unit,
    onOpenFile: (path: String, line: Int) -> Unit,
    runCard: (@Composable () -> Unit)?,
) {
    val c = CcTheme.colors
    var focused by remember { mutableStateOf(false) }

    SidePanel("Search", null, runCard = runCard) {
        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(c.field)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcIcon("search", c.textSoft, 16.dp, 1.9f)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search",
                        style = CcType.small.copy(fontSize = 14.sp),
                        color = c.muted,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    cursorBrush = SolidColor(c.accent),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = c.text,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
        }

        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchMode.values().forEach { m ->
                val on = m == mode
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (on) c.accent else c.field)
                        .clickable {
                            if (on && m == SearchMode.Text) onOpenOptions()
                            else onMode(m)
                        }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OneLine(
                        m.label,
                        CcType.caption.copy(fontWeight = FontWeight.SemiBold),
                        if (on) Color.White else c.textSoft,
                    )
                }
            }
        }

        when (state) {
            SearchUi.Idle -> EmptyState("search", "Search this project", "Type at least 2 characters.")
            SearchUi.Searching -> LoadingList(3)
            is SearchUi.Failed -> ErrorCard(state.message, onRetry)
            is SearchUi.Done -> {
                if (state.groups.isEmpty()) {
                    EmptyState("search", "No results", "Try different words or options.")
                } else {
                    val hits = state.groups.sumOf { it.hits.size }.coerceAtLeast(state.groups.size)
                    val n = state.groups.size
                    SecLabel("$hits ${if (hits == 1) "result" else "results"} in $n ${if (n == 1) "file" else "files"}")
                    state.groups.forEach { g ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFile(g.path, 1) }
                                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CcIcon("code", c.purple, 16.dp, 1.8f)
                            Spacer(Modifier.width(8.dp))
                            OneLine(g.fileName, CcType.small.copy(fontWeight = FontWeight.SemiBold), c.text)
                        }
                        g.hits.forEach { h ->
                            HitRow(h) { onOpenFile(g.path, h.line) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HitRow(h: SearchHit, onClick: () -> Unit) {
    val c = CcTheme.colors
    val styled = buildAnnotatedString {
        append(h.text)
        h.matches.forEach {
            addStyle(
                SpanStyle(background = c.matchBg, color = c.matchText),
                it.first,
                it.last + 1,
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.content)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        val mono = CcType.code.copy(fontSize = 11.sp, lineHeight = 16.sp)
        Text("${h.line}", style = mono, color = c.faint)
        Spacer(Modifier.width(8.dp))
        Text(
            styled,
            style = mono,
            color = c.textSoft,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Screen 12/13 — Search options overlay. */
@Composable
fun SearchOptionsOverlay(
    options: SearchOptions,
    onChange: (SearchOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuOverlay(
        top = 140.dp,
        end = 18.dp,
        width = 210.dp,
        onDismiss = onDismiss,
        entries = listOf(
            MenuEntry.Item(
                "check",
                "Match case",
                iconVisible = options.matchCase,
                onClick = { onChange(options.copy(matchCase = !options.matchCase)) },
            ),
            MenuEntry.Divider,
            MenuEntry.Item(
                "check",
                "Whole word",
                iconVisible = options.wholeWord,
                onClick = { onChange(options.copy(wholeWord = !options.wholeWord)) },
            ),
            MenuEntry.Divider,
            MenuEntry.Item(
                "check",
                "Use regex (.*)",
                iconVisible = options.regex,
                onClick = { onChange(options.copy(regex = !options.regex)) },
            ),
        ),
    )
}

/** Screen 14 — GitHub panel (connected). */
@Composable
fun GitHubPanel(
    repo: String,
    branch: String,
    changes: List<ChangeUi>,
    commits: List<CommitUi>,
    nowMillis: Long,
    message: String,
    onMessage: (String) -> Unit,
    busy: Boolean,
    onRefresh: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onToggleStage: (ChangeUi) -> Unit,
    onOpenChange: (ChangeUi) -> Unit,
    onCommit: () -> Unit,
    onOpenCommit: (CommitUi) -> Unit,
) {
    val c = CcTheme.colors
    val staged = changes.count { it.staged }

    SidePanel("GitHub", "$repo · $branch", actions = {
        HeaderAction("refresh", "Refresh", onClick = onRefresh)
    }) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallAction("down", "Pull", enabled = !busy, onClick = onPull)
            SmallAction("up", "Push", enabled = !busy, onClick = onPush)
        }

        SecLabel("Changes · ${changes.size}")
        if (changes.isEmpty()) {
            Text(
                "No changes",
                style = CcType.small,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        changes.forEach { ch ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.field)
                    .clickable { onOpenChange(ch) }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ch.staged) c.green else Color.Transparent)
                        .border(
                            if (ch.staged) 0.dp else 1.5.dp,
                            c.toggleOff,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onToggleStage(ch) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (ch.staged) {
                        CcIcon("check", Color.White, 12.dp, 3f)
                    }
                }
                Spacer(Modifier.width(10.dp))
                OneLine(
                    ch.name,
                    CcType.small.copy(fontWeight = FontWeight.SemiBold),
                    c.text,
                    Modifier.weight(1f),
                )
                Text(
                    ch.kind.letter,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = changeColor(ch.kind),
                )
            }
        }

        Box(
            Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.rail)
                .border(1.dp, c.border, RoundedCornerShape(13.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (message.isEmpty()) {
                Text(
                    "Commit message…",
                    style = CcType.small,
                    color = c.muted,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = message,
                onValueChange = onMessage,
                cursorBrush = SolidColor(c.accent),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = c.text,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }

        val canCommit = message.isNotBlank() && staged > 0 && !busy
        Row(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (canCommit) c.green else c.field)
                .clickable(enabled = canCommit, onClick = onCommit),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcIcon("check", if (canCommit) Color.White else c.muted, 16.dp, 2.8f)
            Spacer(Modifier.width(8.dp))
            Text(
                if (staged > 1) "Commit $staged files" else if (staged == 1) "Commit 1 file" else "Commit",
                style = CcType.body.copy(fontWeight = FontWeight.Bold),
                color = if (canCommit) Color.White else c.muted,
            )
        }

        SecLabel("History", top = 18.dp)
        if (commits.isEmpty()) {
            Text(
                "No commits yet",
                style = CcType.small,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        commits.take(3).forEach { cm ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .clickable { onOpenCommit(cm) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(
                    Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .padding(top = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .then(
                                if (cm.isHead)
                                    Modifier.background(c.accent)
                                else
                                    Modifier.border(2.dp, c.accent, CircleShape),
                            ),
                    )
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .width(2.dp)
                            .weight(1f)
                            .background(Color(0xFFD9DCE2)),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    OneLine(cm.message, CcType.small, c.text)
                    OneLine(
                        " ${cm.hash.take(7)} · ${shortAgo(cm.timeMillis, nowMillis)}",
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        ),
                        c.muted,
                        Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Screen 15 — GitHub signed out panel. */
@Composable
fun GitHubSignedOutPanel(
    onSignIn: () -> Unit,
    onCloneLink: () -> Unit,
    problem: String? = null,
) {
    val c = CcTheme.colors
    SidePanel("GitHub", "Not connected") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.text2.tint()),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("gh", c.text2, 36.dp, 1.6f)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Connect GitHub",
                style = CcType.sheetTitle.copy(fontSize = 20.sp),
                color = c.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to clone your repositories, push your work and see changes.",
                style = CcType.small.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = c.muted,
                textAlign = TextAlign.Center,
            )
            if (problem != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    problem,
                    style = CcType.caption,
                    color = c.red,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Sign in with GitHub", onSignIn, Modifier.fillMaxWidth(), icon = "gh")
            Spacer(Modifier.height(16.dp))
            Text(
                "Clone a public link instead",
                style = CcType.small.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickable(onClick = onCloneLink),
            )
        }
    }
}

/** Screen 22 — Terminal panel inside SidePanel. */
@Composable
fun TerminalPanel(
    folder: String,
    shells: List<ShellUi>,
    selectedShellId: String?,
    commands: List<SavedCommandUi>,
    onNewShell: () -> Unit,
    onOpenShell: (ShellUi) -> Unit,
    onRunCommand: (SavedCommandUi) -> Unit,
    onAddCommand: () -> Unit,
    runCard: (@Composable () -> Unit)?,
) {
    val c = CcTheme.colors

    SidePanel(
        title = "Terminal",
        subtitle = folder,
        runCard = runCard,
        actions = {
            Box(
                Modifier
                    .size(38.dp, 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.field)
                    .clickable(onClick = onNewShell),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("plus", c.text2, 18.dp, 1.9f)
            }
        },
    ) {
        PanelSection("SHELLS")
        if (shells.isEmpty()) {
            Text(
                "No shells open. Tap + to start one.",
                style = CcType.small,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        shells.forEach { s ->
            val sel = s.id == selectedShellId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (sel) 8.dp else 0.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) c.field else Color.Transparent)
                    .clickable { onOpenShell(s) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.blue.tint()),
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon("term", c.blue, 19.dp)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(3.dp, 3.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(c.panel)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (s.running) c.green else c.muted),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    OneLine(
                        s.name,
                        CcType.itemTitle.copy(fontSize = 14.sp),
                        c.text,
                    )
                    OneLine(
                        "${s.folder} · ${if (s.running) "running" else "idle"}",
                        CcType.log.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        c.muted,
                        Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        PanelSection("SAVED COMMANDS")
        Column(
            Modifier
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            commands.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { cmd ->
                        Row(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.field)
                                .clickable { onRunCommand(cmd) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CcIcon("cr", c.accent, 13.dp, 2.6f)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                cmd.text,
                                style = CcType.log.copy(fontSize = 12.sp, lineHeight = 14.sp),
                                color = c.text2,
                                maxLines = 2,
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(40.dp)
                .drawBehind {
                    val radius = 12.dp.toPx()
                    drawRoundRect(
                        brush = SolidColor(c.accent),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        )),
                    )
                }
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAddCommand),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcIcon("plus", c.accent, 15.dp, 2.4f)
            Spacer(Modifier.width(7.dp))
            Text(
                "Add command",
                style = CcType.small.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
            )
        }
    }
}

@Composable
private fun PanelSection(text: String) = Text(
    text,
    style = CcType.section,
    color = CcTheme.colors.muted,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
)

/** Screen 10 tree row UI data. */
data class TreeRowUi(
    val path: String,
    val name: String,
    val depth: Int,
    val isDir: Boolean,
    val expanded: Boolean,
    val selected: Boolean,
    val gitLetter: String?,
)

/** Screen 14 change UI data. */
data class ChangeUi(
    val path: String,
    val name: String,
    val kind: ChangeKind,
    val staged: Boolean,
)

/** Screen 14 commit UI data. */
data class CommitUi(
    val hash: String,
    val message: String,
    val author: String,
    val timeMillis: Long,
    val isHead: Boolean,
)

/** Screen 12 search mode. */
enum class SearchMode(val label: String) {
    Files("Files"),
    Text("Text"),
    Symbols("Symbols"),
}

/** Screen 12 search hit. */
data class SearchHit(
    val line: Int,
    val text: String,
    val matches: List<IntRange>,
)

/** Screen 12 search group. */
data class SearchGroup(
    val path: String,
    val fileName: String,
    val hits: List<SearchHit>,
)

/** Screen 12 search UI state. */
sealed interface SearchUi {
    data object Idle : SearchUi
    data object Searching : SearchUi
    data class Done(val groups: List<SearchGroup>) : SearchUi
    data class Failed(val message: String) : SearchUi
}

/** Screen 12/13 search options. */
data class SearchOptions(
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false,
)

/** Screen 22 shell UI data. */
data class ShellUi(
    val id: String,
    val name: String,
    val folder: String,
    val running: Boolean,
)

/** Screen 22 saved command UI data. */
data class SavedCommandUi(
    val id: String,
    val text: String,
)
