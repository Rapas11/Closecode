package com.closecode.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.DesignBackButton
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.theme.DarkColors
import com.closecode.ui.theme.DarkMockColors
import com.closecode.ui.theme.LightColors
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

private enum class Tone { Blue, Purple, Green, Amber, Red, Pink, Orange, Gray }

private data class RowVisual(val icon: ImageVector, val tone: Tone)

private fun sectionIcon(id: String): ImageVector = when (id) {
    "general" -> CcIcons.Sliders
    "editor" -> CcIcons.Code
    "intelligence" -> CcIcons.Bulb
    "build" -> CcIcons.Play
    "terminal" -> CcIcons.Terminal
    "git" -> CcIcons.Github
    "ai" -> CcIcons.Sparkle
    "appearance" -> CcIcons.Moon
    "storage" -> CcIcons.Archive
    "security" -> CcIcons.Lock
    else -> CcIcons.Book
}

private fun sectionTone(id: String): Tone = when (id) {
    "general" -> Tone.Gray
    "editor" -> Tone.Purple
    "intelligence" -> Tone.Amber
    "build" -> Tone.Green
    "terminal" -> Tone.Blue
    "git" -> Tone.Gray
    "ai" -> Tone.Pink
    "appearance" -> Tone.Purple
    "storage" -> Tone.Orange
    "security" -> Tone.Green
    else -> Tone.Gray
}

private fun rowVisual(sectionId: String, rowName: String): RowVisual {
    val named = when (rowName) {
        "Default project location" -> RowVisual(CcIcons.FolderOpen, Tone.Amber)
        "Open last project" -> RowVisual(CcIcons.Play, Tone.Blue)
        "Auto-save" -> RowVisual(CcIcons.Zap, Tone.Amber)
        "Setup guide" -> RowVisual(CcIcons.Flag, Tone.Purple)
        "Performance overlay" -> RowVisual(CcIcons.Zap, Tone.Amber)
        "Font size", "Code size" ->
            if (sectionId == "terminal") RowVisual(CcIcons.Code, Tone.Green) else RowVisual(CcIcons.Code, Tone.Purple)
        "Word wrap" -> RowVisual(CcIcons.Document, Tone.Blue)
        "Show line numbers" -> RowVisual(CcIcons.Check, Tone.Green)
        "Auto close brackets", "Format on save" -> RowVisual(CcIcons.Code, Tone.Purple)
        "Tab size" -> RowVisual(CcIcons.Layers, Tone.Blue)
        "Suggestions" -> RowVisual(CcIcons.Sparkle, Tone.Amber)
        "Show problems" -> RowVisual(CcIcons.Warning, Tone.Amber)
        "Kotlin" -> RowVisual(CcIcons.Code, Tone.Purple)
        "TypeScript" -> RowVisual(CcIcons.Code, Tone.Blue)
        "HTML and CSS" -> RowVisual(CcIcons.Code, Tone.Orange)
        "Build tools" -> RowVisual(CcIcons.Check, Tone.Green)
        "Shell tools" -> RowVisual(CcIcons.Terminal, Tone.Blue)
        "OpenCode runtime" -> RowVisual(CcIcons.Zap, Tone.Purple)
        "Default build type" -> RowVisual(CcIcons.Play, Tone.Green)
        "Download over" -> RowVisual(CcIcons.Download, Tone.Blue)
        "Delete build tools" -> RowVisual(CcIcons.Trash, Tone.Red)
        "Use pipe terminal" -> RowVisual(CcIcons.Terminal, Tone.Blue)
        "Start folder" -> RowVisual(CcIcons.Layers, Tone.Purple)
        "Manage saved commands" -> RowVisual(CcIcons.Zap, Tone.Amber)
        "Author name" -> RowVisual(CcIcons.Document, Tone.Blue)
        "Author email" -> RowVisual(CcIcons.Document, Tone.Green)
        "Provider key" -> RowVisual(CcIcons.Lock, Tone.Purple)
        "Update runtime" -> RowVisual(CcIcons.Download, Tone.Blue)
        "Code font" -> RowVisual(CcIcons.Code, Tone.Purple)
        "Syntax colors" -> RowVisual(CcIcons.Layers, Tone.Green)
        "Projects" -> RowVisual(CcIcons.FolderOpen, Tone.Amber)
        "Caches" -> RowVisual(CcIcons.Archive, Tone.Purple)
        "App lock" -> RowVisual(CcIcons.Lock, Tone.Purple)
        "Send usage data" -> RowVisual(CcIcons.AlertCircle, Tone.Blue)
        "Saved sign-ins" -> RowVisual(CcIcons.Github, Tone.Gray)
        "CloseCode" -> RowVisual(CcIcons.Book, Tone.Blue)
        "Source code" -> RowVisual(CcIcons.Github, Tone.Green)
        "Licences" -> RowVisual(CcIcons.Book, Tone.Amber)
        else -> null
    }
    return named ?: RowVisual(sectionIcon(sectionId), sectionTone(sectionId))
}

