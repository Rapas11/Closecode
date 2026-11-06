package com.closecode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

@Composable
private fun cc() = LocalCcColors.current

/** One settings category offered by the panel (screen 32). */
data class SettingsCategory(
    val title: String,
    val icon: String,
    val tint: Color,
)

/** Full settings page frame: "Settings" origin + page title, scrolls. */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = cc()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.content)
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            origin = "Settings",
            title = title,
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            content = content,
        )
    }
}

/** Group label above a rounded card (radius 18, c.field, margin 12). */
@Composable
fun SetGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = cc()
    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = c.muted,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.field),
            content = content,
        )
    }
}

/** One settings row inside a SetGroup: tile, title, sub, then value / chevron / trailing. */
@Composable
fun Item(
    icon: String,
    tint: Color,
    title: String,
    sub: String? = null,
    subColor: Color? = null,
    value: String? = null,
    mono: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = cc()
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                com.closecode.ui.CcIcon(icon, tint, 18.dp, 1.8f)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = c.text,
                    fontSize = 14.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = subColor ?: c.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        lineHeight = 16.8.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else {
                if (value != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = value,
                        color = c.muted,
                        fontSize = if (mono) 11.sp else 13.sp,
                        fontFamily = if (mono) Mono else Sans,
                        lineHeight = if (mono) 16.sp else 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 130.dp),
                    )
                }
                if (chevron) {
                    Spacer(Modifier.width(4.dp))
                    com.closecode.ui.CcIcon(
                        "cr",
                        if (value != null) c.muted else c.faint,
                        if (value != null) 15.dp else 17.dp,
                        2.2f,
                    )
                }
            }
        }
    }
}

/** Right-side status words: "Ready", "Not installed", "On" (12 sp bold). */
@Composable
fun StatusText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 150.dp),
    )
}

/** Right-side tappable words: "Clear", "Sign out" (12.5 sp bold). */
@Composable
fun ActionText(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.5.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .widthIn(max = 120.dp),
    )
}

/** Screen 32: settings category panel shown inside the sidebar. */
@Composable
fun SettingsPanel(
    appName: String,
    versionLine: String,
    selected: String?,
    onOpen: (SettingsCategory) -> Unit,
) {
    val c = cc()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.panel),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                color = c.text,
                fontSize = 21.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = appName,
            color = c.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        listOf(
            SettingsCategory("General", "cog", c.blue),
            SettingsCategory("Editor", "code", c.purple),
            SettingsCategory("Code Intelligence", "flask", c.yellow),
            SettingsCategory("Build & Toolchain", "play", c.green),
            SettingsCategory("Terminal", "term", c.blue),
            SettingsCategory("Git & GitHub", "gh", c.text2),
            SettingsCategory("Appearance", "moon", c.purple),
            SettingsCategory("Storage", "db", c.orange),
            SettingsCategory("Security", "lock", c.teal),
            SettingsCategory("About", "info", c.faint),
        ).forEach { cat ->
            val on = cat.title == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (on) 8.dp else 0.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) c.accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onOpen(cat) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cat.tint.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    com.closecode.ui.CcIcon(cat.icon, cat.tint, 18.dp, 1.8f)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = cat.title,
                    color = c.text,
                    fontSize = 14.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                com.closecode.ui.CcIcon("cr", c.faint, 15.dp, 2.2f)
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        Text(
            text = versionLine,
            color = c.faint,
            fontSize = 10.5.sp,
            fontFamily = Mono,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp)
                .widthIn(max = 300.dp),
        )
    }
}

