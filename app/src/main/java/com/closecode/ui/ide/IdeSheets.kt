package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.closecode.ui.components.SpecMenuItem
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/** Cap on a single project-wide search, so a one-letter query cannot lock the sheet up. */
internal const val MAX_SEARCH_HITS = 200

/** One match in a project-wide search: the file it is in, the 1-based line, and that line. */
data class SearchHit(
    val fileName: String,
    val path: String,
    val line: Int,
    val text: String,
)

/**
 * Case-insensitive substring search over the whole project, in file order and then line order so
 * the results are stable while the user types. Blank lines and folders are skipped, and the run
 * stops at [limit] so a broad query still answers instantly.
 */
/**
 * How a query is matched against a line.
 *
 * A plain substring is the default because it is the one that cannot go wrong: no pattern to
 * mistype, nothing to escape. Case and patterns are the reader's choice.
 */
internal data class SearchMode(
    val pattern: String,
    val caseSensitive: Boolean = false,
    val regex: Boolean = false,
    val wholeWord: Boolean = false,
)

/**
 * Compiles a query into something that can test a line, or null when the pattern is not valid.
 *
 * Null rather than an empty result, because those are very different answers — "you typed it wrong"
 * against "there is none of it here" — and a search that cannot tell them apart sends the reader
 * looking for something that was never there.
 */
internal fun lineMatcher(mode: SearchMode): ((String) -> Boolean)? {
    val needle = mode.pattern.trim()
    if (needle.isEmpty()) return null
    val options = if (mode.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    if (!mode.regex && !mode.wholeWord) {
        return if (mode.caseSensitive) {
            { line -> line.contains(needle) }
        } else {
            { line -> line.contains(needle, ignoreCase = true) }
        }
    }
    // Whole words are word-boundary guards around the needle: a plain needle is escaped first
    // so `(` still searches for a bracket, while a pattern is wrapped untouched.
    val body = if (mode.regex) "(?:$needle)" else Regex.escape(needle)
    val compiled = runCatching { Regex("(?<!\\w)$body(?!\\w)", options) }.getOrNull() ?: return null
    return { line -> compiled.containsMatchIn(line) }
}

/**
 * Search over the whole project, in file order and then line order so the results are stable while
 * the reader types. Blank lines and folders are skipped, and the run stops at [limit] so a broad
 * query still answers instantly.
 *
 * The search always folded case before, which is defensible right up until the day a query for a
 * class name matches the documentation that mentions it, and there was no way to ask for anything a
 * plain string cannot say.
 */
fun findInSources(
    query: String,
    sources: List<Pair<ProjectFile, String>>,
    limit: Int = MAX_SEARCH_HITS,
    caseSensitive: Boolean = false,
    regex: Boolean = false,
    wholeWord: Boolean = false,
): List<SearchHit> {
    val matches = lineMatcher(SearchMode(query, caseSensitive, regex, wholeWord)) ?: return emptyList()
    val hits = ArrayList<SearchHit>()
    for ((file, text) in sources) {
        if (file.kind == FileKind.Folder) continue
        val lines = text.split('\n')
        var index = 0
        while (index < lines.size) {
            if (matches(lines[index])) {
                hits.add(SearchHit(file.name, file.path, index + 1, lines[index].trim()))
                if (hits.size >= limit) return hits
            }
            index++
        }
    }
    return hits
}

/**
 * The surface every bottom sheet in the IDE is drawn on: a full-screen scrim that dismisses on a
 * tap anywhere above the sheet, and the sheet itself pinned to the bottom with a grab handle.
 */
@Composable
fun IdeBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(c.scrim)
            // Above the keyboard: a centered dialog on a small screen would otherwise sit
            // half under it, with the buttons the first thing to go.
            .imePadding()
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.mock.panel)
                .border(1.dp, c.mock.divider, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                // An enabled no-op, not `enabled = false`: a disabled clickable adds no input
                // handler at all, so taps on the sheet body would fall through to the scrim and
                // dismiss the sheet the user is reading.
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.mock.divider),
            )
            content()
        }
    }
}

