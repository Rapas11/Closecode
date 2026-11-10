package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions






import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.ProjectTemplate
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons

private val ColumnGap = 12.dp
private const val MIN_TILE = 140

@Composable
fun TemplateCard(
    template: ProjectTemplate,
    selected: Boolean,
    tab: TemplateTab,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(18.dp)
    val isWeb = tab == TemplateTab.Web

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(c.mock.content)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) c.mock.accent else Color.Transparent,
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(13.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        Color.hsl(template.hue, 0.65f, 0.55f, 0.18f),
                        RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isWeb) {
                    WebGlyph(color = Color.hsl(template.hue, 0.65f, 0.48f))
                } else {
                    PhoneGlyph(color = Color.hsl(template.hue, 0.65f, 0.48f))
                }
            }
            Text(
                text = template.name,
                color = c.mock.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = template.description,
                color = c.mock.muted,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                template.tags.split("·").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.mock.field)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = tag,
                            color = c.mock.textSoft,
                            fontSize = 10.5.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = (10).dp)
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(c.mock.accent)
                    .semantics { contentDescription = "Selected" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Check, c.mock.onAccent, 13.dp)
            }
        }
    }
}

@Composable
fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) c.mock.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (active) c.mock.accent else c.mock.muted,
            fontSize = 14.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SegmentedTabs(
    tab: TemplateTab,
    onSelect: (TemplateTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.mock.track)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TemplateTab.entries.forEach { entry ->
            val active = entry == tab
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) c.mock.accent else Color.Transparent)
                    .clickable { onSelect(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    color = if (active) c.mock.onAccent else c.mock.textSoft,
                    fontSize = 13.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun RecentHeader(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RECENT",
            color = c.mock.muted.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count projects",
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
        )
        ArrowGlyph(color = c.mock.muted, size = 14.dp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun TemplateGrid(
    tab: TemplateTab,
    selected: SelectedTemplate?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecent: (() -> Unit)? = null,
    recentCount: Int = 0,
) {
    val items = Templates[tab].orEmpty()

    BoxWithConstraints(modifier) {
        val available = maxWidth - 44.dp
        val span = ((available + ColumnGap) / (MIN_TILE.dp + ColumnGap))
            .toInt()
            .coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(span),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            verticalArrangement = Arrangement.spacedBy(ColumnGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onOpenRecent != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                    RecentHeader(count = recentCount, onClick = onOpenRecent)
                }
            }
            itemsIndexed(items) { index, template ->
                TemplateCard(
                    template = template,
                    selected = selected != null && selected.tab == tab && selected.index == index,
                    tab = tab,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
fun SheetFooter(
    name: String,
    onNameChange: (String) -> Unit,
    canCreate: Boolean,
    hint: String,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    actionLabel: String = "Create project",
    showNameField: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val fieldShape = RoundedCornerShape(12.dp)
    val buttonShape = RoundedCornerShape(12.dp)

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = c.mock.border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .background(c.mock.panel)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showNameField) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                    .clip(fieldShape)
                    .background(c.mock.field)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = "Project name",
                        color = c.mock.faint,
                        fontSize = 16.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = c.mock.text,
                        fontSize = 16.sp,
                        fontFamily = Sans,
                    ),
                    cursorBrush = SolidColor(c.mock.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (secondaryLabel != null && onSecondary != null) {
                Row(
                    Modifier
                        .height(46.dp)
                        .clip(buttonShape)
                        .border(1.dp, c.mock.border, buttonShape)
                        .clickable(onClick = onSecondary)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = secondaryLabel,
                        color = c.mock.text,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canCreate) c.mock.accent else c.mock.card
                    )
                    .clickable(enabled = canCreate, onClick = onCreate)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                PlusGlyph(color = if (canCreate) c.mock.onAccent else c.mock.faint)
                Text(
                    text = actionLabel,
                    color = if (canCreate) c.mock.onAccent else c.mock.faint,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Text(
            text = hint,
            color = c.mock.muted,
            fontSize = 13.5.sp,
            fontFamily = Sans,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                color = c.mock.danger,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SheetHeader(
    onClose: () -> Unit,
    title: String = "Start a project",
    subtitle: String = "Pick a template to begin. You can change anything later.",
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.mock.text,
                fontSize = 22.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 27.sp,
            )
            Text(
                text = subtitle,
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(34.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            CrossGlyph(color = c.mock.muted)
        }
    }
}