private fun toneBackground(tone: Tone, c: com.closecode.ui.theme.PageColors): Color = when (tone) {
    Tone.Blue -> c.mock.blueBg
    Tone.Purple -> c.mock.purpleBg
    Tone.Green -> c.mock.successBg
    Tone.Amber -> c.mock.warningBg
    Tone.Red -> c.mock.dangerBg
    Tone.Pink -> c.mock.pinkBg
    Tone.Orange -> c.mock.orangeBg
    Tone.Gray -> c.mock.grayBg
}

private fun toneForeground(tone: Tone, c: com.closecode.ui.theme.PageColors): Color = when (tone) {
    Tone.Blue -> c.mock.blue
    Tone.Purple -> c.mock.purple
    Tone.Green -> c.mock.green
    Tone.Amber -> c.mock.warning
    Tone.Red -> c.mock.danger
    Tone.Pink -> c.mock.pink
    Tone.Orange -> c.mock.orange
    Tone.Gray -> c.mock.gray
}

@Composable
private fun TintedTile(icon: ImageVector, tone: Tone, size: Dp = 36.dp) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(toneBackground(tone, c)),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, toneForeground(tone, c), size * 0.52f)
    }
}

@Composable
private fun AccountTile(initials: String, size: Dp) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.mock.grayBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(1).uppercase(),
            color = c.mock.text,
            fontSize = (size.value * 0.4f).sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun SettingsCategoryList(
    sections: List<Section>,
    activeId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    footer: String,
    onDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var footerTaps by remember { mutableIntStateOf(0) }
    Column(
        modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesignBackButton(onClick = onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    color = c.mock.text,
                    fontSize = 20.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Closecode",
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
        ) {
            item(key = "categories") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.mock.card),
                ) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 54.dp)
                                    .height(1.dp)
                                    .background(c.mock.border.copy(alpha = 0.55f)),
                            )
                        }
                        CategoryRow(
                            section = section,
                            active = section.id == activeId,
                            onClick = { onSelect(section.id) },
                        )
                    }
                }
            }
        }
        Text(
            text = footer,
            color = c.mock.faint,
            fontSize = 10.5.sp,
            fontFamily = Mono,
            lineHeight = 14.sp,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .clickable {
                    footerTaps++
                    if (footerTaps >= 7) {
                        footerTaps = 0
                        onDeveloper()
                    }
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun CategoryRow(section: Section, active: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) c.mock.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TintedTile(sectionIcon(section.id), sectionTone(section.id))
        Text(
            text = section.label,
            color = c.mock.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CcIcon(CcIcons.ChevronRight, c.mock.faint, 16.dp)
    }
}

@Composable
internal fun SettingsDetailPage(
    section: Section,
    settings: Settings,
    onBack: () -> Unit,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var appLockNotice by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(
        modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesignBackButton(onClick = onBack)
            Text(
                text = "Settings",
                color = c.mock.text,
                fontSize = 16.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = section.label,
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
        ) {
            section.groups.forEachIndexed { index, group ->
                if (group.title.isNotBlank()) {
                    item(key = "gt-$index") {
                        Text(
                            text = group.title.uppercase(),
                            color = c.mock.faint,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
                        )
                    }
                }
                item(key = "g-$index") {
                    SettingsGroupCard(
                        sectionId = section.id,
                        rows = group.rows,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onAction = onAction,
                        onAppLock = { appLockNotice = true },
                    )
                }
            }
        }
    }
    if (appLockNotice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { appLockNotice = false },
            title = { Text("App lock") },
            text = {
                Text(
                    "App lock is not enforced by this build yet. The switch stays off until the " +
                        "device-lock check is wired into app start.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { appLockNotice = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun SettingsGroupCard(
    sectionId: String,
    rows: List<Row>,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    onAppLock: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.mock.field),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0 && row !is Row.CodePreview && rows[index - 1] !is Row.CodePreview) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 54.dp)
                        .height(1.dp)
                        .background(c.mock.divider),
                )
            }
            SettingsRowItem(
                sectionId = sectionId,
                row = row,
                settings = settings,
                onSettingsChange = onSettingsChange,
                onAction = onAction,
                onAppLock = onAppLock,
            )
        }
    }
}

