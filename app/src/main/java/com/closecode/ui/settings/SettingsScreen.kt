package com.closecode.ui.settings

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.rememberProjectRepository
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Modifier.bottomRule(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height - 0.5f),
        end = Offset(size.width, size.height - 0.5f),
        strokeWidth = 1f,
    )
}

private fun Modifier.leftRule(color: Color) = drawBehind {
    drawLine(
        color = color,
        start = Offset(0.5f, 0f),
        end = Offset(0.5f, size.height),
        strokeWidth = 1f,
    )
}

/**
 * Reads the three facts the Storage and About rows report, and clears the cache when asked.
 *
 * Every line of it is I/O — a recursive directory walk is the expensive one — so it all sits on
 * [Dispatchers.IO] and the screen renders `…` until an answer comes back.
 */
private class SettingsFacts(
    private val context: Context,
    private val repository: ProjectRepository,
) {
    suspend fun measure(): SettingsLive = withContext(Dispatchers.IO) {
        SettingsLive(
            projectRoot = runCatching { repository.projectsDir().absolutePath }.getOrDefault("—"),
            projectsSize = humanSize(dirSize(repository.projectsDir())),
            cachesSize = humanSize(dirSize(context.cacheDir) + dirSize(context.codeCacheDir)),
            version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "1.0.0",
        )
    }

    /** Only ever touches directories the platform owns; nothing under `filesDir` is touched. */
    suspend fun clearCaches(): SettingsLive {
        withContext(Dispatchers.IO) {
            runCatching { context.cacheDir?.deleteRecursively() }
            runCatching { context.codeCacheDir?.deleteRecursively() }
        }
        return measure()
    }
}

