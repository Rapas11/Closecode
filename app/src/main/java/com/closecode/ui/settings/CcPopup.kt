package com.closecode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Sans

internal enum class PopupButtonStyle { Ghost, Filled, Danger }

/** One popup card for every Settings dialog: a scrim, a rounded card, content, then actions. */
@Composable
internal fun CcPopup(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cc = LocalCcColors.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(cc.panel)
                .border(1.dp, cc.border, shape)
                .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 16.dp),
        ) {
            Text(
                text = title,
                color = cc.text,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = cc.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** One option in a list. The selected row gets an accent border, a lighter ground and a tick. */
@Composable
internal fun CcChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: String? = null,
    previewFont: FontFamily? = null,
) {
    val cc = LocalCcColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) cc.selectedRow else cc.field)
            .border(1.5.dp, if (selected) cc.accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = cc.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            if (preview != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = preview,
                    color = cc.textSoft,
                    fontSize = 13.sp,
                    fontFamily = previewFont ?: Sans,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) cc.accent else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (selected) cc.accent else cc.toggleOff,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) CcIcon("check", Color.White, 13.dp)
        }
    }
}

@Composable
internal fun CcPopupButton(
    text: String,
    style: PopupButtonStyle = PopupButtonStyle.Ghost,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cc = LocalCcColors.current
    val background = when (style) {
        PopupButtonStyle.Ghost -> cc.field
        PopupButtonStyle.Filled -> cc.accent
        PopupButtonStyle.Danger -> cc.red
    }
    val foreground = when (style) {
        PopupButtonStyle.Ghost -> cc.textSoft
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) foreground else foreground.copy(alpha = 0.45f),
            fontSize = 13.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun CcConfirmPopup(
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cc = LocalCcColors.current
    CcPopup(
        title = title,
        onDismiss = onDismiss,
        actions = {
            CcPopupButton("Cancel", onClick = onDismiss)
            CcPopupButton(
                confirmText,
                if (destructive) PopupButtonStyle.Danger else PopupButtonStyle.Filled,
                onClick = onConfirm,
            )
        },
    ) {
        Text(
            text = message,
            color = cc.textSoft,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 19.sp,
        )
    }
}

@Composable
internal fun CcInputPopup(
    title: String,
    initial: String,
    hint: String,
    isValid: (String) -> Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cc = LocalCcColors.current
    var text by remember(initial) { mutableStateOf(initial) }
    CcPopup(
        title = title,
        onDismiss = onDismiss,
        actions = {
            CcPopupButton("Cancel", onClick = onDismiss)
            CcPopupButton(
                "Save",
                if (isValid(text)) PopupButtonStyle.Filled else PopupButtonStyle.Ghost,
                enabled = isValid(text),
                onClick = { if (isValid(text)) onSave(text) },
            )
        },
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = cc.text, fontSize = 14.sp, fontFamily = Sans),
            cursorBrush = SolidColor(cc.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cc.field)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(hint, color = cc.muted, fontSize = 14.sp, fontFamily = Sans)
                }
                inner()
            },
        )
    }
}