@Composable
private fun SettingsRowItem(
    sectionId: String,
    row: Row,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    onAppLock: () -> Unit,
) {
    val c = LocalPageColors.current
    when (row) {
        is Row.Themes -> ThemeCards(row.current) { picked ->
            onSettingsChange(settings.withOption(OptionSetting.THEME, picked))
        }
        is Row.Accents -> AccentSwatches(row.selected) { index ->
            onSettingsChange(settings.copy(accentIndex = index))
        }
        is Row.CodePreview -> CodePreviewCard(settings)
        else -> {
            var menuOpen by remember(row.name) { mutableStateOf(false) }
            val clickable = when (row) {
                is Row.Nav -> row.id != null
                is Row.Options -> true
                is Row.Action -> true
                is Row.Account -> true
                is Row.Language -> !row.ready && row.id != null
                else -> false
            }
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = clickable) {
                            when (row) {
                                is Row.Nav -> row.id?.let(onAction)
                                is Row.Options -> menuOpen = true
                                is Row.Action -> onAction(row.id)
                                is Row.Account -> onAction(SettingsAction.DISCONNECT_GITHUB)
                                is Row.Language -> if (!row.ready) row.id?.let(onAction)
                                else -> Unit
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val visual = rowVisual(sectionId, row.name)
                    if (row is Row.Account) {
                        AccountTile(row.initials, 30.dp)
                    } else {
                        TintedTile(visual.icon, visual.tone, 30.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.name,
                            color = c.mock.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val description = row.description
                        if (!description.isNullOrBlank()) {
                            Text(
                                text = description,
                                color = c.mock.muted,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    RowControl(
                        row = row,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onAppLock = onAppLock,
                    )
                }
                if (row is Row.Options) {
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        val key = row.key
                        val current = if (key == null) row.current else settings.option(key)
                        row.choices.forEach { choice ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = optionLabel(choice, choice == current, row.unit),
                                        color = if (choice == current) {
                                            c.mock.accent
                                        } else {
                                            c.mock.text
                                        },
                                        fontSize = 13.sp,
                                        fontFamily = Sans,
                                        fontWeight = if (choice == current) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    row.key?.let { key ->
                                        onSettingsChange(settings.withOption(key, choice))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowControl(
    row: Row,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAppLock: () -> Unit,
) {
    val c = LocalPageColors.current
    when (row) {
        is Row.Toggle -> {
            if (row.fixedOn) {
                Text(
                    text = "On",
                    color = c.mock.green,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                val key = row.key
                SettingsSwitch(
                    on = key?.let { settings.flag(it) } ?: row.default,
                    onChange = { next ->
                        when (key) {
                            BoolSetting.APP_LOCK -> if (next) {
                                onAppLock()
                            } else {
                                onSettingsChange(settings.withFlag(key, false))
                            }
                            null -> Unit
                            else -> onSettingsChange(settings.withFlag(key, next))
                        }
                    },
                )
            }
        }
        is Row.Options -> {
            val key = row.key
            val value = if (key == null) row.current else settings.option(key)
            Text(
                text = value + row.unit,
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            Spacer(Modifier.width(5.dp))
            CcIcon(CcIcons.ChevronRight, c.mock.faint, 15.dp)
        }
        is Row.Nav -> {
            if (row.action.isNotEmpty()) {
                Text(
                    text = row.action,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            CcIcon(CcIcons.ChevronRight, c.mock.faint, 15.dp)
        }
        is Row.Value -> Text(
            text = row.value,
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 210.dp),
        )
        is Row.Status -> Text(
            text = row.value,
            color = if (row.ok) c.mock.green else c.mock.warning,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        is Row.Account -> Text(
            text = row.action,
            color = c.mock.danger,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        is Row.Language -> {
            if (row.ready) {
                CcIcon(CcIcons.Check, c.mock.green, 18.dp)
            } else {
                Text(
                    text = "Download",
                    color = c.mock.accent,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        is Row.Action -> Text(
            text = row.action,
            color = if (row.danger) c.mock.danger else c.mock.accent,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        is Row.Themes, is Row.Accents, is Row.CodePreview -> Unit
    }
}

@Composable
private fun SettingsSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(if (on) c.mock.accent else c.mock.grayBg)
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(c.mock.onAccent),
        )
    }
}

@Composable
private fun ThemeCards(current: String, onSelect: (String) -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("Light", "Dark", "System").forEach { name ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            when (name) {
                                "Light" -> LightColors.mock.page
                                "Dark" -> DarkMockColors.card
                                else -> c.mock.cardMuted
                            },
                        )
                        .then(
                            if (name == current) {
                                Modifier.border(2.dp, c.mock.accent, RoundedCornerShape(9.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(name) },
                ) {
                    if (name == "System") {
                        Row(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(LightColors.mock.page),
                            ) { MiniThemeLines(LightColors.mock.text, LightColors.mock.muted) }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(DarkMockColors.card),
                            ) { MiniThemeLines(DarkMockColors.text, DarkMockColors.muted) }
                        }
                    } else {
                        MiniThemeLines(
                            if (name == "Light") LightColors.mock.text else DarkMockColors.text,
                            if (name == "Light") LightColors.mock.muted else DarkMockColors.muted,
                        )
                    }
                }
                Text(
                    text = name,
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniThemeLines(text: Color, muted: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 9.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.58f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(text.copy(alpha = 0.85f)))
        Box(Modifier.fillMaxWidth(0.9f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.65f)))
        Box(Modifier.fillMaxWidth(0.75f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(0.42f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.4f)))
    }
}

@Composable
private fun AccentSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentOptions.forEachIndexed { index, option ->
            val chosen = index == selected
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(option)
                    .then(
                        if (chosen) Modifier.border(2.dp, c.mock.text, CircleShape) else Modifier,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) {
                    CcIcon(
                        CcIcons.Check,
                        if (option.luminance() > 0.6f) c.mock.text else c.mock.onAccent,
                        14.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodePreviewCard(settings: Settings) {
    val c = LocalPageColors.current
    val palette = if (settings.syntaxColors == "Default dark") DarkColors else LightColors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.mock.cardMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "object CalcColors {",
            color = palette.keyword,
            fontSize = 10.5.sp,
            fontFamily = Mono,
            lineHeight = 15.sp,
        )
        Text(
            text = "    val Background = Color(0xFFF8F114)",
            color = palette.string,
            fontSize = 10.5.sp,
            fontFamily = Mono,
            lineHeight = 15.sp,
        )
        Text(
            text = "}",
            color = palette.tx,
            fontSize = 10.5.sp,
            fontFamily = Mono,
            lineHeight = 15.sp,
        )
    }
}
