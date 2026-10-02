package com.closecode.ui.ide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.ide.model.IdePanels
import com.closecode.ui.ide.model.kindColor
import com.closecode.ui.ide.model.kindGlyph
import com.closecode.ui.ide.model.suggestionsFor
import com.closecode.ui.editor.CompilerProbeScreen
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.model.projectTree
import com.closecode.ui.model.recentFiles
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

@Composable
fun IdeScreen(
    projectName: String,
    templateName: String,
    slug: String,
    repository: ProjectRepository,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler { onExit() }
    var tabs by remember { mutableStateOf(emptyList<ProjectFile>()) }
    var active by remember { mutableStateOf("") }
    val diskTree = remember(slug) { repository.tree(slug) }
    val diskContents = remember { mutableStateMapOf<String, String>() }
    var activePanel by remember { mutableStateOf("Search") }
    var explorerOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var probeOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }

    LaunchedEffect(diskTree) {
        val first = diskTree.firstOrNull { it.kind != FileKind.Folder }
        tabs = diskTree.filter { it.kind != FileKind.Folder }.take(3)
        active = first?.name.orEmpty()
        first?.let { file ->
            runCatching { repository.readFile(slug, file.path) }
                .onSuccess { diskContents[file.name] = it }
        }
    }

    val activeFile = tabs.firstOrNull { it.name == active }
    val language = activeFile?.language ?: "Kotlin"

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1700)
            toast = null
        }
    }

    LaunchedEffect(building) {
        if (building) {
            kotlinx.coroutines.delay(1200)
            building = false
            toast = "Build successful · 0 errors"
        }
    }

    if (probeOpen) {
        CompilerProbeScreen(onBack = { probeOpen = false })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            IdeTopBar(
                projectName = projectName,
                subtitle = templateName,
                onSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onGit = { toast = "Source Control · main · clean" },
                onRun = { building = true },
                onMore = { menuOpen = true },
                menuActive = menuOpen,
                searchActive = searchOpen,
            )

            if (activePanel == "AI") {
                AgentPanel(projectName = projectName)
            } else {
                Row(Modifier.weight(1f)) {
                    IdeExplorer(
                        expanded = explorerOpen,
                        projectName = projectName,
                        slug = slug,
                        tree = diskTree,
                        activeFile = active,
                        query = query,
                        onSelect = { file ->
                            active = file.name
                            val content = if (file.kind != FileKind.Folder) {
                                runCatching { repository.readFile(slug, file.path) }.getOrNull()
                            } else {
                                null
                            }
                            if (tabs.none { it.name == file.name }) {
                                tabs = tabs + file
                            }
                            if (content != null) diskContents[file.name] = content
                        },
                    )

                    IdeEditorArea(
                        explorerOpen = explorerOpen,
                        onToggleExplorer = { explorerOpen = !explorerOpen },
                        modifier = Modifier.weight(1f),
                        tabs = tabs,
                        active = active,
                        language = language,
                        contentFor = { name -> diskContents[name] ?: "" },
                        onSelectTab = { active = it },
                        onCloseTab = { name ->
                            tabs = tabs.filterNot { it.name == name }
                            if (active == name) active = tabs.lastOrNull()?.name.orEmpty()
                        },
                    )
                }
            }

            IdeStatusBar(
                language = language,
                statusText = when {
                    building -> "● Building…"
                    toast?.startsWith("Build successful") == true -> "● Build successful"
                    else -> "● Ready"
                },
                projectName = projectName,
            )

            IdeNav(
                active = activePanel,
                onSelect = { activePanel = it },
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            IdeSearchPanel(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    searchOpen = false
                    query = ""
                },
            )
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 66.dp),
        ) {
            IdeToast(toast.orEmpty())
        }

        if (menuOpen) {
            OverflowMenu(
                actions = listOf(
                    OverflowAction("Settings", "⚙"),
                    OverflowAction("New project", "+"),
                    OverflowAction("Close project", "×"),
                    OverflowAction("Compiler probe", "◆"),
                ),
                onPick = { action ->
                    menuOpen = false
                    when (action.label) {
                        "Settings" -> onOpenSettings()
                        "New project" -> onExit()
                        "Close project" -> onExit()
                        "Compiler probe" -> probeOpen = true
                    }
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

@Composable
private fun IdeTopBar(
    projectName: String,
    subtitle: String,
    onSearch: () -> Unit,
    onGit: () -> Unit,
    onRun: () -> Unit,
    onMore: () -> Unit,
    menuActive: Boolean,
    searchActive: Boolean,
) {
    val c = LocalPageColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(0.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(c.tx, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "</>",
                color = c.bg,
                fontSize = 10.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Closecode",
            color = c.tx,
            fontSize = 17.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 9.dp),
        )

        Column(Modifier.padding(start = 14.dp)) {
            Text(
                text = projectName,
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp),
            )
            Text(
                text = subtitle,
                color = c.mu,
                fontSize = 11.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp),
            )
        }

        Box(Modifier.weight(1f))

        TopIcon("⌕", searchActive, onSearch)
        TopIcon("⌘", false, onGit)
        TopIcon("▶", false, onRun)
        TopIcon("⋮", menuActive, onMore)
    }
}



