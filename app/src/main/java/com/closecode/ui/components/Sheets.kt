package com.closecode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcShape
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

/** Use Material3's ModalBottomSheet with our colours and handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = CcTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        scrimColor = c.dim,
        shape = CcShape.sheetTop,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(40.dp, 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.toggleOff),
            )
        },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(title, style = CcType.sheetTitle, color = c.text)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** A menu row: icon, text, optional destructive style, optional invisible (check lists). */
data class MenuItem(
    val icon: String,
    val text: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Same menu as the spec's CcPopupMenu: 210 dp, radius 16, divider-free rows. */
@Composable
fun CcPopupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItem>,
) {
    val c = CcTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(210.dp)
            .clip(CcShape.menu)
            .background(c.panel, CcShape.menu)
            .border(1.dp, c.border, CcShape.menu),
    ) {
        items.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clickable { onDismiss(); m.onClick() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcIcon(m.icon, if (m.destructive) c.red else c.text2, 17.dp)
                Spacer(Modifier.width(11.dp))
                OneLine(m.text, CcType.body, if (m.destructive) c.red else c.text)
            }
        }
    }
}

/**
 * Floating menu card anchored to the top-right, dimmed behind (0.18). Used by the
 * files menu and the overflow menus; entries can carry a divider and invisible icons.
 */
@Composable
fun MenuOverlay(
    top: Dp,
    end: Dp,
    width: Dp,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
) {
    val c = CcTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF1E2332).copy(alpha = 0.18f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = top, end = end)
                .width(width)
                .shadow(12.dp, CcShape.menu)
                .clip(CcShape.menu)
                .background(c.panel)
                .border(1.dp, c.border, CcShape.menu)
                // taps on the card do not reach the dim layer
                .clickable(enabled = false) {}
                .padding(vertical = 6.dp),
        ) {
            entries.forEach { e ->
                when (e) {
                    MenuEntry.Divider -> Box(
                        Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(c.border),
                    )
                    is MenuEntry.Item -> {
                        val col = if (e.destructive) c.red else c.text2
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable { onDismiss(); e.onClick() }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(17.dp)) {
                                if (e.iconVisible) CcIcon(e.icon, col, 17.dp, 1.8f)
                            }
                            Spacer(Modifier.width(11.dp))
                            OneLine(e.text, CcType.body, col)
                        }
                    }
                }
            }
        }
    }
}

/** Confirmation dialog. Destructive actions use a red confirm button. */
@Composable
fun CcConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = CcTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(CcShape.dialog)
                .background(c.panel)
                .padding(20.dp),
        ) {
            Text(
                title,
                style = CcType.itemTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                color = c.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = CcType.small.copy(fontSize = 13.5.sp),
                color = c.textSoft,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f), height = 44.dp)
                if (destructive) {
                    SecondaryButton(confirmText, onConfirm, Modifier.weight(1f), destructive = true, height = 44.dp)
                } else {
                    PrimaryButton(confirmText, onConfirm, Modifier.weight(1f), height = 44.dp)
                }
            }
        }
    }
}

/** Simple toast host. Show with toast.show("Press back again to exit"). */
class ToastState {
    var message by mutableStateOf<String?>(null)
    suspend fun show(text: String) {
        message = text
        delay(2000)
        message = null
    }
}

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = CcTheme.colors
    AnimatedVisibility(
        state.message != null,
        modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            Modifier
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.text2)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                state.message ?: "",
                style = CcType.small,
                color = Color.White,
            )
        }
    }
}
