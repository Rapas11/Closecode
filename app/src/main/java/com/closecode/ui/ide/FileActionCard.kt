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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Sans

enum class FileCardMode { Menu, NewFile, NewFolder, Rename, ConfirmDelete }

/** What the long press was on. [anchor] = where the finger pressed, in WINDOW pixels. */
data class FileCardTarget(
    val path: String,
    val name: String,
    val isFolder: Boolean,
    val anchor: IntOffset,
)

@Composable
fun FileActionCard(
    target: FileCardTarget,
    // Each returns null when it worked, or an error text to show in the card.
    onCreateFile: (target: FileCardTarget, name: String) -> String?,
    onCreateFolder: (target: FileCardTarget, name: String) -> String?,
    onRename: (target: FileCardTarget, newName: String) -> String?,
    onDuplicate: (target: FileCardTarget) -> Unit,
    onDelete: (target: FileCardTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(FileCardMode.Menu) }
    val cc = LocalCcColors.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val margin = with(density) { 8.dp.roundToPx() }
    val positioner = remember(target.anchor, imeBottom) {
        AnchoredPositionProvider(target.anchor, imeBottom, margin)
    }
    val shape = RoundedCornerShape(22.dp)

    Popup(
        popupPositionProvider = positioner,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .widthIn(min = 188.dp, max = 280.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(cc.panel)
                .border(1.dp, cc.border, shape)
                .animateContentSize(tween(160)),
        ) {
            AnimatedContent(
                targetState = mode,
                label = "fileCard",
                transitionSpec = {
                    (fadeIn(tween(140)) togetherWith fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
            ) { current ->
                when (current) {
                    FileCardMode.Menu -> MenuItems(
                        onNewFile = { mode = FileCardMode.NewFile },
                        onNewFolder = { mode = FileCardMode.NewFolder },
                        onRename = { mode = FileCardMode.Rename },
                        onDuplicate = { onDuplicate(target); onDismiss() },
                        onDelete = { mode = FileCardMode.ConfirmDelete },
                    )

                    FileCardMode.NewFile -> NameForm(
                        title = "New file",
                        initial = "",
                        action = "Create",
                        onCancel = { mode = FileCardMode.Menu },
                    ) { name ->
                        onCreateFile(target, name).also { if (it == null) onDismiss() }
                    }

                    FileCardMode.NewFolder -> NameForm(
                        title = "New folder",
                        initial = "",
                        action = "Create",
                        onCancel = { mode = FileCardMode.Menu },
                    ) { name ->
                        onCreateFolder(target, name).also { if (it == null) onDismiss() }
                    }

                    FileCardMode.Rename -> NameForm(
                        title = "Rename",
                        initial = target.name,
                        action = "Rename",
                        onCancel = { mode = FileCardMode.Menu },
                    ) { name ->
                        onRename(target, name).also { if (it == null) onDismiss() }
                    }

                    FileCardMode.ConfirmDelete -> ConfirmDelete(
                        target = target,
                        onCancel = { mode = FileCardMode.Menu },
                        onConfirm = { onDelete(target); onDismiss() },
                    )
                }
            }
        }
    }
}

/** Puts the card at the pressed point, then keeps it fully on screen and above the keyboard. */
private class AnchoredPositionProvider(
    private val anchor: IntOffset,
    private val imeBottom: Int,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - imeBottom - popupContentSize.height - margin)
            .coerceAtLeast(margin)
        return IntOffset(
            anchor.x.coerceIn(margin, maxX),
            anchor.y.coerceIn(margin, maxY),
        )
    }
}

@Composable
private fun MenuItems(
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val cc = LocalCcColors.current
    Column(Modifier.padding(vertical = 6.dp)) {
        MenuRow("plus", "New file", cc.text2, onNewFile)
        MenuRow("folder", "New folder", cc.text2, onNewFolder)
        MenuRow("edit", "Rename", cc.text2, onRename)
        MenuRow("copy", "Duplicate", cc.text2, onDuplicate)
        Box(Modifier.fillMaxWidth().height(1.dp).background(cc.border))
        MenuRow("trash", "Delete", cc.red, onDelete)
    }
}

@Composable
private fun MenuRow(
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.closecode.ui.CcIcon(icon, color, 20.dp, 1.8f)
        Text(
            text = label,
            fontSize = 16.sp,
            color = color,
            fontFamily = Sans,
        )
    }
}

@Composable
private fun NameForm(
    title: String,
    initial: String,
    action: String,
    onCancel: () -> Unit,
    onSubmit: (String) -> String?,
) {
    val cc = LocalCcColors.current
    // For a rename, select only the name part (not the extension), like most file managers.
    val selectEnd = initial.substringBeforeLast('.', initial).length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectEnd))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canSubmit = value.text.isNotBlank()
    val submit = { if (canSubmit) error = onSubmit(value.text.trim()) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = cc.text2,
            fontFamily = Sans,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cc.field)
                .border(1.dp, if (error != null) cc.red else cc.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = cc.text2, fontFamily = Sans),
                cursorBrush = SolidColor(cc.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        error?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = cc.red,
                fontFamily = Sans,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardButton("Cancel", cc.field, cc.text2, true, Modifier.weight(1f), onCancel)
            CardButton(action, cc.accent, Color.White, canSubmit, Modifier.weight(1f)) { submit() }
        }
    }
}

@Composable
private fun ConfirmDelete(
    target: FileCardTarget,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cc = LocalCcColors.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Delete ${target.name}?",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = cc.text2,
            fontFamily = Sans,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = target.path,
            fontSize = 12.sp,
            color = cc.muted,
            fontFamily = Sans,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (target.isFolder) {
                "This cannot be undone. Everything inside it will be removed."
            } else {
                "This cannot be undone."
            },
            fontSize = 13.sp,
            color = cc.muted,
            fontFamily = Sans,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardButton("Cancel", cc.field, cc.text2, true, Modifier.weight(1f), onCancel)
            CardButton("Delete", cc.red, Color.White, true, Modifier.weight(1f), onConfirm)
        }
    }
}

@Composable
private fun CardButton(
    text: String,
    bg: Color,
    fg: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            fontFamily = Sans,
        )
    }
}
