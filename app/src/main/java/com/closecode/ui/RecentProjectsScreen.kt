package com.closecode.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.ArrowGlyph
import com.closecode.ui.model.RecentProject
import com.closecode.ui.model.relativeTime
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

@Composable
fun RecentProjectsScreen(
    projects: List<RecentProject>,
    onOpen: (RecentProject) -> Unit,
    onDelete: (RecentProject) -> Unit,
    onRename: (RecentProject, String) -> String?,
    onBack: () -> Unit,
) {
    val c = LocalPageColors.current
    var pendingDelete by remember { mutableStateOf<RecentProject?>(null) }
    var pendingRename by remember { mutableStateOf<RecentProject?>(null) }
    var actionTarget by remember { mutableStateOf<RecentProject?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding()
    ) {
        com.closecode.ui.components.DesignPageHeader(
            title = "Projects",
            subtitle = when (projects.size) {
                0 -> "Nothing here yet"
                1 -> "1 project on this device"
                else -> "${projects.size} projects on this device"
            },
            onBack = onBack,
        )

        if (projects.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Projects you create will show up here.",
                    color = c.mock.muted,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 24.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    RecentRow(
                        project = project,
                        onClick = { onOpen(project) },
                        onLongPress = { actionTarget = project },
                    )
                }
            }
        }
    }

    val actions = actionTarget
    if (actions != null) {
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(
                    text = actions.name,
                    color = c.mock.text,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "Rename this project, or delete it and everything in its folder.",
                    color = c.mock.muted,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    pendingRename = actions
                }) {
                    Text(
                        text = "Rename",
                        color = c.mock.accent,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        actionTarget = null
                        pendingDelete = actions
                    }) {
                        Text(
                            text = "Delete",
                            color = c.mock.danger,
                            fontSize = 15.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = { actionTarget = null }) {
                        Text(
                            text = "Cancel",
                            color = c.mock.muted,
                            fontSize = 15.sp,
                            fontFamily = Sans,
                        )
                    }
                }
            },
            containerColor = c.mock.panel,
        )
    }

    val renameTarget = pendingRename
    var renameText by remember(renameTarget) {
        mutableStateOf<String>(renameTarget?.name.orEmpty())
    }
    var renameError by remember(renameTarget) { mutableStateOf<String?>(null) }
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = {
                Text(
                    text = "Rename project",
                    color = c.mock.text,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = {
                            renameText = it
                            renameError = null
                        },
                        singleLine = true,
                        label = { Text("Project name") },
                    )
                    renameError?.let { message ->
                        Text(
                            text = message,
                            color = c.mock.danger,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val error = onRename(renameTarget, renameText)
                    if (error == null) {
                        pendingRename = null
                    } else {
                        renameError = error
                    }
                }) {
                    Text(
                        text = "Rename",
                        color = c.mock.accent,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text(
                        text = "Cancel",
                        color = c.mock.muted,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                    )
                }
            },
            containerColor = c.mock.panel,
        )
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = "Delete ${target.name}?",
                    color = c.mock.text,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "This removes the project folder and everything in it. It cannot be undone.",
                    color = c.mock.muted,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) {
                    Text(
                        text = "Delete",
                        color = c.mock.danger,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        text = "Cancel",
                        color = c.mock.muted,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                    )
                }
            },
            containerColor = c.mock.panel,
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentRow(
    project: RecentProject,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.mock.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.closecode.ui.components.DesignAvatar(
            label = project.name.firstOrNull()?.uppercase() ?: "?",
            size = 44.dp,
            background = c.mock.accent,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 13.dp)
        ) {
            Text(
                text = project.name,
                color = c.mock.text,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${project.templateName} · ${relativeTime(project.lastOpened)}",
                color = c.mock.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        com.closecode.ui.components.DesignChevron()
    }
}
