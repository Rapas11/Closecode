package com.closecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.ProjectAvatar
import com.closecode.ui.ProjectUi
import com.closecode.ui.components.OneLine
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/**
 * Screen 01 — Welcome. The home screen when no project is open.
 *
 * All values come from the real project store: [recent] is the last three opened
 * projects (newest first), [totalProjects] the count of all projects on disk.
 * With no projects the RECENT section is hidden entirely and only brand,
 * headline and buttons show.
 */
@Composable
fun WelcomeScreen(
    recent: List<ProjectUi>,
    totalProjects: Int,
    onStartProject: () -> Unit,
    onGitHub: () -> Unit,
    onOpen: (ProjectUi) -> Unit,
) {
    val c = CcTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.rail)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 26.dp, bottom = 64.dp),
        ) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "C",
                            style = CcType.itemTitle.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Closecode",
                        style = CcType.itemTitle.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = c.text,
                    )
                }
                Text(
                    "THE ANDROID IDE FOR YOUR PHONE",
                    Modifier.padding(top = 22.dp),
                    style = CcType.code.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                        lineHeight = 14.sp,
                    ),
                    color = c.accent,
                )
                Text(
                    buildAnnotatedString {
                        append("Build Android apps, ")
                        withStyle(SpanStyle(color = c.accent)) {
                            append("anywhere.")
                        }
                    },
                    Modifier.padding(top = 10.dp),
                    style = CcType.hero.copy(lineHeight = 35.6.sp),
                    color = c.text,
                )
                Text(
                    "Write, build and ship real apps straight from your phone. No laptop, no setup.",
                    Modifier.padding(top = 12.dp),
                    style = CcType.body.copy(
                        fontWeight = FontWeight.Normal,
                        lineHeight = 21.sp,
                    ),
                    color = c.muted,
                )
                Row(
                    Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.accent)
                            .clickable(onClick = onStartProject),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon("plus", Color.White, 18.dp, 2.6f)
                        Spacer(Modifier.width(8.dp))
                        OneLine(
                            "Start a project",
                            CcType.body.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            Color.White,
                        )
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.panel)
                            .border(1.dp, c.border, RoundedCornerShape(16.dp))
                            .clickable(onClick = onGitHub),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon("gh", c.text, 18.dp, 1.8f)
                        Spacer(Modifier.width(7.dp))
                        OneLine(
                            "GitHub",
                            CcType.body.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            c.text,
                        )
                    }
                }
            }

            if (recent.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, top = 26.dp, end = 22.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "RECENT PROJECTS",
                        style = CcType.section,
                        color = c.muted,
                    )
                    Text(
                        if (totalProjects == 1) "1 project" else "$totalProjects projects",
                        style = CcType.section,
                        color = c.accent,
                    )
                }
                recent.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(p) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectAvatar(p, 44.dp, 14.dp, 16.sp, badgeBorder = c.rail)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            OneLine(p.name, CcType.itemTitle, c.text)
                            OneLine(
                                listOfNotNull(p.kind, p.branch, p.statusText)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                CcType.caption,
                                c.muted,
                                Modifier.padding(top = 2.dp),
                            )
                        }
                        CcIcon("cr", c.faint, 16.dp, 2.2f)
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("Open source", "On-device builds", "Private by default").forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.accent),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it,
                        style = CcType.caption.copy(fontSize = 11.5.sp),
                        color = c.muted,
                    )
                }
            }
        }
    }
}
