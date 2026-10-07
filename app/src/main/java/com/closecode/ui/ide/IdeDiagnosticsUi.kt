package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.closecode.lang.Diagnostic
import com.closecode.lang.DiagnosticCodes
import com.closecode.lang.Severity
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/**
 * Editor metrics shared by the line numbers and the inline labels so their rows line up.
 *
 * Spec section 3: the code line height is exactly 20 dp and that number is load bearing. The gutter,
 * underlines, pills and lightbulb are all positioned against it, so it must not be "measured" into
 * a different value. Pinch zoom multiplies it rather than replacing it.
 */
val CodeLineHeight = 20.sp
val CodeFontSize = 13.sp

/** Spec section 6.3: gutter width, including the 1 dp right border. */
val GutterWidth = 62.dp

/**
 * Spec section 6.3: the editor's single top padding. The gutter and the code share it, because they
 * are siblings inside one scroll container. Adding a second pad to the gutter is what used to drop
 * every line number and status dot half a line below its own code.
 */
val EditorTopPadding = 12.dp

/** Alias kept for callers that read the gutter's padding by name; it is the editor's, not its own. */
val GutterPadding = EditorTopPadding

/**
 * Extra inset the gutter applies on top of [EditorTopPadding]. It must stay zero: any value here
 * shifts the numbers and dots away from the rows they label.
 */
val GutterTopInset = 0.dp

/** Spec section 6.3: gutter numbers are right aligned with this much padding. */
val GutterNumberPadding = 26.dp

/** Spec 6.3: the text area's own left inset, after the gutter divider. */
val CodeLeftInset = 14.dp

/** Left edge of the code column, measured from the left of the editor viewport. */
val CodeColumnStart = GutterWidth + CodeLeftInset

/** Spec section 6.3: status dot diameter, and its offsets inside the row. */
val GutterDotSize = 7.dp
val GutterDotLeft = 11.dp
val GutterDotTop = 7.dp

/** Width reserved for the status dot and the line number. */
val NumberColumnWidth = GutterWidth

/**
 * Character offset of a 1-based [line] and [column], for text-layout lookups.
 */
fun offsetOfLineColumn(source: String, line: Int, column: Int): Int {
    if (line < 1 || column < 1) return 0
    var lineStart = 0
    var current = 1
    var i = 0
    while (i < source.length && current < line) {
        if (source[i] == '\n') {
            current++
            lineStart = i + 1
        }
        i++
    }
    return (lineStart + (column - 1)).coerceIn(0, source.length)
}

/**
 * Whether this character actually draws something in the editor.
 *
 * Two kinds of character are invisible but are not whitespace, so asking "is this line blank" the
 * obvious way gets the wrong answer for them: U+200B zero-width space, U+200C and U+200D, U+FEFF and
 * U+2060. All of them are Unicode format characters, and all of them draw nothing. A line made only
 * of them looks empty, so it must count as empty, or the editor numbers it, treats it as the last
 * line of code and parks the caret there.
 */
fun Char.drawsGlyph(): Boolean = !isWhitespace() && category != CharCategory.FORMAT

/** True when [line] holds at least one character that would actually be visible on screen. */
fun hasVisibleContent(line: String): Boolean = line.any { it.drawsGlyph() }

/**
 * Spec 6.3: where the caret belongs when a file is opened, just past the last character of the last
 * line that has code in it.
 *
 * Every scaffolded file ends with a newline, so "the end of the file" is an empty line that has no
 * code on it. Opening there invented a numbered row past the last real line and put everything the
 * user typed, and every error about it, one row lower than the line they were looking at. The text
 * itself is never rewritten, so saving still keeps the file's trailing newline.
 */
fun caretOffsetAtEndOfCode(source: String): Int {
    var offset = source.length
    while (offset > 0) {
        val newline = source.lastIndexOf('\n', offset - 1)
        val lineStart = newline + 1
        val line = source.substring(lineStart, offset)
        // A blank first line is still the caret's line: there is nothing before it to fall back to.
        if (hasVisibleContent(line)) return offset
        if (lineStart == 0) return lineStart
        offset = newline
    }
    return 0
}

/**
 * How many numbered rows to draw: from 1 to the last line that shows something, or to the caret's
 * own line, whichever is further down.
 *
 * [caretLine] exists for one reason: pressing Enter leaves the caret sitting on a blank row, and a
 * row with no number is a row the reader does not know is there. Rows *below* the caret stay
 * unnumbered, so walking past the code still does not fill the gutter with numbers for blank tails.
 */
fun gutterRowCount(lines: List<String>, caretLine: Int = 1): Int {
    var last = lines.size
    while (last > 0 && !hasVisibleContent(lines[last - 1])) last--
    val toCaret = caretLine.coerceIn(1, lines.size.coerceAtLeast(1))
    return maxOf(last, toCaret).coerceAtLeast(1)
}

/**
 * Problems grouped by the 1-based line they sit on, in source order.
 */
