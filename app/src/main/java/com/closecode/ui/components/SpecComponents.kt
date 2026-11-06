package com.closecode.ui.components

import androidx.compose.foundation.background
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
    Column(Modifier.fillMaxWidth().background(cc.content)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cc.field)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("back", cc.text2, 20.dp, 2.2f)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (origin != null) {
                    Text(
                        text = origin,
                        color = cc.text,
                        fontSize = 16.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = title,
                        color = cc.muted,
                        fontSize = 12.25.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = title,
                        color = cc.text,
                        fontSize = 16.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cc.divider))
    }
}

/** One 38 dp header action tile: field bg, radius 12, 18 dp icon. */
@Composable
fun HeaderAction(
    icon: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cc.field)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, if (enabled) cc.text2 else cc.faint, 18.dp, 1.9f)
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
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = modifier.padding(start = start, top = top, end = end, bottom = bottom),
    )
}

/** Centered empty state: icon tile, title, message and an optional action. */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cc = LocalCcColors.current
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cc.field),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, cc.muted, 28.dp, 1.6f)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = cc.text,
            fontSize = 16.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = cc.muted,
            fontSize = 13.25.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(actionText, onAction)
        }
    }
}

/** N placeholder rows for a loading list. */
@Composable
fun LoadingList(count: Int) {
    val cc = LocalCcColors.current
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(cc.field),
            )
        }
    }
}

/** Inline error card: the real message in red and an optional retry button. */
@Composable
fun ErrorCard(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    val cc = LocalCcColors.current
    Column(
        Modifier
            .padding(14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cc.diffDelBg)
            .padding(12.dp),
    ) {
        Text(
            text = message,
            color = cc.red,
            fontSize = 13.25.sp,
            fontFamily = Sans,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Retry",
                onClick = onRetry,
                icon = "refresh",
                height = 40.dp,
            )
        }
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
            .background(cc.dim)
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
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cc.panel)
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
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                Text(
                    text = message,
                    color = cc.textSoft,
                    fontSize = 13.5.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
                if (destructive) {
                    SecondaryButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        destructive = true,
                        height = 44.dp,
                    )
                } else {
                    PrimaryButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                    )
                }
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
