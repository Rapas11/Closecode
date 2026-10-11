package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.ScreenCard
import com.closecode.ui.components.ScreenSectionLabel
import com.closecode.ui.components.StatusBadge
import com.closecode.ui.components.StatusKind
import com.closecode.ui.components.TextLink
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubException
import com.closecode.ui.github.GitHubRemote
import com.closecode.ui.github.GitHubUser
import com.closecode.ui.github.GitHubRepo
import com.closecode.ui.github.TokenStore
import com.closecode.ui.git.GitChange
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.git.GitCommit
import com.closecode.ui.git.GitService
import com.closecode.ui.git.GitSnapshot
import com.closecode.ui.git.splitDiff
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Source Control tab is showing. Kept as one value so a reload can never show half a state. */
private sealed interface GitUiState {
    object Loading : GitUiState
    object NoProject : GitUiState
    object NoRepository : GitUiState
    data class Failed(val message: String) : GitUiState
    data class Loaded(val snap: GitSnapshot) : GitUiState
}

/**
 * The push/pull line under the branch name, in plain words: where the commits are, not counts
 * and arrows. Pinned by test because it is the one sentence every Git beginner reads.
 */
internal fun gitStatusText(hasRemote: Boolean, ahead: Int, behind: Int): String = when {
    !hasRemote -> "Not published"
    behind > 0 && ahead > 0 -> "$behind to pull · $ahead to push"
    behind > 0 -> "$behind to pull"
    ahead > 0 -> "$ahead to push"
    else -> "Up to date"
}

/** A change path split for its row: the file name white, the folder small and gray after it. */
internal fun displayFolder(path: String): String = path.substringBeforeLast('/', "")

/**
 * The Source Control tab: local version control first, GitHub second.
 *
 * Reads and writes only the repository inside [projectRoot] through [GitService]. Everything
 * blocking runs off the main thread, and every failure comes back as a sentence on screen
 * rather than as a crash. Sign-in, remotes and pushing arrive with the GitHub client.
 */
