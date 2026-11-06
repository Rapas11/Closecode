package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

@Composable
internal fun IconScreenFrame(
    origin: String,
    title: String,
    onBack: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalCcColors.current
    Column(Modifier.fillMaxSize().background(c.content)) {
        ScreenHeader(origin = origin, title = title, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = if (footer != null) 96.dp else 24.dp),
                content = content,
            )
            if (footer != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = footer,
                )
            }
        }
    }
}

@Composable
internal fun IconSectionLabel(text: String) {
    val c = LocalCcColors.current
    Text(
        text = text.uppercase(),
        color = c.muted,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
internal fun IconNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalCcColors.current.muted,
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.5.sp,
        fontFamily = Sans,
        lineHeight = 19.sp,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun Card20(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalCcColors.current
    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.field)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    bg: Color = LocalCcColors.current.accent,
    fg: Color = Color.White,
    enabled: Boolean = true,
) {
    val c = LocalCcColors.current
    val foreground = if (enabled) fg else c.muted
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) bg else c.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CcIcon(icon, foreground, 17.dp, 2f)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = foreground,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun StatusPill(text: String, fg: Color, bgAlpha: Float) {
    Box(
        Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fg.copy(alpha = bgAlpha))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun KvRow(key: String, value: String, mono: Boolean = false) {
    val c = LocalCcColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            color = c.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
        )
        Text(
            text = value,
            color = c.text,
            fontSize = 12.5.sp,
            fontFamily = if (mono) Mono else Sans,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
