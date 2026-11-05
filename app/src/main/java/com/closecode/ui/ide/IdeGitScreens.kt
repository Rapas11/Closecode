package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.closecode.ui.components.DesignSectionLabel
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.git.GitCommit
import com.closecode.ui.git.GitService
import com.closecode.ui.git.GitSnapshot
import com.closecode.ui.git.splitDiff
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubRemote
import com.closecode.ui.github.GitHubUser
import com.closecode.ui.github.TokenStore
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 14/15: the GitHub rail panel. Connected, it shows the link, Pull/Push, the files
 * waiting to be committed, the commit box and the newest history. Signed out, it is only a
 * centred connect card. Real repository state drives every row; nothing here is a mock list.
 */
@Composable
fun IdeGitPanel(
    projectName: String,
    projectRoot: java.io.File?,
    revision: Int = 0,
    onCloneRequest: () -> Unit = {},
    tokenEpoch: Int = 0,
    onDiff: (String) -> Unit = {},
    onCommitDiff: (commitId: String, path: String) -> Unit = { _, _ -> },
    onHistory: () -> Unit = {},
    onCommitOpen: (commitId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tokenStore = remember(context) { TokenStore(context) }
    val client = remember { GitHubClient() }

    var token by remember(tokenEpoch) { mutableStateOf(tokenStore.read()) }
    var account by remember { mutableStateOf<GitHubUser?>(null) }
    var snapshot by remember(projectRoot) { mutableStateOf<GitSnapshot?>(null) }
    var history by remember(projectRoot) { mutableStateOf<List<GitCommit>?>(null) }
    var remoteUrl by remember(projectRoot) { mutableStateOf<String?>(null) }
    var loading by remember(projectRoot) { mutableStateOf(true) }
    var busy by remember(projectRoot) { mutableStateOf(false) }
    var error by remember(projectRoot) { mutableStateOf<String?>(null) }
    var note by remember(projectRoot) { mutableStateOf<String?>(null) }
    var message by remember(projectRoot) { mutableStateOf("") }
    var selected by remember(projectRoot) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedKey by remember(projectRoot) { mutableStateOf("") }
    var signInOpen by remember { mutableStateOf(false) }

    suspend fun reload() {
        val root = projectRoot
        if (root == null) {
            snapshot = null
            history = emptyList()
            remoteUrl = null
            loading = false
            return
        }
        val loaded = withContext(Dispatchers.IO) {
            val git = GitService(root)
            val snap = if (git.isVersioned()) git.snapshot().getOrNull() else null
            val commits = if (snap != null) {
                git.history(30).getOrElse { emptyList() }
            } else {
                emptyList()
            }
            Triple(snap, commits, GitHubRemote(root).originUrl())
        }
        snapshot = loaded.first
        history = loaded.second
        remoteUrl = loaded.third
        loading = false
    }

    LaunchedEffect(projectRoot, revision) { reload() }

    LaunchedEffect(token) {
        val current = token
        account = if (current == null) {
            null
        } else {
            withContext(Dispatchers.IO) { client.currentUser(current).getOrNull() }
        }
    }

    val changes = remember(snapshot) { snapshot?.let { gitAllChanges(it) } ?: emptyList() }
    val changesKey = remember(changes) { changes.joinToString("|") { it.path } }
    LaunchedEffect(changesKey) {
        if (changesKey.isEmpty()) {
            selected = emptySet()
            selectedKey = ""
        } else if (changesKey != selectedKey) {
            selected = changes.map { it.path }.toSet()
            selectedKey = changesKey
        } else {
            selected = selected.intersect(changes.map { it.path }.toSet())
        }
    }

    fun remoteAction(label: String, block: (String) -> Result<String>) {
        val root = projectRoot
        val current = token
        if (root == null || current == null || busy) return
        busy = true
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block(current).getOrThrow() } }
            error = outcome.exceptionOrNull()?.let { describeFailure(it, "$label did not finish.") }
            note = outcome.getOrNull()
            reload()
            busy = false
        }
    }

    fun commitChanges() {
        val root = projectRoot
        if (root == null || busy || selected.isEmpty() || message.isBlank()) return
        val paths = selected.toList()
        val text = message
        busy = true
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val git = GitService(root)
                    git.stage(paths).getOrThrow()
                    git.commit(text).getOrThrow()
                }
            }
            if (outcome.isSuccess) {
                message = ""
                note = "Committed ${paths.size} ${if (paths.size == 1) "file" else "files"}."
            } else {
                error = outcome.exceptionOrNull()?.let {
                    describeFailure(it, "The commit did not finish.")
                }
            }
            reload()
            busy = false
        }
    }

    fun trackProject() {
        val root = projectRoot ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { GitService(root).init().getOrThrow() } }
            error = outcome.exceptionOrNull()?.let { describeFailure(it, "Could not start tracking this project.") }
            reload()
            busy = false
        }
    }

    if (token == null) {
        Column(modifier.fillMaxSize().background(cc.panel)) {
            GitPanelHeader(
                title = "GitHub",
                subtitle = "Not connected",
                onRefresh = { scope.launch { reload() } },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(cc.text2.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        com.closecode.ui.CcIcon("gh", cc.text2, 36.dp, 1.6f)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Connect GitHub",
                        color = cc.text,
                        fontSize = 20.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sign in to clone your repositories, push your work and see changes.",
                        color = cc.muted,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    com.closecode.ui.components.PrimaryButton(
                        text = "Sign in with GitHub",
                        onClick = { signInOpen = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        icon = "gh",
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Clone a public link instead",
                        color = cc.accent,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onCloneRequest)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    } else {
        val branch = snapshot?.branch ?: "main"
        val repoLabel = gitRemoteLabel(remoteUrl)
            ?: account?.login?.let { "@$it" }
            ?: projectName
        Column(
            modifier
                .fillMaxSize()
                .background(cc.panel)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            GitPanelHeader(
                title = "GitHub",
                subtitle = "$repoLabel - $branch",
                onRefresh = { scope.launch { reload() } },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cc.field)
                        .clickable(enabled = remoteUrl != null && !busy && snapshot?.hasCommits == true, onClick = {
                            remoteAction("Pull") { token -> GitHubRemote(projectRoot!!).pull(token) }
                        })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    com.closecode.ui.CcIcon("down", cc.text2, 16.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Pull",
                        color = cc.text2,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cc.field)
                        .clickable(enabled = remoteUrl != null && !busy && snapshot?.hasCommits == true, onClick = {
                            remoteAction("Push") { token -> GitHubRemote(projectRoot!!).push(token) }
                        })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    com.closecode.ui.CcIcon("up", cc.text2, 16.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Push",
                        color = cc.text2,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = cc.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }

            error?.let { GitMessageLine(it, error = true) }
            note?.let { GitMessageLine(it, error = false) }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesignSectionLabel("CHANGES · ${changes.size}", Modifier.weight(1f))
            }

            when {
                loading -> GitEmptyNote("Reading changes…")
                snapshot == null -> {
                    GitEmptyNote("This project is not under version control yet.")
                    GitSecondaryButton(
                        label = "Start tracking this project",
                        onClick = ::trackProject,
                        enabled = !busy,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                changes.isEmpty() -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CcIcon(CcIcons.Check, c.mock.green, 16.dp)
                    Text(
                        text = "Everything is saved",
                        color = c.mock.text,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }
                else -> changes.forEach { change ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 0.dp)
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(cc.field)
                            .clickable { onDiff(change.path) }
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GitCheckBox(
                            checked = change.path in selected,
                            enabled = !busy,
                            onToggle = {
                                selected = if (change.path in selected) {
                                    selected - change.path
                                } else {
                                    selected + change.path
                                }
                            },
                        )
                        Text(
                            text = change.path.substringAfterLast('/'),
                            color = cc.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        GitStatusLetter(change.kind)
                    }
                }
            }

            if (!loading && snapshot != null && changes.isNotEmpty()) {
                GitField(
                    value = message,
                    onValueChange = { message = it },
                    hint = "Commit message...",
                    enabled = !busy,
                    height = 68.dp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    background = cc.rail,
                )
                val staged = selected.size
                val canCommit = message.isNotBlank() && staged > 0 && !busy
                com.closecode.ui.components.PrimaryButton(
                    text = if (staged > 1) "Commit $staged files" else if (staged == 1) "Commit 1 file" else "Commit",
                    onClick = ::commitChanges,
                    modifier = Modifier.padding(horizontal = 14.dp).height(42.dp),
                    enabled = canCommit,
                    icon = "check",
                    container = cc.green,
                    radius = 13.dp,
                )
            }

            if (!loading && snapshot != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesignSectionLabel("HISTORY", Modifier.weight(1f))
                }
                val commits = history
                when {
                    commits == null -> GitEmptyNote("Reading history…")
                    commits.isEmpty() -> GitEmptyNote("No commits yet.")
                    else -> {
                        commits.take(3).forEachIndexed { index, commit ->
                            GitMiniHistoryRow(
                                commit = commit,
                                first = index == 0,
                                onClick = { onCommitOpen(commit.id) },
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onHistory)
                                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "See all",
                                color = cc.accent,
                                fontSize = 13.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            com.closecode.ui.CcIcon("chevron-right", cc.faint, 16.dp)
                        }
                    }
                }
            }
        }
    }

    if (signInOpen) {
        Dialog(
            onDismissRequest = { signInOpen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(Modifier.fillMaxSize().background(c.mock.page)) {
                GitHubSignIn(
                    onSignedIn = { fresh ->
                        val kept = tokenStore.write(fresh)
                        token = fresh
                        signInOpen = false
                        if (!kept) {
                            note = "Signed in. This phone cannot keep sign-ins safely, so it will ask again next time."
                        }
                    },
                    onDismiss = { signInOpen = false },
                )
            }
        }
    }
}

@Composable
private fun GitMessageLine(message: String, error: Boolean) {
    val c = LocalPageColors.current
    Text(
        text = message,
        color = if (error) c.mock.danger else c.mock.green,
        fontSize = 11.sp,
        fontFamily = Sans,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun GitMiniHistoryRow(
    commit: GitCommit,
    first: Boolean,
    onClick: () -> Unit,
) {
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            Modifier
                .width(10.dp)
                .fillMaxHeight()
                .padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .then(
                        if (first) Modifier.background(cc.accent)
                        else Modifier.border(2.dp, cc.accent, CircleShape),
                    ),
            )
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(2.dp)
                    .weight(1f)
                    .background(Color(0xFFD9DCE2)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = commit.summary,
                color = cc.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${commit.shortId} · ${gitRelativeTime(commit.whenMillis)}",
                color = cc.muted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontFamily = com.closecode.ui.theme.Mono,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Screen 17/18: the full Source control page with its menu and branch sheet.
 * It stages every change only when Commit is pressed, so the index is never half-changed
 * just by opening the screen.
 */
@Composable
internal fun GitSourceControlPage(
    projectName: String,
    projectRoot: java.io.File?,
    revision: Int = 0,
    onBack: () -> Unit,
    onDiff: (String) -> Unit,
    onCommitDiff: (String, String) -> Unit = { _, _ -> },
    onHistory: () -> Unit,
    onCommitOpen: (String) -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tokenStore = remember(context) { TokenStore(context) }
    val token = remember(projectRoot) { tokenStore.read() }

    var snapshot by remember(projectRoot) { mutableStateOf<GitSnapshot?>(null) }
    var remoteUrl by remember(projectRoot) { mutableStateOf<String?>(null) }
    var branches by remember(projectRoot) { mutableStateOf(GitBranchChoices(emptyList(), emptyList())) }
    var loading by remember(projectRoot) { mutableStateOf(true) }
    var busy by remember(projectRoot) { mutableStateOf(false) }
    var error by remember(projectRoot) { mutableStateOf<String?>(null) }
    var note by remember(projectRoot) { mutableStateOf<String?>(null) }
    var message by remember(projectRoot) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var branchSheet by remember { mutableStateOf(false) }
    var namingBranch by remember { mutableStateOf(false) }
    var discardPath by remember { mutableStateOf<String?>(null) }
    var discardAll by remember { mutableStateOf(false) }

    suspend fun reload() {
        val root = projectRoot
        if (root == null) {
            snapshot = null
            branches = GitBranchChoices(emptyList(), emptyList())
            remoteUrl = null
            loading = false
            return
        }
        val loaded = withContext(Dispatchers.IO) {
            val git = GitService(root)
            val snap = if (git.isVersioned()) git.snapshot().getOrNull() else null
            val branchChoices = if (snap != null) loadGitBranches(root) else GitBranchChoices(emptyList(), emptyList())
            Triple(snap, branchChoices, GitHubRemote(root).originUrl())
        }
        snapshot = loaded.first
        branches = loaded.second
        remoteUrl = loaded.third
        loading = false
    }

    LaunchedEffect(projectRoot, revision) { reload() }

    val changes = remember(snapshot) { snapshot?.let { gitAllChanges(it) } ?: emptyList() }
    val canCommit = message.isNotBlank() && changes.isNotEmpty() && !busy

    fun runAction(label: String, block: () -> Result<*>) {
        val root = projectRoot ?: return
        if (busy) return
        busy = true
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block().getOrThrow() } }
            error = outcome.exceptionOrNull()?.let { describeFailure(it, "$label did not finish.") }
            note = if (outcome.isSuccess) "$label done." else null
            reload()
            busy = false
        }
    }

    fun commit(andPush: Boolean) {
        val root = projectRoot ?: return
        if (!canCommit) return
        if (andPush && (token == null || remoteUrl == null)) {
            error = "Connect GitHub and link a repository before pushing."
            return
        }
        val paths = changes.map { it.path }
        val text = message
        busy = true
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val git = GitService(root)
                    git.stage(paths).getOrThrow()
                    git.commit(text).getOrThrow()
                    if (andPush) {
                        GitHubRemote(root).push(token!!).getOrThrow()
                    }
                }
            }
            if (outcome.isSuccess) {
                message = ""
                note = if (andPush) "Committed and pushed." else "Committed."
            } else {
                error = outcome.exceptionOrNull()?.let {
                    describeFailure(it, "The commit did not finish.")
                }
            }
            reload()
            busy = false
        }
    }

    fun remoteAction(label: String, block: (String) -> Result<String>) {
        val root = projectRoot ?: return
        val current = token
        if (root == null || current == null || busy) return
        busy = true
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block(current).getOrThrow() } }
            error = outcome.exceptionOrNull()?.let { describeFailure(it, "$label did not finish.") }
            note = outcome.getOrNull()
            reload()
            busy = false
        }
    }

    fun discard(paths: List<String>) {
        val root = projectRoot ?: return
        if (root == null || paths.isEmpty() || busy) return
        busy = true
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { GitService(root).discard(paths).getOrThrow() } }
            error = outcome.exceptionOrNull()?.let { describeFailure(it, "Could not discard those changes.") }
            if (outcome.isSuccess) note = "Discarded ${paths.size} ${if (paths.size == 1) "file" else "files"}."
            reload()
            busy = false
        }
    }

    BackHandler(enabled = menuOpen || branchSheet || namingBranch || discardPath != null || discardAll) {
        when {
            discardPath != null -> discardPath = null
            discardAll -> discardAll = false
            namingBranch -> namingBranch = false
            branchSheet -> branchSheet = false
            menuOpen -> menuOpen = false
        }
    }

    Box(Modifier.fillMaxSize().background(c.mock.page)) {
        Column(Modifier.fillMaxSize()) {
            GitPageHeader(
                origin = "GitHub",
                current = "Source control",
                onBack = onBack,
                actions = {
                    // Screen 17: the menu lives in the header again, as a filled rounded-square
                    // ⋮ on the right — not as a branch pill inside the body.
                    Box {
                        GitHeaderIconButton(CcIcons.More, "More actions", { menuOpen = true }, filled = true)
                    }
                },
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                when {
                    loading -> GitEmptyNote("Reading changes…")
                    snapshot == null -> GitEmptyNote("This project is not under version control yet.")
                     else -> {
                        // Pull and Push are menu items now: the spec takes them off the body and
                        // puts them in the header's ⋮, together with branch and history.
                        DesignSectionLabel("CHANGES · ${changes.size}")
                        if (changes.isEmpty()) {
                            GitEmptyNote("Everything is saved")
                        } else {
                            changes.forEach { change ->
                                GitSwipeDiscardRow(
                                    change = change,
                                    onClick = { onDiff(change.path) },
                                    onDiscard = { discardPath = change.path },
                                )
                            }
                        }
                        GitField(
                            value = message,
                            onValueChange = { message = it },
                            hint = "Commit message",
                            enabled = !busy,
                            height = 84.dp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GitPrimaryButton(
                                label = "Commit",
                                onClick = { commit(andPush = false) },
                                enabled = canCommit,
                                icon = CcIcons.Check,
                                modifier = Modifier.weight(0.82f),
                            )
                            GitSecondaryButton(
                                label = "Commit and push",
                                onClick = { commit(andPush = true) },
                                enabled = canCommit && token != null && remoteUrl != null,
                                icon = CcIcons.Upload,
                                modifier = Modifier.weight(1.18f),
                            )
                        }
                        error?.let { GitMessageLine(it, error = true) }
                        note?.let { GitMessageLine(it, error = false) }
                    }
                }
            }
        }

        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.mock.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { menuOpen = false },
                    ),
            )
            // The popup hangs from the header's ⋮ on the trailing edge.
            GitMenuCard(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 58.dp, end = 12.dp),
            ) {
                GitMenuItem(
                    label = "Pull",
                    icon = CcIcons.Download,
                    enabled = token != null && remoteUrl != null && !busy,
                    onClick = {
                        menuOpen = false
                        remoteAction("Pull") { current -> GitHubRemote(projectRoot!!).pull(current) }
                    },
                )
                GitMenuItem(
                    label = "Push",
                    icon = CcIcons.Upload,
                    enabled = token != null && remoteUrl != null && !busy,
                    onClick = {
                        menuOpen = false
                        remoteAction("Push") { current -> GitHubRemote(projectRoot!!).push(current) }
                    },
                )
                GitMenuItem(
                    label = "Switch branch",
                    icon = CcIcons.Branch,
                    enabled = branches.local.isNotEmpty() || branches.remote.isNotEmpty(),
                    onClick = {
                        menuOpen = false
                        branchSheet = true
                    },
                )
                GitMenuItem(
                    label = "History",
                    icon = CcIcons.History,
                    onClick = {
                        menuOpen = false
                        onHistory()
                    },
                )
                GitMenuDivider()
                GitMenuItem(
                    label = "Discard all changes",
                    icon = CcIcons.Trash,
                    danger = true,
                    enabled = changes.isNotEmpty() && !busy,
                    onClick = {
                        menuOpen = false
                        discardAll = true
                    },
                )
            }
        }

        if (branchSheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.mock.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { branchSheet = false },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.74f)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(c.mock.page)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Switch branch",
                            color = c.mock.text,
                            fontSize = 17.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        GitHeaderIconButton(CcIcons.Close, "Close", { branchSheet = false })
                    }
                    if (branches.local.isNotEmpty()) {
                        DesignSectionLabel(
                            "LOCAL",
                            Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                        )
                        branches.local.forEach { name ->
                            GitBranchRow(
                                name = name,
                                current = name == snapshot?.branch,
                                onClick = {
                                    branchSheet = false
                                    runAction("Switch branch") { GitService(projectRoot!!).checkout(name) }
                                },
                            )
                        }
                    }
                    if (branches.remote.isNotEmpty()) {
                        DesignSectionLabel(
                            "REMOTE",
                            Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        )
                        branches.remote.forEach { name ->
                            GitBranchRow(
                                name = name,
                                current = false,
                                onClick = {
                                    branchSheet = false
                                    runAction("Switch branch") {
                                        checkoutGitRemoteBranch(projectRoot!!, name)
                                    }
                                },
                            )
                        }
                    }
                    GitMenuDivider()
                    GitMenuItem(
                        label = "New branch",
                        icon = CcIcons.Plus,
                        onClick = {
                            branchSheet = false
                            namingBranch = true
                        },
                    )
                }
            }
        }
    }

    if (namingBranch) {
        GitNameDialog(
            title = "New branch",
            hint = "branch-name",
            confirmLabel = "Create",
            onCreate = { name ->
                namingBranch = false
                if (name.isNotEmpty()) {
                    runAction("Create branch") {
                        val git = GitService(projectRoot!!)
                        git.createBranch(name).getOrThrow()
                        git.checkout(name)
                    }
                }
            },
            onDismiss = { namingBranch = false },
        )
    }

    val pending = discardPath
    if (pending != null) {
        GitConfirmDialog(
            title = "Discard changes to ${pending.substringAfterLast('/')}?",
            message = "The file goes back to the last commit. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                discardPath = null
                discard(listOf(pending))
            },
            onDismiss = { discardPath = null },
        )
    }

    if (discardAll) {
        GitConfirmDialog(
            title = "Discard all ${changes.size} changes?",
            message = "Every edit goes back to the last commit, and new files are deleted. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                discardAll = false
                discard(changes.map { it.path })
            },
            onDismiss = { discardAll = false },
        )
    }
}

