package com.closecode.ui.ide

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Source Control tab is showing. Kept as one value so a reload can never show half a state. */
private sealed interface GitUiState {
    object Loading : GitUiState
    object NoProject : GitUiState
    object NoRepository : GitUiState
    data class Failed(val message: String) : GitUiState
    data class Loaded(val snap: GitSnapshot, val history: List<GitCommit>) : GitUiState
}

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
                // Older projects were initialized before the app wrote a .gitignore, so their
                // first status lists generated files the user never touched. Create it once.
                service.ensureGitignore()
                val snap = service.snapshot()
                if (snap.isFailure) {
                    GitUiState.Failed(snap.exceptionOrNull()?.message ?: "Could not read the repository.")
                } else {
                    val history = service.history(HISTORY_LIMIT).getOrElse { emptyList() }
                    GitUiState.Loaded(snap.getOrThrow(), history)
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

            GitUiState.NoRepository -> Column(
                Modifier.padding(start = 17.dp, end = 14.dp, top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Not under version control",
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "This project has no git repository yet. Initialize one to track changes, keep a history and switch between branches. Nothing leaves this device.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(4.dp))
                PanelButton(
                    label = if (busy) "Working…" else "Initialize repository",
                    enabled = !busy,
                ) { actOn { init() } }
            }

            is GitUiState.Failed -> PanelNote(
                title = "Could not read the repository",
                body = current.message,
            )

            is GitUiState.Loaded -> {
                error?.let { ErrorLine(it) }
                BranchBar(
                    snap = current.snap,
                    remoteUrl = remoteUrl,
                    onNewBranch = { namingBranch = true },
                )

                SectionLabel("CHANGES")
                if (current.snap.isClean) {
                    PanelNote(
                        title = "Working tree clean",
                        body = "Nothing to commit. Make an edit and it will show up here.",
                    )
                } else {
                    if (current.snap.conflicts.isNotEmpty()) {
                        ChangeGroup(
                            label = "Conflicts · ${current.snap.conflicts.size}",
                            rows = current.snap.conflicts.map {
                                GitChange(it, false, GitChangeKind.Deleted)
                            },
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
                    )
                    ChangeGroup(
                        label = "Changes · ${current.snap.unstaged.size}",
                        rows = current.snap.unstaged,
                        actionLabel = if (current.snap.unstaged.isEmpty()) null else "Stage all",
                        onAction = { actOn { stage(current.snap.unstaged.map { it.path }) } },
                        onRow = { change -> actOn { stage(listOf(change.path)) } },
                    )
                    ChangeGroup(
                        label = "Untracked · ${current.snap.untracked.size}",
                        rows = current.snap.untracked,
                        actionLabel = if (current.snap.untracked.isEmpty()) null else "Stage all",
                        onAction = { actOn { stage(current.snap.untracked.map { it.path }) } },
                        onRow = { change -> actOn { stage(listOf(change.path)) } },
                    )
                }

                // Stash sits with the changes it would clear, because that is where a reader
                // looking at a dirty tree is already looking.
                if (!current.snap.isClean || hasStash) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!current.snap.isClean && current.snap.conflicts.isEmpty()) {
                            PanelButton(
                                label = "Stash",
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { actOn { stash() } }
                        }
                        if (hasStash) {
                            PanelButton(
                                label = "Restore stash",
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { actOn { stashPop() } }
                        }
                    }
                }

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

                if (current.history.isNotEmpty()) {
                    SectionLabel("HISTORY")
                    current.history.forEach { entry -> HistoryRow(entry) }
                }
            }
        }

        // Outside the switch on purpose. Signing in, cloning and publishing are what most people
        // open this tab for, and none of them need a repository to exist yet — which is exactly
        // the state someone is in when they want to clone a different one.
        val hasCommits = (state as? GitUiState.Loaded)?.snap?.hasCommits == true
        GitHubSection(
            projectName = projectName,
            token = githubToken,
            account = account,
            remoteUrl = remoteUrl,
            hasCommits = hasCommits,
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

@Composable
private fun BranchBar(snap: GitSnapshot, remoteUrl: String?, onNewBranch: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (snap.hasCommits) c.success else c.mu),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = snap.branch,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (snap.detached) {
            Spacer(Modifier.width(6.dp))
            Text("detached HEAD", color = c.amber, fontSize = 10.sp, fontFamily = Sans)
        }
        if (snap.ahead > 0) {
            Spacer(Modifier.width(6.dp))
            CountPill("↑${snap.ahead}", c.ac)
        }
        if (snap.behind > 0) {
            Spacer(Modifier.width(6.dp))
            CountPill("↓${snap.behind}", c.amber)
        }
        if (snap.hasCommits) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c.panel2)
                    .clickable(onClick = onNewBranch),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = c.tx, fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium)
            }
        }
    }
    // Reads the remote we actually know about: ahead 0 and behind 0 with a remote configured
    // means the push landed, and saying "no remote yet" there was simply wrong.
    if (snap.hasCommits && snap.ahead == 0 && snap.behind == 0) {
        Text(
            text = if (remoteUrl == null) "Local only · no remote yet" else "Up to date with origin",
            color = c.mu.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(start = 33.dp, top = 4.dp),
        )
    }
}

