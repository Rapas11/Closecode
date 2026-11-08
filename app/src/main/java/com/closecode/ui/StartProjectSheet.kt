package com.closecode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.ConfigForm
import com.closecode.ui.components.SegmentedTabs
import com.closecode.ui.components.TemplateCard
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.defaultPackageName
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

private enum class Step { Pick, Config }

private val PackageName = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

private fun isValidPackageName(value: String): Boolean = PackageName.matches(value)

@Composable
fun StartProjectSheet(
    onDismiss: () -> Unit,
    existingPackages: Set<String> = emptySet(),
    onCreate: (name: String, selection: SelectedTemplate, config: ProjectConfig) -> String?,
) {
    val c = LocalPageColors.current
    var step by rememberSaveable { mutableStateOf(Step.Pick.name) }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(TemplateTab.Web.name) }
    var pickTab by rememberSaveable { mutableStateOf(TemplateTab.Web.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var configEncoded by rememberSaveable {
        mutableStateOf(ProjectConfig(packageName = "").encode())
    }
    var createError by rememberSaveable { mutableStateOf<String?>(null) }

    val currentStep = Step.valueOf(step)
    val config = ProjectConfig.decode(configEncoded)

    // Pre-fill the package from the project name so creation never stalls on an empty package.
    LaunchedEffect(name) {
        val n = name.trim()
        if (n.isNotEmpty() && config.packageName.isBlank()) {
            configEncoded = config.copy(packageName = defaultPackageName(n)).encode()
        }
    }
    val selected = if (selectedIndex >= 0 && selectedTab.isNotEmpty()) {
        SelectedTemplate(TemplateTab.valueOf(selectedTab), selectedIndex)
    } else {
        null
    }
    val pickTabEnum = TemplateTab.valueOf(pickTab)
    val templates = Templates[pickTabEnum].orEmpty().mapIndexed { index, template ->
        Triple(pickTabEnum, index, template)
    }

    fun goBack() {
        if (currentStep == Step.Config) {
            step = Step.Pick.name
        } else {
            onDismiss()
        }
    }

    fun createNow() {
        val selection = selected ?: return
        val templateName = Templates[selection.tab].orEmpty().getOrNull(selection.index)?.name
        val finalName = name.trim().ifEmpty { templateName ?: "Untitled" }
        val packageName = config.packageName.trim().ifEmpty { defaultPackageName(finalName) }
        val problem = when {
            packageName.isEmpty() -> "Enter a package name in project settings."
            !isValidPackageName(packageName) ->
                "Use a package name like com.yourname.app — letters, numbers, dots and underscores."
            packageName in existingPackages ->
                "That package name is already used by another project. Pick a unique one."
            else -> null
        }
        if (problem != null) {
            createError = problem
            step = Step.Config.name
            return
        }
        createError = onCreate(finalName, selection, config.copy(packageName = packageName))
    }

    BackHandler(onBack = ::goBack)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.mock.scrim)
                .clickable(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 90.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(c.mock.panel)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.mock.toggleOff),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (currentStep == Step.Pick) "Start a project" else "Project options",
                        color = c.mock.text,
                        fontSize = 22.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 27.sp,
                    )
                    Text(
                        text = if (currentStep == Step.Pick) {
                            "Pick a template. You can change anything later."
                        } else {
                            "Package name, SDK and app details."
                        },
                        color = c.mock.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Close, c.mock.text2, 22.dp)
                }
            }

            if (currentStep == Step.Pick) {
                SegmentedTabs(
                    tab = pickTabEnum,
                    onSelect = {
                        pickTab = it.name
                        selectedTab = it.name
                        selectedIndex = 0
                        createError = null
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = templates,
                        key = { "${it.first.name}:${it.second}" },
                    ) { entry ->
                        TemplateCard(
                            template = entry.third,
                            selected = selected?.tab == entry.first && selected.index == entry.second,
                            tab = entry.first,
                            onClick = {
                                selectedTab = entry.first.name
                                selectedIndex = entry.second
                                createError = null
                            },
                        )
                    }
                }
            } else {
                ConfigForm(
                    config = config,
                    onConfigChange = {
                        configEncoded = it.encode()
                        createError = null
                    },
                    webProject = selected?.tab == TemplateTab.Web,
                    packageError = createError?.takeIf {
                        it.contains("package", ignoreCase = true)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            ) {
                createError?.let {
                    Text(
                        text = it,
                        color = c.mock.danger,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentStep == Step.Pick) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(c.mock.rail)
                                .border(1.dp, c.mock.border, RoundedCornerShape(15.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (name.isEmpty()) {
                                Text(
                                    text = "Project name",
                                    color = c.mock.muted,
                                    fontSize = 14.5.sp,
                                    fontFamily = Sans,
                                )
                            }
                            BasicTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    createError = null
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = c.mock.text,
                                    fontSize = 14.5.sp,
                                    fontFamily = Sans,
                                ),
                                cursorBrush = SolidColor(c.mock.accent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(c.mock.field)
                                .clickable { step = Step.Pick.name },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Back",
                                color = c.mock.text,
                                fontSize = 14.5.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Row(
                        Modifier
                            .width(130.dp)
                            .height(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected != null) c.mock.accent else c.mock.field)
                            .clickable(enabled = selected != null, onClick = ::createNow),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Create",
                            color = if (selected != null) c.mock.onAccent else c.mock.muted,
                            fontSize = 14.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        CcIcon(
                            CcIcons.ChevronRight,
                            if (selected != null) c.mock.onAccent else c.mock.faint,
                            15.dp,
                        )
                    }
                }
            }
        }
    }
}
