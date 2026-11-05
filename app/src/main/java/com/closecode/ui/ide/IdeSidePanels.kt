package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.lang.Severity
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v7 side panels: Search, Terminal, Logcat and Problems are panels pinned to the rail's right
 * edge — the same surface as Files. 234 dp wide, both top corners 26, title 21/800, and the run
 * card floating at the bottom over a reserved strip of the panel.
 */

/** The spec's six tile colours, assigned to projects by name so the rail and run card agree. */
internal fun projectTileColor(name: String): Color = listOf(
    Color(0xFF2563EB),
    Color(0xFF16A34A),
    Color(0xFFEA580C),
    Color(0xFF0E7490),
    Color(0xFF7C3AED),
    Color(0xFFBE185D),
)[name.hashCode().and(0x7FFFFFFF) % 6]

/** How the run card's state word is coloured: green ok, red failed, muted anything else. */
internal enum class IdeRunTone { MUTED, OK, FAILED }

/** The run card's second line: the state word and the colour it reads in. */
internal data class IdeRunCardUi(val label: String, val tone: IdeRunTone)

/** Same words as the check panel's: building with progress, failed, ok, or not built yet. */
internal fun runCardUiOf(building: Boolean, progress: Float, errorCount: Int, hasBuilt: Boolean): IdeRunCardUi = when {
    building -> IdeRunCardUi("building ${(progress * 100).toInt()}%", IdeRunTone.MUTED)
    errorCount > 0 -> IdeRunCardUi("failed", IdeRunTone.FAILED)
    hasBuilt -> IdeRunCardUi("ok", IdeRunTone.OK)
    else -> IdeRunCardUi("not built", IdeRunTone.MUTED)
}

/**
 * The side-panel frame itself. Subtitle under the title, header actions on the right, the body
 * in a column, and the run card floating at the bottom (content reserves 84 dp under it so the
 * last row is never hidden behind it).
 */
@Composable
internal fun IdeSidePanel(
    title: String,
    subtitle: String?,
    runCard: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Box(
        Modifier
            .width(234.dp)
            .fillMaxHeight(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(cc.panel)
                .windowInsetsPadding(WindowInsets.statusBars)
                .clipToBounds(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = cc.text,
                        fontSize = 21.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = cc.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (runCard != null) Modifier.padding(bottom = 84.dp) else Modifier),
            ) {
                body()
            }
        }
        if (runCard != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 10.dp, bottom = 12.dp),
            ) {
                runCard()
            }
        }
    }
}

/**
 * The run card every side panel floats at its bottom: 64 tall, radius 22, content background,
 * project tile 36 with its letter, the project name, `branch · state`, and the 40 green run
 * button. The branch is the repository's real one; with no repository only the state shows.
 */
@Composable
internal fun IdeRunCard(
    projectName: String,
    branch: String?,
    state: IdeRunCardUi,
    onRun: () -> Unit,
) {
    val c = LocalPageColors.current
    val stateColor = when (state.tone) {
        IdeRunTone.OK -> c.mock.green
        IdeRunTone.FAILED -> c.mock.danger
        IdeRunTone.MUTED -> c.mock.muted
    }
    val secondLine = branch?.takeIf { it.isNotBlank() }
        ?.let { "$it · ${state.label}" }
        ?: state.label
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.mock.content)
            .border(1.dp, c.mock.border, RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(projectTileColor(projectName)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = projectName.take(1).uppercase().ifEmpty { "C" },
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = projectName,
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CcIcon(CcIcons.Branch, c.mock.muted, 11.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = secondLine,
                    color = stateColor,
                    fontSize = 11.5.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.mock.green)
                .clickable(onClick = onRun)
                .semantics { contentDescription = "Run" },
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Play, c.mock.onGreen, 17.dp)
        }
    }
}

/** The header button the panels share: 38, radius 12, field fill. Same as the Files panel's. */
@Composable
internal fun SidePanelIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit = {}) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.mock.field)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, c.mock.text2, 18.dp)
    }
}

