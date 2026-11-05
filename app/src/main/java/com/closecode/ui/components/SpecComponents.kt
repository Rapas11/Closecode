package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/** Screen header (layer 3): back tile, origin (bold), title (muted), actions on the right. */
@Composable
fun ScreenHeader(
    origin: String?,
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cc.field)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("chevron-left", cc.text2, 18.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = origin ?: "",
                    color = cc.text,
                    fontSize = 16.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        color = cc.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(
                Modifier.widthIn(max = 120.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** One 38 dp header action tile: field bg, radius 12, 18 dp icon. */
@Composable
fun HeaderAction(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cc.field)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, cc.text2, 18.dp)
    }
}

/** Section label: 11 sp, 0.8 tracking, muted, with optional padding. */
@Composable
fun SecLabel(
    text: String,
    modifier: Modifier = Modifier,
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
) {
    val cc = LocalCcColors.current
    Text(
        text = text.uppercase(),
        color = cc.muted,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = modifier.padding(start = start, top = top, end = end, bottom = bottom),
    )
}

/** Centered empty state: icon, title, subtitle. */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    subtitle: String,
) {
    val cc = LocalCcColors.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CcIcon(icon, cc.faint, 28.dp, 1.6f)
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = cc.text2,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = cc.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center,
        )
    }
}

/** N placeholder rows for a loading list. */
@Composable
fun LoadingList(count: Int) {
    val cc = LocalCcColors.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(32.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(cc.field),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(cc.field),
                )
            }
        }
    }
}

/** Error card: message + retry button, centered. */
@Composable
fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    val cc = LocalCcColors.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = cc.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = "Retry",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().widthIn(max = 240.dp),
        )
    }
}

/** Confirm dialog: dim layer + centered card with title, message, cancel + confirm. */
@Composable
fun CcConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(cc.dimSmallMenu)
            .clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cc.panel)
                .border(1.dp, cc.border, RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = cc.text,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                Text(
                    text = message,
                    color = cc.muted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
            }
            Row(
                Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    container = cc.field,
                    radius = 15.dp,
                )
                PrimaryButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(48.dp),
                    container = if (destructive) cc.red else cc.accent,
                    radius = 15.dp,
                )
            }
        }
    }
}

/** Changed file row for the source control list: badge, name, short path. */
@Composable
fun ChangedFileRow(
    kind: com.closecode.ui.git.GitChangeKind,
    name: String,
    shortPath: String,
    onClick: () -> Unit,
) {
    val cc = LocalCcColors.current
    val (badgeBg, letterColor, letter) = when (kind) {
        com.closecode.ui.git.GitChangeKind.Added -> Triple(cc.green, Color.White, "A")
        com.closecode.ui.git.GitChangeKind.Modified -> Triple(cc.amber, Color.White, "M")
        com.closecode.ui.git.GitChangeKind.Deleted -> Triple(cc.red, Color.White, "D")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 0.dp)
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(cc.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                color = letterColor,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = cc.text,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shortPath,
                color = cc.muted,
                fontSize = 10.5.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/** Wide button: two equal halves share a row. Height 48, radius 15, icon 17 dp stroke 2. */
@Composable
fun WideButton(
    text: String,
    icon: String,
    enabled: Boolean,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cc = LocalCcColors.current
    val bg = when {
        !enabled -> cc.field
        primary -> cc.accent
        else -> cc.field
    }
    val fg = when {
        !enabled -> cc.muted
        primary -> Color.White
        else -> cc.text
    }
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CcIcon(icon, fg, 17.dp, 2f)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = fg,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
