package com.closecode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.closecode.ui.theme.CcShape
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/**
 * One project row as the screens need it. Every field comes from the real project
 * store; nothing is typed in.
 */
data class ProjectUi(
    val id: String,
    val name: String,
    val color: Color,
    val colorEnd: Color? = null, // != null -> 135 degree gradient (the mockup's orange tile)
    val kind: String? = null,    // "Android", "Website"
    val branch: String? = null,  // current git branch, null when not a repo
    val statusText: String? = null, // "Build ok", "2 problems", "2 days ago": computed
    val problems: Int = 0,       // red badge, hidden at 0
)

/** The six project tile colours from the spec; a project picks one by its name. */
private val ProjectPalette = listOf(
    Color(0xFF2563EB),
    Color(0xFF16A34A),
    Color(0xFFEA580C),
    Color(0xFF0E7490),
    Color(0xFF7C3AED),
    Color(0xFFBE185D),
)

/** Deterministic tile colour for a project name, from the spec's set. */
fun projectColor(name: String): Color =
    ProjectPalette[Math.abs(name.hashCode()) % ProjectPalette.size]

/** Maps a stored record into the row shape every screen uses. */
fun com.closecode.ui.project.ProjectRecord.toUi(): ProjectUi = ProjectUi(
    id = id,
    name = name,
    color = projectColor(name),
    kind = when (tab) {
        com.closecode.ui.model.TemplateTab.Web -> "Website"
        com.closecode.ui.model.TemplateTab.App -> "Android"
    },
    statusText = com.closecode.ui.model.relativeTime(lastOpened),
    problems = 0,
)

/**
 * Square letter tile with the red count badge. Welcome: 44 / 14 / 16 sp.
 * Switch sheet: 42 / 14 / 16 sp.
 */
@Composable
fun ProjectAvatar(
    p: ProjectUi,
    size: Dp,
    radius: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    badgeBorder: Color,
) {
    val c = CcTheme.colors
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(radius))
                .background(
                    if (p.colorEnd != null) {
                        Brush.linearGradient(listOf(p.color, p.colorEnd))
                    } else {
                        androidx.compose.ui.graphics.SolidColor(p.color)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                p.name.firstOrNull()?.uppercase() ?: "?",
                style = CcType.itemTitle.copy(fontSize = fontSize, fontWeight = FontWeight.ExtraBold),
                color = Color.White,
            )
        }
        if (p.problems > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(c.red)
                    .border(3.dp, badgeBorder, CircleShape)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (p.problems > 99) "99+" else "${p.problems}",
                    style = CcType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Tall bottom sheet that starts `topOffset` below the status bar and runs to the
 * bottom (02: 90 dp, 03: 170 dp). The d3 CcBottomSheet is for content-height
 * sheets; these two are fixed-top, so they are drawn in the screen layer.
 */
@Composable
fun SheetLayer(
    topOffset: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CcTheme.colors
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.dim)
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
        )
        // taps inside never reach the scrim
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = topOffset)
                .clip(CcShape.sheetTop)
                .background(c.panel)
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(40.dp, 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.toggleOff),
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
