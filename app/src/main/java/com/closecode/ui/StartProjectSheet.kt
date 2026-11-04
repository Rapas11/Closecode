package com.closecode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.ConfigForm
import com.closecode.ui.components.EmptyState
import com.closecode.ui.components.OneLine
import com.closecode.ui.components.SheetFooter
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.model.defaultPackageName
import com.closecode.ui.theme.CcShape
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType
import com.closecode.ui.theme.tint

private enum class Step { Pick, Config }

/**
 * Screen 02 — Start a project. Tall bottom sheet over the welcome screen.
 *
 * The picked template flows straight into the project config step (the project's
 * package name and toolchain), then creates the project.
 */
@Composable
fun StartProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, selection: SelectedTemplate, config: ProjectConfig) -> String?,
) {
    val c = CcTheme.colors

    var step by rememberSaveable { mutableStateOf(Step.Pick) }
    var tab by rememberSaveable { mutableStateOf(TemplateTab.Web) }
    // -1 so nothing is selected until the user picks a card (the first template of a tab is
    //    selected by default; the card can be toggled to another).
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(Templates[TemplateTab.Web].orEmpty().first().name) }
    var configEncoded by rememberSaveable { mutableStateOf(ProjectConfig().encode()) }
    var createError by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val currentStep = step
    val config = ProjectConfig.decode(configEncoded)

    val selected = remember(selectedIndex, tab) {
        if (selectedIndex >= 0) SelectedTemplate(tab, selectedIndex) else null
    }
    val current = selected?.let { Templates[it.tab].orEmpty().getOrNull(it.index) }
    val canCreate = selected != null && name.isNotBlank() && createError == null && current != null

    SheetLayer(topOffset = 90.dp, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                TextN(
                    "Start a project",
                    Modifier,
                    CcType.sheetTitle.copy(fontSize = 22.sp),
                    c.text,
                )
                TextN(
                    "Pick a template. You can change anything later.",
                    Modifier.padding(top = 3.dp),
                    CcType.small,
                    c.muted,
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("x", c.textSoft, 22.dp, 1.8f)
            }
        }

        // Back closes the sheet (the Welcome layer is underneath); from the config
        // step it returns to the template picker first.
        BackHandler {
            if (step == Step.Config) step = Step.Pick else onDismiss()
        }

        if (currentStep == Step.Pick) {
            // Two-part switch, margin top 16, bottom 14.
            Row(
                Modifier
                    .padding(top = 16.dp, bottom = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.rail)
                    .padding(3.dp),
            ) {
                TemplateTab.entries.forEachIndexed { i, entry ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (i == TemplateTab.entries.indexOf(tab)) c.accent else Color.Transparent)
                            .clickable {
                                if (tab != entry) {
                                    tab = entry
                                    // Each tab starts on its own first template.
                                    selectedIndex = 0
                                    createError = null
                                    val first = Templates[entry].orEmpty().firstOrNull()
                                    if (first != null &&
                                        (name.isBlank() || Templates.values.flatten().any { it.name == name })
                                    ) {
                                        name = first.name
                                        configEncoded = ProjectConfig(
                                            packageName = defaultPackageName(first.name),
                                        ).encode()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        OneLine(
                            entry.label,
                            CcType.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            if (i == TemplateTab.entries.indexOf(tab)) Color.White else c.muted,
                        )
                    }
                }
            }

            val items = Templates[tab].orEmpty()
            if (items.isEmpty()) {
                EmptyState(
                    icon = "layers",
                    title = "No templates",
                    message = "Templates could not be loaded.",
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(items) { index, template ->
                        val sel = selectedIndex == index
                        val tint = Color.hsl(template.hue, 0.65f, 0.48f)
                        Box(
                            Modifier
                                .clip(CcShape.card)
                                .background(c.content)
                                .border(
                                    width = if (sel) 2.dp else 1.dp,
                                    color = if (sel) c.accent else c.border,
                                    shape = CcShape.card,
                                )
                                .clickable {
                                    selectedIndex = index
                                    name = template.name
                                    createError = null
                                    configEncoded = ProjectConfig(
                                        packageName = defaultPackageName(template.name),
                                    ).encode()
                                }
                                .padding(if (sel) 12.dp else 13.dp),
                        ) {
                            Column {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(tint.tint()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CcIcon(
                                        if (tab == TemplateTab.Web) "globe" else "panel",
                                        tint,
                                        20.dp,
                                        1.8f,
                                    )
                                }
                                TextN(
                                    template.name,
                                    Modifier.padding(top = 10.dp),
                                    CcType.itemTitle.copy(fontWeight = FontWeight.Bold),
                                    c.text,
                                )
                                TextN(
                                    template.description,
                                    Modifier.padding(top = 3.dp).heightIn(min = 32.dp),
                                    CcType.caption.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                    c.muted,
                                )
                                TextN(
                                    template.tags,
                                    Modifier
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(c.rail)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    CcType.code.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                    c.textSoft,
                                )
                            }
                            if (sel) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(c.accent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CcIcon("check", Color.White, 13.dp, 3f)
                                }
                            }
                        }
                    }
                }

                val error = createError
                if (error != null) {
                    TextN(
                        error,
                        Modifier.padding(top = 6.dp),
                        CcType.caption.copy(fontSize = 11.5.sp),
                        c.red,
                    )
                }

                // Name field + Create button, margin top 8, gap 10.
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(c.rail)
                            .border(1.dp, c.border, RoundedCornerShape(15.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) {
                            TextN(
                                "Project name",
                                Modifier,
                                CcType.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Normal),
                                c.muted,
                            )
                        }
                        BasicTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                createError = null
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = CcType.body.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = c.text,
                            ),
                        )
                    }
                    Row(
                        Modifier
                            .width(130.dp)
                            .height(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (canCreate) c.accent else c.field)
                            .clickable(enabled = canCreate) { step = Step.Config },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OneLine(
                            "Create",
                            CcType.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                            if (canCreate) Color.White else c.muted,
                        )
                        Spacer(Modifier.width(6.dp))
                        CcIcon("cr", if (canCreate) Color.White else c.muted, 16.dp, 2.6f)
                    }
                }
            }
        } else {
            // The project config step (after Create): the existing config form.
            ConfigForm(
                config = config,
                onConfigChange = {
                    configEncoded = it.encode()
                    createError = null
                },
                webProject = tab == TemplateTab.Web,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            SheetFooter(
                name = name,
                onNameChange = {},
                canCreate = selected != null && current != null && !creating,
                showNameField = false,
                error = createError,
                hint = current?.let { "${it.name} · ${it.tags}" } ?: "Choose a template above.",
                actionLabel = if (creating) "Creating" else "Create project",
                secondaryLabel = "Back",
                onSecondary = { step = Step.Pick },
                onCreate = {
                    val selection = selected
                    if (selection != null && !creating) {
                        creating = true
                        val error = onCreate(name, selection, config)
                        createError = error
                        if (error != null) creating = false
                    }
                },
            )
        }
    }
}

/** Small helper: a Text node with our theme type (keeps the sheet readable). */
@Composable
private fun TextN(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    androidx.compose.material3.Text(
        text,
        style = style,
        color = color,
        modifier = modifier,
    )
}