@Composable
fun SettingsScreen(
    activeId: String,
    onActiveChange: (String) -> Unit,
    onBack: () -> Unit,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
) {
    val c = LocalPageColors.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val wide = widthDp >= 760

    val context = LocalContext.current
    val repository = rememberProjectRepository()
    val facts = remember(context, repository) { SettingsFacts(context, repository) }
    val scope = rememberCoroutineScope()
    var live by remember { mutableStateOf(SettingsLive()) }
    LaunchedEffect(facts) { live = facts.measure() }

    val allSections = sections(live)
    val current = allSections.firstOrNull { it.id == activeId } ?: allSections.first()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsHeader(onBack = onBack, wide = wide)

        if (wide) {
            Row(
                Modifier
                    .weight(1f)
                    .widthIn(max = 1080.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SettingsNav(
                    sections = allSections,
                    activeId = current.id,
                    onActiveChange = onActiveChange,
                    wide = true,
                    modifier = Modifier.width(235.dp),
                )
                Spacer(Modifier.width(44.dp))
                SectionContent(
                    section = current,
                    wide = true,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onAction = { action ->
                        if (action == SettingsAction.CLEAR_CACHES) {
                            scope.launch { live = facts.clearCaches() }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                SettingsNav(
                    sections = allSections,
                    activeId = current.id,
                    onActiveChange = onActiveChange,
                    wide = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(28.dp))
                SectionContent(
                    section = current,
                    wide = false,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onAction = { action ->
                        if (action == SettingsAction.CLEAR_CACHES) {
                            scope.launch { live = facts.clearCaches() }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit, wide: Boolean) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .widthIn(max = 1080.dp)
            .fillMaxWidth()
            .height(if (wide) 68.dp else 60.dp)
            .background(c.bg)
            .bottomRule(c.ln)
            .padding(horizontal = if (wide) 22.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (wide) 38.dp else 34.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                color = c.mu,
                fontSize = 26.sp,
                fontFamily = Sans,
            )
        }
        Text(
            text = "Closecode",
            color = c.tx,
            fontSize = 18.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4f).sp,
            modifier = Modifier.padding(start = 10.dp),
        )
        if (wide) {
            Text(
                text = "Settings",
                color = c.mu,
                fontSize = 15.sp,
                fontFamily = Sans,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .leftRule(c.ln)
                    .padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingsNav(
    sections: List<Section>,
    activeId: String,
    onActiveChange: (String) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current

    if (wide) {
        Column(modifier.padding(top = 42.dp)) {
            Text(
                text = "SETTINGS",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                sections.forEach { section ->
                    NavItem(
                        section = section,
                        active = section.id == activeId,
                        onClick = { onActiveChange(section.id) },
                        wide = true,
                    )
                }
            }
        }
    } else {
        Column(modifier.padding(top = 10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                sections.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pair.forEach { section ->
                            NavItem(
                                section = section,
                                active = section.id == activeId,
                                onClick = { onActiveChange(section.id) },
                                wide = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    section: Section,
    active: Boolean,
    onClick: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val pad = if (wide) 12.dp else 10.dp
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) c.ac.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = pad, vertical = pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.icon,
            color = if (active) c.ac else c.mu,
            fontSize = 15.sp,
            fontFamily = Sans,
            modifier = Modifier.width(19.dp),
        )
        Text(
            text = section.label,
            color = if (active) c.ac else c.mu,
            fontSize = if (wide) 14.sp else 12.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (wide) 11.dp else 9.dp),
        )
    }
}

@Composable
private fun SectionContent(
    section: Section,
    wide: Boolean,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val narrow = LocalConfiguration.current.screenWidthDp < 430

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = if (wide) 42.dp else 26.dp,
            bottom = if (wide) 70.dp else 50.dp,
        ),
    ) {
        item(key = "heading") {
            Column(Modifier.padding(bottom = if (narrow) 24.dp else 30.dp)) {
                Text(
                    text = section.title,
                    color = c.tx,
                    fontSize = if (wide) 30.sp else 27.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1f).sp,
                )
                Text(
                    text = section.blurb,
                    color = c.mu,
                    fontSize = 14.sp,
                    lineHeight = 22.4.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        section.groups.forEachIndexed { index, group ->
            if (group.title.isNotEmpty()) {
                item(key = "gt-$index") {
                    Text(
                        text = group.title,
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            item(key = "g-$index") {
                Card(
                    rows = group.rows,
                    wide = wide,
                    narrow = narrow,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onAction = onAction,
                )
            }
            if (index != section.groups.lastIndex) {
                item(key = "gs-$index") { Spacer(Modifier.height(34.dp)) }
            }
        }
    }
}

@Composable
private fun Card(
    rows: List<Row>,
    wide: Boolean,
    narrow: Boolean,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(13.dp))
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.ln)
                )
            }
            SettingRow(
                row = row,
                wide = wide,
                narrow = narrow,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun SettingRow(
    row: Row,
    wide: Boolean,
    narrow: Boolean,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val c = LocalPageColors.current
    val minHeight = if (narrow) 62.dp else 67.dp
    val pad = if (wide) 17.dp else 14.dp

    Row(
        Modifier
            .fillMaxWidth()
            .height(minHeight)
            .padding(horizontal = pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            val description = row.description
            if (description != null && !narrow) {
                Text(
                    text = description,
                    color = c.mu,
                    fontSize = 12.sp,
                    lineHeight = 17.4.sp,
                    fontFamily = Sans,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .widthIn(max = if (wide) Dp.Unspecified else 220.dp),
                )
            }
        }

        Spacer(Modifier.width(20.dp))
        SettingControl(
            row = row,
            wide = wide,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onAction = onAction,
        )
    }
}

@Composable
private fun SettingControl(
    row: Row,
    wide: Boolean,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val c = LocalPageColors.current
    val valueSize = if (wide) 13.sp else 12.sp

    when (row) {
        is Row.Toggle -> SwitchControl(
            on = settings.flag(row.key),
            onChange = { onSettingsChange(settings.withFlag(row.key, it)) },
        )

        is Row.Options -> OptionsControl(
            choices = row.choices,
            current = settings.option(row.key),
            unit = row.unit,
            onSelect = { onSettingsChange(settings.withOption(row.key, it)) },
            valueSize = valueSize,
        )

        is Row.Value -> Text(
            text = row.value,
            color = c.mu,
            fontSize = valueSize,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        is Row.Action -> Text(
            text = row.action,
            color = if (row.danger) c.red else c.mu,
            fontSize = valueSize,
            fontFamily = Sans,
            fontWeight = if (row.danger) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.clickable { onAction(row.id) },
        )
    }
}

@Composable
private fun SwitchControl(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPageColors.current
    val track by animateColorAsState(
        targetValue = if (on) c.ac else c.panel3,
        animationSpec = tween(200),
        label = "switch",
    )
    Box(
        Modifier
            .width(43.dp)
            .height(25.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(track)
            .clickable { onChange(!on) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .size(19.dp)
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (on) Color.White else c.tx, RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun OptionsControl(
    choices: List<String>,
    current: String,
    unit: String,
    onSelect: (String) -> Unit,
    valueSize: androidx.compose.ui.unit.TextUnit,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.panel2)
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(start = 11.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current + unit,
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Sans,
                maxLines = 1,
            )
            Text(
                text = "▾",
                color = c.mu,
                fontSize = 10.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = choice + unit,
                            color = if (choice == current) c.ac else c.tx,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = if (choice == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}