@Composable
fun IdeGitPanel(
    projectName: String,
    projectRoot: File?,
    // Bumped by the editor whenever a file is written, created or deleted. CHANGES describes
    // the working tree, so a snapshot taken when the project opened would otherwise go on
    // showing files that have since been edited, reverted or removed.
    revision: Int = 0,
    // A clone builds a brand new project and leaves this one. This panel decides *when* one
    // happens and reports it; the route that runs it lives at screen level, and the caller owns
    // where the finished record lands. No token crosses this boundary: the route reads the store
    // itself, so a sign-in that happens over there is not this panel's to know about.
    onCloneRequest: () -> Unit = {},
    // Bumped whenever the stored token is written from outside this panel — the clone route signs
    // in too. Re-keying the read on it is what stops this panel pushing with a token that has
    // since been replaced, which would look exactly like the failure it replaced.
    tokenEpoch: Int = 0,
    // Opens a side-by-side diff of the file. The panel lives in the drawer, so the route itself
    // lives at screen level; this only says which path was tapped.
    onDiff: (String) -> Unit = {},
    // Opens a commit's before/after diff of one file. Same routing: which commit and path.
    onCommitDiff: (commitId: String, path: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()

    val git = remember(projectRoot) { projectRoot?.let { GitService(it) } }
    var state by remember(projectRoot) { mutableStateOf<GitUiState>(GitUiState.Loading) }
    var message by remember(projectRoot) { mutableStateOf("") }
    var busy by remember(projectRoot) { mutableStateOf(false) }
    var error by remember(projectRoot) { mutableStateOf<String?>(null) }
    var namingBranch by remember(projectRoot) { mutableStateOf(false) }
    var switchingBranch by remember(projectRoot) { mutableStateOf(false) }
    var confirmingDiscard by remember(projectRoot) { mutableStateOf(false) }
    var branches by remember(projectRoot) { mutableStateOf(emptyList<String>()) }
    // Composed deepest in the drawer, so Back dismisses these before the drawer's own chain
    // can close the drawer out from under them.
    androidx.activity.compose.BackHandler(
        enabled = namingBranch || switchingBranch || confirmingDiscard,
    ) {
        BackLog.press("git-drawer", "branch=dialogs")
        namingBranch = false
        switchingBranch = false
        confirmingDiscard = false
    }

    // GitHub. The token is read once from private preferences and kept in memory only for as
    // long as this panel is composed; everything that touches the network runs off the main
    // thread through githubAct.
    val context = LocalContext.current
    val tokenStore = remember(context) { TokenStore(context) }
    val gitHubClient = remember { GitHubClient() }
    var githubToken by remember(tokenEpoch) { mutableStateOf(tokenStore.read()) }
    var account by remember { mutableStateOf<GitHubUser?>(null) }
    // Everything below describes one project. Keyed by it so an error, a note or a linked
    // remote from the last project cannot travel into this one and look like its own.
    var remoteUrl by remember(projectRoot) { mutableStateOf<String?>(null) }
    var githubBusy by remember(projectRoot) { mutableStateOf(false) }
    var githubError by remember(projectRoot) { mutableStateOf<String?>(null) }
    var githubNote by remember(projectRoot) { mutableStateOf<String?>(null) }
    var signInOpen by remember { mutableStateOf(false) }

    var showRepos by remember(projectRoot) { mutableStateOf(false) }
    var repos by remember(projectRoot) { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    // Read straight from `refs/stash` rather than carried on GitSnapshot, so restoring a stash
    // shows up after the reload that follows it without reshaping every snapshot test.
    var hasStash by remember(projectRoot) { mutableStateOf(false) }
    // Commit history: up to 100 rows, newest first. Loaded with the snapshot so a commit
    // made here appears without reopening the tab; null until the first load finishes.
    var history by remember(projectRoot) { mutableStateOf<List<GitCommit>?>(null) }
    // How many history rows show: 50 first, the rest behind a Load-more button.
    var historyShown by remember(projectRoot) { mutableStateOf(50) }
    // The expanded commit, by id. Its file list loads on demand, not with the history.
    var historyOpen by remember(projectRoot) { mutableStateOf<String?>(null) }
    var historyFiles by remember(projectRoot) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    suspend fun reload() {
        val service = git
        if (service == null) {
            state = GitUiState.NoProject
            hasStash = false
            return
        }
        state = withContext(Dispatchers.IO) {
            if (!service.isVersioned()) {
                GitUiState.NoRepository
            } else {
                // Older projects were initialized before the app kept its rules out of the
                // working tree, so their first status lists generated files the user never
                // touched. Add them to the local exclude file, once.
                service.ensureLocalExcludes()
                val snap = service.snapshot()
                if (snap.isFailure) {
                    GitUiState.Failed(snap.exceptionOrNull()?.message ?: "Could not read the repository.")
                } else {
                    branches = service.branches().getOrElse { emptyList() }
                    history = service.history(100).getOrElse { emptyList() }
                    GitUiState.Loaded(snap.getOrThrow())
                }
            }
        }
        hasStash = withContext(Dispatchers.IO) { service.hasStash() }
        remoteUrl = withContext(Dispatchers.IO) {
            projectRoot?.let { GitHubRemote(it).originUrl() }
        }
    }

    fun actOn(block: GitService.() -> Result<*>) {
        val service = git ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { service.block().getOrThrow() } }
            error = outcome.exceptionOrNull()
                ?.let { describeFailure(it, "That git action did not finish.") }
            reload()
            busy = false
        }
    }

    fun githubAct(block: () -> Result<*>) {
        if (githubBusy) return
        githubBusy = true
        githubError = null
        githubNote = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block().getOrThrow() } }
            githubError = outcome.exceptionOrNull()
                ?.let { describeFailure(it, "GitHub did not finish that.") }
            githubNote = outcome.getOrNull() as? String
            remoteUrl = withContext(Dispatchers.IO) {
                projectRoot?.let { GitHubRemote(it).originUrl() }
            }
            // A push or a pull changes what CHANGES and HISTORY should say. Without this the
            // panel kept the snapshot it read when the project opened, so a successful push
            // left the history looking untouched.
            reload()
            githubBusy = false
        }
    }

    LaunchedEffect(projectRoot, revision) { reload() }

    LaunchedEffect(githubToken) {
        val token = githubToken
        account = if (token == null) {
            null
        } else {
            withContext(Dispatchers.IO) { gitHubClient.currentUser(token).getOrNull() }
        }
    }

    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            // Opaque so the file tree sitting underneath this panel in the drawer never shows
            // through while the Source Control tab is open.
            .background(c.panel)
            .verticalScroll(scroll)
            .padding(bottom = 20.dp),
    ) {
        when (val current = state) {
            GitUiState.NoProject -> PanelNote(
                title = "No project open",
                body = "Open a project to see its version control.",
            )

            GitUiState.Loading -> SectionLabel("VERSION CONTROL")

            GitUiState.NoRepository -> GitIntro(
                busy = busy,
                onTrack = { actOn { init() } },
                onClone = onCloneRequest,
                onSignIn = { signInOpen = true },
            )

            is GitUiState.Failed -> PanelNote(
                title = "Could not read the repository",
                body = current.message,
            )

            is GitUiState.Loaded -> {
                error?.let { ErrorLine(it) }
                BranchRow(
                    snap = current.snap,
                    status = gitStatusText(remoteUrl != null, current.snap.ahead, current.snap.behind),
                    branches = branches,
                    hasStash = hasStash,
                    hasChanges = !current.snap.isClean,
                    busy = busy,
                    onRefresh = { scope.launch { reload() } },
                    onSwitchBranch = { switchingBranch = true },
                    onNewBranch = { namingBranch = true },
                    onStash = { actOn { stash() } },
                    onRestoreStash = { actOn { stashPop() } },
                    onDiscardAll = { confirmingDiscard = true },
                )

                if (current.snap.isClean) {
                    // Nothing to stage, so no commit box either: the screen says done and stops.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 17.dp, end = 14.dp, top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CcIcon(CcIcons.Check, c.success, 16.dp)
                        Text(
                            text = "Everything is saved",
                            color = c.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                    }
                } else {
                    if (current.snap.conflicts.isNotEmpty()) {
                        ChangeGroup(
                            label = "Conflicts · ${current.snap.conflicts.size}",
                            rows = current.snap.conflicts.map {
                                GitChange(it, false, GitChangeKind.Deleted)
                            },
                            conflicted = true,
                            actionLabel = null,
                            onAction = null,
                            onRow = {},
                            interactive = false,
                        )
                    }
                    ChangeGroup(
                        label = "Staged · ${current.snap.staged.size}",
                        rows = current.snap.staged,
                        actionLabel = if (current.snap.staged.isEmpty()) null else "Unstage all",
                        onAction = { actOn { unstage(current.snap.staged.map { it.path }) } },
                        onRow = { change -> actOn { unstage(listOf(change.path)) } },
                        onDiff = onDiff,
                    )
                    val unstaged = current.snap.unstaged + current.snap.untracked
                    ChangeGroup(
                        label = "Changes · ${unstaged.size}",
                        rows = unstaged,
                        actionLabel = if (unstaged.isEmpty()) null else "Stage all",
                        onAction = { actOn { stage(unstaged.map { it.path }) } },
                        onRow = { change -> actOn { stage(listOf(change.path)) } },
                        onDiff = onDiff,
                    )
                }

                if (!current.snap.isClean) {
                    CommitBox(
                        value = message,
                        busy = busy,
                        staged = current.snap.staged.size,
                        onChange = { message = it },
                        onCommit = {
                            val text = message
                            actOn { commit(text).onSuccess { message = "" } }
                        },
                    )
                }

                if (current.snap.hasCommits) {
                    HistorySection(
                        commits = history,
                        shown = historyShown,
                        openId = historyOpen,
                        files = historyFiles,
                        onMore = { historyShown = (historyShown + 50).coerceAtMost(100) },
                        onFile = { id, path -> onCommitDiff(id, path) },
                        onToggle = { commit ->
                            historyOpen = if (historyOpen == commit.id) null else commit.id
                            if (historyOpen != null && historyFiles[commit.id] == null) {
                                val service = git
                                if (service != null) {
                                    scope.launch {
                                        val rows = withContext(Dispatchers.IO) {
                                            service.commitFiles(commit.id).getOrElse { emptyList() }
                                        }
                                        historyFiles = historyFiles + (commit.id to rows)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        // Outside the switch on purpose. Signing in, cloning and publishing are what most people
        // open this tab for, and none of them need a repository to exist yet — which is exactly
        // the state someone is in when they want to clone a different one.
        val hasCommits = (state as? GitUiState.Loaded)?.snap?.hasCommits == true
        val snap = (state as? GitUiState.Loaded)?.snap
        GitHubSection(
            projectName = projectName,
            token = githubToken,
            account = account,
            remoteUrl = remoteUrl,
            hasCommits = hasCommits,
            toPull = snap?.behind ?: 0,
            toPush = snap?.ahead ?: 0,
            busy = githubBusy,
            error = githubError,
            note = githubNote,
            showRepos = showRepos,
            repos = repos,
            onToggleRepos = {
                showRepos = !showRepos
                if (showRepos && repos.isEmpty()) {
                    val token = githubToken
                    if (token != null) {
                        githubAct {
                            gitHubClient.repositories(token).onSuccess { repos = it }
                        }
                    }
                }
            },
            onSignIn = { signInOpen = true },
            onClone = { onCloneRequest() },
            onSignOut = {
                tokenStore.clear()
                githubToken = null
                account = null
                repos = emptyList()
                showRepos = false
                githubNote = null
                githubError = null
            },
            onPublish = {
                val token = githubToken
                val root = projectRoot
                if (token != null && root != null) {
                    githubAct {
                        val created = gitHubClient
                            .createRepository(token, projectName, isPrivate = true)
                            .getOrElse { failure ->
                                // 422 is GitHub saying the name is taken on this account —
                                // usually the same Publish pressed twice. Link what is
                                // already there instead of failing the second time.
                                val taken = (failure as? GitHubException)?.code == 422
                                if (!taken) throw failure
                                gitHubClient.repositories(token).getOrThrow()
                                    .firstOrNull {
                                        it.fullName.substringAfter('/') == projectName
                                    } ?: throw failure
                            }
                        GitHubRemote(root).link(created.cloneUrl).getOrThrow()
                        GitHubRemote(root).push(token)
                    }
                }
            },
            onPush = {
                val token = githubToken
                val root = projectRoot
                if (token != null && root != null) {
                    githubAct { GitHubRemote(root).push(token) }
                }
            },
            onPull = {
                val token = githubToken
                val root = projectRoot
                if (token != null && root != null) {
                    githubAct { GitHubRemote(root).pull(token) }
                }
            },
            onLink = { repo ->
                val root = projectRoot
                if (root != null) {
                    showRepos = false
                    githubAct { GitHubRemote(root).link(repo.cloneUrl).map { "Linked ${repo.fullName}." } }
                }
            },
        )
    }

    if (namingBranch && state is GitUiState.Loaded) {
        IdeNameDialog(
            title = "New branch",
            hint = "branch-name",
            onCreate = { name ->
                namingBranch = false
                actOn { createBranch(name) }
            },
            onDismiss = { namingBranch = false },
        )
    }

    if (switchingBranch && state is GitUiState.Loaded) {
        BranchDialog(
            current = (state as GitUiState.Loaded).snap.branch,
            branches = branches,
            onSwitch = { name ->
                switchingBranch = false
                actOn { checkout(name) }
            },
            onDismiss = { switchingBranch = false },
        )
    }

    if (confirmingDiscard && state is GitUiState.Loaded) {
        val snap = (state as GitUiState.Loaded).snap
        val paths = snap.staged.map { it.path } + snap.unstaged.map { it.path } +
            snap.untracked.map { it.path }
        IdeConfirmDialog(
            title = "Throw away ${paths.size} ${if (paths.size == 1) "change" else "changes"}?",
            message = "Every edit goes back to the last commit, and new files are deleted. " +
                "This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmingDiscard = false
                actOn { discard(paths) }
            },
            onDismiss = { confirmingDiscard = false },
        )
    }

    if (signInOpen) {
        GitHubSignIn(
            onSignedIn = { token ->
                tokenStore.write(token)
                githubToken = token
                signInOpen = false
            },
            onDismiss = { signInOpen = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun PanelNote(title: String, body: String) {
    val c = LocalPageColors.current
    Column(
        Modifier.padding(start = 17.dp, end = 14.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ErrorLine(message: String) {
    val c = LocalPageColors.current
    Text(
        text = message,
        color = c.red,
        fontSize = 11.sp,
        fontFamily = Sans,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 10.dp),
    )
}

/**
 * The first-time screen: a project with no Git history yet. One intro, three option cards,
 * nothing else. "Initialize repository" and "Not under version control" must never appear —
 * the first card says what happens in the words a beginner reads.
 */
@Composable
private fun GitIntro(
    busy: Boolean,
    onTrack: () -> Unit,
    onClone: () -> Unit,
    onSignIn: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.primaryTint),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Branch, c.link, 24.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Get started with Git",
            color = c.text,
            fontSize = 17.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Git saves versions of your project so you can undo mistakes and back it up online.",
            color = c.textMuted,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(16.dp))
        // The one primary action: start tracking. The other two are plain cards.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.primary)
                .clickable(enabled = !busy, onClick = onTrack)
                .padding(12.dp),
        ) {
            IntroCardRow(
                tileBg = Color.White.copy(alpha = 0.2f),
                tileFg = Color.White,
                icon = CcIcons.Play,
                title = "Start tracking this project",
                subtitle = if (busy) "Working…" else "Create a Git history on this device",
                titleColor = Color.White,
                subtitleColor = Color.White.copy(alpha = 0.85f),
                chevronColor = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        IntroCard(
            icon = CcIcons.CloudDownload,
            title = "Clone a repository",
            subtitle = "Download a project from GitHub",
            onClick = onClone,
        )
        Spacer(Modifier.height(12.dp))
        IntroCard(
            icon = CcIcons.Github,
            title = "Sign in with GitHub",
            subtitle = "Back up and sync your projects",
            onClick = onSignIn,
        )
    }
}

@Composable
private fun IntroCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.surfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        IntroCardRow(
            tileBg = c.chipBg,
            tileFg = c.textMuted,
            icon = icon,
            title = title,
            subtitle = subtitle,
            titleColor = c.text,
            subtitleColor = c.textMuted,
            chevronColor = c.textFaint,
        )
    }
}

@Composable
private fun IntroCardRow(
    tileBg: androidx.compose.ui.graphics.Color,
    tileFg: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color,
    subtitleColor: androidx.compose.ui.graphics.Color,
    chevronColor: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tileBg),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, tileFg, 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 11.sp,
                fontFamily = Sans,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CcIcon(CcIcons.Chevron, chevronColor, 16.dp)
    }
}

/**
 * The branch row: branch icon and name on the left, refresh plus the plain-words status on the
 * right, and a three-dot menu holding everything that is not staging, committing or pushing —
 * switch and new branch, stash and restore, discard all.
 */
@Composable
private fun BranchRow(
    snap: GitSnapshot,
    status: String,
    branches: List<String>,
    hasStash: Boolean,
    hasChanges: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onSwitchBranch: () -> Unit,
    onNewBranch: () -> Unit,
    onStash: () -> Unit,
    onRestoreStash: () -> Unit,
    onDiscardAll: () -> Unit,
) {
    val c = LocalPageColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CcIcon(CcIcons.Branch, c.textMuted, 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = snap.branch,
            color = c.text,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (snap.detached) {
            Text(
                text = "detached HEAD",
                color = c.warning,
                fontSize = 10.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        // A 30 dp touch box with a 16 dp glyph inside: the thumb gets room, the eye gets the
        // small icon from the mockup. Passing a big box *as* the icon's own modifier stretches
        // the glyph to the box, which is how the refresh drew huge.
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Refresh, c.textMuted, 16.dp)
        }
        Text(
            text = status,
            color = c.textMuted,
            fontSize = 11.sp,
            fontFamily = Sans,
            maxLines = 1,
            modifier = Modifier.padding(end = 2.dp),
        )
        Box {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.More, c.textMuted, 16.dp)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Switch branch", fontFamily = Sans) },
                    enabled = branches.size > 1,
                    onClick = { menuOpen = false; onSwitchBranch() },
                )
                DropdownMenuItem(
                    text = { Text("New branch", fontFamily = Sans) },
                    onClick = { menuOpen = false; onNewBranch() },
                )
                if (hasChanges && !busy) {
                    DropdownMenuItem(
                        text = { Text("Stash changes", fontFamily = Sans) },
                        onClick = { menuOpen = false; onStash() },
                    )
                }
                if (hasStash && !busy) {
                    DropdownMenuItem(
                        text = { Text("Restore stash", fontFamily = Sans) },
                        onClick = { menuOpen = false; onRestoreStash() },
                    )
                }
                if (hasChanges && !busy) {
                    DropdownMenuItem(
                        text = { Text("Discard all changes", fontFamily = Sans, color = c.error) },
                        onClick = { menuOpen = false; onDiscardAll() },
                    )
                }
            }
        }
    }
}

/** Every branch, with a check on the current one. Tapping one switches to it. */
@Composable
private fun BranchDialog(
    current: String,
    branches: List<String>,
    onSwitch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch branch", fontSize = 17.sp, fontFamily = Sans) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                branches.forEach { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSwitch(name) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            color = c.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = if (name == current) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (name == current) CcIcon(CcIcons.Check, c.success, 16.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * One list inside the Changes section — staged, changes or conflicts — with a bulk action in
 * its header. Groups with nothing in them disappear entirely. Tapping a row opens its diff;
 * the round button on the right stages or unstages.
 */
@Composable
private fun ChangeGroup(
    label: String,
    rows: List<GitChange>,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onRow: (GitChange) -> Unit,
    onDiff: (String) -> Unit = {},
    conflicted: Boolean = false,
    interactive: Boolean = true,
) {
    val c = LocalPageColors.current
    if (rows.isEmpty()) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScreenSectionLabel(label, Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextLink(actionLabel, onClick = onAction)
        }
    }

    rows.forEach { change ->
        ChangeRow(
            kind = change.kind,
            path = change.path,
            staged = change.staged,
            conflicted = conflicted,
            interactive = interactive,
            onClick = { onDiff(change.path) },
            onToggle = { onRow(change) },
        )
    }
}

@Composable
private fun ChangeRow(
    kind: GitChangeKind,
    path: String,
    staged: Boolean,
    conflicted: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val c = LocalPageColors.current
    val name = path.substringAfterLast('/')
    val folder = displayFolder(path)
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clickable(enabled = interactive, onClick = onClick)
            .padding(start = 17.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conflicted) {
            // Conflicted files sort first with a red C: the letter is the alarm here, so the
            // badge borrows the error colors rather than inventing new ones.
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(c.statusDeletedBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "C",
                    color = c.error,
                    fontSize = 10.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            StatusBadge(
                when (kind) {
                    GitChangeKind.Added -> StatusKind.Added
                    GitChangeKind.Modified -> StatusKind.Modified
                    GitChangeKind.Deleted -> StatusKind.Deleted
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder.isNotEmpty()) {
                Text(
                    text = folder,
                    color = c.textMuted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (interactive) {
            // Round, 26 dp, minus to unstage and plus to stage — the row's tap opens the diff,
            // so the button owns the stage action alone and never fights the row over a tap.
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c.chipBg)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (staged) "−" else "+",
                    color = c.text,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CommitBox(
    value: String,
    busy: Boolean,
    staged: Int,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier.padding(start = 17.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = !busy,
            textStyle = TextStyle(
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                lineHeight = 18.sp,
            ),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.fieldBg)
                .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Message: what did you change?",
                            color = c.textFaint,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                    }
                    inner()
                }
            },
        )
        // The one primary button: gray and dead until a message and a staged file both exist.
        PrimaryButton(
            label = when {
                busy -> "Working…"
                else -> "Commit $staged ${if (staged == 1) "file" else "files"}"
            },
            enabled = staged > 0 && value.isNotBlank() && !busy,
            onClick = onCommit,
        )
    }
}

/**
 * The GitHub half of the panel: sign in, publish, pull and push.
 *
 * Everything here is downstream of a local repository — a project with no commits has nothing
 * to publish, and a project with no remote has nothing to push to — so each action states its
 * own precondition rather than failing after the press.
 */
@Composable
private fun GitHubSection(
    projectName: String,
    token: String?,
    account: GitHubUser?,
    remoteUrl: String?,
    hasCommits: Boolean,
    toPull: Int,
    toPush: Int,
    busy: Boolean,
    error: String?,
    note: String?,
    showRepos: Boolean,
    repos: List<GitHubRepo>,
    onToggleRepos: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPublish: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onLink: (GitHubRepo) -> Unit,
    onClone: () -> Unit,
) {
    val c = LocalPageColors.current
    // The section says what it is *and* whether it can do anything. "GITHUB" alone reads like a
    // heading over a panel that is about to work; with no token it is a heading over a sign-in
    // button, and saying so saves the tap that finds that out.
    SectionLabel(if (token == null) "GITHUB · NOT CONNECTED" else "GITHUB")

    error?.let { ErrorLine(it) }
    note?.let {
        Text(
            text = it,
            color = c.success,
            fontSize = 11.sp,
            fontFamily = Sans,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 8.dp),
        )
    }

    if (token == null) {
        // The card the mockup shows: what is missing, why it matters, connect, and a quiet
        // clone door underneath. Shown only when not signed in.
        Column(Modifier.padding(start = 17.dp, end = 14.dp, top = 16.dp)) {
            ScreenCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(CcIcons.Github, c.text, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "GitHub is not connected",
                        color = c.text,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Connect it to back up this project online and open it on other devices.",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton(label = "Connect GitHub", onClick = onSignIn)
                Spacer(Modifier.height(4.dp))
                TextLink(label = "Clone a repository", onClick = onClone)
            }
        }
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "@${account?.login ?: "signed in"}",
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Sign out",
            color = c.ac,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onSignOut),
        )
    }

    Column(
        Modifier.padding(start = 17.dp, end = 14.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (remoteUrl == null) {
            if (!hasCommits) {
                Text(
                    text = "Commit something first — a repository with no history has nothing to publish.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
            } else {
                Text(
                    text = "A private repository named \"$projectName\" will be created under " +
                        "${account?.login ?: "your account"} and this project pushed to it.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
                OutlineButton(
                    label = if (busy) "Working…" else "Publish to GitHub",
                    enabled = !busy,
                    onClick = onPublish,
                )
            }
        } else {
            if (!hasCommits) {
                Text(
                    text = "Commit something first — a repository with no history has nothing to push.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        label = if (toPull > 0) "Pull · $toPull" else "Pull",
                        enabled = !busy,
                        onClick = onPull,
                    )
                }
                // Push with an unborn HEAD is a guaranteed transport failure; say so up front
                // rather than handing the user JGit's opaque wrapper.
                Box(Modifier.weight(1f)) {
                    OutlineButton(
                        label = if (toPush > 0) "Push · $toPush" else "Push",
                        enabled = !busy && hasCommits,
                        onClick = onPush,
                    )
                }
            }
            Text(
                text = remoteUrl,
                color = c.mu,
                fontSize = 10.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = if (showRepos) "Hide repositories" else "Use an existing repository",
            color = c.ac,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onToggleRepos),
        )
        Text(
            text = "Clone a repository",
            color = c.ac,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(enabled = !busy, onClick = onClone),
        )
    }

    if (showRepos) {
        if (repos.isEmpty()) {
            Text(
                text = "Loading your repositories…",
                color = c.mu,
                fontSize = 11.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            )
        } else {
            repos.forEach { repo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clickable(enabled = !busy) { onLink(repo) }
                        .padding(start = 17.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = repo.fullName,
                        color = c.tx,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (repo.isPrivate) "private" else "public",
                        color = c.mu,
                        fontSize = 10.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * A side-by-side diff of one file. Working-tree mode (no commit id): HEAD on the left,
 * the working tree on the right. Commit mode: the commit's parent on the left, the commit
 * itself on the right — the before and after of that commit. Changed rows tint green on
 * the new side and red on the old side; added and removed lines show on their side only.
 * Each side scrolls sideways on its own (one shared state per side, so all rows travel
 * together) while the rows scroll vertically as one.
 */
@Composable
internal fun DiffScreen(projectRoot: File?, path: String, onBack: () -> Unit, commitId: String? = null) {
    val c = LocalPageColors.current
    var rows by remember(path, commitId) { mutableStateOf<List<com.closecode.ui.git.DiffRow>?>(null) }
    var failed by remember(path, commitId) { mutableStateOf(false) }
    var binary by remember(path, commitId) { mutableStateOf(false) }

    LaunchedEffect(path, commitId) {
        val root = projectRoot
        if (root == null) {
            failed = true
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val id = commitId
                if (id == null) {
                    val old = GitService(root).fileAtHead(path).getOrNull()
                    val file = File(root, path)
                    old to if (file.isFile) file.readBytes() else ByteArray(0)
                } else {
                    val sides = GitService(root).commitFileContents(id, path).getOrNull()
                    (sides?.first) to (sides?.second ?: ByteArray(0))
                }
            }.getOrNull()
        }
        if (loaded == null) {
            failed = true
            return@LaunchedEffect
        }
        // A nul byte in the probed head means binary: decoded text would be garbage, and a
        // wall of replacement characters is not a diff.
        if (loaded.first?.take(8000)?.any { it == 0.toByte() } == true ||
            loaded.second.take(8000).any { it == 0.toByte() }
        ) {
            binary = true
            return@LaunchedEffect
        }
        rows = splitDiff(
            loaded.first?.toString(Charsets.UTF_8).orEmpty(),
            loaded.second.toString(Charsets.UTF_8),
        )
    }

    BackHandler(onBack = {
        BackLog.press("git-diff", "branch=close")
        onBack()
    })
    Column(
        Modifier
            .fillMaxSize()
            .background(c.panel),
    ) {
        val current = rows
        val added = current?.count { it.newLine != null && it.newLine != it.oldLine } ?: 0
        val removed = current?.count { it.oldLine != null && it.oldLine != it.newLine } ?: 0
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(
                    CcIcons.Chevron,
                    c.textMuted,
                    18.dp,
                    Modifier.graphicsLayer { rotationZ = 180f },
                )
            }
            Text(
                text = if (commitId == null) path.substringAfterLast('/') else
                    "${path.substringAfterLast('/')} · ${commitId.take(7)}",
                color = c.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (current != null) {
                Text(
                    text = "+$added −$removed",
                    color = c.textFaint,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                )
            }
        }
        when {
            failed -> DiffNote("Could not read the diff.")
            binary -> DiffNote("This file is not text, so there is nothing to show side by side.")
            current == null -> DiffNote("Reading the diff…")
            current.isEmpty() -> DiffNote("No differences.")
            else -> {
                // One sideways state per side: every row's cell shares its side's state, so a
                // long line scrolled on one row stays readable while the rows move together.
                val leftScroll = rememberScrollState()
                val rightScroll = rememberScrollState()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        current.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                DiffCell(
                                    text = row.oldLine,
                                    changed = row.oldLine != row.newLine,
                                    isOld = true,
                                    scroll = leftScroll,
                                )
                                DiffCell(
                                    text = row.newLine,
                                    changed = row.oldLine != row.newLine,
                                    isOld = false,
                                    scroll = rightScroll,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Commit history: newest first, up to 100 rows with 50 shown before Load more.
 * Tapping a row expands it to the full id, the date and the files that commit
 * touched, so the reader sees what changed and when without leaving the drawer.
 */
@Composable
private fun HistorySection(
    commits: List<GitCommit>?,
    shown: Int,
    openId: String?,
    files: Map<String, List<String>>,
    onMore: () -> Unit,
    onToggle: (GitCommit) -> Unit,
    onFile: (commitId: String, path: String) -> Unit = { _, _ -> },
) {
    val c = LocalPageColors.current
    SectionLabel("HISTORY · ${commits?.size ?: 0}")
    when {
        commits == null -> {
            Text(
                text = "Loading history…",
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            )
        }
        commits.isEmpty() -> {
            Text(
                text = "No commits yet. Commit staged files to start the history.",
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            )
        }
        else -> {
            commits.take(shown.coerceAtMost(commits.size)).forEach { commit ->
                val open = openId == commit.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onToggle(commit) })
                        .padding(start = 17.dp, end = 14.dp, top = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = commit.shortId,
                            color = c.accent,
                            fontSize = 11.sp,
                            fontFamily = Mono,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = commit.summary,
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            maxLines = if (open) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "${commit.author} · ${relativeTime(commit.whenMillis)}",
                        color = c.textMuted,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (open) {
                        Text(
                            text = commit.id,
                            color = c.textFaint,
                            fontSize = 10.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val rows = files[commit.id]
                        when {
                            rows == null -> {
                                Text(
                                    text = "Loading files…",
                                    color = c.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = Sans,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            rows.isEmpty() -> {
                                Text(
                                    text = "No file changes listed for this commit.",
                                    color = c.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = Sans,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            else -> {
                                rows.forEach { path ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = { onFile(commit.id, path) })
                                            .padding(top = 2.dp),
                                    ) {
                                        Text(
                                            text = path,
                                            color = c.accent,
                                            fontSize = 11.sp,
                                            fontFamily = Mono,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        CcIcon(
                                            CcIcons.Chevron,
                                            c.textFaint,
                                            14.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (shown < commits.size) {
                Text(
                    text = "Show more (${commits.size - shown} older)",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onMore)
                        .padding(start = 17.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                )
            }
        }
    }
}

private fun relativeTime(whenMillis: Long): String {
    val minutes = ((System.currentTimeMillis() - whenMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

@Composable
private fun DiffNote(text: String) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = c.textMuted,
            fontSize = 13.sp,
            fontFamily = Sans,
        )
    }
}

@Composable
private fun RowScope.DiffCell(
    text: String?,
    changed: Boolean,
    isOld: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val c = LocalPageColors.current
    val bg = when {
        text == null -> c.fieldBg
        !changed -> androidx.compose.ui.graphics.Color.Transparent
        isOld -> c.statusDeletedBg
        else -> c.statusAddedBg
    }
    Box(
        Modifier
            .weight(1f)
            .background(bg)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text.orEmpty(),
            color = if (text == null) c.textFaint else c.text,
            fontSize = 11.sp,
            fontFamily = Mono,
            softWrap = false,
            maxLines = 1,
        )
    }
}