@Composable
private fun CountPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 10.sp, fontFamily = Sans, fontWeight = FontWeight.Bold)
    }
}

/**
 * One list inside the Changes section — staged, changes, untracked or conflicts — with a bulk
 * action in its header. Groups with nothing in them disappear entirely.
 */
@Composable
private fun ChangeGroup(
    label: String,
    rows: List<GitChange>,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onRow: (GitChange) -> Unit,
    interactive: Boolean = true,
) {
    val c = LocalPageColors.current
    if (rows.isEmpty()) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = c.ac,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }

    rows.forEach { change ->
        ChangeRow(
            kind = change.kind,
            path = change.path,
            staged = change.staged,
            interactive = interactive,
            onClick = { onRow(change) },
        )
    }
}

@Composable
private fun ChangeRow(
    kind: GitChangeKind,
    path: String,
    staged: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val kindColor = when (kind) {
        GitChangeKind.Added -> c.success
        GitChangeKind.Modified -> c.amber
        GitChangeKind.Deleted -> c.red
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 17.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = kindLetter(kind),
            color = kindColor,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = path,
            color = c.tx,
            fontSize = 12.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (interactive) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(c.panel2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (staged) "−" else "+",
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun kindLetter(kind: GitChangeKind): String = when (kind) {
    GitChangeKind.Added -> "A"
    GitChangeKind.Modified -> "M"
    GitChangeKind.Deleted -> "D"
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
        Modifier.padding(start = 17.dp, end = 14.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "COMMIT",
            color = c.mu.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = !busy,
            textStyle = TextStyle(
                color = c.tx,
                fontSize = 12.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
            ),
            cursorBrush = SolidColor(c.ac),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(c.bg)
                .border(1.dp, c.ln, RoundedCornerShape(9.dp))
                .padding(10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Describe this change",
                            color = c.mu,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                    inner()
                }
            },
        )
        val canCommit = staged > 0 && value.isNotBlank() && !busy
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (canCommit) c.ac.copy(alpha = 0.16f) else c.panel2)
                .border(
                    1.dp,
                    if (canCommit) c.ac else c.ln,
                    RoundedCornerShape(10.dp),
                )
                .then(if (canCommit) Modifier.clickable(onClick = onCommit) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    busy -> "Working…"
                    staged == 0 -> "Nothing staged"
                    else -> "Commit $staged ${if (staged == 1) "file" else "files"}"
                },
                color = if (canCommit) c.ac else c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: GitCommit) {
    val c = LocalPageColors.current
    val stamp = remember(entry.id) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.whenMillis))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 14.dp, top = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.shortId,
                color = c.ac,
                fontSize = 10.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(58.dp),
            )
            Text(
                text = entry.summary,
                color = c.tx,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "${entry.author} · $stamp",
            color = c.mu.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontFamily = Sans,
        )
    }
}

/** A full-width, low-contrast action that reads as a button without shouting. */
@Composable
private fun PanelButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) c.ac.copy(alpha = 0.16f) else c.panel2)
            .border(1.dp, if (enabled) c.ac else c.ln, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) c.ac else c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
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
        Column(
            Modifier.padding(start = 17.dp, end = 14.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Publish this project to GitHub, or link one you already have. Signing in " +
                    "opens github.com/login/device — nothing else leaves the device.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
            )
            PanelButton(label = if (busy) "Working…" else "Sign in with GitHub", enabled = !busy, onClick = onSignIn)
            // A clone never needs *this* project's repository — it makes its own — so it sits
            // beside sign-in rather than inside the signed-in-only controls below.
            Text(
                text = "Clone a repository",
                color = c.ac,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = !busy, onClick = onClone),
            )
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
                PanelButton(
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
                PanelButton(
                    label = if (busy) "…" else "Pull",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onPull,
                )
                // Push with an unborn HEAD is a guaranteed transport failure; say so up front
                // rather than handing the user JGit's opaque wrapper.
                PanelButton(
                    label = if (busy) "…" else "Push",
                    enabled = !busy && hasCommits,
                    modifier = Modifier.weight(1f),
                    onClick = onPush,
                )
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

private const val HISTORY_LIMIT = 30
