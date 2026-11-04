package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/** Small uppercase label above a group.
 *  Panels use (start 16, top 12, end 16, bottom 4); full screens use (18, 16, 18, 6). */
@Composable
fun SecLabel(
    text: String,
    start: Dp = 16.dp,
    top: Dp = 12.dp,
    bottom: Dp = 4.dp,
) = Text(
    text.uppercase(),
    style = CcType.section,
    color = CcTheme.colors.muted,
    modifier = Modifier.padding(start = start, top = top, end = start, bottom = bottom),
)

/** Kind of change for the git badge, ChangeBadge and ChangedFileRow. */
enum class ChangeKind(val letter: String) {
    Modified("M"),
    Added("A"),
    Deleted("D"),
    Renamed("R"),
}

@Composable
fun changeColor(k: ChangeKind): Color = with(CcTheme.colors) {
    when (k) {
        ChangeKind.Modified -> amber
        ChangeKind.Added -> green
        ChangeKind.Deleted -> red
        ChangeKind.Renamed -> blue
    }
}

/** 22 x 22 coloured square with the letter. Used in Source control (17) and Commit detail (20). */
@Composable
fun ChangeBadge(kind: ChangeKind) = Box(
    Modifier
        .size(22.dp)
        .clip(RoundedCornerShape(7.dp))
        .background(changeColor(kind)),
    contentAlignment = Alignment.Center,
) {
    Text(
        kind.letter,
        style = CcType.small.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
        color = Color.White,
    )
}

/** The card used for a changed file: margin 0/14, radius 13, field background, padding 9/11, gap 10. */
@Composable
fun ChangedFileRow(
    kind: ChangeKind,
    name: String,
    sub: String,
    onClick: () -> Unit,
) {
    val c = CcTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChangeBadge(kind)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            OneLine(
                name,
                CcType.small.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                c.text,
            )
            OneLine(
                sub,
                CcType.code.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                c.muted,
                Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** Menu row or divider, free width, optional invisible icon (for check lists). */
sealed interface MenuEntry {
    data class Item(
        val icon: String,
        val text: String,
        val destructive: Boolean = false,
        val iconVisible: Boolean = true,
        val onClick: () -> Unit,
    ) : MenuEntry

    data object Divider : MenuEntry
}
