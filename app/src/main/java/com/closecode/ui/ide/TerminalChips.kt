package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono

/**
 * What a chip does when it is tapped.
 *
 * Two kinds, and the difference matters: a [Command] is really typed into the shell, while
 * [SourceControl] is something the app does itself because the shell has no way to.
 */
internal sealed interface ChipAction {
    /** Run this in the shell, exactly as if the reader had typed it. */
    data class Command(val command: String) : ChipAction

    /**
     * Open the drawer's Source Control tab.
     *
     * Here because there is no `git` in the shell — no binary to run and none to ship — so a chip
     * that claimed to run `git status` would report "not found" every time it was tapped. Git does
     * work in this app, in the panel, so that is where the chip goes.
     */
    data object SourceControl : ChipAction
}

/**
 * The one-tap commands, in the order they earn their place.
 *
 * Deliberately short. A row of chips that needs scrolling to reach its last item is a row nobody
 * reads to the end, and everything here has to be worth a tap that costs no typing.
 */
internal val TerminalChips: List<Pair<String, ChipAction>> = listOf(
    "ls -la" to ChipAction.Command("ls -la"),
    "pwd" to ChipAction.Command("pwd"),
    "ps" to ChipAction.Command("ps"),
    "clear" to ChipAction.Command("clear"),
    "kill" to ChipAction.Command("kill"),
    "Status" to ChipAction.SourceControl,
    "Diff" to ChipAction.SourceControl,
)

/**
 * The strip of one-tap commands above the key bar.
 *
 * Always on rather than summoned, because a row that appears only sometimes is a row that is never
 * found. It scrolls sideways, so the chips at the end are reachable without the row wrapping and
 * pushing the editor off the screen.
 */
@Composable
internal fun TerminalChipsRow(
    onChip: (ChipAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.panel2)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TerminalChips.forEach { (label, action) ->
            Chip(label) { onChip(action) }
        }
    }
}

/** One chip. The two that open a screen rather than typing a command are tinted apart. */
@Composable
private fun Chip(label: String, onTap: () -> Unit) {
    val c = LocalPageColors.current
    val opensAScreen = label == "Status" || label == "Diff"
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (opensAScreen) c.selection else c.panel)
            .border(1.dp, if (opensAScreen) c.ac else c.ln, RoundedCornerShape(6.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .semantics {
                contentDescription = if (opensAScreen) "$label, opens Source Control" else "Run $label"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (opensAScreen) c.tx else c.mu,
            fontSize = 12.sp,
            fontFamily = Mono,
        )
    }
}