/** Render one settings [Row] using the shared Item layout. */
@Composable
fun SettingsRow(
    row: Row,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = cc()
    val icon = rowIcon(row.name)
    val tint = rowTint(row.name, c)

    var trailing: (@Composable () -> Unit)? = null
    if (row is Row.Toggle) {
        val on = row.key?.let { settings.flag(it) } ?: row.default
        trailing = if (row.fixedOn) {
            { StatusText("On", c.green) }
        } else {
            { CcSwitch(on = on, onChange = { next -> onSettingsChange(settings.withFlag(row.key!!, next)) }) }
        }
    } else if (row is Row.Options) {
        val value = if (row.key == null) row.current else settings.option(row.key!!)
        trailing = { ActionText(value + row.unit, c.muted, onClick = { }) }
    } else if (row is Row.Nav) {
        trailing = if (row.action.isNotBlank()) {
            { ActionText(row.action, c.muted) { } }
        } else {
            { }
        }
    } else if (row is Row.Action) {
        trailing = { ActionText(row.action, if (row.danger) c.red else c.accent) { onAction(row.id) } }
    } else if (row is Row.Status) {
        trailing = { StatusText(row.value, if (row.ok) c.green else c.amber) }
    } else if (row is Row.Account) {
        trailing = { ActionText(row.action, c.red) { onAction(SettingsAction.DISCONNECT_GITHUB) } }
    } else if (row is Row.Language) {
        trailing = if (row.ready) {
            { com.closecode.ui.CcIcon("check", c.green, 18.dp) }
        } else {
            { Text("Download", color = c.accent, fontSize = 12.sp, fontFamily = Sans, fontWeight = FontWeight.SemiBold) }
        }
    }

    var onClick: (() -> Unit)? = null
    if (row is Row.Nav) {
        onClick = { if (row.id != null) onAction(row.id) }
    } else if (row is Row.Options) {
        onClick = { }
    } else if (row is Row.Action) {
        onClick = { onAction(row.id) }
    } else if (row is Row.Account) {
        onClick = { onAction(SettingsAction.DISCONNECT_GITHUB) }
    } else if (row is Row.Language) {
        onClick = { if (!row.ready && row.id != null) onAction(row.id) }
    }

    Item(
        icon = icon,
        tint = tint,
        title = row.name,
        sub = row.description,
        value = if (row is Row.Nav) row.action else null,
        mono = false,
        chevron = when (row) {
            is Row.Nav -> row.id != null
            is Row.Options -> true
            is Row.Action -> false
            is Row.Account -> true
            is Row.Language -> !row.ready && row.id != null
            else -> false
        },
        onClick = onClick,
        trailing = trailing,
        modifier = modifier,
    )
}

private fun rowIcon(name: String): String = when (name) {
    "Default project location" -> "folder"
    "Open last project" -> "play"
    "Auto-save" -> "check"
    "Setup guide" -> "flag"
    "Performance overlay" -> "bolt"
    "Font size" -> "code"
    "Word wrap" -> "menu"
    "Show line numbers" -> "check"
    "Auto close brackets" -> "code"
    "Tab size" -> "layers"
    "Suggestions" -> "flask"
    "Show problems" -> "alert"
    "Format on save" -> "code"
    "Kotlin" -> "code"
    "TypeScript" -> "code"
    "HTML and CSS" -> "code"
    "Build tools" -> "check"
    "Shell tools" -> "term"
    "OpenCode runtime" -> "bolt"
    "Default build type" -> "play"
    "Download over" -> "down"
    "Delete build tools" -> "trash"
    "Use pipe terminal" -> "term"
    "Start folder" -> "layers"
    "Manage saved commands" -> "bolt"
    "Author name" -> "user"
    "Author email" -> "file"
    "GitHub account" -> "gh"
    "GitHub" -> "gh"
    "App lock" -> "lock"
    "Send usage data" -> "globe"
    "Saved sign-ins" -> "gh"
    "CloseCode" -> "info"
    "Source code" -> "globe"
    "Licences" -> "file"
    "Projects" -> "folder"
    "Caches" -> "db"
    "Debug log" -> "bug"
    "Self-test" -> "flask"
    "Last crash" -> "alert"
    "Share debug report" -> "share"
    "Clear build cache" -> "refresh"
    "Crash test" -> "alert"
    else -> "file"
}

private fun rowTint(name: String, c: com.closecode.ui.theme.CcColors): Color = when (name) {
    "Default project location" -> c.orange
    "Open last project" -> c.blue
    "Auto-save" -> c.green
    "Setup guide" -> c.purple
    "Performance overlay" -> c.amber
    "Font size" -> c.purple
    "Word wrap" -> c.blue
    "Show line numbers" -> c.teal
    "Auto close brackets" -> c.purple
    "Tab size" -> c.blue
    "Suggestions" -> c.yellow
    "Show problems" -> c.orange
    "Format on save" -> c.purple
    "Kotlin" -> c.purple
    "TypeScript" -> c.blue
    "HTML and CSS" -> c.orange
    "Build tools" -> c.green
    "Shell tools" -> c.blue
    "OpenCode runtime" -> c.purple
    "Default build type" -> c.green
    "Download over" -> c.blue
    "Delete build tools" -> c.red
    "Use pipe terminal" -> c.blue
    "Start folder" -> c.purple
    "Manage saved commands" -> c.yellow
    "Author name" -> c.blue
    "Author email" -> c.teal
    "GitHub account" -> c.text2
    "GitHub" -> c.text2
    "App lock" -> c.purple
    "Send usage data" -> c.blue
    "Saved sign-ins" -> c.text2
    "CloseCode" -> c.blue
    "Source code" -> c.teal
    "Licences" -> c.yellow
    "Projects" -> c.orange
    "Caches" -> c.purple
    "Debug log" -> c.orange
    "Self-test" -> c.purple
    "Last crash" -> c.green
    "Share debug report" -> c.blue
    "Clear build cache" -> c.blue
    "Crash test" -> c.red
    else -> c.muted
}

@Composable
private fun CcSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = cc()
    Box(
        Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) c.accent else c.toggleOff)
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White),
        )
    }
}