@Composable
private fun GitSwipeDiscardRow(
    change: com.closecode.ui.git.GitChange,
    onClick: () -> Unit,
    onDiscard: () -> Unit,
) {
    val c = LocalPageColors.current
    val density = LocalDensity.current
    val limit = with(density) { (-86).dp.toPx() }
    var offset by remember(change.path) { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(13.dp)),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(c.mock.dangerBg)
                .padding(end = 18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = "Discard",
                color = c.mock.danger,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(change.path) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset <= limit * 0.55f) onDiscard()
                            offset = 0f
                        },
                        onHorizontalDrag = { pointer, drag ->
                            pointer.consume()
                            offset = (offset + drag).coerceIn(limit, 0f)
                        },
                    )
                }
                .fillMaxWidth()
                .background(c.mock.card)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GitLetterTile(change.kind)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = change.path.substringAfterLast('/'),
                    color = c.mock.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = change.path,
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = com.closecode.ui.theme.Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GitBranchRow(name: String, current: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = c.mock.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (current) {
            CcIcon(CcIcons.Check, c.mock.green, 16.dp)
        }
    }
}

/**
 * Screen 19: full history with the graph rail and 30 commits per load. Pull down at the top
 * refreshes; reaching the end asks for the next 30.
 */
@Composable
internal fun HistoryPage(
    projectName: String,
    branch: String,
    projectRoot: java.io.File?,
    headId: String?,
    onBack: () -> Unit,
    onCommit: (commitId: String) -> Unit,
) {
    val c = LocalPageColors.current
    val listState = rememberLazyListState()
    var commits by remember(projectRoot) { mutableStateOf<List<GitCommit>?>(null) }
    var limit by remember(projectRoot) { mutableStateOf(30) }
    var refreshKey by remember { mutableStateOf(0) }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    LaunchedEffect(projectRoot, limit, refreshKey) {
        val root = projectRoot
        commits = if (root == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                GitService(root).history(limit).getOrElse { emptyList() }
            }
        }
    }

    LaunchedEffect(listState, commits, limit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                val loaded = commits
                if (loaded != null && loaded.size >= limit && last >= loaded.lastIndex - 2) {
                    limit += 30
                }
            }
    }

    val connection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag &&
                    available.y > 0f &&
                    !listState.canScrollBackward
                ) {
                    pullOffset = (pullOffset + available.y).coerceAtMost(180f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset > with(density) { 72.dp.toPx() }) {
                    limit = 30
                    refreshKey++
                }
                pullOffset = 0f
                return Velocity.Zero
            }
        }
    }

    BackHandler(onBack = {
        BackLog.press("history", "branch=close")
        onBack()
    })

    Column(Modifier.fillMaxSize().background(c.mock.page)) {
        GitPageHeader(
            origin = "Source control",
            current = "History",
            onBack = onBack,
        )

        val pullDp = with(density) { pullOffset.toDp() }
        if (pullOffset > 0f) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (pullDp > 72.dp) {
                    Text(
                        text = "Release to refresh",
                        color = c.mock.accent,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = c.mock.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pull to refresh",
                        color = c.mock.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
            }
        }

        val rows = commits
        if (rows == null) {
            GitEmptyNote("Reading history…")
            return@Column
        }
        if (rows.isEmpty()) {
            GitEmptyNote("No commits yet. Commit something to start the history.")
            return@Column
        }
        val head = headId ?: rows.firstOrNull()?.id
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(connection)
                .padding(top = 2.dp, bottom = 20.dp),
        ) {
            items(rows, key = { it.id }) { commit ->
                GitHistoryRow(
                    commit = commit,
                    isHead = commit.id == head,
                    first = commit.id == rows.firstOrNull()?.id,
                    last = commit.id == rows.lastOrNull()?.id,
                    onClick = { onCommit(commit.id) },
                )
            }
            if (rows.size >= limit) {
                item(key = "older") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = c.mock.accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Loading older commits…",
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHistoryRow(
    commit: GitCommit,
    isHead: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(20.dp)
                .height(62.dp),
        ) {
            if (!first) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .height(10.dp)
                        .background(c.mock.border),
                )
            }
            if (!last) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width(2.dp)
                        .height(52.dp)
                        .background(c.mock.border),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(if (isHead) 11.dp else 9.dp)
                    .clip(CircleShape)
                    .background(if (isHead) c.mock.accent else c.mock.faint),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            Text(
                text = commit.summary,
                color = c.mock.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = commit.shortId,
                    color = c.mock.accent,
                    fontSize = 11.sp,
                    fontFamily = com.closecode.ui.theme.Mono,
                    maxLines = 1,
                )
                Text(
                    text = "  ${commit.author} · ${gitRelativeTime(commit.whenMillis)}",
                    color = c.mock.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isHead) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(c.mock.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "HEAD",
                            color = c.mock.accent,
                            fontSize = 10.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 20: one commit, read straight from the repository. The first changed file's diff is
 * previewed under the file rows; tapping any row opens the full diff without stage actions.
 */
@Composable
internal fun CommitPage(
    commitId: String,
    projectRoot: java.io.File?,
    revision: Int,
    onBack: () -> Unit,
    onDiff: (commitId: String, path: String) -> Unit,
    onChanged: () -> Unit,
) {
    val c = LocalPageColors.current
    var info by remember(commitId) { mutableStateOf<GitCommitInfo?>(null) }
    var failure by remember(commitId) { mutableStateOf<String?>(null) }
    var previewPath by remember(commitId) { mutableStateOf<String?>(null) }
    var previewRows by remember(commitId) { mutableStateOf<List<UnifiedRow>?>(null) }
    var previewLoading by remember(commitId) { mutableStateOf(false) }

    LaunchedEffect(commitId, projectRoot, revision) {
        val root = projectRoot
        if (root == null) {
            failure = "No project is open."
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) { loadGitCommitInfo(root, commitId) }
        info = result.getOrNull()
        failure = result.exceptionOrNull()?.message
        val first = info?.files?.firstOrNull()?.path
        if (first != null) {
            previewPath = first
            previewLoading = true
            previewRows = withContext(Dispatchers.IO) { readCommitDiff(root, commitId, first) }
            previewLoading = false
        }
    }

    BackHandler(onBack = {
        BackLog.press("commit", "branch=close")
        onBack()
    })

    Column(Modifier.fillMaxSize().background(c.mock.page)) {
        GitPageHeader(
            origin = "History",
            current = "Commit ${commitId.take(7)}",
            onBack = onBack,
        )

        val current = info
        if (current == null) {
            if (failure != null) {
                GitEmptyNote(failure.orEmpty())
            } else {
                GitEmptyNote("Reading commit…")
            }
            return@Column
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            item(key = "summary") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                    Text(
                        text = current.message.ifBlank { "(no message)" },
                        color = c.mock.text,
                        fontSize = 17.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 23.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${current.author} · ${gitRelativeTime(current.whenMillis)} · " +
                                "${current.files.size} ${if (current.files.size == 1) "file" else "files"}, ",
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "+${current.added}",
                            color = c.mock.green,
                            fontSize = 12.sp,
                            fontFamily = com.closecode.ui.theme.Mono,
                        )
                        Text(
                            text = " −${current.removed}",
                            color = c.mock.danger,
                            fontSize = 12.sp,
                            fontFamily = com.closecode.ui.theme.Mono,
                        )
                    }
                }
            }

            items(current.files, key = { it.path }) { file ->
                GitCommitFileRow(
                    file = file,
                    selected = file.path == previewPath,
                    onClick = { onDiff(commitId, file.path) },
                )
            }

            item(key = "preview") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
                    if (previewLoading) {
                        Text(
                            text = "Reading diff…",
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        val rows = previewRows
                        if (rows == null) {
                            Text(
                                text = "This file is not text, so there is no preview.",
                                color = c.mock.muted,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            GitReadOnlyDiff(
                                rows = rows,
                                onOpen = {
                                    val path = previewPath
                                    if (path != null) onDiff(commitId, path)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readCommitDiff(root: java.io.File, commitId: String, path: String): List<UnifiedRow>? {
    val sides = GitService(root).commitFileContents(commitId, path).getOrNull() ?: return null
    val before = sides.first?.toString(Charsets.UTF_8).orEmpty()
    val after = sides.second?.toString(Charsets.UTF_8).orEmpty()
    if (before.take(8000).any { it == '\u0000' } || after.take(8000).any { it == '\u0000' }) {
        return null
    }
    return unify(splitDiff(before, after))
}

@Composable
private fun GitCommitFileRow(
    file: GitCommitFileInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) c.mock.field else c.mock.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GitLetterTile(file.kind)
        Spacer(Modifier.width(10.dp))
        Text(
            text = file.path.substringAfterLast('/'),
            color = c.mock.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${file.added}",
            color = c.mock.green,
            fontSize = 11.sp,
            fontFamily = com.closecode.ui.theme.Mono,
        )
        Text(
            text = " −${file.removed}",
            color = c.mock.danger,
            fontSize = 11.sp,
            fontFamily = com.closecode.ui.theme.Mono,
        )
    }
}

@Composable
private fun GitReadOnlyDiff(rows: List<UnifiedRow>, onOpen: () -> Unit) {
    val c = LocalPageColors.current
    var revealed by remember(rows) { mutableStateOf(emptyMap<Int, Int>()) }
    val hScroll = rememberScrollState()
    val isNew = rows.all { it.oldNo == null } && rows.any { it.added }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.mock.field)
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
    ) {
        val minWidth = maxWidth
        Column {
            collapseUnified(rows, isNew, revealed).forEach { block ->
                when (block) {
                    is DiffBlock.Lines -> block.rows.forEach { indexed ->
                        GitDiffLine(
                            row = indexed.value,
                            wrap = false,
                            hScroll = hScroll,
                            minWidth = minWidth,
                        )
                    }
                    is DiffBlock.Gap -> GitDiffGap(
                        hidden = block.hidden,
                        onClick = {
                            revealed = revealed + (block.id to ((revealed[block.id] ?: 0) + 100))
                        },
                        wrap = false,
                        hScroll = hScroll,
                        minWidth = minWidth,
                    )
                }
            }
        }
    }
}

/**
 * Screen 21: the full working-tree or commit diff. Change arrows jump between modified runs,
 * long unchanged stretches fold, and the working-tree mode can discard or stage the file.
 */
@Composable
internal fun DiffScreen(
    projectRoot: java.io.File?,
    path: String,
    onBack: () -> Unit,
    commitId: String? = null,
    onOpenFile: (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var rows by remember(path, commitId) { mutableStateOf<List<com.closecode.ui.git.DiffRow>?>(null) }
    var failed by remember(path, commitId) { mutableStateOf(false) }
    var binary by remember(path, commitId) { mutableStateOf(false) }
    var revealed by remember(path, commitId) { mutableStateOf(emptyMap<Int, Int>()) }
    var busy by remember(path, commitId) { mutableStateOf(false) }
    var error by remember(path, commitId) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember(path, commitId) { mutableStateOf(false) }

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
                    val file = java.io.File(root, path)
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

    fun finishWithRoot(block: (java.io.File) -> Result<*>) {
        val root = projectRoot ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block(root).getOrThrow() } }
            if (outcome.isSuccess) {
                onBack()
            } else {
                error = outcome.exceptionOrNull()?.let { describeFailure(it, "That file action did not finish.") }
                busy = false
            }
        }
    }

    BackHandler(onBack = {
        BackLog.press("git-diff", "branch=close")
        onBack()
    })

    Column(Modifier.fillMaxSize().background(c.mock.page)) {
        val current = rows
        val unified = remember(current) { current?.let { unify(it) } ?: emptyList() }
        val isNew = remember(unified) {
            unified.all { it.oldNo == null } && unified.any { it.added }
        }
        val items = remember(unified, isNew, revealed) {
            flattenUnifiedDiff(unified, isNew, revealed)
        }
        val changedIndices = remember(items) {
            items.mapIndexedNotNull { index, item ->
                if (item is FullDiffItem.Line && (item.row.added || item.row.removed)) index else null
            }
        }

        fun jump(direction: Int) {
            val anchor = listState.firstVisibleItemIndex
            val target = if (direction > 0) {
                changedIndices.firstOrNull { it > anchor }
            } else {
                changedIndices.lastOrNull { it < anchor }
            }
            if (target != null) {
                scope.launch { listState.animateScrollToItem(target) }
            }
        }

        GitPageHeader(
            origin = "GitHub",
            current = path.substringAfterLast('/'),
            onBack = onBack,
            actions = {
                GitHeaderIconButton(
                    icon = CcIcons.Up,
                    label = "Previous change",
                    tint = if (changedIndices.isEmpty()) c.mock.faint else c.mock.muted,
                    onClick = { jump(-1) },
                )
                GitHeaderIconButton(
                    icon = CcIcons.Down,
                    label = "Next change",
                    tint = if (changedIndices.isEmpty()) c.mock.faint else c.mock.muted,
                    onClick = { jump(1) },
                )
            },
        )

        error?.let { GitMessageLine(it, error = true) }

        when {
            failed -> GitEmptyNote("Could not read the diff.")
            binary -> GitEmptyNote("This file is not text, so there is nothing to show.")
            current == null -> GitEmptyNote("Reading the diff…")
            current.isEmpty() -> GitEmptyNote("No differences.")
            current.size > 5000 -> {
                GitEmptyNote("Too large to show here (${current.size} rows).")
                if (onOpenFile != null) {
                    GitSecondaryButton(
                        label = "Open the file",
                        onClick = onOpenFile,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            else -> BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val minWidth = maxWidth
                val hScroll = rememberScrollState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = items,
                        key = { item ->
                            when (item) {
                                is FullDiffItem.Line -> "line:${item.index}"
                                is FullDiffItem.Gap -> "gap:${item.id}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is FullDiffItem.Line -> GitDiffLine(
                                row = item.row,
                                wrap = false,
                                hScroll = hScroll,
                                minWidth = minWidth,
                            )
                            is FullDiffItem.Gap -> GitDiffGap(
                                hidden = item.hidden,
                                onClick = {
                                    revealed = revealed +
                                        (item.id to ((revealed[item.id] ?: 0) + 100))
                                },
                                wrap = false,
                                hScroll = hScroll,
                                minWidth = minWidth,
                            )
                        }
                    }
                    if (commitId == null) {
                        item(key = "actions") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GitSecondaryButton(
                                    label = "Discard",
                                    onClick = { confirmDiscard = true },
                                    enabled = !busy,
                                    danger = true,
                                    modifier = Modifier.weight(1f),
                                )
                                GitPrimaryButton(
                                    label = "Stage",
                                    onClick = {
                                        finishWithRoot { root ->
                                            GitService(root).stage(listOf(path))
                                        }
                                    },
                                    enabled = !busy,
                                    icon = CcIcons.Check,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        item(key = "bottom") { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        GitConfirmDialog(
            title = "Discard changes to ${path.substringAfterLast('/')}?",
            message = "The file goes back to the last commit. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                finishWithRoot { root -> GitService(root).discard(listOf(path)) }
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}
