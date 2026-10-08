package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.settings.KeyValueStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono

/** The keys a phone keyboard cannot send, and which a shell cannot do without. */
internal val TerminalKeyBarKeys: List<Pair<String, String>> = listOf(
    "ESC" to "\u001B",
    "TAB" to "\t",
    "|" to "|",
    "~" to "~",
    "`" to "`",
    "/" to "/",
    "\\" to "\\",
    "-" to "-",
    "↑" to "↑",
    "↓" to "↓",
    "←" to "←",
    "→" to "→",
)

/**
 * The three things a reader reaches for most and a phone keyboard cannot type at all.
 *
 * They are named for what they do rather than for the key that normally does it, because a phone
 * keyboard has no CTRL+C to hold down: the button *is* the shortcut.
 */
internal enum class TerminalAction { CLEAR, CANCEL, EOF }

/**
 * The bar of terminal keys that sits above the soft keyboard.
 *
 * A phone keyboard has no ESC, no pipe and no arrows, so a shell on one is missing exactly the keys
 * that make it navigable. This puts them back. The two modifiers latch rather than toggle-off, so a
 * shortcut is one tap plus one key, and a whole word in capitals is one tap plus every letter.
 *
 * It is always on rather than appearing on demand, because a bar that hides itself is a bar that
 * cannot be found with a thumb.
 */
@Composable
internal fun TerminalKeyBar(
    ctrl: ModifierLatch,
    alt: ModifierLatch,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    onAction: (TerminalAction) -> Unit,
    onHistory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.panel)
            // A vertical drag anywhere on the bar walks the command history, and nothing else
            // claims it: the buttons take taps, and a drag is told apart from a tap by slop rather
            // than by where it landed. The bar is the one big, always-there target under a thumb,
            // which is exactly what reaching for the small arrows is not.
            .verticalSwipeTo(onHistory)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ModifierKey("CTRL", ctrl, onCtrl)
        ModifierKey("ALT", alt, onAlt)
        Box(Modifier.size(width = 1.dp, height = 18.dp).background(c.ln))
        TerminalAction.entries.forEach { action -> ActionKey(action) { onAction(action) } }
        Box(Modifier.size(width = 1.dp, height = 18.dp).background(c.ln))
        TerminalKeyBarKeys.forEach { (label, key) ->
            PlainKey(label) { onKey(key) }
        }
    }
}

/**
 * A vertical drag on the bar, reported once it is clear the reader meant it.
 *
 * Only vertical travel commits, and only after it has passed the touch slop, so a tap on one of the
 * buttons — which is the bar's other job — is never mistaken for a scroll.
 */
private fun Modifier.verticalSwipeTo(onSwipe: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val slop = viewConfiguration.touchSlop
        var travelled = 0f
        var swiping = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed || event.changes.count { it.pressed } > 1) break
            val delta = change.positionChange()
            if (!swiping) {
                travelled += delta.y
                if (kotlin.math.abs(travelled) > slop) swiping = true
            }
            if (swiping) {
                change.consume()
                if (kotlin.math.abs(travelled) > slop) {
                    // One step per slop of travel, so a short flick moves one entry and a long drag
                    // moves as many as the finger travelled rather than the one it meant.
                    onSwipe(if (travelled > 0) 1 else -1)
                    travelled = if (travelled > 0) (travelled % slop) else -(-travelled % slop)
                }
            }
        }
    }
}

/** One modifier key, tinted by what it is currently doing. */
@Composable
private fun ModifierKey(label: String, latch: ModifierLatch, onTap: () -> Unit) {
    val c = LocalPageColors.current
    val armed = modifierApplies(latch)
    val locked = latch == ModifierLatch.LOCKED
    // Armed reads as the accent; locked is drawn darker on top of it so the two are told apart at a
    // glance, which matters because one means "the next key" and the other means "every key".
    val fill = when {
        locked -> c.ac.copy(alpha = 0.45f)
        armed -> c.selection
        else -> Color.Transparent
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .border(1.dp, if (armed) c.ac else c.ln, RoundedCornerShape(6.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .semantics { contentDescription = "$label ${latch.name.lowercase()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (armed) c.tx else c.mu,
            fontSize = 11.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One of the three shortcuts a phone keyboard cannot type at all. */
@Composable
private fun ActionKey(action: TerminalAction, onTap: () -> Unit) {
    val c = LocalPageColors.current
    val label = when (action) {
        TerminalAction.CLEAR -> "CLEAR"
        TerminalAction.CANCEL -> "CANCEL"
        TerminalAction.EOF -> "DEL"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(6.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics {
                contentDescription = when (action) {
                    TerminalAction.CLEAR -> "Clear the screen"
                    TerminalAction.CANCEL -> "Stop the running command"
                    TerminalAction.EOF -> "Delete the character after the caret"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (action == TerminalAction.CANCEL) c.red else c.tx,
            fontSize = 11.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One ordinary key. */
@Composable
private fun PlainKey(label: String, onTap: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, c.ln, RoundedCornerShape(6.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.tx,
            fontSize = 12.sp,
            fontFamily = if (label.length == 1) FontFamily.Monospace else Mono,
        )
    }
}

/**
 * Commands remembered for one project.
 *
 * Stored in the app's own preferences under the project's slug, never in the project folder: a file
 * there would appear in the file drawer and in Source Control, and a log of what somebody typed at
 * their shell is not something to put in front of them unasked, let alone in a commit.
 *
 * The preference store is wrapped rather than reached for directly so this can be tested against an
 * in-memory map, because `SharedPreferences` is not on the unit-test classpath.
 */
internal class TerminalHistoryStore(private val kv: KeyValueStore) {

    /** Newest first, exactly as it is kept, so reading one back needs no reversing. */
    fun read(slug: String): List<String> =
        kv.getString(key(slug), null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun write(slug: String, history: List<String>) {
        if (history.isEmpty()) {
            kv.remove(key(slug))
        } else {
            kv.putString(key(slug), history.take(TERMINAL_HISTORY_LIMIT).joinToString("\n"))
        }
    }

    private fun key(slug: String) = "terminal.history.$slug"
}

/** A store for [slug], remembered for as long as this screen is composed. */
@Composable
internal fun rememberTerminalHistory(slug: String): TerminalHistoryStore {
    val context = LocalContext.current
    return remember(slug) { TerminalHistoryStore(SharedPreferencesKeyValueStore(context)) }
}