/** The v7 chips: 30 tall, radius 15, the selected one on selectedRow in white. */
@Composable
internal fun SidePanelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) c.mock.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) c.mock.text else c.mock.muted,
            fontSize = 11.5.sp,
            fontFamily = Sans,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** The filter field: 40 tall, radius 12, field background, with a clear affordance when typed. */
@Composable
internal fun SidePanelField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.mock.field)
            .padding(start = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CcIcon(CcIcons.Search, c.mock.muted, 15.dp)
            Spacer(Modifier.width(7.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = c.mock.muted,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = c.mock.text, fontSize = 13.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(c.mock.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Cross, c.mock.muted, 13.dp)
                }
            }
        }
    }
}

/**
 * The hit line with every match painted in the spec's match colours. The ranges are found on
 * the same rules the search used, so what is highlighted is exactly what matched; the string is
 * spliced at the match edges, which sidesteps whether style ranges read their end inclusive.
 */
internal fun lineWithMatch(
    line: String,
    query: String,
    caseSensitive: Boolean,
    regex: Boolean,
    wholeWord: Boolean,
    matchBg: Color,
    matchText: Color,
): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty() || line.isEmpty()) return AnnotatedString(line)
    val ranges = when {
        regex -> {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            runCatching { Regex(needle, options).findAll(line).map { it.range } }
                .getOrNull()?.toList() ?: emptyList()
        }
        wholeWord -> {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            runCatching {
                Regex("(?<!\\w)${Regex.escape(needle)}(?!\\w)", options)
                    .findAll(line).map { it.range }
            }.getOrNull()?.toList() ?: emptyList()
        }
        else -> {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            runCatching { Regex(Regex.escape(needle), options).findAll(line).map { it.range } }
                .getOrNull()?.toList() ?: emptyList()
        }
    }.filter { !it.isEmpty() }
    if (ranges.isEmpty()) return AnnotatedString(line)
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges.sortedBy { it.first }) {
            val from = range.first.coerceAtLeast(cursor)
            val end = (range.last + 1).coerceAtMost(line.length)
            if (from >= end) continue
            if (from > cursor) append(line.substring(cursor, from))
            withStyle(SpanStyle(background = matchBg, color = matchText)) {
                append(line.substring(from, end))
            }
            cursor = end
        }
        if (cursor < line.length) append(line.substring(cursor))
    }
}

/**
 * The Search side panel: the filter field, the Aa/Word/.* chips the spec names, and the results
 * grouped one card per file with the match inside the line highlighted. Reading the files is
 * disk work, so it runs on IO and the panel shows no hits until it lands.
 */
