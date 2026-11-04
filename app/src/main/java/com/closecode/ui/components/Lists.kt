package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.util.regex.Pattern

private fun Color.tint13(): Color = copy(alpha = 0.13f)

@Composable
fun PanelRow(
    icon: String,
    tint: Color,
    title: String,
    sub: String?,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalCcColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (selected) 8.dp else 0.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.selectedRow else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tint.tint13()),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, tint, 21.dp, 1.8f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OneLine(
                title,
                TextStyle(
                    fontFamily = Sans,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                c.text,
            )
            if (sub != null) {
                OneLine(
                    sub,
                    TextStyle(fontFamily = Sans, fontSize = 12.5.sp),
                    c.muted,
                )
            }
        }
        trailing?.invoke() ?: CcIcon("cr", c.faint, 16.dp, 2.2f)
    }
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    ring: Color = LocalCcColors.current.panel,
) {
    Box(
        modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, ring, CircleShape),
    )
}

@Composable
fun TreeRow(
    icon: String,
    iconTint: Color,
    label: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chevron: String? = null,
    chevronOpen: Boolean = false,
    gitStatus: StatusKind? = null,
    indent: Dp = 16.dp,
) {
    val c = LocalCcColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) c.selectedFile else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp + indent * depth.coerceAtLeast(0), end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(13.dp), contentAlignment = Alignment.Center) {
            if (chevron != null) {
                CcIcon(
                    chevron,
                    c.muted,
                    13.dp,
                    2.2f,
                    modifier = Modifier.rotate(if (chevronOpen) 90f else 0f),
                )
            }
        }
        CcIcon(icon, iconTint, 17.dp, 1.7f)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = c.text2,
            style = TextStyle(
                fontFamily = Sans,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val letter = gitStatus?.letter
        if (letter != null) {
            Text(
                text = letter,
                color = when (gitStatus) {
                    StatusKind.Added -> c.green
                    StatusKind.Modified -> c.amber
                    StatusKind.Deleted -> c.red
                    StatusKind.Untracked -> c.faint
                },
                style = TextStyle(
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

private val StatusKind.letter: String
    get() = when (this) {
        StatusKind.Added -> "A"
        StatusKind.Modified -> "M"
        StatusKind.Deleted -> "D"
        StatusKind.Untracked -> "U"
    }

@Composable
fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = LocalCcColors.current
    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = c.muted,
            style = TextStyle(
                fontFamily = Sans,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
            ),
            modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.field),
            content = { content() },
        )
    }
}

@Composable
fun SettingRow(
    icon: String,
    tint: Color,
    title: String,
    sub: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val c = LocalCcColors.current
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.tint13()),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(icon, tint, 18.dp, 1.8f)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                OneLine(
                    title,
                    TextStyle(
                        fontFamily = Sans,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    c.text,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = c.muted,
                        style = TextStyle(
                            fontFamily = Sans,
                            fontSize = 12.sp,
                            lineHeight = 16.8.sp,
                        ),
                    )
                }
            }
            if (value != null) {
                OneLine(
                    value,
                    TextStyle(
                        fontFamily = Sans,
                        fontSize = 13.sp,
                    ),
                    c.muted,
                )
                Spacer(Modifier.width(4.dp))
            }
            trailing?.invoke()
                ?: if (onClick != null) CcIcon("cr", c.faint, 15.dp, 2.2f)
                else Unit
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 62.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.divider),
            )
        }
    }
}

internal fun searchRanges(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<IntRange>()
    val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        matches += IntRange(matcher.start(), matcher.end() - 1)
    }
    return matches
}

private fun highlight(text: String, query: String, c: com.closecode.ui.theme.CcColors) =
    buildAnnotatedString {
        val matches = searchRanges(text, query)
        if (matches.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        var cursor = 0
        matches.forEach { range ->
            append(text.substring(cursor, range.first))
            withStyle(SpanStyle(background = c.matchBg, color = c.matchText, fontWeight = FontWeight.Bold)) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        append(text.substring(cursor))
    }

@Composable
fun SearchResultRow(
    lineNumber: Int?,
    text: String,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCcColors.current
    Row(
        modifier
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(c.content)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lineNumber?.toString().orEmpty(),
            color = c.faint,
            style = TextStyle(
                fontFamily = Mono,
                fontSize = 10.sp,
            ),
            modifier = Modifier.widthIn(min = 24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = highlight(text, query, c),
            color = c.text2,
            style = TextStyle(
                fontFamily = Mono,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
