package com.closecode.ui.ide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlin.math.abs
import kotlinx.coroutines.launch

internal data class IdeRailTool(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
)

internal val IdeRailTools = listOf(
    IdeRailTool("Files", "Files", CcIcons.FolderOpen, Color(0xFF2F6FE0)),
    IdeRailTool("Search", "Search", CcIcons.Search, Color(0xFF2B3036)),
    IdeRailTool("GitHub", "GitHub", CcIcons.Github, Color(0xFF2F6FE0)),
    IdeRailTool("Terminal", "Terminal", CcIcons.Term, Color(0xFF60A5FA)),
    IdeRailTool("Logcat", "Logcat", CcIcons.Log, Color(0xFF34D399)),
    IdeRailTool("Problems", "Problems", CcIcons.Warning, Color(0xFFD97706)),
    IdeRailTool("Preview", "Preview", CcIcons.Preview, Color(0xFFA78BFA)),
    IdeRailTool("OpenCode", "AI", CcIcons.Sparkle, Color(0xFFDB4C9B)),
)

// v7: the full 300 dp sidebar (rail 66 + panel 234) rides behind the editor card, and a
// narrow left edge strip opens it without taking the rest of the screen's scrolls.
private val SidebarWidth = 300.dp

/** How far in from the left edge a pull open may start. A region of the screen, not a tolerance. */
internal val DrawerEdgeBand = 40.dp

/**
 * Whether a gesture belongs to the sidebar. Closed, only a rightward, mostly horizontal drag that
 * starts inside [DrawerEdgeBand] may open it; every other drag stays with the editor or terminal.
 * Once open, a horizontal drag anywhere on the card may still close it.
 */
internal fun shouldClaimSidebarSwipe(
    startXPx: Float,
    edgePx: Float,
    accXPx: Float,
    accYPx: Float,
    slopPx: Float,
    sidebarOpen: Boolean,
): Boolean {
    val pastSlop = abs(accXPx) >= slopPx
    val mostlyHorizontal = abs(accXPx) > 2f * abs(accYPx)
    val allowed = sidebarOpen || startXPx <= edgePx
    val opening = sidebarOpen || accXPx > 0f
    return pastSlop && mostlyHorizontal && allowed && opening
}

/**
 * The editor card sits over the rail + panel. The sidebar is one surface: it never opens on its
 * own, and it only opens from a right swipe that begins in [DrawerEdgeBand].
 */
@Composable
internal fun IdeWorkspace(
    sidebarOpen: Boolean,
    onSidebarOpenChange: (Boolean) -> Unit,
    keyboardVisible: Boolean,
    sidebar: @Composable () -> Unit,
    overlays: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { SidebarWidth.toPx() }
    val edgePx = with(density) { DrawerEdgeBand.toPx() }
    val touchSlopPx = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)
    LaunchedEffect(sidebarOpen) {
        offset.animateTo(if (sidebarOpen) widthPx else 0f, spec)
    }
    val progress = (offset.value / widthPx).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(c.mock.rail)) {
        // Layer 2: rail + panel, permanently behind the editor card.
        Box(Modifier.fillMaxHeight().width(SidebarWidth)) { sidebar() }

        // Layer 1: the editor card. It slides right 300 dp and keeps its left corners rounded.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.value
                    // Only the card's left corners round while it slides; the right edge stays
                    // square (guild SidebarHost, correction 7).
                    shape = RoundedCornerShape(
                        topStart = (26f * progress).dp,
                        bottomStart = (26f * progress).dp,
                    )
                    clip = true
                    shadowElevation = 24f * progress
                }
                .background(c.mock.page)
                .pointerInput(sidebarOpen, keyboardVisible) {
                    if (keyboardVisible) return@pointerInput
                    awaitEachGesture {
                        // The Initial pass sees every touch before the editor does, but only
                        // takes the ones that are the sidebar swipe; everything else falls
                        // through to the editor underneath.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startX = down.position.x
                        val tracker = VelocityTracker()
                        val startOffset = offset.value
                        var dragValue = startOffset
                        var claimed = false
                        var accX = 0f
                        var accY = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.positionChange().x
                            val dy = change.positionChange().y
                            accX += dx
                            accY += dy
                            if (!claimed) {
                                // Correction 3: the swipe is only ours after a 10 dp touch slop,
                                // and then only while it is mostly horizontal.
                                if (shouldClaimSidebarSwipe(startX, edgePx, accX, accY, touchSlopPx, sidebarOpen)) {
                                    claimed = true
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    dragValue = (startOffset + accX).coerceIn(0f, widthPx)
                                    scope.launch { offset.snapTo(dragValue) }
                                }
                            } else {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                dragValue = (dragValue + dx).coerceIn(0f, widthPx)
                                scope.launch { offset.snapTo(dragValue) }
                            }
                        }
                        if (claimed) {
                            val velocity = tracker.calculateVelocity().x
                            val openIt = dragValue / widthPx > 0.4f || velocity > 800f * density.density
                            val closeIt = velocity < -800f * density.density
                            val target = if (closeIt) false else openIt
                            onSidebarOpenChange(target)
                            scope.launch { offset.animateTo(if (target) widthPx else 0f, spec) }
                        } else if (sidebarOpen && abs(accX) < touchSlopPx && abs(accY) < touchSlopPx) {
                            // A tap (not a drag) on the dimmed card closes the sidebar.
                            onSidebarOpenChange(false)
                        }
                    }
                },
        ) {
            content()
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // The theme's own scrim token, scaled by how far the card has slid; a
                        // plain black wash made the light card look dark (v7 correction 7).
                        .background(c.mock.scrim.copy(alpha = c.mock.scrim.alpha * progress))
                        .pointerInput(Unit) { detectTapGestures { onSidebarOpenChange(false) } },
                )
            }
        }
        overlays()
    }
}