@Composable
internal fun IdeSearchSidePanel(
    projectId: String,
    files: List<ProjectFile>,
    treeVersion: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    regex: Boolean,
    wholeWord: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onRegexChange: (Boolean) -> Unit,
    onWholeWordChange: (Boolean) -> Unit,
    read: (String) -> String?,
    onOpen: (SearchHit) -> Unit,
    runCard: @Composable () -> Unit,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val searchSources by produceState(
        initialValue = emptyList(),
        key1 = projectId,
        key2 = treeVersion,
    ) {
        val documents = files.filter { it.kind != FileKind.Folder }
        value = withContext(Dispatchers.IO) {
            documents.mapNotNull { file ->
                val text = runCatching { read(file.path) }.getOrNull()
                if (text == null || text.length > MAX_SEARCH_FILE_CHARS) null else file to text
            }
        }
    }
    var scope by remember { mutableStateOf("Text") }
    val hits = remember(query, searchSources, caseSensitive, regex, wholeWord, scope) {
        when {
            // ﻩscope is a remember-backed plain string; no enum needed.
            query.isBlank() -> emptyList()
            scope == "Files" ->
                searchSources
                    .filter { it.first.path.substringAfterLast('/').contains(query.trim(), ignoreCase = true) }
                    .map { (file, _) ->
                        SearchHit(
                            fileName = file.path.substringAfterLast('/'),
                            path = file.path,
                            line = 1,
                            text = file.path,
                        )
                    }
            else -> findInSources(
                query,
                searchSources,
                caseSensitive = caseSensitive,
                regex = regex,
                wholeWord = if (scope == "Symbols") true else wholeWord,
            )
        }
    }
    val groups = remember(hits) {
        hits.groupBy { it.path }.map { (path, groupHits) -> path to groupHits }
    }
    var optionsOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var chipTop by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    IdeSidePanel(
        title = "Search",
        subtitle = null,
        runCard = runCard,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(cc.field)
                .border(
                    width = if (focused) 1.5.dp else 0.dp,
                    color = if (focused) cc.accent else Color.Transparent,
                    shape = RoundedCornerShape(21.dp),
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.closecode.ui.CcIcon("search", cc.textSoft, 16.dp, 1.9f)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search",
                        color = cc.muted,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    cursorBrush = SolidColor(cc.accent),
                    textStyle = TextStyle(color = cc.text, fontSize = 14.sp, fontFamily = Sans),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp)
                .onGloballyPositioned { coords ->
                    chipTop = with(density) { coords.positionInWindow().y.toDp() } - statusTop
                },
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("Files", "Text", "Symbols").forEach { option ->
                val on = scope == option
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (on) cc.accent else cc.field)
                        .clickable {
                            // Tapping the selected Text chip again opens screen 13.
                            if (on && option == "Text") optionsOpen = true else scope = option
                        }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        color = if (on) Color.White else cc.textSoft,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (groups.isNotEmpty()) {
            Text(
                text = "${hits.size} ${if (hits.size == 1) "RESULT" else "RESULTS"} IN " +
                    "${groups.size} ${if (groups.size == 1) "FILE" else "FILES"}",
                color = cc.muted,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        if (query.isBlank() || groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.closecode.ui.CcIcon("search", cc.faint, 28.dp, 1.6f)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (query.isBlank()) "Search this project" else "No results",
                        color = cc.text2,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (query.isBlank()) "Type at least 2 characters."
                        else "Try different words or options.",
                        color = cc.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                groups.forEach { (path, groupHits) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(groupHits.first()) }
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.closecode.ui.CcIcon("code", cc.purple, 16.dp, 1.8f)
                        Text(
                            text = groupHits.first().fileName,
                            color = cc.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    groupHits.forEach { hit ->
                        Row(
                            Modifier
                                .padding(horizontal = 14.dp)
                                .padding(top = 2.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(cc.content)
                                .clickable { onOpen(hit) }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = hit.line.toString(),
                                color = cc.faint,
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = lineWithMatch(
                                    line = hit.text,
                                    query = query,
                                    caseSensitive = caseSensitive,
                                    regex = regex,
                                    wholeWord = wholeWord,
                                    matchBg = cc.matchBg,
                                    matchText = cc.matchText,
                                ),
                                color = cc.textSoft,
                                fontSize = 11.sp,
                                fontFamily = Mono,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (optionsOpen) {
        val screenH = LocalConfiguration.current.screenHeightDp.dp
        com.closecode.ui.components.CcMenuOverlay(
            top = (chipTop + 36.dp).coerceAtMost(screenH - 200.dp).coerceAtLeast(8.dp),
            end = 18.dp,
            width = 210.dp,
            onDismiss = { optionsOpen = false },
        ) {
            com.closecode.ui.components.CcMenuItem(
                icon = "check",
                label = "Match case",
                iconVisible = caseSensitive,
                onClick = { onCaseSensitiveChange(!caseSensitive) },
            )
            com.closecode.ui.components.CcMenuDivider()
            com.closecode.ui.components.CcMenuItem(
                icon = "check",
                label = "Whole word",
                iconVisible = wholeWord,
                onClick = { onWholeWordChange(!wholeWord) },
            )
            com.closecode.ui.components.CcMenuDivider()
            com.closecode.ui.components.CcMenuItem(
                icon = "check",
                label = "Use regex (.*)",
                iconVisible = regex,
                onClick = { onRegexChange(!regex) },
            )
        }
    }
}

/**
 * The Terminal side panel: one row per shell, the running one dotted green, new output amber,
 * a fresh one grey; a New session header button opens a shell right away, and a tap opens the
 * full terminal on that shell.
 */
@Composable
internal fun IdeTerminalSidePanel(
    sessions: TerminalSessions,
    onOpen: (Int) -> Unit,
    onNew: () -> Unit,
    runCard: @Composable () -> Unit,
) {
    val c = LocalPageColors.current
    IdeSidePanel(
        title = "Terminal",
        subtitle = "${sessions.sessions.size} ${if (sessions.sessions.size == 1) "session" else "sessions"}",
        runCard = runCard,
        actions = { SidePanelIconButton(CcIcons.Plus, "New session", onClick = onNew) },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            sessions.sessions.forEach { session ->
                val running = session.isRunning || session.commandRunning
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (session.number == sessions.activeId) c.mock.selected else Color.Transparent)
                        .clickable { onOpen(session.number) }
                        .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    running -> c.mock.green
                                    session.hasNewOutput -> c.mock.warning
                                    else -> c.mock.faint
                                },
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = tabLabel(session.number, session.cwd, session.customName),
                            color = c.mock.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = session.cwd.ifBlank { "Project root" },
                            color = c.mock.muted,
                            fontSize = 10.5.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CcIcon(CcIcons.Chevron, c.mock.faint, 14.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tap a shell to open it. The full terminal keeps the strip's long presses: rename and close.",
                color = c.mock.faint,
                fontSize = 10.5.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * The Logcat side panel: level chips and the live stream, mono 10.5, level letter coloured.
 * The panel follows the tail while the last line is already showing, so a reader scrolling up
 * to read is never yanked back down. Full log stays one tap away.
 */
@Composable
internal fun IdeLogcatSidePanel(
    lines: List<LogLine>,
    filter: LogLevel?,
    onFilterChange: (LogLevel?) -> Unit,
    onClear: () -> Unit = {},
    runCard: @Composable () -> Unit = {},
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    LaunchedEffect(lines) { if (!paused) frozen = lines }
    val visible = remember(frozen, filter, paused, paused) {
        val source = if (paused) frozen else lines
        if (filter == null) source else source.filter { it.level == filter }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size, filter) {
        if (visible.isNotEmpty()) {
            val last = visible.lastIndex
            if (listState.firstVisibleItemIndex >= last - 1) {
                listState.scrollToItem(last)
            }
        }
    }
    IdeSidePanel(
        title = "Logcat",
        subtitle = "${visible.size} ${if (visible.size == 1) "line" else "lines"}",
        runCard = runCard,
        actions = {
            SidePanelIconButton(CcIcons.Download, "Export log") {
                runCatching {
                    java.io.File(context.filesDir, "logcat-export.txt")
                        .writeText(lines.joinToString("\n") { it.formatted })
                }
            }
            SidePanelIconButton(CcIcons.Trash, "Clear log") { onClear() }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SidePanelChip("All", filter == null) { onFilterChange(null) }
            for (level in LogLevel.entries) {
                SidePanelChip(level.letter.toString(), filter == level) { onFilterChange(level) }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Waiting for logs.",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(state = listState) {
                itemsIndexed(visible, key = { index, line -> "$index-${line.formatted}" }) { _, line ->
                    SideLogRow(line)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.mock.field)
                    .clickable { paused = !paused },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (paused) {
                        CcIcon(CcIcons.Play, c.mock.text2, 14.dp)
                    } else {
                        Box(Modifier.width(3.dp).height(12.dp).background(c.mock.text2))
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.width(3.dp).height(12.dp).background(c.mock.text2))
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (paused) "Resume" else "Pause",
                        color = c.mock.text2,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.mock.field)
                    .clickable {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                visible.joinToString("\n") { it.formatted },
                            )
                        }
                        try {
                            context.startActivity(android.content.Intent.createChooser(send, "Share log"))
                        } catch (_: Exception) {
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(CcIcons.Download, c.mock.text2, 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Share",
                        color = c.mock.text2,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** One logcat line: time, level letter coloured, tag, message — mono 10.5 as the spec says. */
@Composable
private fun SideLogRow(line: LogLine) {
    val c = LocalPageColors.current
    val levelColor = when (line.level) {
        LogLevel.VERBOSE -> c.mock.muted
        LogLevel.DEBUG -> c.mock.blue
        LogLevel.INFO -> c.mock.green
        LogLevel.WARN -> c.mock.warning
        LogLevel.ERROR -> c.mock.danger
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = line.time,
                color = c.mock.faint,
                fontSize = 9.5.sp,
                fontFamily = Mono,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = line.level.letter.toString(),
                color = levelColor,
                fontSize = 10.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = line.tag,
                color = c.mock.muted,
                fontSize = 10.5.sp,
                fontFamily = Mono,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = line.message,
                color = c.mock.text2,
                fontSize = 10.5.sp,
                fontFamily = Mono,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(1.dp)
                .background(c.mock.divider),
        )
    }
}

/**
 * The Problems side panel: counts by level first, then the problems grouped one card per file;
 * a tap opens the file with the caret on the problem's line.
 */
@Composable
internal fun IdeProblemsSidePanel(
    errors: List<ProjectDiagnostic>,
    warnings: List<ProjectDiagnostic>,
    onOpen: (ProjectDiagnostic) -> Unit,
    runCard: @Composable () -> Unit,
) {
    val c = LocalPageColors.current
    var filter by remember { mutableStateOf(0) }
    fun Severity.matches() = when (filter) {
        1 -> this == Severity.ERROR
        2 -> this == Severity.WARNING
        else -> true
    }
    val groups = remember(errors, warnings) {
        (errors + warnings).groupBy { it.fileName }
    }
    val shown = remember(groups, filter) { groups.mapValues { it.value.filter { r -> r.diagnostic.severity.matches() } } }
    IdeSidePanel(
        title = "Problems",
        subtitle = if (errors.size + warnings.size == 0) null else "${errors.size + warnings.size} in ${groups.size} files",
        runCard = runCard,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProblemChip("All ${errors.size + warnings.size}", filter == 0) { filter = 0 }
            ProblemChip("Errors ${errors.size}", filter == 1) { filter = 1 }
            ProblemChip("Warn ${warnings.size}", filter == 2) { filter = 2 }
        }
        Spacer(Modifier.height(6.dp))
        if (errors.size + warnings.size == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No problems found",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                shown.forEach { (fileName, rows) ->
                    if (rows.isEmpty()) return@forEach
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.mock.content)
                            .border(1.dp, c.mock.border, RoundedCornerShape(12.dp)),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CcIcon(CcIcons.Logo, c.mock.accent, 15.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = fileName,
                                color = c.mock.text,
                                fontSize = 12.5.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.mock.divider))
                        rows.forEach { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(row) }
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                val error = row.diagnostic.severity == Severity.ERROR
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (error) c.mock.dangerBg else c.mock.warningBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CcIcon(
                                        if (error) CcIcons.Cross else CcIcons.Warning,
                                        if (error) c.mock.danger else c.mock.warning,
                                        15.dp,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = row.diagnostic.message,
                                        color = c.mock.text,
                                        fontSize = 12.sp,
                                        fontFamily = Sans,
                                        lineHeight = 16.sp,
                                        minLines = 1,
                                    )
                                    Text(
                                        text = "${row.fileName}:${row.diagnostic.line}:${row.diagnostic.column}",
                                        color = c.mock.accent,
                                        fontSize = 10.5.sp,
                                        fontFamily = Mono,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** The All / Errors / Warn filter chip above the problem groups. */
@Composable
private fun ProblemChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Text(
        text = label,
        color = if (selected) c.mock.onAccent else c.mock.text2,
        fontSize = 12.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) c.mock.accent else c.mock.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
