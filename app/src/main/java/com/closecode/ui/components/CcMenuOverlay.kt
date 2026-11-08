package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors

/** Dim behind a small menu. Spec 11: #1E2332 at 18% light, stronger in dark. */
private val MenuDim = Color(0xFF1E2332)

/**
 * Dim layer + floating menu card from spec screens 11 and 13.
 *
 * [top] is measured from the top-right of the area under the status bar, matching the
 * reference `MenuOverlay` in the guide. The card is kept inside the screen because callers
 * clamp [top] against the available height.
 */
@Composable
fun CcMenuOverlay(
    top: Dp,
    onDismiss: () -> Unit,
    end: Dp = 24.dp,
    width: Dp = 200.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cc = LocalCcColors.current
    val dim = MenuDim.copy(alpha = if (isSystemInDarkTheme()) 0.42f else 0.18f)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(dim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = top.coerceAtLeast(0.dp), end = end)
                    .width(width)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(cc.panel)
                    .border(1.dp, cc.border, RoundedCornerShape(16.dp))
                    // Taps on the card must not reach the dim layer.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

/** One 42 dp row in a [CcMenuOverlay]. Icons are 17 dp, text 14 sp Medium. */
@Composable
fun CcMenuItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    iconVisible: Boolean = true,
) {
    val cc = LocalCcColors.current
    val colour = if (destructive) cc.red else cc.text2
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
            if (iconVisible) CcIcon(icon, colour, 17.dp, 1.8f)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            color = colour,
            fontSize = 14.sp,
            fontFamily = com.closecode.ui.theme.Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** 1 dp full-width rule with 4 dp above and below (spec 11). */
@Composable
fun CcMenuDivider() {
    val cc = LocalCcColors.current
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(cc.border),
    )
}
