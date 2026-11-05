package com.closecode.ui.ide

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.DesignBackButton
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/** The GitHub/Source control pages share the light mock tokens, never a literal surface. */
internal val MockSurfaces get() = Unit

internal fun gitLetter(kind: GitChangeKind): String = when (kind) {
    GitChangeKind.Added -> "A"
    GitChangeKind.Modified -> "M"
    GitChangeKind.Deleted -> "D"
}

internal fun gitLetterBackground(kind: GitChangeKind): Color = when (kind) {
    GitChangeKind.Added -> com.closecode.ui.theme.MockColors().successBg
    GitChangeKind.Modified -> com.closecode.ui.theme.MockColors().warningBg
    GitChangeKind.Deleted -> com.closecode.ui.theme.MockColors().dangerBg
}

internal fun gitLetterColor(kind: GitChangeKind): Color = when (kind) {
    GitChangeKind.Added -> com.closecode.ui.theme.MockColors().green
    GitChangeKind.Modified -> com.closecode.ui.theme.MockColors().warning
    GitChangeKind.Deleted -> com.closecode.ui.theme.MockColors().danger
}

internal fun gitRelativeTime(whenMillis: Long): String {
    val minutes = ((System.currentTimeMillis() - whenMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
        else -> {
            val format = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
            format.format(java.util.Date(whenMillis))
        }
    }
}

/** owner/repo from any GitHub remote URL, or null when the remote is not GitHub. */
internal fun gitRemoteLabel(url: String?): String? {
    val cleaned = url?.trim()?.removeSuffix(".git") ?: return null
    val match = Regex("github\\.com[/:]([^/]+)/([^/]+)").find(cleaned) ?: return null
    return "${match.groupValues[1]}/${match.groupValues[2]}"
}

internal fun gitAllChanges(snapshot: com.closecode.ui.git.GitSnapshot): List<com.closecode.ui.git.GitChange> {
    val byPath = LinkedHashMap<String, com.closecode.ui.git.GitChange>()
    snapshot.staged.forEach { byPath[it.path] = it }
    snapshot.unstaged.forEach { byPath.putIfAbsent(it.path, it) }
    snapshot.untracked.forEach { byPath.putIfAbsent(it.path, it) }
    return byPath.values.toList()
}

@Composable
internal fun GitPanelHeader(
    title: String,
    subtitle: String?,
    onRefresh: (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = cc.text,
                fontSize = 21.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = cc.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (onRefresh != null) {
            GitHeaderIconButton(CcIcons.Refresh, "Refresh", onRefresh, filled = true)
        }
    }
}

@Composable
internal fun GitPageHeader(
    origin: String,
    current: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DesignBackButton(onBack)
        Text(
            text = origin,
            color = c.mock.text,
            fontSize = 16.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = current,
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
internal fun GitHeaderIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
    // Screen 17: the overflow action sits on a filled rounded-square, not a bare glyph.
    filled: Boolean = false,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (filled) Modifier.background(cc.field) else Modifier,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, tint ?: cc.text2, 17.dp)
    }
}

@Composable
internal fun GitPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    background: Color? = null,
    foreground: Color? = null,
    height: Dp = 48.dp,
    radius: Dp = 15.dp,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val bg = background ?: cc.accent
    val fg = foreground ?: Color.White
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (enabled) bg else cc.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            CcIcon(icon, if (enabled) fg else cc.muted, 17.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = if (enabled) fg else cc.muted,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GitSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) c.mock.field else c.mock.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            CcIcon(icon, if (enabled) (if (danger) c.mock.danger else c.mock.text2) else c.mock.faint, 17.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = when {
                !enabled -> c.mock.faint
                danger -> c.mock.danger
                else -> c.mock.text2
            },
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GitSmallButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cc.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) {
            CcIcon(
                icon,
                when {
                    !enabled -> cc.faint
                    danger -> cc.red
                    else -> cc.text2
                },
                16.dp,
            )
        }
        Text(
            text = label,
            color = when {
                !enabled -> cc.faint
                danger -> cc.red
                else -> cc.text2
            },
            fontSize = 13.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun GitCheckBox(checked: Boolean, onToggle: () -> Unit, enabled: Boolean = true) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) cc.green else Color.Transparent)
            .border(
                if (checked) 0.dp else 1.5.dp,
                if (checked) cc.green else cc.toggleOff,
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .semantics { contentDescription = if (checked) "Unselect file" else "Select file" },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            com.closecode.ui.CcIcon("check", Color.White, 12.dp, 3f)
        }
    }
}