@Composable
private fun TopIcon(glyph: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) c.panel2 else Color.Transparent)
            .border(1.dp, if (active) c.ln else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (active) c.tx else c.mu,
            fontSize = 17.sp,
            fontFamily = Sans,
        )
    }
}

@Composable
private fun IdeExplorer(
    expanded: Boolean,
    projectName: String,
    slug: String,
    tree: List<ProjectFile>,
    activeFile: String,
    query: String,
    onSelect: (ProjectFile) -> Unit,
) {
    val c = LocalPageColors.current

    val wide = LocalConfiguration.current.screenWidthDp >= 760
    val fullWidth = if (wide) 290.dp else 235.dp
    val width by animateDpAsState(
        targetValue = if (expanded) fullWidth else 0.dp,
        animationSpec = tween(200),
        label = "explorer-width",
    )

    var collapsed by remember(slug) { mutableStateOf(setOf<String>()) }

    val q = query.trim().lowercase()

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(c.panel)
            .clipToBounds(),
    ) {
        Row(
            Modifier
                .width(if (expanded) fullWidth else 0.dp)
                .fillMaxWidth()
                .height(50.dp)
                .border(1.dp, c.ln, RoundedCornerShape(0.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EXPLORER",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .width(if (expanded) fullWidth else 0.dp)
        ) {
            item(key = "root") {
                TreeRow(
                    label = projectName,
                    depth = 0,
                    bold = true,
                    glyph = "▾",
                    glyphColor = c.ac,
                    active = false,
                    onClick = {},
                )
            }

            item(key = "sec-project") { SectionLabel("PROJECT") }

            itemsIndexed(tree, key = { i, f -> "tree-$i-${f.name}" }) { _, file ->
                if (file.kind == FileKind.Folder && file.name in collapsed) return@itemsIndexed
                if (q.isNotEmpty() && !file.name.lowercase().contains(q)) {
                    if (file.kind != FileKind.Folder) return@itemsIndexed
                }
                TreeRow(
                    label = file.name,
                    depth = file.depth + 1,
                    bold = false,
                    glyph = kindGlyph(file.kind),
                    glyphColor = kindColor(file.kind, c),
                    active = file.name == activeFile,
                    onClick = {
                        if (file.kind == FileKind.Folder) {
                            collapsed = if (file.name in collapsed) {
                                collapsed - file.name
                            } else {
                                collapsed + file.name
                            }
                        } else {
                            onSelect(file)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalPageColors.current
    Text(
        text = text,
        color = c.mu.copy(alpha = 0.7f),
        fontSize = 10.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 17.dp, top = 13.dp, bottom = 5.dp),
    )
}

@Composable
private fun TreeRow(
    label: String,
    depth: Int,
    bold: Boolean,
    glyph: String,
    glyphColor: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) c.selection else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 9.dp + 17.dp * depth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = glyph,
            color = glyphColor,
            fontSize = 11.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = label,
            color = if (active) c.tx else c.tx.copy(alpha = 0.86f),
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IdeEditorArea(
    tabs: List<ProjectFile>,
    active: String,
    language: String,
    contentFor: (String) -> String,
    explorerOpen: Boolean,
    onToggleExplorer: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current

    val fallback = remember(language) {
        snippetFor(language, c).joinToString("\n") { it.text }
    }
    val fromDisk = remember(active, contentFor) { contentFor(active) }
    var code by remember(active, language) {
        mutableStateOf(fromDisk.ifEmpty { fallback })
    }
    LaunchedEffect(active) {
        val next = contentFor(active)
        if (next.isNotEmpty()) code = next
    }
    val codeScroll = rememberScrollState()
    val suggestions = remember(language) { suggestionsFor(language) }
    var selectedSug by remember(language) { mutableStateOf(0) }

    val codeLines = remember(code) { code.lines() }

    val visibleSuggestions = remember(code, suggestions) {
        val tail = code.takeLastWhile { it.isLetterOrDigit() }
        if (tail.isEmpty()) emptyList() else suggestions.filter { it.name.startsWith(tail) }
    }

    Column(
        modifier
            .fillMaxHeight()
            .background(c.editor)
    ) {
        LazyRow(
            Modifier
                .fillMaxWidth()
                .height(47.dp)
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(0.dp)),
        ) {
            item(key = "explorer-toggle") {
                Box(
                    Modifier
                        .width(47.dp)
                        .height(47.dp)
                        .background(c.panel)
                        .border(1.dp, c.ln, RoundedCornerShape(0.dp))
                        .clickable(onClick = onToggleExplorer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (explorerOpen) "‹" else "›",
                        color = c.mu,
                        fontSize = 22.sp,
                        fontFamily = Sans,
                    )
                }
            }
            itemsIndexed(tabs, key = { _, t -> "tab-${t.name}" }) { _, tab ->
                val isActive = tab.name == active
                Row(
                    Modifier
                        .widthIn(min = 150.dp)
                        .height(47.dp)
                        .background(if (isActive) c.editor else Color.Transparent)
                        .border(1.dp, c.ln, RoundedCornerShape(0.dp))
                        .clickable { onSelectTab(tab.name) }
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isActive) {
                        Box(
                            Modifier
                                .fillMaxWidth(1f)
                                .height(2.dp)
                                .background(c.ac)
                        )
                    }
                    Text(
                        text = kindGlyph(tab.kind),
                        color = kindColor(tab.kind, c),
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = tab.name,
                        color = if (isActive) c.tx else c.mu,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                    )
                    if (isActive) {
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .size(6.dp)
                                .background(c.ac, RoundedCornerShape(3.dp))
                        )
                    }
                    Text(
                        text = "×",
                        color = c.mu,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onCloseTab(tab.name) },
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxSize()) {
                Column(
                        Modifier
                            .width(52.dp)
                            .background(c.editor)
                            .verticalScroll(codeScroll)
                            .padding(top = 17.dp, end = 12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        codeLines.forEachIndexed { i, line ->
                            val errored = isErrorLine(line)
                            Text(
                                text = "${i + 1}",
                                color = if (errored) c.red else c.mu.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                lineHeight = 22.sp,
                                fontFamily = Mono,
                                fontWeight = if (errored) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }

                Box(
                    Modifier
                        .weight(1f)
                        .verticalScroll(codeScroll)
                        .padding(start = 8.dp, end = 22.dp, top = 17.dp, bottom = 40.dp)
                ) {
                    Row(Modifier.horizontalScroll(codeScroll)) {
                        Box {
                            Text(
                                text = highlightCode(code, c),
                                color = c.tx,
                                fontSize = 13.sp,
                                lineHeight = 22.sp,
                                fontFamily = Mono,
                                softWrap = false,
                            )
                            BasicTextField(
                                value = code,
                                onValueChange = { code = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.Transparent,
                                    fontSize = 13.sp,
                                    lineHeight = 22.sp,
                                    fontFamily = Mono,
                                ),
                                cursorBrush = SolidColor(c.ac),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = visibleSuggestions.isNotEmpty(),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp, bottom = 40.dp),
            ) {
                SuggestionPopup(
                    suggestions = visibleSuggestions,
                    selected = selectedSug,
                    onSelect = { selectedSug = it },
                    modifier = Modifier,
                )
            }
        }
    }
}


@Composable
private fun SuggestionPopup(
    suggestions: List<com.closecode.ui.ide.model.Suggestion>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current

    Column(
        modifier
            .width(300.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(10.dp))
    ) {
        suggestions.forEachIndexed { index, item ->
            val isSel = index == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(if (isSel) c.selection else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(c.panel3, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.glyph,
                        color = c.fn,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                    )
                }
                Text(
                    text = item.name,
                    color = c.tx,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    modifier = Modifier.padding(start = 9.dp),
                )
                Text(
                    text = item.detail,
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun IdeStatusBar(
    language: String,
    statusText: String,
    projectName: String,
) {
    val c = LocalPageColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(27.dp)
            .background(c.panel)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Text(
            text = statusText,
            color = if (statusText.contains("Building")) c.ac else c.string,
            fontSize = 10.sp,
            fontFamily = Mono,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "Ln 1, Col 1",
            color = c.mu,
            fontSize = 10.sp,
            fontFamily = Mono,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = language,
            color = c.mu,
            fontSize = 10.sp,
            fontFamily = Mono,
            maxLines = 1,
            softWrap = false,
        )
        Box(Modifier.weight(1f))
        Text(
            text = "UTF-8",
            color = c.mu,
            fontSize = 10.sp,
            fontFamily = Mono,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "Spaces: 4",
            color = c.mu,
            fontSize = 10.sp,
            fontFamily = Mono,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = projectName,
            color = c.mu,
            fontSize = 10.sp,
            fontFamily = Mono,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun IdeNav(active: String, onSelect: (String) -> Unit) {
    val c = LocalPageColors.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(0.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdePanels.forEach { panel ->
            val isActive = panel == active
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isActive) c.panel3 else Color.Transparent)
                    .clickable { onSelect(panel) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = panel,
                    color = if (isActive) c.tx else c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun IdeSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalPageColors.current

    Column(
        Modifier
            .padding(top = 67.dp, end = 14.dp)
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.editor)
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search files and symbols…",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = c.tx,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                ),
                cursorBrush = SolidColor(c.ac),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = "⌘ K command palette · Esc close",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Mono,
            modifier = Modifier
                .padding(start = 2.dp, top = 8.dp, bottom = 2.dp)
                .clickable(onClick = onClose),
        )
    }
}

@Composable
private fun IdeToast(text: String) {
    val c = LocalPageColors.current

    Text(
        text = text,
        color = c.tx,
        fontSize = 12.sp,
        fontFamily = Sans,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.panel3)
            .border(1.dp, c.ln, RoundedCornerShape(10.dp))
            .padding(horizontal = 15.dp, vertical = 10.dp),
    )
}

