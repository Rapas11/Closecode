package com.closecode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll



import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.background.PageBackground
import com.closecode.ui.components.CodeCard
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubUser
import com.closecode.ui.github.TokenStore
import com.closecode.ui.ide.GitHubSignIn
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.problemBadge
import com.closecode.ui.project.statusParts
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.closecode.ui.components.CodeGlyph
import com.closecode.ui.components.PlusGlyph
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Facts = listOf("Open source", "On-device builds", "Private by default")

internal fun projectAge(lastOpened: Long): String {
    val days = ((System.currentTimeMillis() - lastOpened) / 86_400_000L).toInt().coerceAtLeast(0)
    return when (days) {
        0 -> "today"
        1 -> "yesterday"
        else -> "$days days ago"
    }
}

@Composable
private fun Reveal(
    index: Int,
    content: @Composable () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                delayMillis = (index * 80).coerceAtMost(520),
            ),
        ) { value, _ -> progress = value }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12f * density
        }
    ) { content() }
}

@Composable
private fun Pill(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(52.dp)
            .clip(shape)
            .background(if (primary) c.mock.accent else c.mock.panel)
            .border(1.dp, if (primary) c.mock.accent else c.mock.divider, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (leading != null) leading()
        Text(
            text = label,
            color = if (primary) c.mock.onAccent else c.mock.text,
            fontSize = 16.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// The disclosure hangs below the GitHub button: a stem down out of the button, a trunk running
// left, and one stub into each choice. The trunk sits at BranchTrunkY; the stubs are the space
// left over before the pills start.
private val BranchLabelHeight = 44.dp
private val BranchGap = 14.dp
private val BranchTrunkY = 16.dp
private val BranchConnectorHeight = 26.dp

/**
 * The two things the GitHub button opens, drawn under it as a trunk with a branch per choice.
 *
 * The two pills sit side by side and each one owns its half of the trunk, so the line closes
 * without either side having to know how wide the other is. The account line sits underneath
 * both, where it cannot disturb that geometry — the stem has to reach the button above it.
 */
@Composable
private fun GitHubDisclosure(
    signedIn: Boolean,
    account: GitHubUser?,
    onSignIn: () -> Unit,
    onClone: () -> Unit,
) {
    Column(
        Modifier.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BranchGap),
            verticalAlignment = Alignment.Top,
        ) {
            Branch(
                label = "Sign in GitHub",
                note = null,
                stem = false,
                trunkRunsLeft = false,
                onClick = onSignIn,
            )
            Branch(
                label = "Clone GitHub",
                note = null,
                stem = true,
                trunkRunsLeft = true,
                onClick = onClone,
            )
        }
        if (signedIn) AccountRow(account)
    }
}

/**
 * Who the device is actually signed in as. The pills say GitHub is connected; this says who it is
 * connected *as*, which is the thing worth checking before a clone writes into your projects.
 *
 * The account arrives from the network after sign-in, so "Connected" is a real state and not a
 * placeholder that ever claims a name it has not been told.
 */
@Composable
private fun AccountRow(account: GitHubUser?) {
    val c = LocalPageColors.current
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(c.mock.green),
        )
        Text(
            text = accountLabel(account),
            color = c.mock.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Who the device is signed in as.
 *
 * "Connected" is a real answer and not a placeholder: it is what can honestly be said while the
 * account is still being fetched, and it never borrows a name that has not been told to us.
 */
internal fun accountLabel(account: GitHubUser?): String = when {
    account == null -> "Connected"
    account.name.isNullOrBlank() -> "@${account.login}"
    else -> "${account.name} · @${account.login}"
}

/**
 * One choice in [GitHubDisclosure]. Its box is exactly as wide as its pill, so the centre of the
 * canvas is the centre of the pill — that is where the stem into the button and the stub into the
 * label land.
 *
 * [stem] rises into the GitHub button above; only the branch directly under the button draws one.
 * The trunk always runs away from its own side and overshoots the edge by half the gap, so two
 * neighbouring branches meet in the middle of the gap.
 */
@Composable
private fun Branch(
    label: String,
    note: String?,
    stem: Boolean,
    trunkRunsLeft: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box {
        Column {
            Spacer(Modifier.height(BranchConnectorHeight))
            BranchLabel(label = label, note = note, onClick = onClick)
        }
        Canvas(Modifier.matchParentSize()) {
            val line = 1.dp.toPx()
            val cx = size.width / 2f
            val trunkY = BranchTrunkY.toPx()
            val stubEnd = BranchConnectorHeight.toPx()
            val reach = BranchGap.toPx() / 2f
            drawLine(
                color = c.mock.divider,
                start = Offset(cx, trunkY),
                end = Offset(if (trunkRunsLeft) -reach else size.width + reach, trunkY),
                strokeWidth = line,
            )
            drawLine(
                color = c.mock.divider,
                start = Offset(cx, trunkY),
                end = Offset(cx, stubEnd),
                strokeWidth = line,
            )
            if (stem) {
                drawLine(
                    color = c.mock.divider,
                    start = Offset(cx, 0f),
                    end = Offset(cx, trunkY),
                    strokeWidth = line,
                )
            }
        }
    }
}

@Composable
private fun BranchLabel(label: String, note: String?, onClick: () -> Unit) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .height(BranchLabelHeight)
            .clip(shape)
            .background(c.mock.panel)
            .border(1.dp, c.mock.divider, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = c.mock.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        if (note != null) {
            Text(
                text = note,
                color = c.mock.green,
                fontSize = 11.sp,
                fontFamily = Sans,
            )
        }
    }
}

@Composable
private fun Wordmark() {
    val c = LocalPageColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .background(c.mock.text, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                color = c.mock.page,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Closecode",
            color = c.mock.text,
            fontSize = 18.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Headline(size: TextUnit) {
    val c = LocalPageColors.current
    Text(
        text = buildAnnotatedString {
            append("Build Android apps,\n")
            withStyle(SpanStyle(color = c.mock.accent)) { append("anywhere.") }
        },
        color = c.mock.text,
        fontSize = size,
        lineHeight = size * 1.02f,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.045f).em,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 620.dp),
    )
}

@Composable
fun HeroScreen(
    onProjectCreated: (name: String, selection: SelectedTemplate, templateName: String, config: ProjectConfig) -> String?,
    onProjectCloned: (ProjectRecord) -> Unit,
    onOpenProject: (ProjectRecord) -> Unit,
    recentProjects: List<ProjectRecord> = emptyList(),
    branchOf: (ProjectRecord) -> String? = { null },
    // Moves when the GitHub account changes elsewhere — a sign-in or a disconnect done on the
    // settings screen. The token is read again when it does, so this screen never keeps
    // showing an account that is no longer connected.
    tokenEpoch: Int = 0,
) {
    val c = LocalPageColors.current
    var sheetOpen by remember { mutableStateOf(false) }
    var projectsOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    // GitHub. The token is read once from private preferences and kept in memory only as long as
    // this screen is composed; everything that touches the network runs off the main thread.
    val context = LocalContext.current
    val tokenStore = remember(context) { TokenStore(context) }
    var githubToken by remember(tokenEpoch) { mutableStateOf(tokenStore.read()) }
    // The token only says that *some* account is connected. This is which one, and it is fetched
    // rather than derived — off the main thread, because it is a network call made on screen load.
    val gitHubClient = remember { GitHubClient() }
    var githubAccount by remember { mutableStateOf<GitHubUser?>(null) }
    var githubOpen by remember { mutableStateOf(false) }
    var signInOpen by remember { mutableStateOf(false) }
    var cloneOpen by remember { mutableStateOf(false) }

    LaunchedEffect(githubToken) {
        val token = githubToken
        githubAccount = if (token == null) {
            null
        } else {
            withContext(Dispatchers.IO) { gitHubClient.currentUser(token).getOrNull() }
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2400)
            toast = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headlineSize = 35.6.sp

        com.closecode.ui.components.DesignPage(modifier = Modifier.fillMaxSize(), background = c.mock.page) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp),
            ) {
                Row(
                    Modifier
                        .widthIn(max = 430.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.mock.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "C",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Text(
                        text = "Closecode",
                        color = c.mock.text,
                        fontSize = 20.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                Spacer(Modifier.height(22.dp))
                Reveal(2) {
                    Text(
                        text = "THE ANDROID IDE FOR YOUR PHONE",
                        color = c.mock.accent,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Reveal(2) {
                    Text(
                        text = buildAnnotatedString {
                            append("Build Android apps,\n")
                            withStyle(SpanStyle(color = c.mock.accent)) { append("anywhere.") }
                        },
                        color = c.mock.text,
                        fontSize = headlineSize,
                        lineHeight = 35.6.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.045f).em,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Reveal(3) {
                    Text(
                        text = "Write, build and ship real apps straight from your phone. " +
                            "No laptop, no setup.",
                        color = c.mock.muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontFamily = Sans,
                        modifier = Modifier.widthIn(max = 380.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Reveal(4) {
                    Column {
                    Row(
                        Modifier.widthIn(max = 430.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        com.closecode.ui.components.DesignPrimaryButton(
                            label = "Start a project",
                            onClick = { sheetOpen = true },
                            leading = com.closecode.ui.ide.CcIcons.Plus,
                            height = 52.dp,
                            radius = 16.dp,
                            modifier = Modifier.weight(1.5f),
                        )
                        Row(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.mock.panel)
                                .border(1.dp, c.mock.border, RoundedCornerShape(16.dp))
                                .clickable { githubOpen = true },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CcIcon(
                                com.closecode.ui.ide.CcIcons.Github,
                                c.mock.text,
                                18.dp,
                            )
                            Text(
                                text = "GitHub",
                                color = c.mock.text,
                                fontSize = 15.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    }
                }
                if (recentProjects.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp))
                    Row(
                        Modifier.widthIn(max = 430.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "RECENT PROJECTS",
                            color = c.mock.muted,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (recentProjects.size == 1) "1 project" else "${recentProjects.size} projects",
                            color = c.mock.accent,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { projectsOpen = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.widthIn(max = 430.dp).fillMaxWidth()) {
                        recentProjects.take(3).forEachIndexed { index, project ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    .clickable { onOpenProject(project) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(com.closecode.ui.ide.projectTileColor(project.name)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = project.name.take(1).uppercase().ifEmpty { "C" },
                                            color = Color.White,
                                            fontSize = 18.sp,
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
                                                .semantics {
                                                    contentDescription = "$count problems"
                                                },
                                            contentAlignment = Alignment.Center,
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
                                        fontSize = 14.5.sp,
                                        fontFamily = Sans,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildAnnotatedString {
                                            append(if (project.tab == TemplateTab.Web) "Website" else "Android")
                                            append(" · ")
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
                                        fontSize = 11.5.sp,
                                        fontFamily = Sans,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                com.closecode.ui.components.DesignChevron()
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(20.dp))
                Reveal(5) {
                    Row(
                        Modifier.widthIn(max = 430.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Facts.forEach { fact ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(c.mock.accent, RoundedCornerShape(3.dp)),
                                )
                                Text(
                                    text = fact,
                                    color = c.mock.muted,
                                    fontSize = 11.5.sp,
                                    fontFamily = Sans,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = toast.orEmpty(),
                color = c.mock.onAccent,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.mock.accent)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }

    if (projectsOpen) {
        ProjectsSheet(
            projects = recentProjects,
            branchOf = branchOf,
            onOpen = { project ->
                projectsOpen = false
                onOpenProject(project)
            },
            onNewProject = {
                projectsOpen = false
                sheetOpen = true
            },
            onTemplates = {
                projectsOpen = false
                sheetOpen = true
            },
            onClose = { projectsOpen = false },
        )
    }

    if (sheetOpen) {
        StartProjectSheet(
            onDismiss = { sheetOpen = false },
            existingPackages = recentProjects
                .map { it.config.packageName.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            onCreate = { projectName, selection, config ->
                val templateName = Templates[selection.tab]
                    .orEmpty()
                    .getOrNull(selection.index)
                    ?.name
                    .orEmpty()
                onProjectCreated(projectName, selection, templateName, config)
                    .also { error -> if (error == null) sheetOpen = false }
            },
        )
    }

    if (githubOpen) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable { githubOpen = false },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(c.mock.panel)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.mock.border),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "GitHub",
                    color = c.mock.text,
                    fontSize = 20.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sign in to push your work, or clone a public repository.",
                    color = c.mock.muted,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.mock.accent)
                        .clickable {
                            githubOpen = false
                            if (githubToken == null) signInOpen = true else cloneOpen = true
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcIcon(CcIcons.Github, c.mock.onAccent, 18.dp)
                    Text(
                        text = if (githubToken == null) "Sign in with GitHub" else "Open my GitHub repositories",
                        color = c.mock.onAccent,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.mock.field)
                        .clickable {
                            githubOpen = false
                            cloneOpen = true
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcIcon(CcIcons.Code, c.mock.text, 18.dp)
                    Text(
                        text = "Clone a public repository",
                        color = c.mock.text,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    if (signInOpen) {
        GitHubSignIn(
            onSignedIn = { token ->
                githubToken = token
                signInOpen = false
                toast = if (tokenStore.write(token)) {
                    "Signed in to GitHub."
                } else {
                    // The Keystore is broken: this sign-in lives in memory only.
                    "Signed in. This phone cannot keep sign-ins safely, so it will ask again next time."
                }
            },
            onDismiss = { signInOpen = false },
        )
    }

    if (cloneOpen) {
        GitHubCloneScreen(
            token = githubToken,
            onToken = { fresh ->
                tokenStore.write(fresh)
                githubToken = fresh
            },
            onDismiss = { cloneOpen = false },
            onCloned = { record ->
                cloneOpen = false
                githubOpen = false
                onProjectCloned(record)
            },
        )
    }
}