@Composable
internal fun GitLetterTile(kind: GitChangeKind, size: Dp = 30.dp) {
    val c = LocalPageColors.current
    // Screen 17: badges are saturated squares with a white letter — M gold, A green, D red.
    val background = when (kind) {
        GitChangeKind.Added -> c.mock.green
        GitChangeKind.Modified -> c.mock.warning
        GitChangeKind.Deleted -> c.mock.danger
    }
    val foreground = when (kind) {
        GitChangeKind.Added -> c.mock.onAccent
        GitChangeKind.Modified -> c.mock.onAccent
        GitChangeKind.Deleted -> c.mock.onAccent
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = gitLetter(kind),
            color = foreground,
            fontSize = if (size >= 30.dp) 13.sp else 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun GitStatusLetter(kind: GitChangeKind) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Text(
        text = gitLetter(kind),
        color = when (kind) {
            GitChangeKind.Added -> cc.green
            GitChangeKind.Modified -> cc.amber
            GitChangeKind.Deleted -> cc.red
        },
        fontSize = 16.sp,
        fontFamily = com.closecode.ui.theme.Mono,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
internal fun GitField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    background: Color? = null,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(
            color = cc.text,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(cc.accent),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(13.dp))
            .background(background ?: cc.field)
            .border(1.dp, cc.border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = cc.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
internal fun GitMenuCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .widthIn(min = 200.dp, max = 250.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.mock.page)
            .border(1.dp, c.mock.border, RoundedCornerShape(14.dp))
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
internal fun GitMenuItem(
    label: String,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (icon != null) {
            CcIcon(
                icon,
                when {
                    !enabled -> c.mock.faint
                    danger -> c.mock.danger
                    else -> c.mock.muted
                },
                17.dp,
            )
        }
        Text(
            text = label,
            color = when {
                !enabled -> c.mock.faint
                danger -> c.mock.danger
                else -> c.mock.text2
            },
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = if (danger) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun GitMenuDivider() {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .height(1.dp)
            .background(c.mock.border.copy(alpha = 0.7f)),
    )
}

@Composable
internal fun GitConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.mock.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                .background(c.mock.page)
                .border(1.dp, c.mock.border, RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                lineHeight = 19.sp,
            )
            Row(
                Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GitSecondaryButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GitPrimaryButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    background = if (destructive) c.mock.danger else c.mock.accent,
                    foreground = if (destructive) c.mock.onAccent else c.mock.onAccent,
                )
            }
        }
    }
}

@Composable
internal fun GitNameDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    var value by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.mock.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                .background(c.mock.page)
                .border(1.dp, c.mock.border, RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            GitField(
                value = value,
                onValueChange = { value = it },
                hint = hint,
                height = 48.dp,
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GitSecondaryButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GitPrimaryButton(
                    label = confirmLabel,
                    onClick = { onCreate(value.trim()) },
                    modifier = Modifier.weight(1f),
                    enabled = value.trim().isNotEmpty(),
                )
            }
        }
    }
}

@Composable
internal fun GitEmptyNote(text: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = c.mock.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One unified diff line in the light handoff: a single 34 dp number column, a 14 dp sign
 * column, then mono code. The whole row is tinted for added and removed lines.
 */
@Composable
internal fun GitDiffLine(
    row: UnifiedRow,
    wrap: Boolean,
    hScroll: ScrollState,
    minWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val background = when {
        row.added -> c.mock.successBg
        row.removed -> c.mock.dangerBg
        else -> Color.Transparent
    }
    val foreground = when {
        row.added -> c.mock.green
        row.removed -> c.mock.danger
        else -> c.mock.text2
    }
    val number = row.newNo ?: row.oldNo
    val rowModifier = if (wrap) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .horizontalScroll(hScroll)
            .widthIn(min = minWidth)
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(background),
    ) {
        Row(
            rowModifier.heightIn(min = 18.dp).padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(34.dp)
                    .padding(end = 7.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = number?.toString().orEmpty(),
                    color = c.mock.faint,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                )
            }
            Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        row.added -> "+"
                        row.removed -> "−"
                        else -> " "
                    },
                    color = foreground,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                )
            }
            Text(
                text = row.text,
                color = foreground,
                fontSize = 11.sp,
                fontFamily = Mono,
                lineHeight = 18.7.sp,
                softWrap = wrap,
                maxLines = if (wrap) Int.MAX_VALUE else 1,
            )
        }
    }
}

@Composable
internal fun GitDiffGap(
    hidden: Int,
    onClick: () -> Unit,
    wrap: Boolean,
    hScroll: ScrollState,
    minWidth: Dp,
) {
    val c = LocalPageColors.current
    Box(Modifier.fillMaxWidth().background(c.mock.card)) {
        Row(
            (if (wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(hScroll).widthIn(min = minWidth))
                .height(30.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CcIcon(CcIcons.Down, c.mock.accent, 13.dp)
            Text(
                text = "$hidden unchanged lines",
                color = c.mock.accent,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
