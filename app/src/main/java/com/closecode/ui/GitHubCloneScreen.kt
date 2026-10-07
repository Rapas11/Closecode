package com.closecode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.rememberProjectRepository
import com.closecode.ui.components.SheetHeader
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubClone
import com.closecode.ui.github.GitHubRepo
import com.closecode.ui.github.needsAuthentication
import com.closecode.ui.ide.GitHubSignInBody
import com.closecode.ui.ide.SignInAction
import com.closecode.ui.ide.describeFailure
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The one download that is running, if any: what to say is happening, and which row is on it. */
private data class CloneJob(val label: String, val rowKey: String?)

/**
 * The repository picker, now the whole screen rather than a sheet over it.
 *
 * Two ways in, deliberately stacked rather than either-or. The paste box takes any https remote —
 * GitHub, GitLab, Bitbucket, a server on the office LAN — and needs no account at all, so it stays
 * where it is even when nobody has signed in; hiding it behind a sign-in would punish the common
 * case (a public link someone just copied) for the uncommon one. The list underneath is a
 * convenience that does need an account, so a signed-out user gets the sign-in card in its place
 * instead of an empty shelf they could never fill.
 *
 * Nothing here names a branch. `git clone` checks out the remote's own default HEAD, which is the
 * branch that list would have preselected anyway; picking among the rest is work for the Source
 * Control view once the code is on the device.
 *
 * Success is not reported back as "cloned" — it hands the new [ProjectRecord] to the caller, which
 * opens it the same way a freshly created project opens.
 */
