package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcShape
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/** Screen 09: the sliding editor card + sidebar shell.
 *
 * The editor card slides 300 dp to the right, revealing the 66 dp tool rail and 234 dp
 * files panel underneath. A dim overlays the card at full progress.
 *
 * [open] is owned by the caller. [keyboardVisible] turns gestures off.
 * [canScrollLeft] tells the host whether the code is scrolled to its left edge.
 */
@Composable
fun SidebarHost(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    keyboardVisible: Boolean,
    canScrollLeft: () -> Boolean,
    sidebar: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slidePx = with(density) { 300.dp.toPx() }

    Row(Modifier.fillMaxSize()) {
        sidebar()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (open) slidePx else 0f
                }
                .clip(RoundedCornerShape(if (open) 26f else 0f)),
            contentAlignment = Alignment.Center,
        ) {
            card()
        }
    }
}

/** Tool rail: 66 dp wide. Avatar on top, six tool tiles, Settings at the bottom. */
@Composable
fun ToolRail(
    projectName: String,
    selectedTool: String,
    onTool: (String) -> Unit,
    githubCount: Int = 0,
    problemsCount: Int = 0,
) {
    val c = CcTheme.colors
    Column(
        Modifier
            .width(66.dp)
            .fillMaxHeight()
            .background(c.rail),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar tile: 46 x 46, radius 15, with a 16 dp ring badge showing a chevron.
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(c.panel)
                .padding(10.dp)
                .clickable(onClick = { onTool("avatar") }),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("cd", Color.White, 10.dp, 2.6f)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Divider: 30 x 2.
        Box(Modifier.width(30.dp).height(2.dp).background(c.divider))
        Spacer(Modifier.height(6.dp))

        // Tool tiles: 46 x 46, radius 23 (selected 15), gap 11, icons 22 dp stroke 1.8.
        Column(
            Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tools = listOf(
                Triple("files", c.yellow, 0),
                Triple("search", c.blue, 0),
                Triple("github", c.text2, githubCount),
                Triple("terminal", c.blue, 0),
                Triple("logcat", c.green, 0),
                Triple("problems", c.orange, problemsCount),
            )
            tools.forEachIndexed { index, (tool, colour, badgeCount) ->
                val selected = selectedTool == tool
                val hasBadge = badgeCount > 0 && (tool == "github" || tool == "problems")
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(if (selected) 15.dp else 23.dp))
                        .background(if (selected) c.accent else Color.Transparent)
                        .clickable(onClick = { onTool(tool) }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasBadge) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp)
                                .size(19.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.red),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = badgeCount.coerceAtMost(9).toString(),
                                style = CcType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                    CcIcon(tool, if (selected) Color.White else colour, 22.dp, 1.8f)
                }
                if (index < tools.size - 1) {
                    Spacer(Modifier.height(11.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Settings cog at bottom.
        Box(
            Modifier
                .size(46.dp)
                .padding(bottom = 10.dp)
                .clickable(onClick = { onTool("settings") }),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon("cog", c.textSoft, 22.dp, 1.8f)
        }
    }
}

/** Side panel: 234 dp wide, bg c.panel, top-left radius 26. */
@Composable
fun SidePanel(
    projectName: String,
    content: @Composable () -> Unit,
) {
    val c = CcTheme.colors
    Column(
        Modifier
            .width(234.dp)
            .fillMaxHeight()
            .background(c.panel)
            .clip(CcShape.panelTopLeft),
    ) {
        Column(
            Modifier
                .padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 6.dp)
                .fillMaxWidth(),
        ) {
            Text(
                "Files",
                style = CcType.panelTitle,
                color = c.text,
            )
            Text(
                projectName,
                modifier = Modifier.padding(top = 2.dp),
                style = CcType.caption.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }
        content()
    }
}

/** Run card: floats at bottom of panel, 64 dp high, radius 22. */
@Composable
fun RunCard(onRun: () -> Unit) {
    val c = CcTheme.colors
    Box(
        Modifier
            .width(212.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.field)
            .clickable(onClick = onRun),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon("play", Color.White, 17.dp)
    }
}
