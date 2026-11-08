package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono

/** The first row, exactly as screen 23 draws it: each pair inserts both characters. */
internal val PrimaryTerminalKeys: List<Pair<String, String>> = listOf(
    "Esc" to "\u001B",
    "{ }" to "{}",
    "( )" to "()",
    "[ ]" to "[]",
    "\" \"" to "\"\"",
    ";" to ";",
    "=" to "=",
    "→" to "\t",
)

/** The slim second row: modifiers and the keys the guide leaves out. */
internal val SecondaryTerminalKeys: List<Pair<String, String>> = listOf(
    "|" to "|",
    "~" to "~",
    "↑" to "↑",
    "↓" to "↓",
)

/**
 * The two-row terminal key bar above the input bar. The first row is the guide's punctuation
 * row; the second keeps the modifiers and the keys a phone keyboard hides. ESC and Tab are still
 * reachable, and a vertical drag on either row walks command history.
 */
@Composable
internal fun TerminalKeyBar(
    ctrl: ModifierLatch,
    alt: ModifierLatch,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    onHistory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cc = LocalCcColors.current
    Column(modifier.fillMaxWidth().background(cc.panel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(cc.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .verticalSwipeTo(onHistory)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PrimaryTerminalKeys.forEach { (label, key) ->
                KeyChip(
                    label,
                    label,
                    modifier = Modifier.weight(1f),
                    onTap = { onKey(key) },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .verticalSwipeTo(onHistory)
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModifierKey("Ctrl", ctrl, onCtrl)
            ModifierKey("Alt", alt, onAlt)
            SecondaryTerminalKeys.forEach { (label, key) ->
                KeyChip(label, label, slim = true, onTap = { onKey(key) })
            }
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

/** One modifier key: field fill, accent outline while armed, stronger fill while locked. */
@Composable
private fun ModifierKey(label: String, latch: ModifierLatch, onTap: () -> Unit) {
    val cc = LocalCcColors.current
    val armed = modifierApplies(latch)
    val locked = latch == ModifierLatch.LOCKED
    val fill = when {
        locked -> cc.accent.copy(alpha = 0.28f)
        else -> cc.field
    }
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .border(
                1.dp,
                if (armed) cc.accent else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = "$label ${latch.name.lowercase()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (armed) cc.accent else cc.text2,
            fontSize = 11.5.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** One key: 32 dp on the guide row, 26 dp on the slim row, field fill, mono label. */
@Composable
private fun KeyChip(
    label: String,
    description: String,
    slim: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    content: @Composable (() -> Unit)? = null,
) {
    val cc = LocalCcColors.current
    Box(
        modifier
            .height(if (slim) 26.dp else 32.dp)
            .clip(RoundedCornerShape(if (slim) 8.dp else 9.dp))
            .background(cc.field)
            .clickable(onClick = onTap)
            .padding(horizontal = if (slim) 10.dp else 9.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = label,
                color = cc.text2,
                fontSize = if (slim) 11.5.sp else 12.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
            )
        }
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
internal class TerminalHistoryStore(
    private val kv: KeyValueStore,
    private val persist: Boolean = true,
) {

    /** Newest first, exactly as it is kept, so reading one back needs no reversing. */
    fun read(slug: String): List<String> =
        if (!persist) emptyList()
        else kv.getString(key(slug), null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun write(slug: String, history: List<String>) {
        if (!persist) return
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
internal fun rememberTerminalHistory(slug: String, persist: Boolean = true): TerminalHistoryStore {
    val context = LocalContext.current
    return remember(slug, persist) {
        TerminalHistoryStore(SharedPreferencesKeyValueStore(context), persist)
    }
}