@Immutable
data class ProblemsByLine(
    val byLine: Map<Int, List<Diagnostic>> = emptyMap(),
    val linesWithErrors: Set<Int> = emptySet(),
    val linesWithWarnings: Set<Int> = emptySet(),
    val all: List<Diagnostic> = emptyList(),
) {
    fun on(line: Int): List<Diagnostic> = byLine[line].orEmpty()

    /** Worst problem on a line; errors win over warnings. */
    fun worstOn(line: Int): Diagnostic? =
        on(line).minByOrNull { if (it.severity == Severity.ERROR) 0 else 1 }

    companion object {
        fun from(diagnostics: List<Diagnostic>): ProblemsByLine {
            if (diagnostics.isEmpty()) return ProblemsByLine()
            val byLine = LinkedHashMap<Int, MutableList<Diagnostic>>()
            for (d in diagnostics) byLine.getOrPut(d.line) { ArrayList() }.add(d)
            return ProblemsByLine(
                byLine = byLine,
                linesWithErrors = byLine.filterValues { list -> list.any { it.severity == Severity.ERROR } }
                    .keys,
                linesWithWarnings = byLine.keys.filterTo(HashSet()) { line ->
                    byLine[line]!!.none { it.severity == Severity.ERROR }
                },
                all = diagnostics,
            )
        }
    }
}
/**
 * Indentation a new row inherits: the line's own leading whitespace, plus one more level when it
 * ends with `{` or `(`.
 */
private fun carriedIndent(lineSoFar: String): String {
    val trimmed = lineSoFar.trimEnd()
    val extra = if (trimmed.endsWith("{") || trimmed.endsWith("(")) "  " else ""
    return lineSoFar.takeWhile { it == ' ' || it == '\t' } + extra
}

/**
 * Spec 6.3: Enter keeps the current indentation and adds two more spaces when the line ends with
 * `{` or `(`.
 *
 * This runs as a transform on the text the IME handed back, not by intercepting the key, so Enter
 * still inserts a newline when the autocomplete popup is open and only Tab or a tap accepts a
 * suggestion. Returns the new text and caret, or null when the edit was not a plain Enter.
 */
fun autoIndentAfterEnter(oldText: String, newText: String, caret: Int): Pair<String, Int>? {
    // A typed Enter is the only edit that grows the text by exactly one character and leaves the
    // caret straight after a newline. Anything else passes through untouched.
    if (newText.length != oldText.length + 1) return null
    if (caret <= 0 || caret > newText.length) return null
    if (newText[caret - 1] != '\n') return null
    val lineStart = newText.lastIndexOf('\n', caret - 2) + 1
    val pad = carriedIndent(newText.substring(lineStart, caret - 1))
    if (pad.isEmpty()) return null
    return (newText.substring(0, caret) + pad + newText.substring(caret)) to (caret + pad.length)
}

/**
 * Spec 6.3: exactly one newline per press of Enter.
 *
 * The newline is written here rather than left to the IME. Soft keyboards cannot be trusted to hand
 * back one row per press — some insert the key's newline and the composing editor's on top of it —
 * and an editor that drops the caret two rows down for one press cannot be undone by pressing
 * harder. The selection, if there is one, is replaced the way every editor replaces it.
 *
 * Returns the new text and the caret's new offset.
 */
fun enterEdit(text: String, selectionStart: Int, selectionEnd: Int): Pair<String, Int> {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val previousNewline = if (start <= 0) -1 else text.lastIndexOf('\n', start - 1)
    val lineStart = previousNewline + 1
    val pad = carriedIndent(text.substring(lineStart, start))
    val next = text.substring(0, start) + '\n' + pad + text.substring(end)
    return next to (start + 1 + pad.length)
}

/**
 * Top edge of zero-based [index] as the renderer actually placed it, falling back to the row model
 * when the layout is not ready or has fewer lines than the gutter is asked to draw.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun lineTopFromLayout(layout: TextLayoutResult?, index: Int, lineHeightPx: Float): Float {
    if (layout != null && index < layout.lineCount) {
        runCatching { layout.getLineTop(index) }.getOrNull()?.let { return it }
    }
    return index.coerceAtLeast(0) * lineHeightPx
}

/** Rows of slack above and below the viewport, so a fast fling never lands on an unmeasured gap. */
private const val GutterWindowOverhang = 2

/**
 * The slice of zero-based line indices the gutter should actually compose for a scroll position.
 *
 * The gutter lives inside the same scroll container as the code, so it used to build one row per
 * line in the file — two thousand rows for a two thousand line file, on every recomposition of the
 * editor, of which about forty were ever on screen. This returns only the rows that can be visible,
 * plus [GutterWindowOverhang] above and below.
 *
 * A viewport that has not been measured yet returns every row: the safe answer, and only ever the
 * first frame. Same for a non-positive line height, which would otherwise divide into nonsense.
 */
internal fun gutterWindow(
    lineCount: Int,
    scrollTopPx: Float,
    viewportHeightPx: Float,
    lineHeightPx: Float,
): IntRange {
    if (lineCount <= 0) return IntRange.EMPTY
    if (viewportHeightPx <= 0f || lineHeightPx <= 0f) return 0 until lineCount
    val visibleRows = (viewportHeightPx / lineHeightPx).toInt() + 1
    val first = (scrollTopPx / lineHeightPx).toInt() - GutterWindowOverhang
    val from = first.coerceIn(0, lineCount - 1)
    val to = (from + visibleRows + GutterWindowOverhang * 2).coerceAtMost(lineCount - 1)
    return from..to
}

