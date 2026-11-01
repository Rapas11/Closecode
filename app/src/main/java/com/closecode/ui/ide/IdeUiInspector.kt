package com.closecode.ui.ide

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.device.CloseCodeAccessibilityService
import com.closecode.device.UiNode
import com.closecode.device.UiSnapshot
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.launch

/**
 * A read-only view of the accessibility tree the app's own automation sees. It is the phone-side
 * counterpart of `closecode ui dump`: the same nodes, the same bounds, the same package, so a
 * failed tap can be diagnosed without leaving the editor.
 */
@Composable
fun IdeUiInspectorOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp,
) {
    if (!open) return
    IdeUiInspectorSheet(onDismiss = onDismiss, barHeight = barHeight)
}

@Composable
fun IdeUiInspectorSheet(
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<UiSnapshot?>(null) }
    var selected by remember { mutableStateOf<UiNode?>(null) }
    val service = CloseCodeAccessibilityService.current()

    LaunchedEffect(refresh) {
        snapshot = service?.snapshot(400)
        selected = null
    }

    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "UI INSPECTOR",
                    color = c.mu,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = snapshot?.let { "${it.packageName.ifBlank { "unknown" }} · ${it.width}×${it.height}" }
                        ?: if (service == null) "Accessibility service is off" else "Reading the screen…",
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            HeaderIconButton(
                icon = CcIcons.Refresh,
                label = "Refresh UI tree",
                tint = c.iconTint,
                boxSize = 30.dp,
                iconSize = 17.dp,
                onClick = { refresh++ },
            )
        }

        if (service == null) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(
                    text = "CloseCode needs its accessibility service to read the screen and " +
                        "tap nodes. Enable it, then come back here.",
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
                Spacer(Modifier.height(12.dp))
                OutlineButton(
                    label = "Open accessibility settings",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
            return@IdeAnchoredSheet
        }

        val nodes = snapshot?.nodes.orEmpty()
        if (nodes.isEmpty()) {
            Text(
                text = "No visible window. Open the app you want to inspect, then refresh.",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            )
            return@IdeAnchoredSheet
        }

        Text(
            text = "${nodes.size} nodes${if (snapshot?.truncated == true) " (truncated)" else ""}",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(start = 18.dp, bottom = 4.dp),
        )
        LazyColumn(Modifier.weight(1f, fill = false)) {
            itemsIndexed(nodes) { index, node ->
                UiNodeRow(
                    node = node,
                    selected = selected == node,
                    onClick = { selected = if (selected == node) null else node },
                    onTap = {
                        scope.launch {
                            service.tap(node.centerX.toFloat(), node.centerY.toFloat())
                            refresh++
                        }
                    },
                )
            }
        }

        selected?.let { node ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlineButton(
                    label = "Tap selected",
                    onClick = {
                        scope.launch {
                            service.tap(node.centerX.toFloat(), node.centerY.toFloat())
                            refresh++
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlineButton(
                    label = "Copy node",
                    onClick = {
                        clipboard.setText(AnnotatedString(node.describe()))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UiNodeRow(
    node: UiNode,
    selected: Boolean,
    onClick: () -> Unit,
    onTap: () -> Unit,
) {
    val c = LocalPageColors.current
    val label = node.text ?: node.description ?: node.id ?: node.className ?: "(node)"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) c.panel2 else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (10 + node.depth * 10).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (node.enabled) c.tx else c.mu,
                fontSize = 12.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    node.id?.let { append(it.substringAfterLast('/')) }
                    if (node.bounds.size == 4) {
                        if (isNotEmpty()) append(" · ")
                        append("${node.bounds[0]},${node.bounds[1]} ${node.bounds[2] - node.bounds[0]}×${node.bounds[3] - node.bounds[1]}")
                    }
                }.ifBlank { node.className.orEmpty() },
                color = c.mu,
                fontSize = 10.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (node.clickable || node.scrollable || node.focused || node.password) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (node.clickable) UiBadge("tap")
                if (node.scrollable) UiBadge("scroll")
                if (node.focused) UiBadge("focus")
                if (node.password) UiBadge("•••")
            }
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = node.clickable, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(
                CcIcons.Play,
                if (node.clickable) c.link else c.textFaint,
                14.dp,
            )
        }
    }
}

@Composable
private fun UiBadge(label: String) {
    val c = LocalPageColors.current
    Text(
        text = label,
        color = c.mu,
        fontSize = 9.sp,
        fontFamily = Mono,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c.panel2)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private fun UiNode.describe(): String = buildString {
    append("class=").append(className.orEmpty())
    if (!text.isNullOrBlank()) append("\ntext=").append(text)
    if (!description.isNullOrBlank()) append("\ndesc=").append(description)
    if (!id.isNullOrBlank()) append("\nid=").append(id)
    if (bounds.size == 4) append("\nbounds=").append(bounds.joinToString(","))
    append("\nclickable=").append(clickable)
    append(" enabled=").append(enabled)
    append(" focused=").append(focused)
    append(" scrollable=").append(scrollable)
    append(" password=").append(password)
}