@Composable
fun GitHubCloneScreen(
    token: String?,
    onToken: (String) -> Unit,
    onDismiss: () -> Unit,
    onCloned: (ProjectRecord) -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val client = remember { GitHubClient() }
    val cloner = remember { GitHubClone() }
    val repository = rememberProjectRepository()

    var link by remember { mutableStateOf("") }
    // null means "still asking GitHub"; an empty list means "you have none yet".
    var repos by remember { mutableStateOf<List<GitHubRepo>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Which kind of failure it was, because only one of them has an answer that helps: a network
    // error wants a retry, an authentication error wants a token the user does not have here.
    var authFailure by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<CloneJob?>(null) }
    // The sign-in card replaces the list while it is up. It starts out governed by the token alone
    // — signed out means the card — and this is only for asking again while a token already exists.
    var signingIn by remember { mutableStateOf(false) }
    // The clone that failed on auth, kept so it can run again the moment a token arrives rather
    // than making the user retype the link they already gave us.
    var retryAfterSignIn by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val showCard = token == null || signingIn

    // The screen underneath installs its own handler, and it always goes one level up — out of
    // the project entirely. So this route has to claim Back for itself while it is composed.
    BackHandler(onBack = onDismiss)

    LaunchedEffect(token) {
        val active = token
        if (active == null) {
            repos = null
            return@LaunchedEffect
        }
        // Reaching here means a token just arrived, so whatever the old one was complained about
        // is no longer the question.
        repos = null
        failure = null
        authFailure = false
        val listed = withContext(Dispatchers.IO) { client.repositories(active) }
        listed
            .onSuccess { repos = it }
            .onFailure { error ->
                failure = describeFailure(error, "GitHub could not list your repositories.")
                authFailure = needsAuthentication(error)
            }
    }

    fun startClone(
        displayName: String,
        remoteUrl: String,
        tokenUsed: String?,
        rowKey: String? = null,
    ) {
        if (job != null) return
        val name = displayName.trim().ifBlank { "cloned" }
        failure = null
        authFailure = false
        retryAfterSignIn = null
        job = CloneJob(name, rowKey)
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    repository.importCloned(name) { target ->
                        cloner.clone(remoteUrl, tokenUsed, target).getOrThrow()
                    }
                }
            }
            outcome
                .onSuccess { onCloned(it) }
                .onFailure { error ->
                    job = null
                    failure = describeFailure(error, "Could not clone $name.")
                    authFailure = needsAuthentication(error)
                    if (authFailure) {
                        retryAfterSignIn = { withToken ->
                            startClone(name, remoteUrl, withToken, rowKey)
                        }
                    }
                }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(
                    onClose = onDismiss,
                    title = "Clone a repository",
                    subtitle = "Paste any https link, or pick one of your repositories. It opens " +
                        "as a project when the download finishes.",
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    failure?.let { message ->
                        CloneFailure(
                            message = message,
                            // The action only earns its place when the card is not already doing
                            // that job. A signed-out user has "Sign in to GitHub" on screen
                            // already, so what is left to offer is a way back in for a token that
                            // has stopped working — and reaching here at all means there is one.
                            authLabel = if (authFailure && !showCard) "Re-authenticate" else null,
                            onAuthenticate = { signingIn = true },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            text = "Paste a repository link",
                            color = c.mu,
                            fontSize = 12.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.editor)
                                .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                        ) {
                            if (link.isEmpty()) {
                                Text(
                                    text = "https://github.com/owner/repository",
                                    color = c.mu,
                                    fontSize = 14.sp,
                                    fontFamily = Mono,
                                )
                            }
                            BasicTextField(
                                value = link,
                                onValueChange = { link = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = c.tx,
                                    fontSize = 14.sp,
                                    fontFamily = Mono,
                                ),
                                cursorBrush = SolidColor(c.ac),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    keyboard?.hide()
                                    startClone(folderNameFromRemote(link), link, token)
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PrimaryButton(
                            label = if (job == null) "Clone Repository" else "Cloning…",
                            enabled = link.isNotBlank() && job == null,
                            onClick = {
                                keyboard?.hide()
                                startClone(folderNameFromRemote(link), link, token)
                            },
                        )
                    }

                    job?.let { running ->
                        Text(
                            text = "Downloading ${running.label}…",
                            color = c.ac,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))

                    if (showCard) {
                        GitHubSignInBody(
                            onSignedIn = { fresh ->
                                onToken(fresh)
                                signingIn = false
                                val retry = retryAfterSignIn
                                retryAfterSignIn = null
                                failure = null
                                authFailure = false
                                retry?.invoke(fresh)
                            },
                            onCancel = if (token == null) null else ({ signingIn = false }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        RepositoryList(
                            repos = repos,
                            busyRow = job?.rowKey,
                            disabled = job != null,
                            onPick = { repo ->
                                startClone(
                                    displayName = repo.fullName.substringAfter('/'),
                                    remoteUrl = repo.cloneUrl,
                                    tokenUsed = token,
                                    rowKey = repo.fullName,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A failure, said plainly, with the one action that would actually change the answer.
 *
 * The sign-in half only appears when signing in is not already on screen — offering it over a
 * card that is right there would just be a second way to press the same thing.
 */
@Composable
private fun CloneFailure(
    message: String,
    authLabel: String?,
    onAuthenticate: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.red.copy(alpha = 0.10f))
            .border(1.dp, c.red.copy(alpha = 0.45f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = c.red,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        if (authLabel != null) {
            Spacer(Modifier.width(10.dp))
            SignInAction(label = authLabel, primary = true, onClick = onAuthenticate)
        }
    }
}

/** The signed-in shelf: every repository the account can reach, most recently pushed first. */
@Composable
private fun RepositoryList(
    repos: List<GitHubRepo>?,
    busyRow: String?,
    disabled: Boolean,
    onPick: (GitHubRepo) -> Unit,
) {
    val c = LocalPageColors.current
    val pad = Modifier.fillMaxWidth().padding(top = 2.dp)
    val listed = repos
    when {
        listed == null -> Text(
            text = "Asking GitHub for your repositories…",
            color = c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            modifier = pad,
        )

        listed.isEmpty() -> Text(
            text = "You have no repositories yet. Publish a project first and it will show up " +
                "here.",
            color = c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 19.sp,
            modifier = pad,
        )

        else -> Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Your repositories",
                color = c.mu,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 1.dp),
            )
            listed.forEach { repo ->
                RepoRow(
                    repo = repo,
                    busy = busyRow == repo.fullName,
                    disabled = disabled,
                    onClick = { onPick(repo) },
                )
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

/** The one button on this screen, matching the sheets': solid accent, grey while it cannot be used. */
@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) c.ac else c.panel2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) c.af else c.mu,
            fontSize = 13.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * The folder a remote is cloned into: the last path segment, with any trailing `.git` taken off.
 *
 * It only ever names the directory and then the project, so the shape of the answer matters more
 * than the shape of the input — a query string after the path, a fragment, a link copied with a
 * stray trailing slash all have to reduce to something a filesystem is happy to hold. Anything
 * that reduces to nothing (an empty line, a lone slash) falls back to a name rather than failing
 * the clone over it.
 */
internal fun folderNameFromRemote(url: String): String {
    val path = url.trim().substringBefore('?').substringBefore('#').trimEnd('/')
    return path.substringAfterLast('/').removeSuffix(".git").trim().ifBlank { "cloned" }
}
