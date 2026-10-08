package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (!mode.regex) {
        return if (mode.caseSensitive) {
            { line -> line.contains(needle) }
        } else {
            { line -> line.contains(needle, ignoreCase = true) }
        }
    }
    val options = if (mode.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    val compiled = runCatching { Regex(needle, options) }.getOrNull() ?: return null
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
): List<SearchHit> {
    val matches = lineMatcher(SearchMode(query, caseSensitive, regex)) ?: return emptyList()
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
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
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
                    .background(c.ln),
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
 * The rows the bottom More sheet draws, in order. Kept as data so a unit test can pin them without
 * rendering anything — and so every row the editor does not own has one home here rather than
 * turning up in the header's overflow as well.
 */
internal val MoreLabels = listOf(
    "Logcat",
    "Build",
    "Settings",
    "Toggle theme",
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
) {
    val c = LocalPageColors.current
    IdeBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "MORE",
            color = c.mu,
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
                    color = c.ac,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    modifier = Modifier.size(width = 20.dp, height = 18.dp),
                )
                Text(
                    text = action.label,
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
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
) {
    val c = LocalPageColors.current
    IdeBottomSheet(onDismiss = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CcIcon(CcIcons.Search, c.mu, 16.dp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Find in project…",
                        color = c.mu,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.tx,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    ),
                    cursorBrush = SolidColor(c.ac),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    text = "${hits.size}",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                )
            }
        }
        // The two switches sit under the query and beside the count, which is where a reader's eye
        // already is after typing. Letters rather than a glyph font: Aa and .* are the marks every
        // search box has carried for thirty years.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchToggle(
                label = "Aa",
                spoken = "Match capitalisation",
                on = caseSensitive,
                onToggle = onCaseSensitiveChange,
            )
            SearchToggle(
                label = ".*",
                spoken = "Treat the query as a pattern",
                on = regex,
                onToggle = onRegexChange,
            )
            Spacer(Modifier.weight(1f))
            val patternBroken = regex && query.isNotBlank() &&
                lineMatcher(SearchMode(query, caseSensitive, regex)) == null
            if (patternBroken) {
                Text(
                    text = "Not a valid pattern",
                    color = c.amber,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
        when {
            query.isBlank() -> Text(
                text = "Type to search every file in the project.",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(18.dp),
            )

            hits.isEmpty() -> Text(
                text = "No matches for \"$query\"",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(18.dp),
            )

            else -> Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                hits.forEach { hit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(hit) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${hit.line}",
                            color = c.mu,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                            modifier = Modifier.width(30.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = hit.fileName,
                                color = c.ac,
                                fontSize = 12.sp,
                                fontFamily = Mono,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                text = hit.text,
                                color = c.tx,
                                fontSize = 13.sp,
                                fontFamily = Mono,
                                maxLines = 2,
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
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
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
                        color = c.ac,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        text = template.lines.joinToString(" "),
                        color = c.tx.copy(alpha = 0.85f),
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
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(14.dp))
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
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.editor)
                    .border(1.dp, c.ln, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.value.isEmpty()) {
                    Text(
                        text = hint,
                        color = c.mu,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                    )
                }
                BasicTextField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.tx,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                    ),
                    cursorBrush = SolidColor(c.ac),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetButton(label = "Cancel", fill = false, onClick = onDismiss)
                SheetButton(
                    label = confirmLabel,
                    fill = true,
                    enabled = changed,
                    onClick = { onCreate(trimmed) },
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
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = message,
                color = c.mu,
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
        destructive -> c.red
        else -> c.ac
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
            color = if (enabled) c.af else c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * One of the search sheet's two switches.
 *
 * Outlined rather than filled when off and filled when on, which is the same pair of states the rest
 * of the app's switches use, so the mark reads the same way here as anywhere else.
 */
@Composable
private fun SearchToggle(
    label: String,
    spoken: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) c.selection else Color.Transparent)
            .border(1.dp, if (on) c.ac else c.ln, RoundedCornerShape(8.dp))
            .clickable { onToggle(!on) }
            .semantics { contentDescription = spoken }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (on) c.tx else c.mu,
            fontSize = 12.sp,
            fontFamily = Mono,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
