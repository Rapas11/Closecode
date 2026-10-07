package com.closecode.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.rememberProjectRepository
import com.closecode.ui.components.SheetHeader
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubClone
import com.closecode.ui.github.GitHubRepo
import com.closecode.ui.ide.describeFailure
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picking one of your GitHub repositories and cloning it onto the device.
 *
 * The clone runs on [Dispatchers.IO] through [GitHubClone] and lands in the project folder that
 * [ProjectRecord] names. The directory is created empty on purpose: JGit refuses a non-empty
 * destination, so nothing else may be written into it until the fetch finishes.
 */
@Composable
fun GitHubCloneSheet(
    token: String,
    onDismiss: () -> Unit,
    onCloned: (ProjectRecord) -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val client = remember { GitHubClient() }
    val cloner = remember { GitHubClone() }
    val repository = rememberProjectRepository()

    // null means "still asking GitHub"; an empty list means "you have none yet".
    var repos by remember { mutableStateOf<List<GitHubRepo>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var cloning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        val listed = withContext(Dispatchers.IO) { client.repositories(token) }
        listed
            .onSuccess { repos = it }
            .onFailure { failure = describeFailure(it, "GitHub could not list your repositories.") }
    }

    fun start(repo: GitHubRepo) {
        if (cloning != null) return
        failure = null
        cloning = repo.fullName
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    repository.importCloned(repo.fullName.substringAfter('/')) { target ->
                        cloner.clone(repo.cloneUrl, token, target).getOrThrow()
                    }
                }
            }
            outcome
                .onSuccess { onCloned(it) }
                .onFailure {
                    failure = describeFailure(it, "GitHub could not clone ${repo.fullName}.")
                    cloning = null
                }
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val wide = screenWidth >= 720

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(tween(300)) { it / 5 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(240)) { it / 5 } + fadeOut(tween(240)),
            modifier = Modifier.align(if (wide) Alignment.Center else Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .heightIn(max = (screenHeight * if (wide) 0.8f else 0.9f).dp)
                    .clip(
                        if (wide) {
                            RoundedCornerShape(22.dp)
                        } else {
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        },
                    )
                    .background(c.bg),
            ) {
                SheetHeader(
                    onClose = onDismiss,
                    title = "Clone from GitHub",
                    subtitle = "Pick a repository to bring onto this device. It opens as a project " +
                        "when the download finishes.",
                )

                val listed = repos
                when {
                    failure != null -> Text(
                        text = failure.orEmpty(),
                        color = c.red,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        lineHeight = 19.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                    )

                    listed == null -> Text(
                        text = "Asking GitHub for your repositories…",
                        color = c.mu,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    )

                    listed.isEmpty() -> Text(
                        text = "You have no repositories yet. Publish a project first and it will " +
                            "show up here.",
                        color = c.mu,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        lineHeight = 19.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                    )

                    else -> Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .heightIn(min = 0.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listed.forEach { repo ->
                            RepoRow(
                                repo = repo,
                                busy = cloning == repo.fullName,
                                disabled = cloning != null,
                                onClick = { start(repo) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: GitHubRepo,
    busy: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.ln, shape)
            .clickable(enabled = !disabled) { onClick() }
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = repo.fullName,
                color = c.tx,
                fontSize = 14.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (busy) "Cloning…" else if (repo.isPrivate) "Private" else "Public",
                color = if (busy) c.ac else c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (repo.isPrivate && !busy) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.ac),
            )
        }
    }
}
