package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.closecode.ui.CcIcon

internal data class SelectionUi(
    val visible: Boolean,
    val anchorX: Int,
    val anchorY: Int,
    val lineHeight: Int,
)

@Composable
internal fun SelectionToolbar(
    ui: SelectionUi,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onCut: () -> Unit,
) {
    if (!ui.visible) return
    val density = LocalDensity.current
    val gap = with(density) { 12.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val lineHeight = ui.lineHeight.coerceAtLeast(margin)
    Popup(
        popupPositionProvider = remember(ui.anchorX, ui.anchorY, lineHeight, gap, margin) {
            SelectionAboveProvider(ui.anchorX, ui.anchorY, lineHeight, gap, margin)
        },
        properties = PopupProperties(focusable = false),
    ) {
        Row(
            Modifier
                .height(50.dp)
                .shadow(12.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF1E2236))
                .border(1.dp, Color(0xFF3A3F7A), RoundedCornerShape(25.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionAction("select-all", "Select all", onSelectAll)
            SelectionAction("copy", "Copy", onCopy)
            SelectionAction("paste", "Paste", onPaste)
            SelectionAction("cut", "Cut", onCut)
        }
    }
}

@Composable
private fun SelectionAction(icon: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CcIcon(icon, Color.White, 19.dp)
        Text(
            text = label,
            color = Color(0xFFC9CCE0),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private class SelectionAboveProvider(
    private val x: Int,
    private val y: Int,
    private val lineHeight: Int,
    private val gap: Int,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left = (x - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        var top = y - popupContentSize.height - gap
        if (top < margin) top = y + lineHeight + gap
        top = top.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        return IntOffset(left, top)
    }
}
