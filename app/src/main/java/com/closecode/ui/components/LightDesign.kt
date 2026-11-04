package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

@Composable
fun DesignPage(
    modifier: Modifier = Modifier,
    background: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(background ?: c.mock.page),
        content = content,
    )
}

@Composable
fun DesignBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Box(
        modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.mock.field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(CcIcons.ChevronLeft, c.mock.text2, 20.dp)
    }
}

@Composable
fun DesignPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = if (onBack != null) 12.dp else 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) DesignBackButton(onBack)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 16.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun DesignIconTile(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    radius: Dp = 14.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, tint, iconSize)
    }
}

@Composable
fun DesignCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color? = null,
    radius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(background ?: c.mock.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun DesignListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (danger) c.mock.danger else c.mock.text,
                fontSize = 14.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
fun DesignSectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Text(
        text = text.uppercase(),
        color = c.mock.faint,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun DesignPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    height: Dp = 48.dp,
    radius: Dp = 15.dp,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (enabled) c.mock.accent else c.mock.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            CcIcon(leading, if (enabled) c.mock.onAccent else c.mock.muted, 17.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = if (enabled) c.mock.onAccent else c.mock.muted,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun DesignPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) c.mock.accent else c.mock.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) c.mock.onAccent else c.mock.textSoft,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun DesignAvatar(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color? = null,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background ?: c.mock.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).uppercase(),
            color = c.mock.onAccent,
            fontSize = (size.value * 0.42f).sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun DesignDivider(modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.mock.border.copy(alpha = 0.65f)),
    )
}

@Composable
fun DesignSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Box(
        modifier
            .width(42.dp)
            .height(25.dp)
            .clip(CircleShape)
            .background(if (checked) c.mock.accent else c.mock.toggleOff)
            .clickable { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(19.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun DesignChevron(tint: Color? = null) {
    val c = LocalPageColors.current
    Icon(CcIcons.ChevronRight, null, tint = tint ?: c.mock.faint, modifier = Modifier.size(16.dp))
}
