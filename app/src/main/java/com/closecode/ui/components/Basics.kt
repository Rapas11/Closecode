package com.closecode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.CcColors
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/** Text that never wraps in headers, rows and buttons. */
@Composable
fun OneLine(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) = Text(
    text,
    style = style,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
)

/** Section label — 11 sp / 700, uppercase, tracking 0.8, colour muted. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalCcColors.current
    Text(
        text.uppercase(),
        style = TextStyle(
            fontFamily = Sans,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
        ),
        color = c.muted,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp),
    )
}

/** Primary action button. 48 dp tall by default (52 on Welcome), radius 15. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    container: Color = LocalCcColors.current.accent,
    height: Dp = 48.dp,
    radius: Dp = 15.dp,
) {
    val c = LocalCcColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (enabled) container else c.field)
            .alpha(if (pressed && enabled) 0.9f else 1f)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CcIcon(icon, if (enabled) Color.White else c.muted, 17.dp, 2f)
            Spacer(Modifier.width(8.dp))
        }
        OneLine(
            text,
            TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            if (enabled) Color.White else c.muted,
        )
    }
}

/** Success button — same shape, green fill. Used for Run / Open app. */
@Composable
fun SuccessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) = PrimaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    enabled = enabled,
    height = height,
    container = LocalCcColors.current.green,
)

/** Secondary button. Fill field, text textSoft (or red when destructive). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    destructive: Boolean = false,
    height: Dp = 48.dp,
) {
    val c = LocalCcColors.current
    val color = if (destructive) c.red else c.textSoft
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(15.dp))
            .background(c.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CcIcon(icon, color, 16.dp, 2f)
            Spacer(Modifier.width(7.dp))
        }
        OneLine(
            text,
            TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color,
        )
    }
}

/** 42 x 25 toggle. Off: toggleOff track. On: accent track. Knob moves 160 ms. */
@Composable
fun CcToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCcColors.current
    val track by animateColorAsState(
        if (checked) c.accent else c.toggleOff,
        tween(160),
        label = "track",
    )
    val knobX by animateDpAsState(
        if (checked) 20.dp else 3.dp,
        tween(160),
        label = "knob",
    )
    Box(
        modifier
            .size(42.dp, 25.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = knobX, top = 3.dp)
                .size(19.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 30 dp chip. Selected: accent fill, white text. Unselected: field fill, textSoft text. */
@Composable
fun CcChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCcColors.current
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) c.accent else c.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        OneLine(
            text,
            TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            if (selected) Color.White else c.textSoft,
        )
    }
}

/** Labelled text field, 44 dp tall, focus ring 1.5 dp accent, error line in red. */
@Composable
fun CcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String = "",
    mono: Boolean = false,
    error: String? = null,
    leadingIcon: String? = null,
    singleLine: Boolean = true,
) {
    val c = LocalCcColors.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                style = TextStyle(
                    fontFamily = Sans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = c.textSoft,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.field)
                .border(
                    if (focused) 1.5.dp else 0.dp,
                    if (focused) c.accent else Color.Transparent,
                    RoundedCornerShape(13.dp),
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                CcIcon(leadingIcon, c.muted, 16.dp)
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        hint,
                        style = TextStyle(
                            fontFamily = if (mono) Mono else Sans,
                            fontSize = 12.5.sp,
                        ),
                        color = c.muted,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        fontFamily = if (mono) Mono else Sans,
                        fontSize = 12.5.sp,
                        color = c.text2,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                error,
                style = TextStyle(fontFamily = Sans, fontSize = 11.5.sp),
                color = c.red,
            )
        }
    }
}