/** One row in the More sheet: a glyph, a label, and what tapping it does. */
data class MoreAction(
    val label: String,
    val glyph: String,
    val onClick: () -> Unit,
)

/**
 * The surface More and Search sit on: a scrim over everything above the bottom bar, and the
 * sheet pinned to the bar's top edge with no gap, growing upward to at most 60% of the screen.
 * The bar stays visible and tappable underneath (its active item stays highlighted), content
 * taller than the cap scrolls inside its own region, and a tap outside, a downward drag or
 * Back closes the sheet.
 *
 * [barHeight] is the measured bottom bar (0 while the keyboard hides it); the sheet and the
 * scrim both stop at the bar's top so neither can cover it.
 */
@Composable
fun IdeAnchoredSheet(
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
    val dismissPx = with(density) { 96.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }
    Box(
        modifier
            .fillMaxSize()
            // Stop at the bar: no gap above it, nothing over it.
            .padding(bottom = barHeight)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // The scrim ends where the sheet begins and where the bar begins: taps above the
        // sheet dismiss, taps on the bar reach the bar.
        Box(
            Modifier
                .fillMaxSize()
                .background(c.mock.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .graphicsLayer { translationY = dragOffset.value }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy ->
                        scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f)) }
                    },
                    onDragStopped = {
                        scope.launch {
                            if (dragOffset.value > dismissPx) onDismiss()
                            else dragOffset.animateTo(0f)
                        }
                    },
                )
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.mock.panel)
                // An enabled no-op, not `enabled = false`: a disabled clickable adds no input
                // handler at all, so taps on the sheet body would fall through to the scrim
                // and dismiss the sheet the user is reading.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.mock.divider),
            )
            content()
        }
    }
}

/**
 * The rows the bottom More sheet draws, in order. Kept as data so a unit test can pin them without
 * rendering anything — and so every row the editor does not own has one home here rather than
 * turning up in the header's overflow as well.
 */
internal val MoreLabels = listOf(
    "Logcat",
    "Build",
    "UI inspector",
    "Project settings",
    "Settings",
    "Toggle theme",
    "Developer",
)

/**
 * The More sheet: the panels and preferences that are not about the file that is open, in one list
 * so the bottom bar can stay at three items. The rows that act on the open file — search, format —
 * live in the header's overflow.
 */
@Composable
fun IdeMoreSheet(
    actions: List<MoreAction>,
    onDismiss: () -> Unit,
    versionLabel: String = "",
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Text(
            text = "MORE",
            color = c.mock.muted,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        actions.forEach { action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        action.onClick()
                        onDismiss()
                    }
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = action.glyph,
                    color = c.mock.accent,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    modifier = Modifier.size(width = 20.dp, height = 18.dp),
                )
                Text(
                    text = action.label,
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        // The version line (spec, Debug tools 1): whichever APK is installed says which one.
        if (versionLabel.isNotEmpty()) {
            Box(
                Modifier
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.mock.divider),
            )
            Text(
                text = versionLabel,
                color = c.mock.muted,
                fontSize = 11.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
            )
        }
    }
}

/**
 * The More sheet with its rows wired. Kept out of the screen composable because the screen is
 * already at the JVM method-size limit and every inline lambda there counts.
 */
@Composable
fun IdeMoreOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    onLogcat: () -> Unit,
    onBuild: () -> Unit,
    onUiInspector: () -> Unit,
    onProjectSettings: () -> Unit,
    onSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onDeveloper: () -> Unit,
    versionLabel: String,
    barHeight: androidx.compose.ui.unit.Dp,
) {
    if (!open) return
    IdeMoreSheet(
        actions = listOf(
            MoreAction("Logcat", "≡", onLogcat),
            MoreAction("Build", "✓", onBuild),
            MoreAction("UI inspector", "◇", onUiInspector),
            MoreAction("Project settings", "▤", onProjectSettings),
            MoreAction("Settings", "⚙", onSettings),
            MoreAction("Toggle theme", "◐", onToggleTheme),
            MoreAction("Developer", "◆", onDeveloper),
        ),
        onDismiss = onDismiss,
        versionLabel = versionLabel,
        barHeight = barHeight,
    )
}

