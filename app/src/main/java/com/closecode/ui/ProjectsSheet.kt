package com.closecode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.problemBadge
import com.closecode.ui.project.statusParts
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

/**
 * Screen 3, the project switcher. A bottom sheet, not a destination: it can open over Welcome
 * or over the Files panel and always comes back to the screen that asked for it.
 */
@Composable
fun ProjectsSheet(
    projects: List<ProjectRecord>,
    openProjectId: String? = null,
    branchOf: (ProjectRecord) -> String? = { null },
    onOpen: (ProjectRecord) -> Unit,
    onNewProject: () -> Unit,
    onTemplates: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalPageColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visible = if (query.isBlank()) {
        projects
    } else {
        projects.filter { it.name.contains(query, ignoreCase = true) }
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.mock.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(tween(280)) { it / 4 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .heightIn(max = (screenHeight * 0.78f).dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(c.mock.panel),
            ) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(c.mock.toggleOff),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Projects",
                        color = c.mock.text,
                        fontSize = 20.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(34.dp)
                            .clickable { searching = !searching },
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon(CcIcons.Search, c.mock.textSoft, 21.dp)
                    }
                }
                if (searching) {
                    Box(
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.mock.field)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = c.mock.text,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search projects",
                                        color = c.mock.muted,
                                        fontSize = 14.sp,
                                        fontFamily = Sans,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                if (visible.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 160.dp)
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.mock.field),
                            contentAlignment = Alignment.Center,
                        ) {
                            CcIcon(CcIcons.FolderOpen, c.mock.muted, 28.dp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "No projects yet",
                            color = c.mock.text,
                            fontSize = 16.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Start a project, clone one or open a folder.",
                            color = c.mock.muted,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 6.dp,
                            bottom = 4.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(visible, key = { it.id }) { project ->
                            ProjectSwitchRow(
                                project = project,
                                open = project.id == openProjectId,
                                branchOf = branchOf,
                                onClick = { onOpen(project) },
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(c.mock.accent)
                            .clickable(onClick = onNewProject),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon(CcIcons.Plus, c.mock.onAccent, 17.dp)
                        Text(
                            text = "New project",
                            color = c.mock.onAccent,
                            fontSize = 14.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(c.mock.field)
                            .clickable(onClick = onTemplates),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon(CcIcons.Layers, c.mock.text2, 17.dp)
                        Text(
                            text = "Templates",
                            color = c.mock.text2,
                            fontSize = 14.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectSwitchRow(
    project: ProjectRecord,
    open: Boolean,
    branchOf: (ProjectRecord) -> String? = { null },
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val tile = com.closecode.ui.ide.projectTileColor(project.name)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) c.mock.field else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tile),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = project.name.take(1).uppercase().ifEmpty { "C" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            project.problemBadge()?.let { count ->
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 5.dp)
                        .size(19.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(c.mock.badge)
                        .border(3.dp, c.mock.rail, androidx.compose.foundation.shape.CircleShape)
                        .semantics { contentDescription = "$count problems" },
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = project.name,
                color = c.mock.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildAnnotatedString {
                    val branch = branchOf(project)
                    if (!branch.isNullOrBlank()) {
                        append(branch)
                        append(" · ")
                    }
                    val (word, kind) = project.statusParts(branch)
                    withStyle(
                        SpanStyle(
                            color = when (kind) {
                                com.closecode.ui.project.ProjectStatusKind.Ok -> c.mock.green
                                com.closecode.ui.project.ProjectStatusKind.Warning -> c.mock.warning
                                com.closecode.ui.project.ProjectStatusKind.Error -> c.mock.danger
                                com.closecode.ui.project.ProjectStatusKind.Muted -> c.mock.muted
                            },
                        ),
                    ) { append(word) }
                },
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (open) {
            CcIcon(CcIcons.Check, c.mock.accent, 20.dp)
        }
    }
}
