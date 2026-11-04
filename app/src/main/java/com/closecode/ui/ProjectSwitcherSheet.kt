package com.closecode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.CcTextField
import com.closecode.ui.components.EmptyState
import com.closecode.ui.components.OneLine
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/**
 * Screen 03 — Switch project.
 *
 * Tall sheet (starts 170 dp below the status bar) over the open sidebar. Rows are the
 * real project store, sorted by last opened; the current project is marked with the
 * accent check. The two buttons stay pinned while the list scrolls.
 */
@Composable
fun ProjectSwitcherSheet(
    projects: List<ProjectUi>,
    currentId: String?,
    onPick: (ProjectUi) -> Unit,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = CcTheme.colors
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) {
        projects
    } else {
        projects.filter { it.name.contains(query, ignoreCase = true) }
    }

    BackHandler {
        if (searching) {
            searching = false
            query = ""
        } else {
            onDismiss()
        }
    }

    SheetLayer(topOffset = 170.dp, horizontalPadding = 14.dp, onDismiss = onDismiss) {
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) {
                CcTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    hint = "Find a project",
                    leadingIcon = "search",
                )
            } else {
                Text(
                    "Projects",
                    modifier = Modifier.weight(1f),
                    style = CcType.sheetTitle.copy(fontSize = 20.sp),
                    color = c.text,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clickable {
                        searching = !searching
                        if (!searching) query = ""
                    }
                    .semantics { contentDescription = "Search projects" },
            ) {
                CcIcon(if (searching) "x" else "search", c.textSoft, 21.dp, 1.8f)
            }
        }

        when {
            projects.isEmpty() -> EmptyState(
                icon = "folder",
                title = "No projects yet",
                message = "Create your first project.",
                actionText = "New project",
                onAction = onNew,
                modifier = Modifier.weight(1f),
            )

            shown.isEmpty() -> EmptyState(
                icon = "search",
                title = "No projects match",
                message = "Try another name.",
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown, key = { it.id }) { p ->
                    val current = p.id == currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (current) c.field else Color.Transparent)
                            .clickable { onPick(p) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectAvatar(
                            p = p,
                            size = 42.dp,
                            radius = 14.dp,
                            fontSize = 16.sp,
                            badgeBorder = c.rail,
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            OneLine(
                                p.name,
                                CcType.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                c.text,
                            )
                            OneLine(
                                listOfNotNull(p.branch, p.statusText)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                CcType.caption,
                                c.muted,
                                Modifier.padding(top = 2.dp),
                            )
                        }
                        if (current) {
                            CcIcon("check", c.accent, 20.dp, 2.6f)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.accent)
                    .clickable(onClick = onNew),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcIcon("plus", Color.White, 17.dp, 2.6f)
                Spacer(Modifier.width(7.dp))
                OneLine(
                    "New project",
                    CcType.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    Color.White,
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.field)
                    .clickable(onClick = onTemplates),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcIcon("layers", c.text2, 17.dp, 2f)
                Spacer(Modifier.width(7.dp))
                OneLine(
                    "Templates",
                    CcType.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    c.text2,
                )
            }
        }
    }
}