/**
 * Status dot and line number for every row, drawn in the same box as the code so the two share one
 * coordinate space and one vertical scroll. The row height comes from [lineHeightPx], which is
 * measured off the rendered text rather than assumed, and the number carries that same height as
 * its line height so its baseline lands on the code's baseline.
 *
 * Red is used only for real errors and amber only for warnings. The background is opaque so code
 * scrolled sideways passes underneath instead of colliding with the numbers.
 *
 * The root is a Box on purpose. A Column places each child at the accumulated y of its slot and the
 * child's own `offset` then *adds* to that, so every number landed at about twice its true line and
 * the gutter drifted down past the end of the code. In a Box every child starts at one shared origin,
 * so `offset` alone decides the position and the row top is absolute — the same value the pills and
 * the bulb use.
 *
 * Only the rows that can be on screen are composed; see [gutterWindow]. The container still spans
 * the whole file, because an opaque gutter that stopped halfway down would let the code show
 * through below it.
 */
@Composable
fun LineNumberLayer(
    lineCount: Int,
    lineHeightPx: Float,
    problems: ProblemsByLine,
    caretLine: Int,
    // The layout the glyphs were drawn with. Row positions come from its line tops, so a number and
    // the code it labels cannot drift apart even if the row model ever disagrees with the renderer.
    layout: TextLayoutResult? = null,
    // Spec 6.3: the first import line carries a small "▸" affordance at the right of its row.
    foldLine: Int = 0,
    onFoldTap: () -> Unit = {},
    // Read here rather than at the call site on purpose: a scroll then recomposes only this layer,
    // not the whole editor around it. Null means the caller does not scroll, so compose every row.
    scrollState: ScrollState? = null,
    // The other half of the window calculation: how many rows fit under the gutter at once.
    viewportHeightPx: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeight = with(density) { lineHeightPx.toDp() }
    // The Box has no children whose sizes add up any more, so the layer carries the height the old
    // Column produced by stacking them: one row per numbered line.
    val gutterHeight = rowHeight * lineCount.toFloat()
    // The number is drawn with the row's own height as its line height, centred the same way the code
    // is, so its baseline sits exactly on the code's baseline.
    val numberLineHeight = with(density) { lineHeightPx.toSp() }

    Box(
        modifier
            .width(GutterWidth)
            .height(gutterHeight)
            .background(c.editor)
            .padding(top = GutterTopInset)
            // Thin rule between the numbers and the code, as in the reference editor.
            .drawBehind {
                val x = size.width - with(density) { 0.5.dp.toPx() }
                drawLine(
                    color = c.ln,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = with(density) { 1.dp.toPx() },
                )
            },
    ) {
        val window = if (scrollState == null) {
            0 until lineCount
        } else {
            gutterWindow(lineCount, scrollState.value.toFloat(), viewportHeightPx, lineHeightPx)
        }
        for (index in window) {
            val line = index + 1
            val errored = line in problems.linesWithErrors
            val warned = !errored && line in problems.linesWithWarnings
            val onCaret = line == caretLine
            // The renderer's own line top when it has one, and the row model otherwise. Both are
            // content-space, so the number lines up with its glyphs at any scroll position.
            val rowTop = lineTopFromLayout(layout, index, lineHeightPx)

            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(0, rowTop.roundToInt()) }
                    .fillMaxWidth()
                    .height(rowHeight),
                contentAlignment = Alignment.Center,
            ) {
                // Spec 6.3: a 7 dp solid dot at left 11, top 7 of the row. Error wins over warning.
                if (errored || warned) {
                    Box(
                        Modifier
                            .offset(x = GutterDotLeft, y = GutterDotTop)
                            .size(GutterDotSize)
                            .clip(CircleShape)
                            .background(if (errored) c.red else c.amber),
                    )
                }
                Text(
                    text = "$line",
                    color = when {
                        errored -> c.red
                        warned -> c.amber
                        // The prototype dims plain numbers with the comment colour, and brightens
                        // only the row the caret is on.
                        else -> c.comment.copy(alpha = if (onCaret) 1f else 0.75f)
                    },
                    fontSize = 12.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        // The row's own height as the number's line height, centred the way the code
                        // is, so the two baselines coincide.
                        lineHeight = numberLineHeight,
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                        ),
                    ),
                    fontFamily = Mono,
                    fontWeight = if (errored || warned) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = GutterNumberPadding),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                // Spec 6.3: a 10 sp muted chevron at right 8 on the first import line. Real code
                // folding needs approval, so tapping it only says so.
                if (line == foldLine) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp)
                            .clickable(onClick = onFoldTap),
                    ) {
                        Text(
                            text = "▸",
                            color = c.mu,
                            fontSize = 10.sp,
                            fontFamily = Sans,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top edge, in pixels, of the row that renders 1-based [line] of code, inside the shared editor
 * coordinate space.
 *
 * This is the single row model. The gutter numbers, the auto-scroll and the fallback positions for
 * overlays all call it, so the numbers and the code cannot drift apart the way two separately
 * maintained row heights did.
 */
fun rowTopPx(line: Int, lineHeightPx: Float, topPaddingPx: Float): Float =
    topPaddingPx + (line - 1).coerceAtLeast(0) * lineHeightPx

/**
 * Top edge, in pixels, of the row that renders 1-based [line] of code.
 *
 * Pills are placed with this rather than by stacking rows, because stacking made every problem
 * consume two code lines and the labels drifted away from the code they describe.
 */
fun pillTopPx(line: Int, lineHeightPx: Float): Float =
    (line - 1).coerceAtLeast(0) * lineHeightPx

/**
 * Width a problem pill needs for everything except its message: padding, the severity icon, the
 * code label and the stack badge. Nothing but the message is allowed to shrink.
 */
val PillChromeWidth = 124.dp

/** Smallest message a pill will show before it starts ellipsising. */
val PillMinMessageWidth = 24.dp

/**
 * Left edge of a problem pill: a little past the end of its line, and never past the point where it
 * would stop fitting on screen.
 *
 * [lineEndX] is the right edge of the line's own text and [gapPx] is the air between them, so the
 * pill always begins *after* the code instead of on top of it. A line long enough to push the pill
 * past the right edge wraps it back to the last position where it still fits, leaving
 * [rightReservePx] for its own icon, code label and badge.
 *
 * [viewportWidthPx] is measured from the code column's own origin, because that is where the layer
 * sits; measuring from the editor's left edge let the clamp accept a pill that ran off the screen.
 */
fun pillAnchorX(
    lineEndX: Float,
    gapPx: Float,
    viewportWidthPx: Float,
    rightReservePx: Float,
): Float {
    val raw = lineEndX + gapPx.coerceAtLeast(0f)
    val maxLeft = (viewportWidthPx - rightReservePx).coerceAtLeast(0f)
    return raw.coerceIn(0f, maxLeft)
}

/**
 * The pill's on-screen offset, in the layer's own pixels.
 *
 * Both halves are load bearing. Dropping [x] — which is what shipped — drew every pill on top of
 * the first characters of its own line, because the position computed from the line's real right
 * edge was thrown away one line before it reached the offset.
 */
internal fun pillOffsetOf(x: Float, top: Float): IntOffset =
    IntOffset(x.roundToInt(), top.roundToInt())

/**
 * Inline problem pills drawn on the code surface, anchored immediately after the offending
 * token and kept inside the viewport so they never run off the right edge.
 *
 * [anchorX] supplies the right edge of a line's text, measured from the text field that actually
 * rendered the code, so the pill lands past the real glyphs rather than an estimate. [gapPx] is the
 * air left between the text and the pill. [viewportWidthPx] is the width the user can actually see
 * from the code column's own origin, which is narrower than the scrollable content whenever a line
 * is longer than the screen. [hScrollPx] is the code column's sideways scroll: this layer scrolls
 * with the text, so everything it draws is in content coordinates and the right edge of what the
 * user can see sits at `hScrollPx + viewportWidthPx`, not at `viewportWidthPx`.
 */
@Composable
fun InlineProblemLayer(
    problems: ProblemsByLine,
    lineHeightPx: Float,
    viewportWidthPx: Float,
    gapPx: Float = 0f,
    hScrollPx: Float = 0f,
    anchorX: (line: Int) -> Float?,
    yOfLine: (Int) -> Float?,
    onTap: (Diagnostic) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rows = problems.byLine.entries.sortedBy { it.key }
    val chrome = with(density) { PillChromeWidth.toPx() }
    val minMessage = with(density) { PillMinMessageWidth.toPx() }
    val visibleRight = hScrollPx + viewportWidthPx

    // No clip here on purpose. The pills sit on their own rows via Modifier.offset, which does not
    // contribute to measurement, so the box only ever measured itself as tall as the widest pill —
    // and clipped every pill below the first one away. The scrollers around this layer already
    // clip it to the viewport.
    Box(modifier) {
        rows.forEach { (line, list) ->
            // Spec 6.5: when a line has several problems, show the most severe one and a count.
            val primary = list.minByOrNull { if (it.severity == Severity.ERROR) 0 else 1 } ?: return@forEach
            val isWarning = primary.severity == Severity.WARNING
            val tone = if (isWarning) c.warnPillTx else c.errorPillTx
            val fill = if (isWarning) c.warnPillBg else c.errorPillBg

            // The layout has not rendered this line yet, so there is no honest place to put the
            // pill. Anchoring at 0 instead would draw it over the first characters of the line.
            val lineEnd = anchorX(line) ?: return@forEach

            // Spec 6.5: past the end of the line's text, wrapped back to the edge when the line is
            // long enough that the pill would otherwise stop fitting.
            val x = pillAnchorX(lineEnd, gapPx, visibleRight, chrome + minMessage)
            val textRoom = (visibleRight - x - chrome).coerceAtLeast(minMessage)
            // Spec 6.5: top = line top minus 1 dp.
            val top = (yOfLine(line) ?: pillTopPx(line, lineHeightPx)) -
                with(density) { 1.dp.toPx() }

            Row(
                Modifier
                    // The x is load bearing. Dropping it here drew every pill on top of the first
                    // characters of its own line, with no room of its own.
                    .offset { pillOffsetOf(x, top) }
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(fill)
                    // Spec 7: tapping a pill opens the Problems sheet at that problem. The pill sits
                    // past the end of the line's text, so it only ever takes taps aimed at itself.
                    .clickable { onTap(primary) }
                    .padding(start = 7.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SeverityGlyph(severity = primary.severity)
                Text(
                    text = primary.message,
                    color = tone,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = with(density) { textRoom.toDp() }),
                )
                // Spec 6.5: the code is mono 11 sp at 75 percent opacity.
                Text(
                    text = primary.code,
                    color = tone.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                if (list.size > 1) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.stackBadge)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        StackGlyph(tone)
                        Text(
                            text = "${list.size}",
                            color = tone,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spec 6.5 severity icon: 15 dp, a circle with a cross for an error and a triangle with a bang
 * for a warning. Drawn rather than typed, so it matches the spec shape instead of depending on
 * which glyphs the bundled fonts happen to carry.
 */
@Composable
private fun SeverityGlyph(severity: Severity) {
    val c = LocalPageColors.current
    val tone = if (severity == Severity.WARNING) c.warnPillTx else c.errorPillTx
    // The prototype's own 24 x 24 glyph, so the pill's icon matches the reference shape.
    CcIcon(if (severity == Severity.WARNING) CcIcons.Warning else CcIcons.ErrorCircle, tone, 15.dp)
}

/** Spec 6.5 "stack" glyph for the multi-problem badge, in the pill's own tone. */
@Composable
private fun StackGlyph(tone: Color) {
    CcIcon(CcIcons.Stack, tone, 11.dp)
}

/** Horizontal x of [offset] within the rendered text, in the same coordinate space as the code. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun xForOffset(layout: TextLayoutResult?, offset: Int): Float? {
    if (layout == null) return null
    val safe = offset.coerceIn(0, layout.layoutInput.text.length)
    return runCatching { layout.getBoundingBox(safe).left }.getOrNull()
}

/**
 * Geometry read straight out of the rendered text rather than assumed from a line number.
 *
 * Every overlay in the editor positions itself through these, so nothing can drift from the code
 * if the font, the zoom level or the line height changes. All of them read the renderer's own line
 * metrics, the same source the gutter numbers are drawn from, so a label and the row it describes
 * cannot disagree.
 *
 * Measuring a *character* instead of the row was the old bug: a blank line's only character is its
 * newline, and a newline draws at the start of the next row, so the numbers, the dots and the pills
 * all landed somewhere else than the code they belonged to.
 */

/** Top edge of the 1-based [line], as the renderer drew it. */
fun lineTopPx(layout: TextLayoutResult?, source: String, line: Int): Float? {
    if (layout == null || line < 1) return null
    // Past the end of the text there is no row to measure; the caller falls back to the row model.
    if (line > source.count { it == '\n' } + 1) return null
    val index = line - 1
    if (index >= layout.lineCount) return null
    return runCatching { layout.getLineTop(index) }.getOrNull()
}

/**
 * Offset of the last character a label on [line] should sit after.
 *
 * The line's own newline is never the answer when the line has text, because the renderer draws it
 * at the start of the *next* row; anchoring there put every label at x of zero, on top of the code
 * it was describing. A blank line has no such character, so it returns the line's start.
 */
fun endOfLineAnchorOffset(source: String, line: Int): Int {
    val start = offsetOfLineColumn(source, line, 1)
    if (start >= source.length) return source.length
    val newline = source.indexOf('\n', start)
    val end = if (newline < 0) source.length else newline
    return if (end > start) end - 1 else start
}

/**
 * Right edge of [line]'s text, i.e. where its label should begin.
 *
 * Read from the renderer's line metrics: the newline has no width of its own, so asking the
 * character at the end of the line for its box answered with the wrong edge and the label started
 * over the code instead of after it.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun endOfLineX(layout: TextLayoutResult?, source: String, line: Int): Float? {
    if (layout == null || line < 1) return null
    val index = line - 1
    if (index >= layout.lineCount) return null
    runCatching { layout.getLineRight(index) }.getOrNull()?.let { return it }
    val anchor = endOfLineAnchorOffset(source, line)
        .coerceAtMost(layout.layoutInput.text.length)
    return runCatching { layout.getBoundingBox(anchor).left }.getOrNull()
}

/** Offset of the first character of the identifier the caret sits in or after. */
fun wordStartOffset(source: String, caret: Int): Int {
    val safe = caret.coerceIn(0, source.length)
    var start = safe
    fun isWord(ch: Char) = ch.isLetterOrDigit() || ch == '_'
    while (start > 0 && isWord(source[start - 1])) start--
    return start
}

/** Width of one advance in the rendered text, used to convert "two characters" into pixels. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun charWidthPx(layout: TextLayoutResult?, source: String): Float? {
    if (layout == null) return null
    val textLength = layout.layoutInput.text.length
    // Two consecutive characters that both draw something. Anything else measures the wrong thing:
    // a newline sits on the next row, and the character after the last one has no box at all.
    var probe = -1
    for (i in 0 until textLength - 1) {
        if (source.getOrNull(i)?.drawsGlyph() != true) continue
        if (source.getOrNull(i + 1)?.drawsGlyph() != true) continue
        probe = i
        break
    }
    if (probe < 0) return null
    val a = runCatching { layout.getBoundingBox(probe) }.getOrNull() ?: return null
    val b = runCatching { layout.getBoundingBox(probe + 1) }.getOrNull() ?: return null
    val width = b.left - a.left
    return if (width > 0.1f) width else null
}

/**
 * The quick-fix bulb, styled as a small dark rounded square like the reference editor. Sits at the
 * start of the code line under the caret — just left of the first typed character on that line,
 * so it never covers code. Tapping it opens the action list for that line.
 */
/**
 * Wavy underlines beneath every problem token, the way a desktop IDE marks a bad import. Red for
 * errors, amber for warnings, matching the dot and the label so one problem is always the same
 * colour in all three places.
 *
 * Drawn on a canvas rather than as a font decoration so it can follow the token's real bounding box
 * and stay pinned to the text when the editor is zoomed. It has no pointer input, so it cannot
 * steal taps meant for the text field.
 */
@Composable
fun SquiggleLayer(
    problems: ProblemsByLine,
    layout: androidx.compose.ui.text.TextLayoutResult?,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Spec 6.4: a 4 dp tall wave with a 6 dp period, sitting 16 dp below the top of the line.
    val strokePx = with(density) { 1.2.dp.toPx() }
    val amplitudePx = with(density) { 2.dp.toPx() }
    val periodPx = with(density) { 6.dp.toPx() }
    val belowLineTopPx = with(density) { 16.dp.toPx() }
    val minWidthPx = with(density) { 4.dp.toPx() }

    androidx.compose.foundation.Canvas(modifier) {
        for (diagnostic in problems.all) {
            val tone = if (diagnostic.severity == Severity.WARNING) c.warnUnderline else c.errorUnderline
            val range = squiggleRange(layout, diagnostic, minWidthPx) ?: continue
            val left = range.first
            val right = range.second
            if (right <= left) continue
            val baseY = range.third + belowLineTopPx
            val path = androidx.compose.ui.graphics.Path()
            var x = left
            var up = true
            path.moveTo(x, baseY)
            while (x < right) {
                val end = (x + periodPx / 2f).coerceAtMost(right)
                val peak = if (up) baseY - amplitudePx else baseY + amplitudePx
                path.quadraticBezierTo((x + end) / 2f, peak, end, baseY)
                x = end
                up = !up
            }
            drawPath(
                path = path,
                color = tone,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

/**
 * Left edge, right edge and top of the line for a diagnostic, taken from the text layout so the wave sits
 * under the exact characters the problem points at. Null when the layout is not ready yet.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun squiggleRange(
    layout: androidx.compose.ui.text.TextLayoutResult?,
    diagnostic: Diagnostic,
    minWidthPx: Float,
): Triple<Float, Float, Float>? {
    if (layout == null) return null
    val textLength = layout.layoutInput.text.length
    val from = diagnostic.offset.coerceIn(0, textLength)
    val to = (diagnostic.offset + diagnostic.length).coerceIn(from, textLength)
    val startBox = runCatching { layout.getBoundingBox(from) }.getOrNull() ?: return null
    val endBox = runCatching { layout.getBoundingBox(to) }.getOrNull() ?: return null
    val left = startBox.left
    val right = maxOf(endBox.right, left + minWidthPx)
    return Triple(left, right, startBox.top)
}

@Composable
fun LineBulb(
    top: Float,
    anchorX: Float,
    lineHeightPx: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier
            .offset { androidx.compose.ui.unit.IntOffset(anchorX.toInt(), top.toInt()) }
            // Spec 6.6: 38 x 33 with a radius of 11, so the corners are about 30% of the height.
            .width(38.dp)
            .height(33.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.bulbBg)
            .border(1.5.dp, c.bulbBorder, RoundedCornerShape(11.dp))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        // Spec 6.6: the prototype's own 19 dp bulb glyph, 2 dp stroke, in the amber tone.
        CcIcon(CcIcons.Bulb, c.bulbIcon, 19.dp)
    }
}

/**
 * Spec 6.6: vertical position of the quick-fix bulb.
 *
 * The bulb's bottom edge is aligned to the vertical centre of the line above the caret line, so it
 * hangs off the end of the previous line the way the reference screenshot shows. That is
 * `lineTop - lineHeight / 2 - bulbHeight`, clamped so it never rises above the editor top.
 *
 * The prose spec writes this as `caretLineIndex * 20 - 31`, but that number describes the same rule
 * only if the row above is treated as a point rather than a band: -31 leaves the bulb sitting above
 * the previous line's centre. The half-row term is the part that puts it on the line.
 */
fun bulbTopPx(
    caretLineTopPx: Float,
    lineHeightPx: Float,
    bulbHeightPx: Float,
    minTopPx: Float,
): Float =
    (caretLineTopPx - lineHeightPx / 2f - bulbHeightPx).coerceAtLeast(minTopPx)

/**
 * Horizontal position of the quick-fix bulb, in the code column's own pixels.
 *
 * The bulb hangs off the caret rather than a fixed inset, so it travels with the text the way the
 * reference screenshot shows. [caretXPx] is the caret's horizontal position straight from the
 * renderer; the result is clamped so the bulb never sits partly past the right edge of what the
 * user can see.
 */
fun bulbAnchorX(caretXPx: Float, bulbWidthPx: Float, viewportWidthPx: Float): Float =
    caretXPx.coerceIn(0f, (viewportWidthPx - bulbWidthPx).coerceAtLeast(0f))

/**
 * Horizontal position of [caret] in the rendered text, or null while there is no layout yet.
 *
 * Read from the same [TextLayoutResult] the glyphs come from, so the bulb lands on the character
 * the caret is actually sitting in rather than an estimate from a monospace advance.
 */
fun caretX(layout: TextLayoutResult?, caret: Int): Float? {
    if (layout == null) return null
    val offset = caret.coerceIn(0, layout.layoutInput.text.length)
    return runCatching { layout.getHorizontalPosition(offset, true) }.getOrNull()
}

/**
 * Spec 6.7 autocomplete popup placement, in viewport pixels.
 *
 * The popup never overlaps the line being typed. It sits on the row below the caret line, which
 * leaves the caret line free to read while typing; when the keyboard leaves no room underneath, it
 * flips to sit entirely above the caret line instead. Either way the popup's own band and the caret
 * line's band never intersect.
 *
 * All inputs are already converted out of the scrolled content's coordinates by the caller.
 */
fun popupTopPx(
    caretTopPx: Float,
    lineHeightPx: Float,
    gapPx: Float,
    popupHeightPx: Float,
    viewportHeightPx: Float,
    edgePx: Float,
): Float {
    val below = caretTopPx + lineHeightPx + gapPx
    val above = caretTopPx - gapPx - popupHeightPx
    val fitsBelow = below + popupHeightPx <= viewportHeightPx - edgePx
    return when {
        fitsBelow -> below
        above >= 0f -> above
        // Neither side fits: keep the popup on screen and let the caller scroll the caret into view.
        else -> below.coerceIn(0f, (viewportHeightPx - popupHeightPx).coerceAtLeast(0f))
    }
}

/**
 * Spec 6.7: the popup's width. It lives in the code column, so the gutter is subtracted before the
 * screen margin: a popup wider than the code area would run under the line numbers.
 */
fun popupWidthPx(maxWidthPx: Float, viewportWidthPx: Float, gutterWidthPx: Float, insetPx: Float): Float =
    minOf(maxWidthPx, viewportWidthPx - gutterWidthPx - insetPx * 2f).coerceAtLeast(0f)

/**
 * Spec 6.7: the popup's left edge is the start of the word being typed, clamped so at least
 * [insetPx] of code column is left on both sides, so it can never run off the right edge.
 */
fun popupLeftPx(
    wordX: Float,
    columnStartPx: Float,
    hScrollPx: Int,
    popupWidthPx: Float,
    codeWidthPx: Float,
    insetPx: Float,
): Float {
    val max = (codeWidthPx - popupWidthPx - insetPx).coerceAtLeast(insetPx)
    return (columnStartPx + wordX - hScrollPx).coerceIn(insetPx, max)
}

/**
 * Height of one rendered code row, measured from the text field rather than converted by hand.
 *
 * This is what keeps the line numbers and the error labels on the same row as the code. A hand
 * conversion is wrong whenever the device scales fonts, because `sp` is multiplied by the user's
 * font-size setting and `dp` is not.
 *
 * The measurement is taken from the renderer's own line tops and averaged over every row, which is
 * immune to the two things that used to break it: a blank row near the top, whose only character is
 * its newline, and any single row that measures differently because of the glyph it happens to
 * start with.
 *
 * Null when there is no layout or no second line to compare against.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun measuredLineHeightPx(
    layout: TextLayoutResult?,
    source: String,
): Float? {
    if (layout == null) return null
    if (!source.contains('\n')) return null
    val rows = layout.lineCount - 1
    if (rows < 1) return null
    val first = runCatching { layout.getLineTop(0) }.getOrNull() ?: return null
    val last = runCatching { layout.getLineTop(rows) }.getOrNull() ?: return null
    return rowHeightFromTops(first, last, rows)
}

/**
 * Average height of one rendered row, from the renderer's own top edges. Pure so the averaging can
 * be tested without a layout: [rows] rows span [lastTopPx] - [firstTopPx].
 *
 * Null when the rows collapse, which only happens for a layout that has not been measured yet.
 */
fun rowHeightFromTops(firstTopPx: Float, lastTopPx: Float, rows: Int): Float? {
    if (rows < 1) return null
    val height = (lastTopPx - firstTopPx) / rows
    return if (height > 0.5f) height else null
}

enum class QuickFixKind {
    ADD_IMPORT,
    REMOVE_IMPORT,
    REMOVE_TOKEN,
    ADD_SEMICOLON,
    TOGGLE_COMMENT,
    MOVE_LINE_UP,
    MOVE_LINE_DOWN,
    NO_FIX,
}

/** An action offered by the quick-fix list. */
@Immutable
data class QuickFixAction(
    val kind: QuickFixKind,
    val target: String,
    val label: String,
)

/**
 * Every action available on a line: fixes for the problems there first, then the plain code
 * actions that always apply.
 *
 * Spec 6.6 pins the order, because tapping the bulb applies the first entry: E0101 removes the
 * unknown token, E0102 appends ";", W0201 deletes the import line. A code with no fix still gets a
 * visible "No quick fix for CODE" row rather than an empty menu.
 */
fun buildQuickFixActions(
    line: Int?,
    problems: ProblemsByLine,
    analysis: com.closecode.lang.KotlinFileAnalysis?,
    source: String,
): List<QuickFixAction> {
    if (line == null || line < 1 || analysis == null) return emptyList()
    val out = ArrayList<QuickFixAction>()
    for (diagnostic in problems.on(line)) {
        when (diagnostic.code) {
            DiagnosticCodes.E0101_UNRESOLVED -> {
                // Spec 6.6 wants the token deleted on tap, so this is offered before the import.
                out.add(
                    QuickFixAction(
                        kind = QuickFixKind.REMOVE_TOKEN,
                        target = diagnostic.data ?: "",
                        label = "Remove '${diagnostic.data.orEmpty()}'",
                    ),
                )
                val name = diagnostic.data
                if (name != null && isCapitalised(name)) {
                    val candidates = analysis.api.candidateFqns(name)
                    if (candidates.isNotEmpty()) {
                        val fqn = candidates.first()
                        out.add(
                            QuickFixAction(
                                kind = QuickFixKind.ADD_IMPORT,
                                target = fqn,
                                label = "Import ${com.closecode.lang.SourceEdits.packageOf(fqn)}",
                            ),
                        )
                    }
                }
            }

            DiagnosticCodes.E0102_MISSING_SEMICOLON -> {
                out.add(
                    QuickFixAction(
                        kind = QuickFixKind.ADD_SEMICOLON,
                        target = "$line",
                        label = "Add ; at end of line",
                    ),
                )
            }

            DiagnosticCodes.W0201_UNUSED_IMPORT -> {
                val fqn = diagnostic.data ?: continue
                out.add(
                    QuickFixAction(
                        kind = QuickFixKind.REMOVE_IMPORT,
                        target = fqn,
                        label = "Remove import $fqn",
                    ),
                )
            }
        }
    }
    val unfixable = problems.on(line).filter { d ->
        when (d.code) {
            DiagnosticCodes.E0101_UNRESOLVED,
            DiagnosticCodes.E0102_MISSING_SEMICOLON,
            DiagnosticCodes.W0201_UNUSED_IMPORT,
            -> false

            else -> true
        }
    }
    for (d in unfixable) {
        out.add(QuickFixAction(QuickFixKind.NO_FIX, d.code, "No quick fix for ${d.code}"))
    }
    if (problems.on(line).isEmpty()) return out
    val commented = source.split('\n').getOrNull(line - 1)?.trimStart()?.startsWith("//") == true
    out.add(
        QuickFixAction(
            kind = QuickFixKind.TOGGLE_COMMENT,
            target = "$line",
            label = if (commented) "Uncomment Line" else "Comment Line",
        ),
    )
    out.add(
        QuickFixAction(QuickFixKind.MOVE_LINE_UP, "$line", "Move Line Up"),
    )
    out.add(
        QuickFixAction(QuickFixKind.MOVE_LINE_DOWN, "$line", "Move Line Down"),
    )
    return out.distinctBy { it.kind to it.target }
}

/**
 * Spec 7 problem cards, shown in the bottom sheet now that the Problems tab is gone.
 *
 * Each card is radius 10 with a 1 dp border, a 10 dp severity dot on the left, the message in
 * bold, the code as a chip and `file:line` underneath. Errors sort above warnings, and tapping a
 * card puts the caret on that problem.
 */
@Composable
fun ProblemSheet(
    problems: List<Diagnostic>,
    onSelect: (Diagnostic) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fileLabel: String? = null,
) {
    val c = LocalPageColors.current

    // Spec 7: errors sort above warnings; within a severity keep source order.
    val ordered = remember(problems) {
        problems.sortedBy { if (it.severity == Severity.ERROR) 0 else 1 }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Problems",
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Box(Modifier.weight(1f))
            Text(
                text = "${ordered.size}",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(
                text = "✕",
                color = c.mu,
                fontSize = 16.sp,
                fontFamily = Sans,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }

        if (ordered.isEmpty()) {
            // Spec 7 empty state.
            Text(
                text = "No problems. Code is clean.",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp, horizontal = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            return@Column
        }

        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        ) {
            for (diagnostic in ordered) {
                val isWarning = diagnostic.severity == Severity.WARNING
                val tone = if (isWarning) c.warnPillTx else c.errorPillTx
                val fill = if (isWarning) c.warnPillBg else c.errorPillBg
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.panel2)
                        .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                        .clickable { onSelect(diagnostic) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Severity dot, 10 dp, offset 5 dp from the top.
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tone),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            text = diagnostic.message,
                            color = c.tx,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(fill)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = diagnostic.code,
                                    color = tone,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (fileLabel != null) {
                                Text(
                                    text = "$fileLabel:${diagnostic.line}",
                                    color = c.mu,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}private fun isCapitalised(name: String): Boolean =
    name.firstOrNull()?.isUpperCase() == true

/**
 * Shared card used for both the quick-fix list and the completion popup, so the two always look
 * identical and only one is ever visible at a time.
 */
@Composable
fun IdeActionCard(
    rows: List<Pair<String, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(10.dp)),
    ) {
        rows.forEachIndexed { index, (title, subtitle) ->
            val isSel = index == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) c.ac.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = title,
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = c.mu,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index != rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln.copy(alpha = 0.5f)))
            }
        }
    }
}