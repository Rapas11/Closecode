package com.closecode.ui.settings

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

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

@Composable
fun SettingsScreen(
    activeId: String,
    onActiveChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalPageColors.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val wide = widthDp >= 760

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
                    activeId = activeId,
                    onActiveChange = onActiveChange,
                    wide = true,
                    modifier = Modifier.width(235.dp),
                )
                Spacer(Modifier.width(44.dp))
                SectionContent(
                    section = sectionById(activeId),
                    wide = true,
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
                    activeId = activeId,
                    onActiveChange = onActiveChange,
                    wide = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(28.dp))
                SectionContent(
                    section = sectionById(activeId),
                    wide = false,
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
                Sections.forEach { section ->
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
                Sections.chunked(2).forEach { pair ->
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
                Card(rows = group.rows, wide = wide, narrow = narrow)
            }
            if (index != section.groups.lastIndex) {
                item(key = "gs-$index") { Spacer(Modifier.height(34.dp)) }
            }
        }
    }
}

@Composable
private fun Card(rows: List<Row>, wide: Boolean, narrow: Boolean) {
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
            SettingRow(row, wide = wide, narrow = narrow)
        }
    }
}

@Composable
private fun SettingRow(row: Row, wide: Boolean, narrow: Boolean) {
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
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (row) {
                is Row.Account -> AccountInfo(row, wide)
                else -> {
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
                }
            }
        }

        Spacer(Modifier.width(20.dp))
        SettingControl(row, wide)
    }
}

@Composable
private fun SettingControl(row: Row, wide: Boolean) {
    val c = LocalPageColors.current
    val valueSize = if (wide) 13.sp else 12.sp

    when (row) {
        is Row.Toggle -> SwitchControl(default = row.default)

        is Row.Value -> Text(
            text = row.value,
            color = c.mu,
            fontSize = valueSize,
            fontFamily = Sans,
            maxLines = 1,
        )

        is Row.Nav -> NavControl(row.action, valueSize)

        is Row.Status -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (row.ok) c.string else c.ac, RoundedCornerShape(4.dp))
            )
            Text(
                text = row.value,
                color = c.mu.copy(alpha = 0.9f),
                fontSize = valueSize,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 7.dp),
            )
        }

        is Row.Options -> Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.panel2)
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
                .padding(start = 11.dp, end = 32.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.current,
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Sans,
            )
        }

        is Row.Accents -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AccentOptions.forEachIndexed { index, color ->
                val selected = index == 0
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) c.tx else Color.Transparent)
                        .padding(if (selected) 2.dp else 3.dp)
                        .clip(RoundedCornerShape(if (selected) 9.dp else 8.dp))
                        .background(color)
                )
            }
        }

        is Row.Action -> Text(
            text = row.action,
            color = if (row.danger) c.red else c.mu,
            fontSize = valueSize,
            fontFamily = Sans,
            fontWeight = if (row.danger) FontWeight.Medium else FontWeight.Normal,
        )

        is Row.Account -> NavControl(row.action, valueSize)
    }
}

@Composable
private fun AccountInfo(row: Row.Account, wide: Boolean) {
    val c = LocalPageColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.ac.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.initials,
                color = c.ac,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.padding(start = if (wide) 13.dp else 11.dp)) {
            Text(
                text = row.name,
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.subtitle,
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SwitchControl(default: Boolean) {
    val c = LocalPageColors.current
    var on by remember { mutableStateOf(default) }
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
            .clickable { on = !on }
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
private fun NavControl(action: String, valueSize: androidx.compose.ui.unit.TextUnit) {
    val c = LocalPageColors.current
    Row(Modifier.clickable { }, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = action,
            color = c.mu,
            fontSize = valueSize,
            fontFamily = Sans,
        )
        Text(
            text = "›",
            color = c.mu.copy(alpha = 0.65f),
            fontSize = 18.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}