@Composable
internal fun IdeRail(
    projectName: String,
    activeTool: String?,
    problemCount: Int,
    changeCount: Int,
    onTool: (String) -> Unit,
    onRun: () -> Unit,
    onSettings: () -> Unit,
    onProjectClick: () -> Unit = {},
    onProjectSettings: () -> Unit = {},
    onDeveloper: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .width(66.dp)
            .fillMaxHeight()
            .background(c.mock.rail)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 14.dp, top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DesignRailAvatar(projectName, onClick = onProjectClick)
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(width = 30.dp, height = 2.dp)
                .background(c.mock.border),
        )
        Spacer(Modifier.size(10.dp))
        IdeRailTools.forEachIndexed { index, tool ->
            if (index > 0) Spacer(Modifier.size(11.dp))
            val badge = when (tool.id) {
                "Problems" -> problemCount
                "GitHub" -> changeCount
                else -> 0
            }
            // Tints come from the theme tokens, so dark mode carries them with it.
            val tint = when (tool.id) {
                "Files" -> c.mock.yellow
                "Search" -> c.mock.blue
                "GitHub" -> c.mock.text2
                "Terminal" -> Color(0xFF60A5FA)
                "Logcat" -> Color(0xFF34D399)
                "Problems" -> c.mock.orange
                "Preview" -> Color(0xFFA78BFA)
                "OpenCode" -> c.mock.pink
                else -> tool.tint
            }
            RailItem(
                label = tool.label,
                icon = tool.icon,
                selected = activeTool == tool.id,
                badge = badge,
                tint = tint,
                onClick = { onTool(tool.id) },
            )
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            label = "Project settings",
            icon = CcIcons.RailSliders,
            selected = false,
            badge = 0,
            tint = Color(0xFFC7CBD9),
            onClick = onProjectSettings,
        )
        Spacer(Modifier.size(11.dp))
        RailItem(
            label = "Developer",
            icon = CcIcons.Wrench,
            selected = false,
            badge = 0,
            tint = Color(0xFF2DD4BF),
            onClick = onDeveloper,
        )
        Spacer(Modifier.size(11.dp))
        RailItem(
            label = "Settings",
            icon = CcIcons.Settings,
            selected = false,
            badge = 0,
            tint = c.mock.textSoft,
            onClick = onSettings,
        )
    }
}

@Composable
private fun DesignRailAvatar(projectName: String, onClick: () -> Unit = {}) {
    val c = LocalPageColors.current
    // The six tile colours the spec assigns projects, derived from the name when no colour is
    // stored with the record.
    val tile = listOf(
        Color(0xFF2563EB),
        Color(0xFF16A34A),
        Color(0xFFEA580C),
        Color(0xFF0E7490),
        Color(0xFF7C3AED),
        Color(0xFFBE185D),
    )[projectName.hashCode().and(0x7FFFFFFF) % 6]
    Box(
        Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = projectName.take(1).uppercase().ifEmpty { "C" },
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        // The switcher affordance: a rail-coloured circle holding a small white chevron.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 0.dp, y = 0.dp)
                .size(16.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(c.mock.rail),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Down, Color.White, 10.dp)
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pillHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (pressed) 8.dp else 30.dp,
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "rail-pill",
    )
    Box(Modifier.size(46.dp)) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-10).dp)
                    .size(width = 5.dp, height = pillHeight)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(Color.White),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (selected) 15.dp else 23.dp))
                .background(if (selected) c.mock.accent else c.mock.panel)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, if (selected) Color.White else tint, 22.dp)
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .defaultMinSize(minWidth = 19.dp, minHeight = 19.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(c.mock.badge)
                    .border(3.dp, c.mock.rail, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