/**
 * One match line with every query occurrence highlighted: matchBg behind matchText,
 * so the eye lands on why the line matched. Plain queries match case-insensitively
 * unless asked otherwise; patterns reuse the search's own matcher.
 */
internal fun highlightedMatch(
    text: String,
    mode: SearchMode,
    matchBg: androidx.compose.ui.graphics.Color,
    matchFg: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val ranges: List<IntRange> = runCatching {
        if (!mode.regex && !mode.wholeWord) {
            val needle = if (mode.caseSensitive) mode.pattern else mode.pattern.lowercase()
            if (needle.isEmpty()) return@runCatching emptyList<IntRange>()
            val haystack = if (mode.caseSensitive) text else text.lowercase()
            val out = mutableListOf<IntRange>()
            var from = 0
            while (true) {
                val at = haystack.indexOf(needle, from)
                if (at < 0) break
                out.add(at until at + needle.length)
                from = at + needle.length
            }
            out
        } else {
            val options = if (mode.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val body = if (mode.regex) "(?:${mode.pattern})" else Regex.escape(mode.pattern)
            val compiled = Regex("(?<!\\w)$body(?!\\w)", options)
            compiled.findAll(text).map { it.range }.toList()
        }
    }.getOrElse { emptyList() }
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            if (range.first > cursor) append(text.substring(cursor, range.first))
            withStyle(SpanStyle(background = matchBg, color = matchFg)) {
                append(text.substring(range.first, (range.last + 1).coerceAtMost(text.length)))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/**
 * The Search sheet: find in project. It sits above the editor rather than replacing it, so the
 * results and the code they came from are on screen together, and a tap on the scrim dismisses it.
 */
@Composable
fun IdeSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<SearchHit>,
    onOpen: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
    caseSensitive: Boolean = false,
    regex: Boolean = false,
    onCaseSensitiveChange: (Boolean) -> Unit = {},
    onRegexChange: (Boolean) -> Unit = {},
    wholeWord: Boolean = false,
    onWholeWordChange: (Boolean) -> Unit = {},
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    var optionsOpen by remember { mutableStateOf(false) }
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        // Rounded field: search icon, query, sliders options at the end.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.fieldBg)
                .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CcIcon(CcIcons.Search, c.mock.muted, 16.dp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Find in project…",
                        color = c.mock.muted,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.mock.text,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    ),
                    cursorBrush = SolidColor(c.mock.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The options live behind one sliders icon at the field's end.
            Box {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { optionsOpen = true }
                        .semantics { contentDescription = "Search options" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(
                        CcIcons.Sliders,
                        if (caseSensitive || regex || wholeWord) c.mock.accent else c.mock.muted,
                        18.dp,
                    )
                }
                if (optionsOpen) {
                    // Anchor the menu to the sliders button and give it a compact, fixed width.
                    // The old full-width dismiss layer stretched the menu across the sheet.
                    DropdownMenu(
                        expanded = optionsOpen,
                        onDismissRequest = { optionsOpen = false },
                        modifier = Modifier.width(200.dp),
                    ) {
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (caseSensitive) "✓ " else "") + "Match case",
                            onClick = { optionsOpen = false; onCaseSensitiveChange(!caseSensitive) },
                        )
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (wholeWord) "✓ " else "") + "Whole word",
                            onClick = { optionsOpen = false; onWholeWordChange(!wholeWord) },
                        )
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (regex) "✓ " else "") + "Regex",
                            onClick = { optionsOpen = false; onRegexChange(!regex) },
                        )
                    }
                }
            }
        }
        // A broken pattern says so under the field, where the eye already is after typing.
        val patternBroken = regex && query.isNotBlank() &&
            lineMatcher(SearchMode(query, caseSensitive, regex, wholeWord)) == null
        if (patternBroken) {
            Text(
                text = "Not a valid pattern",
                color = c.amber,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.mock.divider))
        when {
            query.isBlank() -> Text(
                text = "Type to search every file in the project.",
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(18.dp),
            )

            hits.isEmpty() -> Text(
                text = "No matches for \"$query\"",
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(18.dp),
            )

            else -> Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                // "N results in M files", then one group per file: icon, name, folder
                // and a count badge, with each match line highlighted under it.
                Text(
                    text = "${hits.size} results in ${hits.map { it.path }.distinct().size} files",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
                val mode = SearchMode(query, caseSensitive, regex, wholeWord)
                hits.groupBy { it.path }.forEach { (path, group) ->
                    val first = group.first()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon(CcIcons.Document, c.mock.muted, 15.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = first.fileName,
                            color = c.mock.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = path.substringBeforeLast('/', ""),
                            color = c.mock.muted,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(c.chipBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${group.size}",
                                color = c.mock.accent,
                                fontSize = 11.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    group.forEach { hit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(hit) }
                                .padding(start = 41.dp, end = 18.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${hit.line}",
                                color = c.mock.muted,
                                fontSize = 12.sp,
                                fontFamily = Mono,
                                modifier = Modifier.width(34.dp),
                            )
                            Text(
                                text = highlightedMatch(hit.text, mode, c.spec.matchBg, c.spec.matchText),
                                color = c.mock.text,
                                fontSize = 13.sp,
                                fontFamily = Mono,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The add-a-block sheet. One row per [BlockTemplate]; tapping a row writes that template's lines
 * into the buffer at the caret and closes. The sheet owns no text — it only chooses what
 * [insertBlockAt] should write, so the whole transaction is one call back to the screen.
 */
@Composable
fun IdeAddBlockSheet(
    onInsert: (BlockTemplate) -> Unit,
    onDismiss: () -> Unit,
    templates: List<BlockTemplate> = StatementSlots,
) {
    val c = LocalPageColors.current
    IdeBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "statement slot",
            color = c.mock.muted,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.mock.divider))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            templates.forEach { template ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onInsert(template) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = template.label,
                        color = c.mock.accent,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        text = template.lines.joinToString(" "),
                        color = c.mock.text.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A one-field prompt for naming a new file or folder. Kept as its own dialog rather than a sheet
 * because it is answered once and then gets out of the way.
 *
 * [initial] and [confirmLabel] are what let the same dialog rename and move: creating starts blank
 * with "Create", renaming starts on the leaf with "Rename", and moving starts on the whole path
 * with "Move". One field, one set of keys, one thing to get right.
 */
@Composable
fun IdeNameDialog(
    title: String,
    hint: String,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    confirmLabel: String = "Create",
    horizontalBias: Float = 0f,
    subtitle: String? = null,
) {
    val c = LocalPageColors.current
    // Keyed on the value it starts with: a dialog that swaps one target for another without
    // leaving composition would otherwise keep the previous name in the field.
    val value = remember(initial) { mutableStateOf(initial) }
    val trimmed = value.value.trim()
    val changed = trimmed.isNotEmpty() && trimmed != initial

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = BiasAlignment(horizontalBias, 0f),
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 340.dp)
                // Above the keyboard: a centered dialog on a small screen would otherwise sit
                // half under it, with the buttons the first thing to go.
                .imePadding()
                .clip(RoundedCornerShape(14.dp))
                .background(c.mock.panel)
                .border(1.dp, c.mock.divider, RoundedCornerShape(14.dp))
                // Enabled no-op: a disabled clickable would let the tap fall through to the scrim
                // and dismiss the dialog before the field took focus.
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = if (subtitle == null) 12.dp else 4.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.mock.muted,
                    fontSize = 11.5.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.editor)
                    .border(1.dp, c.mock.divider, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.value.isEmpty()) {
                    Text(
                        text = hint,
                        color = c.mock.muted,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                    )
                }
                BasicTextField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.mock.text,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                    ),
                    cursorBrush = SolidColor(c.mock.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Plain text, never a filled square: Cancel walks away, it does nothing.
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = c.link,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                    )
                }
                com.closecode.ui.components.PrimaryButton(
                    label = confirmLabel,
                    enabled = changed,
                    onClick = { onCreate(trimmed) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A yes-or-no question, asked before anything is destroyed. Delete and overwrite both take
 * something the app cannot hand back — the trash is not a place this editor keeps — so neither is
 * allowed to happen on the same tap that offered it.
 */
@Composable
fun IdeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.mock.panel)
                .border(1.dp, c.mock.divider, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = message,
                color = c.mock.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontFamily = Sans,
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetButton(label = "Cancel", fill = false, onClick = onDismiss)
                SheetButton(
                    label = confirmLabel,
                    fill = true,
                    destructive = true,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SheetButton(
    label: String,
    fill: Boolean,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val resting = when {
        !enabled -> c.panel2
        destructive -> c.mock.danger
        else -> c.mock.accent
    }
    Box(
        Modifier
            .then(if (fill) Modifier.weight(1f, fill = true) else Modifier)
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(resting)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) c.mock.onAccent else c.mock.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** One row of the search options menu: a plain word and a check mark when it is on. */
@Composable
private fun SearchOptionItem(
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalPageColors.current
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (on) "✓" else "",
                    color = c.mock.accent,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    text = label,
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }
        },
        onClick = onToggle,
    )
}

/** What a run finished with: the APK it produced, its size and how long the build took. */
internal data class RunResultInfo(
    val ok: Boolean,
    val apkName: String,
    val sizeMb: Double,
    val seconds: Long,
    val message: String = "",
)

/**
 * The run sheet (screen 25): what the bottom card's Run button opens before anything runs —
 * build type, whether the finished app opens itself, and the live build card while it runs with
 * Stop within reach. Nothing here starts a build silently; the sheet is the one place a run
 * is chosen, and its buttons are the only doors in and out.
 */
/** One line of the project's options. */
@Composable
private fun OptionLine(label: String, value: String) {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            modifier = Modifier.width(74.dp),
        )
        Text(
            text = value,
            color = c.mock.text,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun IdeRunSheet(
    appName: String,
    buildType: String,
    onBuildType: (String) -> Unit,
    packageName: String = "",
    minSdk: String = "",
    targetSdk: String = "",
    appVersion: String = "",
    onOpenSettings: (() -> Unit)? = null,
    openAfterInstall: Boolean,
    onOpenAfterInstall: (Boolean) -> Unit,
    building: Boolean,
    progress: Float,
    steps: List<BuildStepUi>,
    canRun: Boolean,
    onStop: () -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(c.mock.panel)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.mock.toggleOff),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Run $appName",
                    color = c.mock.text,
                    fontSize = 20.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Close, c.mock.textSoft, 21.dp)
                }
            }
            Spacer(Modifier.height(14.dp))
            // What a run costs here: this phone is both the builder and the device, so no
            // cable, no other machine and no emulator enters the picture.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.mock.content)
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CcIcon(CcIcons.Stack, c.mock.blue, 18.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "The app is built and installed on this phone. " +
                        "You do not need a cable or another device.",
                    color = c.mock.textSoft,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            // The project's own options, so the sheet answers "what am I about to make?"
            // without a trip to Project settings.
            if (packageName.isNotBlank() || minSdk.isNotBlank() || appVersion.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "PROJECT OPTIONS",
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.mock.content)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (packageName.isNotBlank()) {
                        OptionLine("Package", packageName)
                    }
                    if (minSdk.isNotBlank() || targetSdk.isNotBlank()) {
                        OptionLine("Android", "min $minSdk · target $targetSdk")
                    }
                    if (appVersion.isNotBlank()) {
                        OptionLine("Version", appVersion)
                    }
                }
                if (onOpenSettings != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Change project options",
                        color = c.mock.accent,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = "BUILD TYPE",
                color = c.mock.muted,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.mock.rail)
                    .padding(3.dp),
            ) {
                listOf("Debug", "Release").forEach { option ->
                    val on = option.equals(buildType, ignoreCase = true)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) c.mock.toggleOff else Color.Transparent)
                            .clickable(enabled = !building) {
                                if (!on) onBuildType(option)
                            }
                            .semantics { contentDescription = "$option build" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            color = if (on) Color.White else c.mock.muted,
                            fontSize = 13.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Open app after install",
                        color = c.mock.text,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "Starts it as soon as it is ready",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                com.closecode.ui.components.DesignSwitch(
                    checked = openAfterInstall,
                    onChange = onOpenAfterInstall,
                )
            }
            if (building || steps.any { it.state != StepState.PENDING }) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.mock.content)
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = {},
                        )
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (building) "Building…" else "Build",
                            color = c.mock.text,
                            fontSize = 13.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(progress * 100).toInt().coerceIn(0, 100)}%",
                            color = c.mock.muted,
                            fontSize = 13.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.mock.rail),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(c.mock.accent),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    steps.forEach { step ->
                        Row(
                            Modifier.padding(vertical = 3.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (step.state) {
                                StepState.DONE -> com.closecode.ui.CcIcon("check", c.mock.green, 15.dp, 2.4f)
                                StepState.FAILED -> com.closecode.ui.CcIcon("x", c.mock.danger, 15.dp, 2.4f)
                                StepState.RUNNING -> com.closecode.ui.CcIcon("refresh", c.mock.accent, 15.dp, 2.4f)
                                StepState.PENDING -> com.closecode.ui.CcIcon("more", c.mock.faint, 15.dp, 2.4f)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = step.label,
                                color = when (step.state) {
                                    StepState.DONE -> c.mock.green
                                    StepState.RUNNING -> c.mock.accent
                                    StepState.FAILED -> c.mock.danger
                                    StepState.PENDING -> c.mock.muted
                                },
                                fontSize = 12.5.sp,
                                fontFamily = Sans,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.mock.field)
                        .clickable(enabled = building, onClick = onStop)
                        .semantics { contentDescription = "Stop building" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Stop",
                        color = if (building) c.mock.text else c.mock.faint,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier
                        .weight(2f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (canRun && !building) c.mock.green else c.mock.field)
                        .clickable(enabled = canRun && !building, onClick = onRun)
                        .semantics { contentDescription = "Run" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CcIcon(CcIcons.Play, if (canRun && !building) Color.White else c.mock.muted, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Run",
                            color = if (canRun && !building) Color.White else c.mock.muted,
                            fontSize = 15.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The run page (screen 26): what a run launched from the sheet lands on when it finished and
 * did not open the app itself. The APK's name, size and seconds, then the two doors: open
 * the installed app, or the build log's output.
 */
@Composable
internal fun IdeRunFinishedPage(
    result: RunResultInfo,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val c = LocalPageColors.current
    androidx.activity.compose.BackHandler { onBack() }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding(),
    ) {
        com.closecode.ui.components.ScreenHeader(
            origin = "Run",
            title = "Build finished",
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (result.ok) c.mock.green.copy(alpha = 0.13f) else c.mock.danger.copy(alpha = 0.13f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Check, c.mock.green, 38.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (result.ok) "Built and installed" else "Build failed",
                color = c.mock.text,
                fontSize = 21.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (result.ok) {
                    "${result.apkName} · %.1f MB · ${result.seconds} s".format(result.sizeMb)
                } else {
                    result.message.ifBlank { "The build did not finish. See the output." }
                },
                color = c.mock.muted,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            if (result.ok) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.mock.green)
                        .clickable(onClick = onOpenApp)
                        .semantics { contentDescription = "Open app" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CcIcon(CcIcons.Play, c.mock.onGreen, 17.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open app",
                            color = c.mock.onGreen,
                            fontSize = 15.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.mock.field)
                    .clickable(onClick = onOpenLog)
                    .semantics { contentDescription = "Build output" },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(CcIcons.Terminal, c.mock.text, 17.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Build output",
                        color = c.mock.text,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
