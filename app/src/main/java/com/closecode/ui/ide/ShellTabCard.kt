package com.closecode.ui.ide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors

private enum class ShellCardMode { Menu, Rename, ConfirmClose }

@Composable
internal fun ShellTabCard(
    anchor: IntOffset,
    shellName: String,
    isRunning: Boolean,
    onRename: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ShellCardMode.Menu) }
    val cc = LocalCcColors.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val margin = with(density) { 8.dp.roundToPx() }
    val positioner = remember(anchor, imeBottom) {
        AnchoredPositionProvider(anchor, imeBottom, margin)
    }
    val shape = RoundedCornerShape(20.dp)

    Popup(
        popupPositionProvider = positioner,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .width(210.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(cc.panel)
                .border(1.dp, cc.border, shape)
                .animateContentSize(tween(160)),
        ) {
            AnimatedContent(
                targetState = mode,
                label = "shellCard",
                transitionSpec = {
                    (fadeIn(tween(140)) togetherWith fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
            ) { current ->
                when (current) {
                    ShellCardMode.Menu -> Column(Modifier.padding(vertical = 6.dp)) {
                        ShellCardItem("pencil", "Rename shell", cc.text2) {
                            mode = ShellCardMode.Rename
                        }
                        ShellCardItem("eraser", "Clear output", cc.text2) {
                            onClear()
                            onDismiss()
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(1.dp)
                                .background(cc.divider),
                        )
                        ShellCardItem("x", "Close shell", cc.red) {
                            if (isRunning) mode = ShellCardMode.ConfirmClose
                            else {
                                onClose()
                                onDismiss()
                            }
                        }
                    }

                    ShellCardMode.Rename -> RenameForm(
                        initial = shellName,
                        onSave = {
                            onRename(it)
                            onDismiss()
                        },
                        onCancel = { mode = ShellCardMode.Menu },
                    )

                    ShellCardMode.ConfirmClose -> Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Close $shellName?",
                            color = cc.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "A command is still running. It will be stopped.",
                            color = cc.muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShellSmallButton("Cancel", false, Modifier.weight(1f)) {
                                mode = ShellCardMode.Menu
                            }
                            ShellSmallButton("Close shell", true, Modifier.weight(1f), danger = true) {
                                onClose()
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellCardItem(
    icon: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CcIcon(icon, color, 19.dp)
        Text(
            text = label,
            color = color,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RenameForm(
    initial: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val cc = LocalCcColors.current
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(14.dp)) {
        Text(
            text = "Rename shell",
            color = cc.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(24) },
            singleLine = true,
            textStyle = TextStyle(color = cc.text, fontSize = 13.sp),
            cursorBrush = SolidColor(cc.accent),
            keyboardActions = KeyboardActions(
                onDone = { if (text.isNotBlank()) onSave(text.trim()) },
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .clip(RoundedCornerShape(14.dp))
                .background(cc.field)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShellSmallButton("Cancel", false, Modifier.weight(1f), onClick = onCancel)
            ShellSmallButton("Save", true, Modifier.weight(1f)) {
                if (text.isNotBlank()) onSave(text.trim())
            }
        }
    }
}

@Composable
private fun ShellSmallButton(
    text: String,
    filled: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val cc = LocalCcColors.current
    val background = when {
        danger -> cc.red
        filled -> cc.accent
        else -> cc.field
    }
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled || danger) Color.White else cc.text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
