package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

internal data class IdeRailTool(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

internal val IdeRailTools = listOf(
    IdeRailTool("Files", "Files", CcIcons.FolderOpen),
    IdeRailTool("Search", "Search", CcIcons.Search),
    IdeRailTool("GitHub", "GitHub", CcIcons.Github),
    IdeRailTool("Terminal", "Terminal", CcIcons.Terminal),
    IdeRailTool("Logcat", "Logcat", CcIcons.Bug),
    IdeRailTool("Problems", "Problems", CcIcons.Warning),
    IdeRailTool("OpenCode", "OpenCode", CcIcons.Code),
)

@Composable
internal fun IdeWorkspace(
    projectName: String,
    activeTool: String?,
    problemCount: Int,
    changeCount: Int,
    onTool: (String) -> Unit,
    onRun: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalPageColors.current
    Row(Modifier.fillMaxSize().background(c.mock.shell)) {
        IdeRail(
            projectName = projectName,
            activeTool = activeTool,
            problemCount = problemCount,
            changeCount = changeCount,
            onTool = onTool,
            onRun = onRun,
            onSettings = onSettings,
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize(), content = content)
        }
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
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(c.mock.shell)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DesignRailAvatar(projectName)
        Spacer(Modifier.size(4.dp))
        IdeRailTools.forEach { tool ->
            val badge = when (tool.id) {
                "Problems" -> problemCount
                "GitHub" -> changeCount
                else -> 0
            }
            RailItem(
                label = tool.label,
                icon = tool.icon,
                selected = activeTool == tool.id,
                badge = badge,
                onClick = { onTool(tool.id) },
            )
        }
        Spacer(Modifier.weight(1f))
        RunRailItem(onRun)
        RailItem(
            label = "Settings",
            icon = CcIcons.Sliders,
            selected = false,
            badge = 0,
            onClick = onSettings,
        )
    }
}

@Composable
private fun DesignRailAvatar(projectName: String) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.mock.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = projectName.take(1).uppercase().ifEmpty { "C" },
            color = c.mock.onAccent,
            fontSize = 17.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) c.mock.content else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            CcIcon(icon, if (selected) c.mock.accent else c.mock.muted, 20.dp)
            if (badge > 0) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.mock.badge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 9) "9+" else badge.toString(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = label,
            color = if (selected) c.mock.text else c.mock.faint,
            fontSize = 8.sp,
            fontFamily = Sans,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun RunRailItem(onRun: () -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(c.mock.green)
            .clickable(onClick = onRun)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CcIcon(CcIcons.Play, c.mock.onGreen, 18.dp)
        Text(
            text = "Run",
            color = c.mock.onGreen,
            fontSize = 8.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
