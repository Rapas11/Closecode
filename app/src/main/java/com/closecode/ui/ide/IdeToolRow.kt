package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Symbol

/** Every action the spec 6.8 tool row can trigger, in the order the row lays them out. */
enum class ToolAction {
    TAB,
    COMMENT,
    LINE_UP,
    LINE_DOWN,
    COPY,
    PROBLEMS,
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_PAREN,
    CLOSE_PAREN,
    SEMICOLON,
    EQUALS,
}

/**
 * Spec 6.8 tool row: 46 dp tall, a 1 dp top border, panel background, scrolling sideways when it
 * does not fit. Order is fixed by the spec, including the divider after the warning triangle.
 *
 * Buttons are min 46 wide with 10 dp side padding. Labels are 15 sp mono; the four icons are 20 dp
 * with 2 dp strokes.
 */
/** Row height. Exported so the editor can reserve exactly this much while the row is parked. */
val ToolRowHeight = 46.dp

@Composable
fun IdeToolRow(
    onAction: (ToolAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current

    Row(
        modifier
            .fillMaxWidth()
            .height(ToolRowHeight)
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(0.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("Tab", Mono) { onAction(ToolAction.TAB) }
        ToolButton("//", Mono) { onAction(ToolAction.COMMENT) }
        ToolIcon(CcIcons.Up) { onAction(ToolAction.LINE_UP) }
        ToolIcon(CcIcons.Down) { onAction(ToolAction.LINE_DOWN) }
        ToolIcon(CcIcons.Copy) { onAction(ToolAction.COPY) }
        ToolIcon(CcIcons.Warning) { onAction(ToolAction.PROBLEMS) }

        // Spec 6.8: a 1 x 28 divider in the line colour.
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(28.dp)
                .background(c.ln),
        )

        ToolButton("{", Mono) { onAction(ToolAction.OPEN_BRACE) }
        ToolButton("}", Mono) { onAction(ToolAction.CLOSE_BRACE) }
        ToolButton("(", Mono) { onAction(ToolAction.OPEN_PAREN) }
        ToolButton(")", Mono) { onAction(ToolAction.CLOSE_PAREN) }
        ToolButton(";", Mono) { onAction(ToolAction.SEMICOLON) }
        ToolButton("=", Mono) { onAction(ToolAction.EQUALS) }
    }
}

/** A 15 sp mono text button, min 46 wide. */
@Composable
private fun ToolButton(label: String, family: androidx.compose.ui.text.font.FontFamily, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .widthIn(min = 46.dp)
            .height(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.tx,
            fontSize = 15.sp,
            fontFamily = family,
            maxLines = 1,
        )
    }
}

/** A 20 dp icon button, used where the spec calls for an icon rather than a label. */
@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .widthIn(min = 46.dp)
            .height(46.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, c.tx, 20.dp)
    }
}

/**
 * The exact button order from spec 6.8, excluding the divider. Kept as data so a unit test can pin
 * the sequence without rendering anything.
 */
val ToolRowOrder: List<ToolAction> = listOf(
    ToolAction.TAB,
    ToolAction.COMMENT,
    ToolAction.LINE_UP,
    ToolAction.LINE_DOWN,
    ToolAction.COPY,
    ToolAction.PROBLEMS,
    ToolAction.OPEN_BRACE,
    ToolAction.CLOSE_BRACE,
    ToolAction.OPEN_PAREN,
    ToolAction.CLOSE_PAREN,
    ToolAction.SEMICOLON,
    ToolAction.EQUALS,
)

/** The character each insert button types, for the actions that are just text. */
val ToolRowInserts: Map<ToolAction, String> = mapOf(
    ToolAction.OPEN_BRACE to "{",
    ToolAction.CLOSE_BRACE to "}",
    ToolAction.OPEN_PAREN to "(",
    ToolAction.CLOSE_PAREN to ")",
    ToolAction.SEMICOLON to ";",
    ToolAction.EQUALS to "=",
)