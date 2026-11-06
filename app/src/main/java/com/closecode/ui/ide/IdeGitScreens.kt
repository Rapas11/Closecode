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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import com.closecode.ui.CcIcon
import com.closecode.ui.components.CcConfirmDialog
import com.closecode.ui.components.ChangedFileRow
import com.closecode.ui.components.EmptyState
import com.closecode.ui.components.ErrorCard
import com.closecode.ui.components.HeaderAction
import com.closecode.ui.components.LoadingList
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.components.SecLabel
import com.closecode.ui.components.WideButton
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.git.GitCommit
import com.closecode.ui.git.GitService
import com.closecode.ui.git.GitSnapshot
import com.closecode.ui.git.splitDiff
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.github.GitHubRemote
import com.closecode.ui.github.GitHubUser
import com.closecode.ui.github.TokenStore
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
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
    var busyAction by remember { mutableStateOf<String?>(null) }

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
        busyAction = label
        error = null
        note = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { block(current).getOrThrow() } }
            error = outcome.exceptionOrNull()?.let { describeFailure(it, "$label did not finish.") }
            note = outcome.getOrNull()
            reload()
            busy = false
            busyAction = null
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
                subtitle = "$repoLabel · $branch",
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
                    if (busyAction == "Pull") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = cc.accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        com.closecode.ui.CcIcon("down", cc.text2, 16.dp)
                    }
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
                    if (busyAction == "Push") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = cc.accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        com.closecode.ui.CcIcon("up", cc.text2, 16.dp)
                    }
                    Text(
                        text = "Push",
                        color = cc.text2,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            error?.let { GitMessageLine(it, error = true) }
            note?.let { GitMessageLine(it, error = false) }

            SecLabel(
                text = "Changes · ${changes.size}",
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 4.dp,
            )

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
                changes.isEmpty() -> Text(
                    text = "No changes",
                    color = cc.muted,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(42.dp),
                    enabled = canCommit,
                    icon = "check",
                    container = cc.green,
                    radius = 13.dp,
                )
            }

            if (!loading && snapshot != null) {
                SecLabel(
                    text = "History",
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 4.dp,
                )
                val commits = history
                when {
                    commits == null -> GitEmptyNote("Reading history…")
                    commits.isEmpty() -> GitEmptyNote("No commits yet")
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

    BackHandler(enabled = menuOpen || branchSheet || namingBranch || discardPath != null || discardAll) {
        when {
            discardPath != null -> discardPath = null
            discardAll -> discardAll = false
            namingBranch -> namingBranch = false
            branchSheet -> branchSheet = false
            menuOpen -> menuOpen = false
        }
    }

    val cc = com.closecode.ui.theme.LocalCcColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(cc.content),
    ) {
        ScreenHeader(
            origin = "GitHub",
            title = "Source control",
            onBack = onBack,
            actions = {
                HeaderAction(
                    icon = "more",
                    label = "Git actions",
                    onClick = { menuOpen = true },
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                loading -> LoadingList(3)
                changes.isEmpty() -> EmptyState(
                    icon = "check",
                    title = "No changes",
                    subtitle = "Your files match the last commit.",
                )
                else -> {
                    SecLabel(
                        text = "Changes · ${changes.size}",
                        start = 18.dp,
                        top = 16.dp,
                        bottom = 6.dp,
                    )
                    changes.forEach { change ->
                        GitSwipeDiscardRow(
                            change = change,
                            onClick = { onDiff(change.path) },
                            onDiscard = { discardPath = change.path },
                        )
                    }
                    Box(
                        Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cc.field)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (message.isEmpty()) {
                            Text(
                                text = "Commit message",
                                color = cc.muted,
                                fontSize = 13.5.sp,
                                fontFamily = Sans,
                            )
                        }
                        BasicTextField(
                            value = message,
                            onValueChange = { message = it },
                            cursorBrush = SolidColor(cc.accent),
                            textStyle = TextStyle(
                                color = cc.text,
                                fontSize = 13.5.sp,
                                fontFamily = Sans,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WideButton(
                            text = "Commit",
                            icon = "check",
                            enabled = canCommit,
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = { commit(andPush = false) },
                        )
                        WideButton(
                            text = "Commit and push",
                            icon = "up",
                            enabled = canCommit && token != null && remoteUrl != null,
                            primary = false,
                            modifier = Modifier.weight(1f),
                            onClick = { commit(andPush = true) },
                        )
                    }
                    error?.let { GitMessageLine(it, error = true) }
                    note?.let { GitMessageLine(it, error = false) }
                }
            }
        }

        if (menuOpen) {
            com.closecode.ui.components.CcMenuOverlay(
                top = 26.dp,
                end = 14.dp,
                width = 220.dp,
                onDismiss = { menuOpen = false },
            ) {
                com.closecode.ui.components.CcMenuItem(
                    icon = "down",
                    label = "Pull",
                    onClick = {
                        menuOpen = false
                        remoteAction("Pull") { current -> GitHubRemote(projectRoot!!).pull(current) }
                    },
                )
                com.closecode.ui.components.CcMenuItem(
                    icon = "up",
                    label = "Push",
                    onClick = {
                        menuOpen = false
                        remoteAction("Push") { current -> GitHubRemote(projectRoot!!).push(current) }
                    },
                )
                com.closecode.ui.components.CcMenuItem(
                    icon = "branch",
                    label = "Switch branch",
                    onClick = {
                        menuOpen = false
                        branchSheet = true
                    },
                )
                com.closecode.ui.components.CcMenuItem(
                    icon = "refresh",
                    label = "History",
                    onClick = {
                        menuOpen = false
                        onHistory()
                    },
                )
                com.closecode.ui.components.CcMenuDivider()
                if (changes.isNotEmpty()) {
                    com.closecode.ui.components.CcMenuItem(
                        icon = "trash",
                        label = "Discard all changes",
                        destructive = true,
                        onClick = {
                            menuOpen = false
                            discardAll = true
                        },
                    )
                }
            }
        }

        if (branchSheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(cc.dimSmallMenu)
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
                        .background(cc.panel)
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
                            color = cc.text,
                            fontSize = 17.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        HeaderIconButton(
                            icon = CcIcons.Close,
                            label = "Close",
                            tint = cc.text2,
                            background = cc.field,
                            boxSize = 38.dp,
                            iconSize = 18.dp,
                            onClick = { branchSheet = false },
                        )
                    }
                    if (branches.local.isNotEmpty()) {
                        SecLabel(
                            text = "LOCAL",
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
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
                        SecLabel(
                            text = "REMOTE",
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
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
                    com.closecode.ui.components.CcMenuDivider()
                    com.closecode.ui.components.CcMenuItem(
                        icon = "plus",
                        label = "New branch",
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
        CcConfirmDialog(
            title = "Discard changes to ${pending.substringAfterLast('/')}?",
            message = "The file goes back to the last commit. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                discardPath = null
                discard(listOf(pending))
            },
            onDismiss = { discardPath = null },
            destructive = true,
        )
    }

    if (discardAll) {
        CcConfirmDialog(
            title = "Discard all changes?",
            message = "${changes.size} files will go back to the last commit. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                discardAll = false
                discard(changes.map { it.path })
            },
            onDismiss = { discardAll = false },
            destructive = true,
        )
    }
}

/** Shortens a path for display: keeps the first 3 segments and the last segment. */
private fun shortPath(p: String): String {
    val parts = p.split('/')
    return if (parts.size <= 3) p else parts.take(3).joinToString("/") + "/…/" + parts.last()
}

@Composable
private fun GitSwipeDiscardRow(
    change: com.closecode.ui.git.GitChange,
    onClick: () -> Unit,
    onDiscard: () -> Unit,
) {
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val density = LocalDensity.current
    val limit = with(density) { (-86).dp.toPx() }
    var offset by remember(change.path) { mutableFloatStateOf(0f) }
    val (badgeBg, letterColor, letter) = when (change.kind) {
        com.closecode.ui.git.GitChangeKind.Added -> Triple(cc.green, Color.White, "A")
        com.closecode.ui.git.GitChangeKind.Modified -> Triple(cc.amber, Color.White, "M")
        com.closecode.ui.git.GitChangeKind.Deleted -> Triple(cc.red, Color.White, "D")
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 0.dp)
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(13.dp)),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(cc.red)
                .padding(end = 18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = "Discard",
                color = Color.White,
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
                .background(cc.field)
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = letterColor,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = change.path.substringAfterLast('/'),
                    color = cc.text,
                    fontSize = 13.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortPath(change.path),
                    color = cc.muted,
                    fontSize = 10.5.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun GitBranchRow(name: String, current: Boolean, onClick: () -> Unit) {
    val cc = com.closecode.ui.theme.LocalCcColors.current
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
            color = cc.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (current) {
            com.closecode.ui.CcIcon("check", cc.green, 16.dp)
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
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val listState = rememberLazyListState()
    var commits by remember(projectRoot) { mutableStateOf<List<GitCommit>?>(null) }
    var limit by remember(projectRoot) { mutableStateOf(50) }
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
                    limit += 50
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
                    limit = 50
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

    Column(Modifier.fillMaxSize().background(cc.content)) {
        ScreenHeader(
            origin = "Source control",
            title = "History",
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
                        color = cc.accent,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = cc.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pull to refresh",
                        color = cc.muted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
            }
        }

        val rows = commits
        if (rows == null) {
            LoadingList(4)
            return@Column
        }
        if (rows.isEmpty()) {
            EmptyState(
                icon = "history",
                title = "No commits yet",
                subtitle = "Commit your changes to see them here.",
            )
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clickable(onClick = { onCommit(commit.id) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        Modifier
                            .width(10.dp)
                            .fillMaxHeight()
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (commit.id == head) cc.accent else cc.faint),
                        )
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .width(2.dp)
                                .weight(1f)
                                .background(cc.border),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = commit.summary,
                            color = cc.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.padding(top = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = commit.shortId.take(7),
                                color = cc.accent,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontFamily = Mono,
                                maxLines = 1,
                            )
                            Text(
                                text = "${commit.author} · ${gitRelativeTime(commit.whenMillis)}",
                                color = cc.muted,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (commit.id == head) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cc.accent)
                                        .padding(horizontal = 7.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "HEAD",
                                        color = Color.White,
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
                            color = cc.accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Loading older commits…",
                            color = cc.muted,
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
    val cc = com.closecode.ui.theme.LocalCcColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier
                .width(10.dp)
                .fillMaxHeight()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isHead) cc.accent else cc.faint),
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(2.dp)
                    .weight(1f)
                    .background(cc.border),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = commit.summary,
                color = cc.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = commit.shortId.take(7),
                    color = cc.accent,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                )
                Text(
                    text = "${commit.author} · ${gitRelativeTime(commit.whenMillis)}",
                    color = cc.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isHead) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(cc.accent)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "HEAD",
                            color = Color.White,
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
    val cc = com.closecode.ui.theme.LocalCcColors.current
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

    Column(Modifier.fillMaxSize().background(cc.content)) {
        ScreenHeader(
            origin = "History",
            title = "Commit ${commitId.take(7)}",
            onBack = onBack,
        )

        val current = info
        if (current == null) {
            if (failure != null) {
                ErrorCard(failure.orEmpty(), { failure = null })
            } else {
                LoadingList(3)
            }
            return@Column
        }

        val add = current.files.sumOf { it.added }
        val del = current.files.sumOf { it.removed }
        val n = current.files.size

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = current.message.ifBlank { "(no message)" },
                    color = cc.text,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 23.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${current.author} · ${gitLongRelativeTime(current.whenMillis)} · $n ${if (n == 1) "file" else "files"}, +$add −$del",
                    color = cc.muted,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            current.files.forEach { file ->
                CommitFileRow(
                    kind = file.kind,
                    name = file.path.substringAfterLast('/'),
                    counts = if (file.added > 0 && file.removed > 0) "+${file.added} −${file.removed}"
                    else if (file.added > 0) "+${file.added}"
                    else if (file.removed > 0) "−${file.removed}"
                    else "",
                    onClick = { onDiff(commitId, file.path) },
                    selected = file.path == previewPath,
                )
            }

            val diffLines = previewRows
            if (diffLines != null && diffLines.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cc.panel)
                        .padding(vertical = 8.dp),
                ) {
                    diffLines.take(12).forEach { line ->
                        CommitDiffLine(
                            kind = line.sign,
                            text = line.text,
                            numberText = when {
                                line.sign == ' ' && line.oldNo != null -> "${line.oldNo}  "
                                line.sign == '+' && line.newNo != null -> "  ${line.newNo}"
                                line.sign == '-' && line.oldNo != null -> "${line.oldNo}  "
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One changed file in a commit detail: badge, name, +/- counts. */
@Composable
private fun CommitFileRow(
    kind: com.closecode.ui.git.GitChangeKind,
    name: String,
    counts: String,
    onClick: () -> Unit,
    selected: Boolean,
) {
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val badgeBg = when (kind) {
        com.closecode.ui.git.GitChangeKind.Added -> cc.green
        com.closecode.ui.git.GitChangeKind.Modified -> cc.amber
        com.closecode.ui.git.GitChangeKind.Deleted -> cc.red
    }
    val letter = when (kind) {
        com.closecode.ui.git.GitChangeKind.Added -> "A"
        com.closecode.ui.git.GitChangeKind.Modified -> "M"
        com.closecode.ui.git.GitChangeKind.Deleted -> "D"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 0.dp)
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) cc.selectedFile else cc.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = cc.text,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (counts.isNotEmpty()) {
                Text(
                    text = counts,
                    color = cc.muted,
                    fontSize = 10.5.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** One diff line in the commit preview: mono 11 sp, line height 17 sp, marker column 14 dp. */
@Composable
private fun CommitDiffLine(
    kind: Char,
    text: String,
    numberText: String? = null,
) {
    val cc = com.closecode.ui.theme.LocalCcColors.current
    val bg = when (kind) {
        '+' -> cc.diffAddBg
        '-' -> cc.diffDelBg
        else -> Color.Transparent
    }
    val fg = when (kind) {
        '+' -> Color(0xFF1A7F37)
        '-' -> Color(0xFFDA373C)
        else -> cc.textSoft
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (numberText != null) {
            Text(
                text = numberText,
                color = cc.faint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                fontFamily = Mono,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp).padding(end = 8.dp),
            )
        }
        Text(
            text = if (kind == ' ') "" else kind.toString(),
            color = fg,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            fontFamily = Mono,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            fontFamily = Mono,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
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
    var staged by remember(path, commitId) { mutableStateOf(false) }

    LaunchedEffect(path, commitId) {
        val root = projectRoot
        if (root == null) {
            failed = true
            return@LaunchedEffect
        }
        staged = withContext(Dispatchers.IO) {
            GitService(root).snapshot().getOrNull()?.staged?.any { it.path == path } == true
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

    Column(Modifier.fillMaxSize().background(c.mock.panel)) {
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

        ScreenHeader(
            origin = "GitHub",
            title = path.substringAfterLast('/'),
            onBack = onBack,
            actions = {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).clickable(onClick = { jump(-1) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon("up", c.mock.textSoft, 19.dp, 1.8f)
                    }
                    Box(
                        Modifier.size(38.dp).clickable(onClick = { jump(1) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon("down", c.mock.textSoft, 19.dp, 1.8f)
                    }
                }
            },
        )

        error?.let { GitMessageLine(it, error = true) }

        when {
            failed -> EmptyState("cross", "Could not read the diff.", "Try again later.")
            binary -> Text(
                text = "Binary file, no preview",
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(24.dp),
            )
            current == null -> LoadingList(4)
            current.isEmpty() -> Text(
                text = "No changes in this file",
                color = c.mock.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(24.dp),
            )
            current.size > 5000 -> {
                EmptyState("cross", "Too large to show.", "${current.size} rows exceed the diff viewer.")
                if (onOpenFile != null) {
                    com.closecode.ui.components.PrimaryButton(
                        text = "Open the file",
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
                                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                com.closecode.ui.components.PrimaryButton(
                                    text = "Discard",
                                    onClick = { confirmDiscard = true },
                                    modifier = Modifier.weight(1f),
                                    container = c.mock.field,
                                    contentColor = c.mock.danger,
                                    radius = 15.dp,
                                )
                                com.closecode.ui.components.PrimaryButton(
                                    text = if (staged) "Unstage" else "Stage",
                                    onClick = {
                                        finishWithRoot { root ->
                                            if (staged) GitService(root).unstage(listOf(path))
                                            else GitService(root).stage(listOf(path))
                                        }
                                    },
                                    enabled = !busy,
                                    icon = "check",
                                    modifier = Modifier.weight(1f),
                                    container = c.accent,
                                    radius = 15.dp,
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
        CcConfirmDialog(
            title = "Discard changes to ${path.substringAfterLast('/')}?",
            message = "The file goes back to the last commit. This cannot be undone.",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                finishWithRoot { root -> GitService(root).discard(listOf(path)) }
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }
}
