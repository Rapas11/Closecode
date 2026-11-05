package com.closecode.ui.ide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.closecode.BuildConfig
import com.closecode.CrashReport
import com.closecode.debug.BuildInfo
import com.closecode.debug.DebugLog
import com.closecode.debug.DebugReport
import com.closecode.debug.SelfTest
import com.closecode.debug.sharePlainText
import com.closecode.lang.AndroidApiAssets
import com.closecode.lang.CompletionItem
import com.closecode.lang.CompletionKind
import com.closecode.lang.Diagnostic
import com.closecode.lang.DiagnosticCodes
import com.closecode.lang.KotlinFileAnalysis
import com.closecode.lang.KotlinAnalyzer
import com.closecode.lang.Severity
import com.closecode.lang.SourceEdits
import com.closecode.ui.GitHubCloneScreen
import com.closecode.ui.github.TokenStore
import com.closecode.ui.ide.model.IdeNavItems
import com.closecode.ui.ide.model.kindColor
import com.closecode.ui.ide.model.kindGlyph
import com.closecode.ui.ide.model.statusKindOf
import com.closecode.ui.ide.model.suggestionsFor
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.project.headBranch
import com.closecode.ui.project.ProjectPathException
import com.closecode.ui.project.languageOf
import com.closecode.ui.project.ProjectSafety
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.model.projectTree
import com.closecode.ui.model.recentFiles
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.motion
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun IdeScreen(
    projectName: String,
    // The permanent identity. Slugs name folders; this names the project across renames.
    projectId: String,
    // Held by MainActivity rather than created here, so the shells outlive the settings screen.
    terminalSessions: TerminalSessions,
    templateName: String, // kept for the project record; the header shows the project name
    slug: String,
    repository: ProjectRepository,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    // Reports whether any open tab has unsaved changes, so leaving can ask first.
    onDirtyChange: (Boolean) -> Unit = {},
    // A clone performed inside the IDE finishes with a record for a *different* project. The
    // caller decides where that lands; this screen never navigates itself.
    onProjectCloned: (ProjectRecord) -> Unit = {},
    onToggleTheme: () -> Unit = {},
    /** The real projects, shown by the rail avatar's project switcher. */
    projectRecords: List<ProjectRecord> = emptyList(),
    onOpenProject: (ProjectRecord) -> Unit = {},
    onNewProject: () -> Unit = {},
    // Moves when the GitHub account changes elsewhere — a sign-in or a disconnect done on the
    // settings screen. The token is read again when it does, so the Source Control tab never
    // keeps showing an account that is no longer connected.
    tokenEpoch: Int = 0,
) {
    // The one place the settings are read from for this screen: saving and the editor both honour
    // them, and reading them once keeps a row changed on the settings screen in force immediately.
    val settings = LocalSettings.current
    val context = LocalContext.current
    val treeClipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val c = LocalPageColors.current
    var tabs by remember { mutableStateOf(emptyList<ProjectFile>()) }
    var active by remember { mutableStateOf("") }
    var treeVersion by remember { mutableIntStateOf(0) }
    // Bumped whenever a file on disk changes, by any route. The GitHub panel reads the working
    // tree, so without this it kept the snapshot taken when the project opened and went on
    // showing edits that had since been made, reverted or abandoned.
    var gitRevision by remember { mutableIntStateOf(0) }
    // State, not a `remember { repository.tree(slug) }`: a tree walk is a recursive directory read,
    // and running it inside `remember` blocked composition on the disk. It is filled in below by an
    // effect that does the walk on Dispatchers.IO, so the first frame simply shows an empty drawer.
    var diskTree by remember(slug) { mutableStateOf(emptyList<ProjectFile>()) }
    // The row the drawer is pointed at: a folder, or the file being read. Keyed by path, because
    // the highlight, the parent a new file lands in and "which one is open" are three questions
    // about the same row, and two files called MainActivity.kt are not the same row.
    var selectedPath by remember(slug) { mutableStateOf<String?>(null) }
    // Folders the reader has closed, by path. It used to live inside the drawer, but creating a
    // file has to open every folder between the root and the new row or the row arrives invisible
    // and the creation looks like nothing happened.
    //
    // Persisted per project, because the spec says the tree remembers its open folders between
    // launches. Paths join on a unit separator, which a file name cannot practically contain.
    val kvStore = remember(context) {
        com.closecode.ui.settings.SharedPreferencesKeyValueStore(context)
    }
    var collapsedFolders by remember(slug) {
        mutableStateOf(
            kvStore.getString("collapsed_$projectId", null).orEmpty()
                .split('\u001F').filter { it.isNotEmpty() }.toSet(),
        )
    }
    // Generated output and secrets stay out of the drawer by default; this reveals them for the
    // rare look inside build/ or .opencode/ without making them normal project files.
    var showGenerated by remember(slug) {
        mutableStateOf(kvStore.getBoolean("show_generated_$projectId", false))
    }
    LaunchedEffect(slug, collapsedFolders) {
        kvStore.putString("collapsed_$projectId", collapsedFolders.joinToString(""))
    }
    // Folders start closed: on the first open of a project (no stored choice yet) every
    // folder collapses once the tree loads, so the reader opens only what they need.
    // The choice persists afterwards, so reopening a folder survives relaunches.
    // Captured before the write-back above ever runs, so first-run detection survives it.
    val hadStoredCollapsed = remember(slug) { kvStore.getString("collapsed_$projectId", null) != null }
    var collapsedSeeded by remember(slug) { mutableStateOf(false) }
    LaunchedEffect(slug, diskTree) {
        if (!collapsedSeeded && !hadStoredCollapsed && diskTree.isNotEmpty()) {
            collapsedFolders = diskTree
                .filter { it.kind == FileKind.Folder }
                .map { it.path }.toSet()
            collapsedSeeded = true
        }
    }
    // Reopening restores the last session: open tabs, the active file, and each file's caret
    // and scroll. Entries use the unit separator like the collapsed set; a corrupt value parses
    // to nothing rather than to a tab that does not exist.
    val restoredTabNames = remember(slug) {
        kvStore.getString("restore_tabs_$projectId", null).orEmpty()
            .split('').filter { it.isNotEmpty() }
    }
    val restoredActive = remember(slug) {
        kvStore.getString("restore_active_$projectId", null)?.takeIf { it.isNotEmpty() }
    }
    val restoredCaret = remember(slug) {
        kvStore.getString("restore_caret_$projectId", null).orEmpty()
            .split('').mapNotNull { entry ->
                val (name, at) = entry.split('', limit = 2).let {
                    it.getOrElse(0) { "" } to it.getOrNull(1)?.toIntOrNull()
                }
                if (name.isNotEmpty() && at != null) name to at else null
            }.toMap()
    }
    val restoredScroll = remember(slug) {
        kvStore.getString("restore_scroll_$projectId", null).orEmpty()
            .split('').mapNotNull { entry ->
                val parts = entry.split('', limit = 2)
                val counts = parts.getOrNull(1)?.split(',')?.mapNotNull { it.toIntOrNull() }
                if (parts[0].isNotEmpty() && counts?.size == 2) {
                    parts[0] to (counts[0] to counts[1])
                } else {
                    null
                }
            }.toMap()
    }
    fun persistSession() {
        if (tabs.isEmpty()) return
        kvStore.putString("restore_tabs_$projectId", tabs.joinToString("") { it.path })
        kvStore.putString("restore_active_$projectId", active)
    }
    fun persistPlaces(caret: Map<String, Int>, scroll: Map<String, Pair<Int, Int>>) {
        kvStore.putString(
            "restore_caret_$projectId",
            caret.entries.joinToString("") { "${it.key}${it.value}" },
        )
        kvStore.putString(
            "restore_scroll_$projectId",
            scroll.entries.joinToString("") { "${it.key}${it.value.first},${it.value.second}" },
        )
    }
    val diskContents = remember(slug) { mutableStateMapOf<String, String>() }
    var activePanel by remember(slug) { mutableStateOf("Code") }
    // Full pages and tool panels are a real history, not a fixed "Code is underneath"
    // rule: Terminal -> Logcat -> Back must land on Terminal, and AI -> Terminal -> Back
    // must land on AI. Back pops this stack; every other panel move pushes the old one.
    val panelHistory = remember(slug) { mutableStateListOf<String>() }
    var previousPanel by remember(slug) { mutableStateOf("Code") }
    var backNavigation by remember(slug) { mutableStateOf(false) }
    LaunchedEffect(activePanel) {
        if (activePanel != previousPanel) {
            if (backNavigation) {
                backNavigation = false
            } else {
                panelHistory.add(previousPanel)
                if (panelHistory.size > 32) panelHistory.removeAt(0)
            }
            previousPanel = activePanel
        }
    }
    var explorerOpen by remember { mutableStateOf(false) }
    // True while the code view has been scrolled sideways: the sidebar swipe then belongs to the
    // code until it has been scrolled back to its left end (see IdeWorkspace.canScrollLeft).
    val codeCanScrollLeft = remember { mutableStateOf(false) }
    var projectSwitcherOpen by remember { mutableStateOf(false) }
    // How many times the drawer has been opened: the hint line under the tree shows for the
    // first three opens, then never again.
    var drawerOpens by remember { mutableStateOf(kvStore.getInt("drawer_hint_opens", 0)) }
    LaunchedEffect(explorerOpen) {
        if (explorerOpen) {
            drawerOpens++
            kvStore.putInt("drawer_hint_opens", drawerOpens)
        }
    }
    // One Git letter per file for the tree rows, from a single status call per revision: A/M/D
    // from staged entries first, then unstaged, U for untracked. Refreshed on the same revision
    // counter as the Source Control tab, so the two never disagree.
    var gitLetters by remember(slug) {
        mutableStateOf(emptyMap<String, com.closecode.ui.components.StatusKind>())
    }
    LaunchedEffect(slug, gitRevision) {
        val root = runCatching { repository.projectDir(slug) }.getOrNull()
        gitLetters = if (root == null) {
            emptyMap()
        } else {
            withContext(Dispatchers.Default) {
                val snap = runCatching { com.closecode.ui.git.GitService(root).snapshot() }
                    .getOrNull()?.getOrNull() ?: com.closecode.ui.git.GitSnapshot.Empty
                buildMap {
                    snap.unstaged.forEach { put(it.path, statusKindOf(it.kind)) }
                    snap.staged.forEach { put(it.path, statusKindOf(it.kind)) }
                    snap.untracked.forEach {
                        put(it.path, com.closecode.ui.components.StatusKind.Untracked)
                    }
                    snap.conflicts.forEach {
                        put(it, com.closecode.ui.components.StatusKind.Modified)
                    }
                }
            }
        }
    }
    // The branch the repository is really on, for the run card's second line. Null means there
    // is no repository, and the card then shows only the state.
    var gitBranch by remember(slug) { mutableStateOf<String?>(null) }
    LaunchedEffect(slug, gitRevision) {
        val root = runCatching { repository.projectDir(slug) }.getOrNull() ?: return@LaunchedEffect
        val branch = withContext(Dispatchers.Default) {
            runCatching { com.closecode.ui.git.GitService(root).snapshot() }
                .getOrNull()?.getOrNull()?.branch
        }?.takeIf { it.isNotBlank() }
        gitBranch = branch
    }
    // Which half of the drawer is showing. Source Control opens first — it holds the account,
    // clone and publish controls, none of which need this project to have a repository of its own.
    var explorerTab by remember { mutableStateOf(ExplorerTabs.first()) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // v7: which non-explorer tool the sidebar is showing — Search, Terminal, Logcat or Problems.
    // Null means the sidebar shows the explorer's own tabs (Files or Source Control).
    var sidebarTool by remember { mutableStateOf<String?>(null) }
    // AI is a full page rather than an overlay: it keeps the bottom bar and the panel
    // history remembers where it was opened from. More stays the anchored sheet it has
    // always been.
    var moreOpen by remember { mutableStateOf(false) }
    var uiInspectorOpen by remember { mutableStateOf(false) }
    // Every one-field tree operation lands here: creating, renaming, moving and copying all ask
    // for a name first, so there is one place a name is asked for and one dialog that asks.
    var prompt by remember { mutableStateOf<TreePrompt?>(null) }
    // The row a delete is waiting to be confirmed on. Nothing goes on the tap that offered it:
    // there is no undo in this editor, so the second tap is the one that counts.
    var confirmDelete by remember { mutableStateOf<ProjectFile?>(null) }
    // The folder a system picker should drop its contents into, and the pick itself. The picker's
    // callback only pairs the two up; the read and the write happen in the effect below, which
    // runs with the composition that is actually on screen.
    var importFolder by remember { mutableStateOf<String?>(null) }
    val pendingImport = remember { mutableStateOf<Pair<String, Uri>?>(null) }
    // Whether the clone route is on screen. It takes no token here: it reads the store itself and
    // writes it back when it re-authenticates, so the hand-off is just "open it" and there is no
    // second copy of the token to fall out of date.
    var cloneOpen by remember { mutableStateOf(false) }
    // A unified diff opened from the Source Control tab. Full-screen over everything,
    // like the clone route, because a two-column diff has no business inside the drawer.
    var diffPath by remember { mutableStateOf<String?>(null) }
    // A commit's before/after diff opened from a History row. Same full-screen treatment:
    // commit id plus path, closed by the same Back order as the working-tree diff.
    var commitDiff by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The full history page and one commit's detail page, stacked: a commit opens over
    // the history, and Back walks back down the stack.
    var historyOpen by remember { mutableStateOf(false) }
    // OpenCode's own web page, opened from the AI tab's Running card. It covers the screen,
    // like the clone route: it is a browser, not a panel inside the drawer.
    var openCodeChat by remember { mutableStateOf(false) }
    var commitOpen by remember { mutableStateOf<String?>(null) }
    // The full build log over everything, for the same reason: errors want the whole screen.
    var buildLogOpen by remember { mutableStateOf(false) }
    // Written by that route when it re-authenticates. The drawer caches the token for as long as
    // it is composed, and this is how it is told to read it again rather than go on with the copy
    // it took when it was opened.
    var githubTokenEpoch by remember { mutableStateOf(0) }
    // The token is read again when either side moves: this screen's own sign-in, and a change
    // made on the settings screen while this screen was in the back stack.
    val gitTokenEpoch = githubTokenEpoch + tokenEpoch
    var query by remember { mutableStateOf("") }
    var findQuery by remember { mutableStateOf("") }
    // The search sheet's switches. They hold while the sheet is open so a pattern survives a
    // detour to look at a result and back.
    var findCaseSensitive by remember { mutableStateOf(false) }
    var findRegex by remember { mutableStateOf(false) }
    var findWholeWord by remember { mutableStateOf(false) }
    var explorerFilter by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }
    // Phase 3 install state: the APK the engine produced (null until packaging lands
    // one), and where its install stands. The panel draws rows only for a real file.
    var builtApkPath by remember(slug) { mutableStateOf<String?>(null) }
    var installState by remember(slug) {
        mutableStateOf<com.closecode.build.InstallState>(
            com.closecode.build.InstallState.Idle,
        )
    }
    // A plain stat per composition (microseconds): always fresh after a build without
    // depending on declaration order. Only the engine's own record counts: nothing probes
    // the project tree, because a file a clone happens to carry is not a build result.
    val builtApk: java.io.File? = resolveBuiltApk(builtApkPath)
    // Read by the install receiver, which is registered before the rest of the screen's
    // state exists: the package to open, and whether this run should open it.
    var autoOpenAfterInstall by remember { mutableStateOf(false) }
    var installTargetPackage by remember { mutableStateOf("") }
    var installedPackage by remember { mutableStateOf<String?>(null) }
    // One engine per project: the chain reports its seven steps through this stream.
    val buildEngine = remember(projectId) {
        com.closecode.build.BuildEngine(
            com.closecode.build.ToolchainHolder.store(context),
            com.closecode.build.BuildCache(java.io.File(context.cacheDir, "build-cache")),
        )
    }
    val engineState by buildEngine.state.collectAsState()
    val buildPanel = buildPanelModel(engineState)
    // One notification for the real steps. It is updated off the state stream, not off
    // recomposition, so going Home does not stop the updates. The terminal notifications
    // are posted by the build effect once the outcome is known.
    LaunchedEffect(buildEngine) {
        buildEngine.state.collect { state ->
            if (state is com.closecode.build.BuildState.Running &&
                com.closecode.build.BuildService.running
            ) {
                val progress = ((state.step.ordinal + 1) * 100 / 7).coerceIn(0, 100)
                com.closecode.build.BuildService.update(
                    context,
                    projectName,
                    state.step.label,
                    progress,
                )
            }
        }
    }
    var buildErrorCards by remember(slug) {
        mutableStateOf<List<ProjectDiagnostic>>(emptyList())
    }
    // The system's answer to our install session, wired into the state machine.
    DisposableEffect(slug) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context?,
                intent: android.content.Intent?,
            ) {
                val status = intent?.getIntExtra(
                    android.content.pm.PackageInstaller.EXTRA_STATUS,
                    -100,
                ) ?: return
                val packageName = intent.getStringExtra(
                    android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME,
                ).orEmpty()
                val message = intent.getStringExtra(
                    android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE,
                ).orEmpty()
                val next = com.closecode.build.nextInstallState(
                    installState,
                    com.closecode.build.InstallEvent.SystemStatus(status, packageName, message),
                )
                installState = next
                when (next) {
                    is com.closecode.build.InstallState.Installed -> {
                        installedPackage = next.packageName.ifBlank { installTargetPackage }
                    }
                    is com.closecode.build.InstallState.Failed -> {
                        autoOpenAfterInstall = false
                    }
                    else -> Unit
                }
                // The system's own confirmation screen for the install session.
                if (status == com.closecode.build.STATUS_PENDING_USER_ACTION) {
                    val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(
                            android.content.Intent.EXTRA_INTENT,
                            android.content.Intent::class.java,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT)
                    }
                    confirm?.let {
                        it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context?.startActivity(it) }
                    }
                }
                DebugLog.crumb(
                    "INSTALL",
                    "status=$status pkg=${packageName.ifEmpty { "?" }} msg=${message.take(120)}",
                )
            }
        }
        val filter = android.content.IntentFilter(
            com.closecode.build.InstallHelper.ACTION_INSTALL_STATUS,
        )
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    // The build and logcat streams. The log still carries nothing but what a build reports; the
    // build now reports a real sweep of the project's own files.
    val logLines = remember { mutableStateListOf<LogLine>() }
    // v7: the side panel's own level chip. The full Logcat page keeps its own state inside.
    var logcatFilter by remember { mutableStateOf<LogLevel?>(null) }
    // Scroll forensics, on demand: while Frame stats is on, every programmatic scroll move
    // (and every move nobody asked for) is mirrored into the Logcat panel, so the phone
    // itself produces the log — no adb needed. Off otherwise, so ordinary scrolling never
    // spams the panel builds use.
    // Debug log: off at every start (spec, Debug tools 2), turned on per session from
    // More > Developer. Off, the lambda in every DebugLog.line stays unbuilt, the file is
    // untouched, and ordinary scrolling never spams the Logcat panel either.
    var debugLogs by remember { mutableStateOf(false) }
    LaunchedEffect(debugLogs) {
        DebugLog.setEnabled(debugLogs)
        if (debugLogs) {
            ScrollLog.mirror = { message ->
                logLines.appendCapped(LogLine(LogLevel.DEBUG, "Scroll", message))
            }
            BackLog.mirror = { message ->
                logLines.appendCapped(LogLine(LogLevel.DEBUG, "Back", message))
            }
        } else {
            ScrollLog.mirror = null
            BackLog.mirror = null
        }
    }
    // More > Developer state: the developer sheet, the self-test sheet, the crash viewer and
    // the two destructive questions behind it.
    var developerOpen by remember { mutableStateOf(false) }
    var selfTestOpen by remember { mutableStateOf(false) }
    var selfTestFacts by remember { mutableStateOf("") }
    var selfTestChecks by remember { mutableStateOf<List<SelfTest.Check>>(emptyList()) }
    var selfTestRunning by remember { mutableStateOf(false) }
    var crashView by remember { mutableStateOf<String?>(null) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmDeleteTools by remember { mutableStateOf(false) }
    // Project settings: the package and Android versions the project check validates, and
    // the check itself. The record is read once when the project opens (and again after a
    // save), and the report is what the settings sheet and the build panel's blocked state
    // both show — the same check, not two guesses that could disagree.
    var projectSettingsOpen by remember { mutableStateOf(false) }
    var projectCheckBusy by remember { mutableStateOf(false) }
    var projectRecord by remember(slug) { mutableStateOf(repository.find(slug)) }
    val projectRoot = remember(slug) { runCatching { repository.projectDir(slug) }.getOrNull() }
    var projectPackageName by remember(slug) { mutableStateOf<String?>(null) }
    // The toolchain and package settings a real preview render needs. Loaded once when the
    // Preview panel opens (never on the compose's first frame), then reused across saves.
    var previewSetup by remember(slug, projectPackageName) {
        mutableStateOf<PreviewSetup?>(null)
    }
    var projectReport by remember(slug) {
        mutableStateOf<com.closecode.build.ProjectCheck.Report?>(null)
    }
    // What the build panel shows when the project check stopped a build.
    var buildBlocked by remember(slug) {
        mutableStateOf<com.closecode.ui.ide.BlockedInfo?>(null)
    }
    // Run means build → install → open. Build means only build. Each step is a real
    // Android call; nothing is called installed or opened without the system saying so.
    var autoOpenAfterBuild by remember { mutableStateOf(false) }
    // The run family (screens 25/26): the sheet the bottom card opens — build type, whether the
    // finished app opens itself — and the run page the sheet lands on after the install, when
    // the app does not open itself.
    var runSheetOpen by remember { mutableStateOf(false) }
    var runOpenAfterInstall by remember { mutableStateOf(true) }
    var runningFromSheet by remember { mutableStateOf(false) }
    var runInstallWaitingForPage by remember { mutableStateOf(false) }
    var runFinished by remember { mutableStateOf<com.closecode.ui.ide.RunResultInfo?>(null) }
    var runFinishedOpen by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<java.io.File?>(null) }
    var pendingOverlayOpen by remember { mutableStateOf<String?>(null) }
    var overlayAsked by remember { mutableStateOf(kvStore.getBoolean("overlay_asked", false)) }
    var showInstallSheet by remember { mutableStateOf(false) }
    var showOverlaySheet by remember { mutableStateOf(false) }
    var lastOpenedPackage by remember(slug) { mutableStateOf<String?>(null) }
    // The build's progress lives in a notification; Android 13+ needs this asked once.
    // The toolchain screen asks too, so this only covers a pack that is already on disk.
    var askedBuildNotifications by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    fun packageOfProject(): String = projectPackageName
        ?: diskTree.firstOrNull { it.path.endsWith("AndroidManifest.xml") }
            ?.let { manifestPackageOf(diskContents[it.path]) }
        ?: com.closecode.build.TemplateContract.packageForSlug(slug)

    LaunchedEffect(activePanel, slug, projectPackageName) {
        if (activePanel != "Preview" || previewSetup != null) return@LaunchedEffect
        val root = projectRoot ?: return@LaunchedEffect
        previewSetup = withContext(Dispatchers.IO) {
            loadPreviewSetup(
                context = context,
                projectRoot = root,
                packageName = packageOfProject(),
                savedMinSdk = projectRecord?.config?.minSdk?.toIntOrNull(),
                savedTargetSdk = projectRecord?.config?.targetSdk?.toIntOrNull(),
            )
        }
    }

    fun launchBuiltApp(packageName: String) {
        lastOpenedPackage = packageName
        val intent = com.closecode.build.InstallHelper.launchIntent(context, packageName)
        if (intent == null) {
            toast = "The app has no screen to open."
            return
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Opens the built app and, when Android allows it, adds the Back to editor pill.
     * No permission is asked twice: the first Open explains it, and a refusal becomes
     * the notification with the same action.
     */
    fun openBuiltApp(packageName: String) {
        lastOpenedPackage = packageName
        if (com.closecode.overlay.BackToEditorService.allowed(context)) {
            com.closecode.overlay.BackToEditorService.start(context)
            launchBuiltApp(packageName)
        } else if (!overlayAsked) {
            pendingOverlayOpen = packageName
            showOverlaySheet = true
        } else {
            launchBuiltApp(packageName)
            com.closecode.overlay.BackToEditorService.showFallback(context)
        }
    }

    fun installNow(apk: java.io.File) {
        pendingInstall = null
        installTargetPackage = packageOfProject()
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstall = apk
            installState = com.closecode.build.InstallState.NeedsPermission
            showInstallSheet = true
            return
        }
        val session = com.closecode.build.InstallHelper.install(
            context,
            apk,
            installTargetPackage,
        )
        if (session < 0) {
            pendingInstall = apk
            installState = com.closecode.build.InstallState.NeedsPermission
            showInstallSheet = true
            DebugLog.crumb("INSTALL", "permission missing before the session")
        } else {
            DebugLog.crumb(
                "INSTALL",
                "session opened id=$session · ${apk.name} · ${apk.length()} bytes",
            )
            installState = com.closecode.build.nextInstallState(
                installState,
                com.closecode.build.InstallEvent.SessionOpened(session),
            )
        }
    }

    // Returning from Android's permission screens. The install continues by itself once
    // the toggle is on; the floating button's permission is checked before the app opens.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                com.closecode.overlay.BackToEditorService.stop(context)
                val waiting = pendingInstall
                if (waiting != null &&
                    installState is com.closecode.build.InstallState.NeedsPermission &&
                    context.packageManager.canRequestPackageInstalls()
                ) {
                    installNow(waiting)
                }
                pendingOverlayOpen?.let { packageName ->
                    pendingOverlayOpen = null
                    if (com.closecode.overlay.BackToEditorService.allowed(context)) {
                        openBuiltApp(packageName)
                    } else {
                        overlayAsked = true
                        kvStore.putBoolean("overlay_asked", true)
                        launchBuiltApp(packageName)
                        com.closecode.overlay.BackToEditorService.showFallback(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The install ended; open the app only if this run asked for it.
    LaunchedEffect(installedPackage) {
        val packageName = installedPackage ?: return@LaunchedEffect
        installedPackage = null
        if (autoOpenAfterInstall) {
            autoOpenAfterInstall = false
            openBuiltApp(packageName)
        } else if (runInstallWaitingForPage) {
            // A sheet run with the toggle off: the install landed, so the run page can.
            runInstallWaitingForPage = false
            runFinishedOpen = true
        }
    }
    // Stored like the other preferences; the PTY backend that will read it is not built yet,
    // so the row says so and the pipe terminal runs either way for now.
    var usePipeTerminal by remember { mutableStateOf(kvStore.getInt("terminal_use_pipe", 0) == 1) }
    // True while the suggestion popup is up. It is the tool row's other reason to hide: the popup
    // covers the row, and a row under a popup only steals taps.
    var suggestionsOpen by remember { mutableStateOf(false) }
    // The soft keyboard is what the tool row exists for — tab, brace, quote and comment are not on
    // a phone keyboard — so its presence decides whether the row is there. Measured from the
    // window height, never cached in a flag and never from the inset APIs: one reading drives
    // the key row, the bottom bar and the back handler, so the three can never disagree. The
    // inset values survive below as log columns, so the log still shows when they disagree.
    val imeOpen = KeyboardVisibility.open
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeInsetOpen = WindowInsets.isImeVisible
    LaunchedEffect(imeOpen, imeBottomPx, imeInsetOpen, KeyboardVisibility.heightPx) {
        ScrollLog.record(
            "ime",
            if (imeOpen) 1 else 0,
            KeyboardVisibility.heightPx,
            (if (imeOpen) "ime-open" else "ime-closed") +
                " wInsets=$imeInsetOpen bottom=$imeBottomPx",
        )
        DebugLog.line("KEYBOARD") {
            "ime=${if (imeOpen) "open" else "closed"} height=${KeyboardVisibility.heightPx}px " +
                "windowInsets=$imeInsetOpen bottom=${imeBottomPx}px"
        }
    }
    // Tapping outside the code — a tab, the bar, the drawer, another panel — puts the
    // keyboard away: the key row follows the keyboard, so it follows these taps too, within
    // the row's own 100 ms. Typing-adjacent taps (save, undo, search field) keep it.
    val navKeyboard = LocalSoftwareKeyboardController.current
    val navFocus = LocalFocusManager.current
    fun dismissKeyboard() {
        navKeyboard?.hide()
        navFocus.clearFocus()
    }

    /**
     * Opens OpenCode's provider login in a fresh terminal.
     *
     * The command is a wrapper that runs the installed OpenCode binary with the same HOME and
     * XDG directories as the web server. The terminal is the app's own PTY, so the login can
     * ask its questions and show its codes. Nothing is sent anywhere by CloseCode itself.
     */
    fun authenticateOpenCode() {
        val command = com.closecode.ai.OpenCode.authCommand(context)
        if (command == null) {
            toast = "Start OpenCode once, then authenticate."
            return
        }
        terminalSessions.openAndRun(command)
        dismissKeyboard()
        activePanel = "Terminal"
        toast = "Finish authentication in Terminal, then return to AI and tap Chat."
    }
    // How far the code text is pinched away from the size the settings chose. The gesture is heard
    // on the whole screen — a pinch over the top bar or the tool row zooms the editor too — so the
    // state lives here rather than down in the editor that only reads it.
    var editorScale by remember { mutableFloatStateOf(1f) }
    // How far in from the left edge a pull open may start, and how far that pull has to travel.
    // The system's own slop is used for the second so the gesture feels like every other panel on
    // the phone; the band is fixed, because it is a region of the screen rather than a tolerance.
    // Live editor buffers keyed by file name. The editor owns its TextFieldValue, so this mirror is
    // how the parent saves before Preview and counts diagnostics across the whole project.
    val liveBuffers = remember { mutableStateMapOf<String, String>() }
    // Cold disk reads used to run inline on the tap that asked for them, which stalled the very
    // frame that opened a file. Every one of them now goes through this scope instead.
    val fileScope = rememberCoroutineScope()
    // Bumped by every write to a buffer or to its saved copy. One Int stands in for two maps whose
    // entries hold the whole text of every open file, so the project-wide pass below restarts when
    // content really changes instead of copying both maps on every single frame.
    var contentRevision by remember(slug) { mutableIntStateOf(0) }
    // Answering "is this tab dirty?" is a whole-file string compare, and the top bar asks on every
    // keystroke while the tab strip asks once per visible tab. `noteContent` keeps the answer next
    // to the write that could have changed it, so the question becomes a map lookup.
    val dirtyTabs = remember(slug) { mutableStateMapOf<String, Boolean>() }

    /**
     * Every write to a buffer or to its saved copy goes through here. The counter is what the
     * project-wide analysis keys on instead of copying both content maps on every frame; the
     * cached answer is what the top bar and the tab strip read instead of re-comparing whole files
     * on every keystroke. Write first, then call — that ordering is what keeps the cache honest.
     */
    fun noteContent(name: String) {
        contentRevision++
        dirtyTabs[name] = liveBuffers[name] != null && liveBuffers[name] != diskContents[name]
    }
    // Spec 6.2 and 6.8 both need to open problems from outside the editor, so the sheet's target
    // line lives here rather than inside IdeEditorArea.
    val problemSheet = remember { mutableStateOf<Int?>(null) }
    // Which set of problems the sheet is showing. The Run button blocks on the whole project, so a
    // sheet opened from there has to show the whole project too — otherwise it says "No problems.
    // Code is clean." immediately after the app has just said it cannot run.
    var problemSheetScope by remember { mutableStateOf(ProblemScope.FILE) }
    // The totals cover the project, not just the open file. The diagnostics themselves are kept too,
    // because a count cannot answer the question a blocked Run raises: *which* file is broken.
    var projectErrors by remember { mutableStateOf(0) }
    var projectWarnings by remember { mutableStateOf(0) }
    var projectErrorList by remember { mutableStateOf<List<ProjectDiagnostic>>(emptyList()) }
    var projectWarningList by remember { mutableStateOf<List<ProjectDiagnostic>>(emptyList()) }
    // Build panel state: the sweep's own numbers, not a timer walking a list of task names.
    var buildProgress by remember { mutableFloatStateOf(0f) }
    var buildElapsedMs by remember { mutableStateOf(0L) }
    var buildErrorCount by remember { mutableStateOf(0) }
    var buildWarningCount by remember { mutableStateOf(0) }
    var buildPhase by remember { mutableStateOf(BuildPhase.IDLE) }
    var buildFileCount by remember { mutableIntStateOf(0) }
    // How long each sweep phase took, for the step rows. Zero until a build measures them.
    var buildSymbolsMs by remember { mutableStateOf(0L) }
    var buildFilesMs by remember { mutableStateOf(0L) }
    // Whether a build has ever finished: the "last build" line only exists after one.
    // Keyed on the project: a switch must not carry the last project's "built" word over.
    var hasBuilt by remember(slug) { mutableStateOf(false) }
    // Every name declared anywhere in the project. The editor's own per-file pass needs it so a
    // composable defined in Theme.kt is not reported as missing in MainActivity.kt.
    var projectNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Format requests are a counter because the editor owns the text field. The editor formats
    // the live buffer when this changes; the parent only asks for a format.
    var formatRequest by remember { mutableStateOf(0) }
    // Same counter shape as formatRequest, for the selection-scoped formatter. A closure cannot cross
    // here because the buffer the formatter edits lives in the editor, not in this composable.
    var formatSelectionRequest by remember { mutableStateOf(0) }
    // Web preview reloads when this changes. It is bumped only after a file has actually been
    // written, so switching panels or typing does not restart the page by itself.
    var previewRefresh by remember { mutableStateOf(0) }
    // Bumped when something other than typing rewrites the open buffer — today only the block
    // editor does. The editor owns its TextFieldValue, so this is the signal telling it to re-seed
    // from the buffer instead of silently keeping text the outline no longer matches.
    var sourceRevision by remember { mutableIntStateOf(0) }
    // Where the next external rewrite should leave the caret. Only the add-a-block sheet sets a real
    // offset — it knows exactly which line it just wrote — and everything else resets it to -1 so
    // the editor falls back to the end of the file's code rather than to a stale position.
    var caretRequest by remember { mutableIntStateOf(-1) }
    // The caret the editor is currently on, reported on every move. The block sheet inserts at the
    // line the reader is looking at, not at the end of the buffer.
    var editorCaret by remember { mutableIntStateOf(0) }
    var addBlockOpen by remember { mutableStateOf(false) }
    fun openAiPanel() {
        if (activePanel == "AI") return
        activePanel = "AI"
    }

    /**
     * Ask AI on one build error: the diagnostic becomes the agent's next message on the
     * project's session, and the chat page opens while the agent works. When the server is
     * not up, the prompt lands in the clipboard and the AI panel opens with a note — the
     * prompt is never silently lost.
     */
    fun askAiFromDiagnostic(fileName: String, line: Int, message: String) {
        val prompt = com.closecode.ai.OpenCodePrompts.diagnosticPrompt(fileName, line, message)
        val open = com.closecode.ai.OpenCode.state.value
        val ready = open.phase == com.closecode.ai.OpenCodePhase.Running &&
            open.url.isNotBlank() && open.sessionId.isNotBlank()
        activePanel = "AI"
        if (ready) {
            fileScope.launch(Dispatchers.IO) {
                val sent = com.closecode.ai.OpenCodePrompts.send(open.url, open.sessionId, prompt, open.basicAuth)
                withContext(Dispatchers.Main) {
                    if (sent) {
                        openCodeChat = true
                    } else {
                        treeClipboard.setText(androidx.compose.ui.text.AnnotatedString(prompt))
                        toast = "Server did not take the message; the prompt is in the clipboard."
                    }
                }
            }
        } else {
            treeClipboard.setText(androidx.compose.ui.text.AnnotatedString(prompt))
            toast = "Prompt copied. Start OpenCode, then paste it in the chat."
        }
    }

    // Spec 4.1: the header's Undo and Redo drive the editor's own history. Requests are counters
    // because only the editor owns the text field; the availability flags travel back up so the
    // icons can dim when there is nothing left to step to.
    var undoRequest by remember { mutableStateOf(0) }
    var redoRequest by remember { mutableStateOf(0) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    // First paint only, keyed on the project rather than on the tree: a tree refresh after a new
    // file is created must not shove the editor back onto the project's first file. Every opened
    // tab is read here, not just the active one, so selecting a neighbour shows its own source
    // instead of the placeholder snippet. The walk and the reads run off the main thread; the state
    // they feed is written back on it, because `withContext` returns here.
    LaunchedEffect(slug, treeVersion, showGenerated) {
        val files = withContext(Dispatchers.IO) { repository.tree(slug, showGenerated) }
        diskTree = files
        if (treeVersion != 0) return@LaunchedEffect
        // Reopening restores the last session when it still matches the tree; otherwise the
        // first three files open as before. Paths that no longer exist are dropped silently;
        // entries saved by older builds held names and simply match nothing.
        val remembered = restoredTabNames.mapNotNull { path ->
            files.firstOrNull { it.kind != FileKind.Folder && it.path == path }
        }
        val sources = if (remembered.isNotEmpty()) remembered
        else files.filter { it.kind != FileKind.Folder }.take(3)
        tabs = sources
        active = sources.firstOrNull { it.path == restoredActive }?.path
            ?: sources.firstOrNull()?.path.orEmpty()
        selectedPath = active.ifEmpty { null }
        val loaded = withContext(Dispatchers.IO) {
            sources.mapNotNull { file ->
                runCatching { repository.readFile(slug, file.path) }.getOrNull()
                    ?.let { file.path to it }
            }
        }
        loaded.forEach { (path, text) ->
            diskContents[path] = text
            noteContent(path)
            // An empty buffer for a file that has text is the editor's opening seed, not an
            // edit. Drop it so the reseed below can show the text that just arrived.
            if (liveBuffers[path].isNullOrEmpty() && text.isNotEmpty()) liveBuffers.remove(path)
        }
        // The editor composes before this read lands and seeds from an empty buffer. If the
        // reader has not typed into it, reseed now: otherwise the empty seed stays on screen,
        // differs from the text that just arrived, and auto save writes the empty buffer over
        // the file. A slow device makes that race wide enough to lose a real file. An empty
        // live buffer counts as "not typed into": it is the seed, and must not win over disk.
        val live = liveBuffers[active]
        if (live == null || (live.isEmpty() && diskContents[active]?.isNotEmpty() == true)) {
            sourceRevision++
        }
    }

    val activeFile = tabs.firstOrNull { it.path == active }
    // The open file's display name. Identity below is the path — two files called index.js in
    // different folders are two buffers — and this is only ever shown or matched by name.
    val activeName = activeFile?.name ?: active.substringAfterLast('/')
    val language = activeFile?.language ?: "Kotlin"

    // One place writes the active file, so the header button, the palette, Save-on-Preview and the
    // unsaved dot can never disagree about what "saved" means.
    fun saveActiveFile(notify: Boolean = true): Boolean {
        val path = activeFile?.path
        val raw = liveBuffers[active]
        if (path == null || raw == null) {
            toast = "Nothing to save"
            return false
        }
        // Format on save rewrites the buffer before it reaches disk, and the editor is handed the
        // result through `sourceRevision`. Skipping that would leave the file on disk and the text
        // on screen as two different documents — worse than never formatting at all.
        val buffer = if (settings.formatOnSave) {
            val formatted = if (active.endsWith(".kt", true)) {
                SourceEdits.formatKotlin(raw)
            } else {
                SourceEdits.normalizeTrailingWhitespace(raw)
            }
            if (formatted != raw) {
                liveBuffers[active] = formatted
                sourceRevision++
            }
            formatted
        } else {
            raw
        }
        // Deliberately still on the caller's thread: Save-on-Preview hands straight over to a
        // panel that reads the file back from disk, and a write in flight would serve the page it
        // had before the save. A save is a tap, not a keystroke, so it is not on the typing path.
        return runCatching { repository.writeFile(slug, path, buffer) }
            .onSuccess {
                diskContents[active] = buffer
                noteContent(active)
                previewRefresh++
                gitRevision++
                if (notify) toast = "Saved $active"
            }
            .onFailure { toast = "Could not save $active" }
            .isSuccess
    }

    // Auto save: one write once typing stops rather than one per keystroke, because a save is a
    // directory write. The guard re-reads the buffer after the pause so a save that already
    // happened — or a tab switched away meanwhile — cannot write a second time, and it never
    // announces itself: a toast every time you stop typing would be unusable.
    LaunchedEffect(settings.autoSave, contentRevision, active) {
        if (!settings.autoSave || active.isEmpty()) return@LaunchedEffect
        val buffer = liveBuffers[active] ?: return@LaunchedEffect
        if (buffer == diskContents[active]) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        if (liveBuffers[active] == buffer) saveActiveFile(notify = false)
    }

    // The session persists on every tab switch, never on keystroke: tabs, active file and
    // places only move when the reader moves between files.
    LaunchedEffect(tabs, active) {
        persistSession()
    }

    // Every tree mutation ends here, so a file created from the drawer, a file created from the
    // command palette and a file deleted from the shell all repaint the drawer the same way.
    fun refreshTree() {
        treeVersion++
        gitRevision++
    }

    /**
     * The project check, run when a project opens and after every settings save. It reads
     * the project's own files, so it happens off the main thread; the report lands in
     * state for the settings sheet and the build panel's blocked state — one check, so
     * the two can never disagree.
     */
    fun runProjectCheck() {
        val dir = runCatching { repository.projectDir(slug) }.getOrNull() ?: return
        projectCheckBusy = true
        fileScope.launch {
            val (input, packageOnScreen) = withContext(Dispatchers.IO) {
                val templateValue = projectRecord?.takeIf { !it.imported }
                val input = com.closecode.build.ProjectCheck.Input(
                    savedPackage = templateValue?.config?.packageName?.takeIf { it.isNotBlank() },
                    savedMinSdk = templateValue?.config?.minSdk?.toIntOrNull(),
                    savedTargetSdk = templateValue?.config?.targetSdk?.toIntOrNull(),
                    savedCompileSdk = templateValue?.config?.compileSdk?.toIntOrNull(),
                    otherPackages = repository.reservedPackages(projectId),
                    freeBytes = context.filesDir.usableSpace,
                )
                val shown = templateValue?.config?.packageName
                    ?: projectRecord?.let {
                        runCatching { repository.packageOf(it) }.getOrNull()
                    }
                input to shown
            }
            projectPackageName = packageOnScreen
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    com.closecode.build.ProjectCheck.check(dir, slug, input)
                }.getOrNull()
            }
            if (report != null) {
                projectReport = report
                DebugLog.crumb(
                    "BUILD",
                    "project check · ${report.errors.size} errors · ${report.warnings.size} warnings",
                )
            }
            projectCheckBusy = false
        }
    }
    LaunchedEffect(slug, projectRecord) { runProjectCheck() }

    /**
     * Unsaved changes: the buffer in memory differs from the last thing read from disk. The answer
     * is kept beside the write that could have changed it — see [noteContent] — so a miss is the
     * only thing that pays for the comparison, and a miss still falls through to the honest one.
     */
    fun isDirtyTab(name: String): Boolean =
        dirtyTabs[name]
            ?: (liveBuffers[name] != null && liveBuffers[name] != diskContents[name]).also {
                dirtyTabs[name] = it
            }

    // Unsaved files by path, for the tree's yellow dots. Recomputed when the revision counter
    // moves, which is every route that could have changed an answer.
    val dirtyPaths = remember(tabs, contentRevision) {
        tabs.filter { isDirtyTab(it.path) }.map { it.path }.toSet()
    }

    // Leaving asks first when dirty; the plain onExit only runs for a clean exit or after the
    // dialog. The ordered chain further down ends here, never past it. Declared beside the
    // dirty helpers because only they can answer the question.
    var exitConfirm by remember { mutableStateOf(false) }
    fun requestExit() {
        exitConfirm = tabs.any { isDirtyTab(it.path) }
        if (!exitConfirm) onExit()
    }

    // The exit question reads this, so report it whenever it could have moved. The compare is
    // one pass over open tabs, not over file contents — the contents were already compared by
    // noteContent on the writes that changed them.
    LaunchedEffect(tabs, contentRevision) {
        onDirtyChange(tabs.any { isDirtyTab(it.path) })
    }

    /**
     * Writes every dirty buffer back, with the same format-on-save the single save applies, so
     * leaving via Save never writes a different file than tapping Save would have. Reports false
     * when anything failed, so the exit confirm can stay open on the failure instead of leaving
     * a half-saved project behind.
     */
    fun saveAllDirty(): Boolean {
        var ok = true
        tabs.filter { isDirtyTab(it.path) }.forEach { file ->
            val raw = liveBuffers[file.path] ?: return@forEach
            val buffer = if (settings.formatOnSave) {
                if (file.name.endsWith(".kt", true)) SourceEdits.formatKotlin(raw)
                else SourceEdits.normalizeTrailingWhitespace(raw)
            } else {
                raw
            }
            runCatching { repository.writeFile(slug, file.path, buffer) }
                .onSuccess {
                    liveBuffers[file.path] = buffer
                    diskContents[file.path] = buffer
                    noteContent(file.path)
                }
                .onFailure {
                    ok = false
                    toast = "Could not save ${file.name}"
                }
        }
        previewRefresh++
        gitRevision++
        return ok
    }

    /**
     * Saves what Project settings edited: the files first — the build files, the manifest
     * and the source folder for a package change — then the record that names them. Dirty
     * buffers land on disk before a folder moves, and the open tabs are dropped after a
     * package change because every path in them belongs to the old folder.
     */
    fun saveProjectSettings(newPackage: String, newMinSdk: Int, newTargetSdk: Int, newCompileSdk: Int) {
        val record = projectRecord ?: return
        if (record.imported) {
            toast = "Imported projects keep their own package"
            return
        }
        val before = record.config
        val packageChanged = newPackage != before.packageName
        val sdkChanged = newMinSdk.toString() != before.minSdk ||
            newTargetSdk.toString() != before.targetSdk ||
            newCompileSdk.toString() != before.compileSdk
        if (!packageChanged && !sdkChanged) {
            projectSettingsOpen = false
            return
        }
        projectCheckBusy = true
        fileScope.launch {
            saveAllDirty()
            val dir = runCatching { repository.projectDir(slug) }.getOrNull()
            val problems = if (dir == null) {
                listOf("The project folder could not be opened.")
            } else {
                withContext(Dispatchers.IO) {
                    val results = mutableListOf<com.closecode.build.ProjectFiles.Result>()
                    if (sdkChanged) {
                        results += com.closecode.build.ProjectFiles.applySdks(
                            dir,
                            newMinSdk,
                            newTargetSdk,
                            newCompileSdk,
                        )
                    }
                    if (packageChanged) {
                        results += com.closecode.build.ProjectFiles.applyPackage(
                            dir,
                            before.packageName,
                            newPackage,
                        )
                    }
                    results.flatMap { it.problems }
                }
            }
            val updated = repository.updateConfig(
                slug,
                before.copy(
                    packageName = newPackage,
                    minSdk = newMinSdk.toString(),
                    targetSdk = newTargetSdk.toString(),
                    compileSdk = newCompileSdk.toString(),
                ),
            )
            projectRecord = updated
            if (packageChanged) {
                tabs = emptyList()
                active = ""
            }
            refreshTree()
            DebugLog.crumb(
                "BUILD",
                "project settings saved · pkg=$newPackage min=$newMinSdk target=$newTargetSdk " +
                    "compile=$newCompileSdk" +
                    if (problems.isEmpty()) "" else " · ${problems.size} file problems",
            )
            toast = when {
                problems.isNotEmpty() -> problems.first()
                packageChanged -> "Package changed to $newPackage"
                else -> "Project settings saved"
            }
            projectCheckBusy = false
            runProjectCheck()
        }
    }

    // Set when a search result opens a file: the next Back returns to the results sheet with
    // its query and hits intact, instead of leaving the project.
    var findReturn by remember { mutableStateOf(false) }
    LaunchedEffect(activePanel) {
        // A search result's way back is its results sheet, but only while the reader stays
        // where the result put them: leaving for another panel orphans the return, and a stale
        // flag would later resurrect an empty sheet over unrelated work.
        if (activePanel != "Code" && findReturn) findReturn = false
    }

    /** Opens a file in a tab, reading it only when it has not been read before. */
    fun openFile(file: ProjectFile) {
        if (file.kind == FileKind.Folder) return
        active = file.path
        selectedPath = file.path
        if (tabs.none { it.path == file.path }) tabs = tabs + file
        tabs = retainTabs(tabs, active, ::isDirtyTab)
        // A cached empty copy is not proof that the file is empty: the first read can land
        // before the template's own write finishes, or before an outside edit. Re-read in that
        // case so opening the file recovers instead of showing a stale "Empty file".
        if (file.kind != FileKind.Image && diskContents[file.path].isNullOrEmpty()) {
            // Off the main thread: this runs from a tap on a drawer row, and a cold read of a big
            // file used to be the frame that opened it. The tab shows nothing until it lands rather
            // than freezing — and a freshly opened buffer is never dirty, so no dot flashes.
            val path = file.path
            fileScope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { repository.readFile(slug, path) }.getOrNull()
                }
                if (text != null) {
                    val live = liveBuffers[path]
                    // Replace a missing copy or an empty opening seed. A buffer the reader has
                    // actually typed into is theirs and is never overwritten by this read.
                    if (live == null || (live.isEmpty() && text.isNotEmpty())) {
                        diskContents[path] = text
                        noteContent(path)
                        if (live != null) liveBuffers.remove(path)
                        if (active == path) sourceRevision++
                    }
                }
            }
        }
    }

    fun createEntry(isFolder: Boolean, rawName: String) {
        // The parent is the row the drawer is pointed at rather than the file that happens to be
        // open: a file created from a folder lands in that folder, one created with nothing
        // selected lands at the root, and neither depends on which tab was last switched to.
        val selected = diskTree.firstOrNull { it.path == selectedPath }
        val parent = when {
            selected == null -> ""
            selected.kind == FileKind.Folder -> selected.path
            else -> selected.path.substringBeforeLast('/', "")
        }
        val leaf = rawName.trim().removePrefix("/")
        if (leaf.isEmpty()) return
        val relative = if (parent.isEmpty()) leaf else "$parent/$leaf"
        runCatching {
            if (isFolder) repository.createFolder(slug, relative)
            else repository.createFile(slug, relative, "")
        }.onSuccess {
            // Open every folder between the root and the new entry. The drawer hides a subtree the
            // moment its folder is collapsed, so without this the row is computed and never drawn
            // — which is what "the file did not appear" always was.
            ancestorFoldersOf(relative).forEach { folder ->
                collapsedFolders = collapsedFolders - folder
            }
            // Built rather than rediscovered: the record lands in the same frame as the write, and
            // there is no second tree walk left to race the refresh below.
            val created = repository.entryFor(slug, relative, isFolder)
            refreshTree()
            if (isFolder) {
                selectedPath = created.path
            } else {
                openFile(created)
            }
            toast = "Created $leaf"
        }.onFailure { toast = it.message ?: "Could not create $leaf" }
    }

    /**
     * Where a renamed or moved entry leaves the editor. Tabs hold the path, and buffers and
     * dirty flags are keyed by it too — two files called index.js in different folders are two
     * buffers — so all three follow the file, or the tab keeps drawing a path that no longer
     * exists and the next save writes to the old place.
     */
    fun retargetPath(from: ProjectFile, to: ProjectFile) {
        tabs = tabs.map { if (it.path == from.path) to else it }
        if (from.path != to.path) {
            liveBuffers.remove(from.path)?.let { liveBuffers[to.path] = it }
            diskContents.remove(from.path)?.let { diskContents[to.path] = it }
            dirtyTabs.remove(from.path)?.let { dirtyTabs[to.path] = it }
            if (active == from.path) active = to.path
        }
        if (selectedPath == from.path) selectedPath = to.path
    }

    // Renames may land on a name another folder already uses. Buffers are keyed by path, so
    // the two files stay two buffers and nothing is refused: tabs sharing a name show their
    // parent folder beside it instead.
    fun renameEntry(target: ProjectFile, newName: String) {
        runCatching { repository.renamePath(slug, target.path, newName) }
            .onSuccess { landed ->
                val isFolder = target.kind == FileKind.Folder
                retargetPath(target, repository.entryFor(slug, landed, isFolder))
                refreshTree()
                toast = "Renamed to $newName"
            }
            .onFailure { toast = it.message ?: "Could not rename ${target.name}" }
    }

    fun transferEntry(target: ProjectFile, destination: String, copy: Boolean) {
        runCatching {
            if (copy) repository.copyPath(slug, target.path, destination)
            else repository.movePath(slug, target.path, destination)
        }.onSuccess { landed ->
            if (!copy) {
                val isFolder = target.kind == FileKind.Folder
                retargetPath(target, repository.entryFor(slug, landed, isFolder))
            }
            refreshTree()
            toast = (if (copy) "Copied to " else "Moved to ") + landed
        }.onFailure {
            toast = it.message ?: "Could not ${if (copy) "copy" else "move"} ${target.name}"
        }
    }

    fun deleteEntry(target: ProjectFile) {
        val prefix = "${target.path}/"
        runCatching { repository.deletePath(slug, target.path) }
            .onSuccess {
                // A deleted file's tab, buffer and dirty flag go with it. Left behind, the strip
                // keeps drawing a file that is no longer on disk and the next save recreates it.
                fun inside(file: ProjectFile) = file.path == target.path || file.path.startsWith(prefix)
                val gone = tabs.filter(::inside)
                gone.forEach { file ->
                    liveBuffers.remove(file.path)
                    diskContents.remove(file.path)
                    dirtyTabs.remove(file.path)
                }
                tabs = tabs.filterNot(::inside)
                if (gone.any { it.path == active }) active = tabs.lastOrNull()?.path.orEmpty()
                if (selectedPath?.let { it == target.path || it.startsWith(prefix) } == true) {
                    selectedPath = tabs.firstOrNull { it.path == active }?.path
                }
                refreshTree()
                toast = "Deleted ${target.name}"
            }
            .onFailure { toast = it.message ?: "Could not delete ${target.name}" }
    }

    /**
     * "Reveal in file manager" hands the file to whatever app asks for it. Projects live in this
     * app's private storage, which no file manager can open, so there is no path another app could
     * be pointed at — a FileProvider URI is the honest version of the same promise, and it grants
     * this one file and nothing around it. Folders are not offered one: a content URI for a
     * directory has no receiver.
     */
    fun shareEntry(target: ProjectFile) {
        runCatching {
            val file = ProjectSafety.resolveInProject(repository.projectDir(slug), target.path)
            if (!file.isFile) throw ProjectPathException("Not a file: ${target.path}")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${target.name}"))
        }.onFailure { toast = it.message ?: "Could not share ${target.name}" }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val folder = importFolder
        importFolder = null
        if (uri != null && folder != null) pendingImport.value = folder to uri
    }

    // The picker's callback only pairs a folder with a URI. Reading it, deciding it is text and
    // writing it into the project all happen here, where the composition on screen — and so the
    // tree, the tabs and the toast — is the current one.
    LaunchedEffect(pendingImport.value) {
        val pending = pendingImport.value ?: return@LaunchedEffect
        pendingImport.value = null
        val (folder, uri) = pending
        val name = documentName(context, uri)?.substringAfterLast('/') ?: "imported.txt"
        val declared = documentSize(context, uri)
        if (declared != null && declared > MAX_IMPORT_BYTES) {
            toast = "$name is too large to import"
            return@LaunchedEffect
        }
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            toast = "Could not read $name"
            return@LaunchedEffect
        }
        if (bytes.size > MAX_IMPORT_BYTES) {
            toast = "$name is too large to import"
            return@LaunchedEffect
        }
        // There is no undo here, and a binary decoded as UTF-8 is garbage the moment it is
        // written, so a nul byte in the first page is the end of the conversation.
        if (bytes.asSequence().take(MAX_IMPORT_PROBE).any { it == 0.toByte() }) {
            toast = "$name is not a text file"
            return@LaunchedEffect
        }
        val leaf = uniqueLeafIn(folder, name) { candidate -> diskTree.any { it.path == candidate } }
        val relative = if (folder.isEmpty()) leaf else "$folder/$leaf"
        runCatching { repository.createFile(slug, relative, bytes.toString(Charsets.UTF_8)) }
            .onSuccess {
                ancestorFoldersOf(relative).forEach { collapsedFolders = collapsedFolders - it }
                refreshTree()
                openFile(repository.entryFor(slug, relative, isFolder = false))
                toast = "Imported $leaf"
            }
            .onFailure { toast = it.message ?: "Could not import $name" }
    }

    /**
     * A copy beside the original, named "name copy.ext" (then "name copy2.ext" and so on when
     * taken). Folders copy whole; the drawer reveals the copy so it does not arrive silently.
     */
    fun duplicateEntry(target: ProjectFile) {
        val parent = target.path.substringBeforeLast('/', "")
        val dot = target.name.lastIndexOf('.')
        val base = if (dot > 0) target.name.substring(0, dot) else target.name
        val ext = if (dot > 0) target.name.substring(dot) else ""
        val leaf = uniqueLeafIn(parent, "$base copy$ext") { candidate ->
            diskTree.any { it.path == candidate }
        }
        val dest = if (parent.isEmpty()) leaf else "$parent/$leaf"
        runCatching { repository.copyPath(slug, target.path, dest) }
            .onSuccess {
                refreshTree()
                selectedPath = dest
                toast = "Duplicated as $leaf"
            }
            .onFailure { toast = it.message ?: "Could not duplicate ${target.name}" }
    }

    /** Every long-press action, in one place: the row is selected first, so creation lands in it. */
    fun onTreeAction(action: TreeAction, file: ProjectFile) {
        when (action) {
            TreeAction.NEW_FILE -> {
                selectedPath = file.path
                prompt = TreePrompt.New(isFolder = false)
            }

            TreeAction.NEW_FOLDER -> {
                selectedPath = file.path
                prompt = TreePrompt.New(isFolder = true)
            }

            TreeAction.IMPORT -> {
                selectedPath = file.path
                importFolder = file.path
                importLauncher.launch(ImportMimeTypes)
            }

            TreeAction.RENAME -> prompt = TreePrompt.Rename(file)
            TreeAction.DUPLICATE -> duplicateEntry(file)
            TreeAction.COPY_PATH -> {
                treeClipboard.setText(androidx.compose.ui.text.AnnotatedString(file.path))
                toast = "Path copied"
            }
            TreeAction.MOVE -> prompt = TreePrompt.Transfer(file, copy = false)
            TreeAction.COPY -> prompt = TreePrompt.Transfer(file, copy = true)
            TreeAction.SHARE -> shareEntry(file)
            TreeAction.DELETE -> confirmDelete = file
        }
    }

    // Spec 6.9: the status bar counts the whole project. Debounced harder than the per-file pass
    // and capped by file count, because this re-runs on every keystroke in any file. The key is the
    // revision counter, not the maps: a Map key here meant deep-copying the whole text of every
    // open file on every frame just to find out that nothing had changed.
    LaunchedEffect(slug, contentRevision) {
        // Only Kotlin is analysed, so only Kotlin can be counted. Silently including Java and XML
        // here would show a total that never changes; silently excluding them would look like those
        // files are clean. The status bar says "not checked" for them instead.
        val sources = diskTree
            .filter { it.kind == FileKind.Kotlin }
            .take(PROJECT_ANALYSIS_MAX_FILES)
            .map { it.name to (liveBuffers[it.path] ?: diskContents[it.path].orEmpty()) }
        if (sources.isEmpty()) {
            projectErrorList = emptyList()
            projectWarningList = emptyList()
            projectErrors = 0
            projectWarnings = 0
            return@LaunchedEffect
        }
        delay(PROJECT_ANALYSIS_DEBOUNCE_MS)
        val index = AndroidApiAssets.cached()
        val totals = withContext(Dispatchers.Default) {
            // Names declared anywhere in the project, so a type or function coming from another
            // file is not reported as missing in this one.
            val declared = KotlinAnalyzer.projectSymbols(sources.map { it.second })
            val errors = ArrayList<ProjectDiagnostic>()
            val warnings = ArrayList<ProjectDiagnostic>()
            for ((name, text) in sources) {
                if (text.isBlank() || text.length > MAX_ANALYZED_CHARS) continue
                runCatching { KotlinAnalyzer.analyze(text, index, declared) }.getOrNull()?.diagnostics
                    ?.forEach { d ->
                        when (d.severity) {
                            Severity.ERROR -> errors += ProjectDiagnostic(name, d)
                            Severity.WARNING -> warnings += ProjectDiagnostic(name, d)
                        }
                    }
            }
            Triple(errors, warnings, declared)
        }
        projectErrorList = totals.first
        projectWarningList = totals.second
        projectErrors = totals.first.size
        projectWarnings = totals.second.size
        projectNames = totals.third
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1700)
            toast = null
        }
    }
    // The real build. The engine's chain does the work — resources, Compose, dex, pack,
    // sign — and the panel draws its seven steps from the state stream. Every row is work
    // that actually ran, with the milliseconds it took; there is no Gradle and no timer.
    LaunchedEffect(building) {
        if (!building) return@LaunchedEffect
        buildBlocked = null
        buildErrorCards = emptyList()
        hasBuilt = false
        buildProgress = 0f
        buildPhase = BuildPhase.SYMBOLS
        val store = com.closecode.build.ToolchainHolder.store(context)
        val projectRoot = runCatching { repository.projectDir(slug) }.getOrNull()
        if (projectRoot == null) {
            building = false
            toast = "The project folder is missing"
            return@LaunchedEffect
        }
        // The foreground slot keeps the process alive when the user goes Home, and its
        // Cancel action calls the same engine method the panel's Cancel button does.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !askedBuildNotifications
        ) {
            askedBuildNotifications = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        com.closecode.build.BuildService.start(context, projectName)
        com.closecode.build.BuildCancel.register { buildEngine.cancel() }
        // Every piece the chain needs. A missing piece is setup, and the engine says so;
        // nothing here guesses at a tool that is not on disk.
        val configured = projectRecord?.takeIf { !it.imported }
        val setup = com.closecode.build.BuildSetupFactory.create(
            context,
            com.closecode.build.BuildSetup.Project(
                id = projectId,
                slug = slug,
                name = projectName,
                directory = projectRoot,
                savedPackage = configured?.config?.packageName?.takeIf { it.isNotBlank() },
                savedMinSdk = configured?.config?.minSdk?.toIntOrNull(),
                savedTargetSdk = configured?.config?.targetSdk?.toIntOrNull(),
                savedCompileSdk = configured?.config?.compileSdk?.toIntOrNull(),
                otherPackages = repository.reservedPackages(projectId),
            ),
        )
        val outcome = buildEngine.build(
            projectRoot,
            projectId,
            setup.checkInput,
            setup.tools,
            cacheEnabled = settings.buildCache,
            buildType = if ((projectRecord?.config?.buildType ?: "Debug").equals("Release", true)) {
                "release"
            } else {
                "debug"
            },
        )
        when (outcome) {
            is com.closecode.build.BuildOutcome.Blocked -> {
                val errors = outcome.findings
                    .filter { it.severity == com.closecode.build.ProjectCheck.Severity.Error }
                val warnings = outcome.findings
                    .filter { it.severity == com.closecode.build.ProjectCheck.Severity.Warning }
                DebugLog.crumb(
                    "BUILD",
                    "blocked: ${errors.size} errors · ${errors.firstOrNull()?.message.orEmpty()}",
                )
                buildBlocked = BlockedInfo(
                    title = "$projectName can't be built yet",
                    lines = errors.map { finding ->
                        if (finding.where.isEmpty()) finding.message
                        else "${finding.message} (${finding.where})"
                    },
                )
                warnings.forEach { warning ->
                    logLines.appendCapped(
                        LogLine(LogLevel.WARN, "Package", warning.message),
                    )
                }
                logLines.appendCapped(
                    LogLine(
                        LogLevel.ERROR,
                        "Check",
                        "$projectName can't be built yet: " +
                            "${errors.size} ${if (errors.size == 1) "item" else "items"} missing",
                    ),
                )
                buildPhase = BuildPhase.IDLE
                buildProgress = 0f
            }
            is com.closecode.build.BuildOutcome.NeedsSetup -> {
                DebugLog.crumb("BUILD", "engine: build tools are not ready")
                outcome.warnings.forEach { warning ->
                    logLines.appendCapped(
                        LogLine(LogLevel.WARN, "Package", warning.message),
                    )
                }
                logLines.appendCapped(
                    LogLine(
                        LogLevel.INFO,
                        "Package",
                        if (!store.isReady()) {
                            "Build tools are not downloaded yet — one Wi-Fi setup, then offline."
                        } else {
                            "The build tools are incomplete. Open Build tools and download them again."
                        },
                    ),
                )
                buildPhase = BuildPhase.IDLE
                buildProgress = 0f
            }
            is com.closecode.build.BuildOutcome.CompileFailed -> {
                DebugLog.crumb(
                    "BUILD",
                    "engine: compile failed · ${outcome.diagnostics.size} diagnostics",
                )
                buildErrorCards = outcome.diagnostics.map { diagnostic ->
                    com.closecode.build.CompileDriver.toProjectDiagnostic(diagnostic) { path ->
                        path.replace('\\', '/').substringAfterLast('/')
                    }
                }
                outcome.diagnostics.take(20).forEach { diagnostic ->
                    logLines.appendCapped(
                        LogLine(
                            LogLevel.ERROR,
                            "Code",
                            "${diagnostic.path}:${diagnostic.line}: ${diagnostic.message}",
                        ),
                    )
                }
                hasBuilt = true
                buildPhase = BuildPhase.DONE
                buildProgress = 1f
                toast = "Build failed · see the errors"
                repository.setStatus(
                    slug,
                    (gitBranch?.takeIf { it.isNotBlank() }?.plus(" · ") ?: "") +
                        "${outcome.diagnostics.size} problems",
                )
                if (runningFromSheet) {
                    runningFromSheet = false
                    runSheetOpen = false
                    runFinished = com.closecode.ui.ide.RunResultInfo(
                        false,
                        "",
                        0.0,
                        0,
                        "${outcome.diagnostics.size} compile errors",
                    )
                    runFinishedOpen = true
                }
            }
            is com.closecode.build.BuildOutcome.Failed -> {
                DebugLog.crumb("BUILD", "failed · ${outcome.message}")
                buildErrorCards = outcome.diagnostics.map { diagnostic ->
                    com.closecode.build.CompileDriver.toProjectDiagnostic(diagnostic) { path ->
                        path.replace('\\', '/').substringAfterLast('/')
                    }
                }
                outcome.diagnostics.take(20).forEach { diagnostic ->
                    logLines.appendCapped(
                        LogLine(
                            LogLevel.ERROR,
                            "Build",
                            "${diagnostic.path}:${diagnostic.line}: ${diagnostic.message}",
                        ),
                    )
                }
                logLines.appendCapped(LogLine(LogLevel.ERROR, "Build", outcome.message))
                hasBuilt = true
                buildPhase = BuildPhase.DONE
                buildProgress = 1f
                toast = "Build failed · see the log"
                repository.setStatus(
                    slug,
                    (gitBranch?.takeIf { it.isNotBlank() }?.plus(" · ") ?: "") + "Build failed",
                )
                if (runningFromSheet) {
                    runningFromSheet = false
                    runSheetOpen = false
                    runFinished = com.closecode.ui.ide.RunResultInfo(
                        false,
                        "",
                        0.0,
                        0,
                        outcome.message.take(140),
                    )
                    runFinishedOpen = true
                }
            }
            is com.closecode.build.BuildOutcome.Built -> {
                builtApkPath = outcome.apk.absolutePath
                installState = com.closecode.build.InstallState.Idle
                buildBlocked = null
                hasBuilt = true
                buildPhase = BuildPhase.DONE
                buildProgress = 1f
                buildElapsedMs = outcome.seconds * 1000
                val mb = outcome.sizeBytes / (1024.0 * 1024.0)
        val projRecord = repository.setStatus(
            slug,
            (gitBranch?.takeIf { it.isNotBlank() }?.plus(" · ") ?: "") + "Build ok",
        )
        if (projRecord != null) projectRecord = projRecord
                logLines.appendCapped(
                    LogLine(
                        LogLevel.INFO,
                        "Build",
                        if (outcome.cached) {
                            "BUILD SUCCESSFUL · ${outcome.apk.name} · %.1f MB · cached".format(mb)
                        } else {
                            "BUILD SUCCESSFUL · ${outcome.apk.name} · %.1f MB · %d s".format(mb, outcome.seconds)
                        },
                    ),
                )
                toast = if (autoOpenAfterBuild) "Installing…" else "Build finished"
                DebugLog.crumb(
                    "BUILD",
                    "success · ${outcome.sizeBytes} bytes · ${outcome.seconds}s",
                )
                if (autoOpenAfterBuild) {
                    autoOpenAfterBuild = false
                    autoOpenAfterInstall = true
                    // A sheet run with the toggle on: the app opens itself and the sheet goes.
                    runSheetOpen = false
                    runningFromSheet = false
                    installNow(outcome.apk)
                } else if (runningFromSheet) {
                    // A sheet run with the toggle off: install, then land on the run page.
                    runningFromSheet = false
                    runFinished = com.closecode.ui.ide.RunResultInfo(
                        true,
                        outcome.apk.name,
                        mb,
                        outcome.seconds,
                    )
                    runSheetOpen = false
                    runInstallWaitingForPage = true
                    installNow(outcome.apk)
                }
            }
            com.closecode.build.BuildOutcome.Cancelled -> {
                logLines.appendCapped(LogLine(LogLevel.WARN, "Build", "BUILD CANCELLED"))
                toast = "Build cancelled"
                buildPhase = BuildPhase.IDLE
                buildProgress = 0f
                autoOpenAfterBuild = false
                autoOpenAfterInstall = false
                // A sheet run that stopped: the sheet stays up with its steps, ready for Run.
                runningFromSheet = false
                runInstallWaitingForPage = false
                DebugLog.crumb("BUILD", "cancelled")
            }
        }
        val notice = when (outcome) {
            is com.closecode.build.BuildOutcome.Built ->
                "Built and signed · %.1f MB".format(
                    outcome.sizeBytes / (1024.0 * 1024.0),
                ) to true
            is com.closecode.build.BuildOutcome.CompileFailed ->
                "${outcome.diagnostics.size} compile errors" to false
            is com.closecode.build.BuildOutcome.Failed -> outcome.message.take(140) to false
            is com.closecode.build.BuildOutcome.Blocked ->
                "The project check stopped the build" to false
            is com.closecode.build.BuildOutcome.NeedsSetup ->
                "The build tools are not installed" to false
            com.closecode.build.BuildOutcome.Cancelled -> "Build cancelled" to false
        }
        com.closecode.build.BuildService.finish(context, projectName, notice.first, notice.second)
        com.closecode.build.BuildCancel.clear()
        building = false
    }
    // The Android back button closes what is open, newest first, in one fixed order, and only
    // leaves the project when there is nothing left to close. One function, called by the system
    // BackHandler below AND by every header back arrow, so the two can never disagree: an arrow
    // closes the keyboard, the popups, the sheets and the full pages exactly like Back does.
    // Full pages return to where they were opened from; Preview and the other views drop back
    // to Code first; a search result returns to its results sheet; the editor asks about
    // unsaved work and only then leaves.
    val keyboardController = LocalSoftwareKeyboardController.current
    val screenFocus = LocalFocusManager.current
    fun handleBackPress() {
        // Back forensics: the state that armed the chain plus the branch that consumed the
        // press, so a dead Back leaves the exact reason in the log instead of a guess.
        fun backState() = "panel=$activePanel ime=$imeOpen popups=" + listOfNotNull(
            "addBlock".takeIf { addBlockOpen },
            "prompt".takeIf { prompt != null },
            "confirmDelete".takeIf { confirmDelete != null },
            "clone".takeIf { cloneOpen },
            "runSheet".takeIf { runSheetOpen },
            "runPage".takeIf { runFinishedOpen },
            "problems".takeIf { problemSheet.value != null },
            "more".takeIf { moreOpen },
            "uiInspector".takeIf { uiInspectorOpen },
            "developer".takeIf { developerOpen },
            "selfTest".takeIf { selfTestOpen },
            "crash".takeIf { crashView != null },
            "projectSettings".takeIf { projectSettingsOpen },
            "search".takeIf { searchOpen },
            "sidebarTool".takeIf { explorerOpen && sidebarTool != null },
            "menu".takeIf { menuOpen },
            "explorer".takeIf { explorerOpen },
            "diff".takeIf { diffPath != null },
            "commitDiff".takeIf { commitDiff != null },
            "commit".takeIf { commitOpen != null },
            "history".takeIf { historyOpen },
            "buildLog".takeIf { buildLogOpen },
            "findReturn".takeIf { findReturn },
        ).joinToString("+").ifEmpty { "none" }
        when {
            // 1. The keyboard goes first: Back while typing just puts the keys away.
            imeOpen -> {
                BackLog.press("main", "${backState()} branch=keyboard")
                keyboardController?.hide()
                screenFocus.clearFocus()
            }
            // 2. The top popup or panel: dialogs, sheets, the drawer, the palette.
            addBlockOpen -> {
                BackLog.press("main", "${backState()} branch=addBlock")
                addBlockOpen = false
            }
            prompt != null -> {
                BackLog.press("main", "${backState()} branch=prompt")
                prompt = null
            }
            confirmDelete != null -> {
                BackLog.press("main", "${backState()} branch=confirmDelete")
                confirmDelete = null
            }
            cloneOpen -> {
                BackLog.press("main", "${backState()} branch=clone")
                cloneOpen = false
            }
            runSheetOpen -> {
                BackLog.press("main", "${backState()} branch=runSheet")
                runSheetOpen = false
            }
            runFinishedOpen -> {
                BackLog.press("main", "${backState()} branch=runPage")
                runFinishedOpen = false
            }
            problemSheet.value != null -> {
                BackLog.press("main", "${backState()} branch=problems")
                problemSheet.value = null
            }
            moreOpen -> {
                BackLog.press("main", "${backState()} branch=more")
                moreOpen = false
            }
            uiInspectorOpen -> {
                BackLog.press("main", "${backState()} branch=uiInspector")
                uiInspectorOpen = false
            }
            developerOpen -> {
                BackLog.press("main", "${backState()} branch=developer")
                developerOpen = false
            }
            selfTestOpen -> {
                BackLog.press("main", "${backState()} branch=selfTest")
                selfTestOpen = false
            }
            crashView != null -> {
                BackLog.press("main", "${backState()} branch=crash")
                crashView = null
            }
            projectSettingsOpen -> {
                BackLog.press("main", "${backState()} branch=projectSettings")
                projectSettingsOpen = false
            }
            searchOpen -> {
                BackLog.press("main", "${backState()} branch=search")
                searchOpen = false
                query = ""
            }

            menuOpen -> {
                BackLog.press("main", "${backState()} branch=menu")
                menuOpen = false
            }
            explorerOpen -> {
                BackLog.press("main", "${backState()} branch=explorer")
                explorerOpen = false
            }
            // 3. Full pages, back to the screen that opened them. The log and the diff are
            // overlays above the panels, so they close before the panel beneath can.
            diffPath != null -> {
                BackLog.press("main", "${backState()} branch=diff")
                diffPath = null
            }
            commitDiff != null -> {
                BackLog.press("main", "${backState()} branch=commitDiff")
                commitDiff = null
            }
            commitOpen != null -> {
                BackLog.press("main", "${backState()} branch=commit")
                commitOpen = null
            }
            historyOpen -> {
                BackLog.press("main", "${backState()} branch=history")
                historyOpen = false
            }
            buildLogOpen -> {
                BackLog.press("main", "${backState()} branch=buildLog")
                buildLogOpen = false
            }
            // 4. A search result returns to its results panel, results still there.
            findReturn -> {
                BackLog.press("main", "${backState()} branch=findReturn")
                findReturn = false
                sidebarTool = "Search"
                explorerOpen = true
            }
            // 5. Full pages and tool panels return to the place they were opened from.
            // The stack carries the whole path, so Terminal -> Logcat -> Back is Terminal,
            // and AI -> Terminal -> Back is AI, rather than both falling through to Code.
            panelHistory.isNotEmpty() -> {
                val target = panelHistory.removeLastOrNull() ?: "Code"
                BackLog.press("main", "${backState()} branch=panel-to-$target")
                backNavigation = true
                activePanel = target
            }
            // 6. A panel with no history still has the editor underneath it.
            activePanel != "Code" -> {
                BackLog.press("main", "${backState()} branch=panel-to-Code")
                backNavigation = true
                activePanel = "Code"
            }
            // 7. Nothing left open: leave the project (asking about unsaved work first).
            else -> {
                BackLog.press("main", "${backState()} branch=exit")
                requestExit()
            }
        }
    }
    // Composed after the exit request below and after the editor's own chain, so both win while
    // armed: the editor's chain is deeper (suggestions, tooltip, selection, quick fix) and the
    // exit dialog's own handler is composed last.
    BackHandler(
        // Always armed: with nothing left open Back leaves through requestExit (which asks
        // about unsaved work first), never through a bare route pop that would skip the ask.
        enabled = true,
        onBack = ::handleBackPress,
    )

    fun runProject() {
        if (projectErrors > 0) {
            toast = blockedByErrors(projectErrorList, "running")
            problemSheet.value = problemSheet.value ?: 1
            problemSheetScope = ProblemScope.PROJECT
            activePanel = "Code"
        } else {
            autoOpenAfterBuild = true
            building = true
        }
    }

    // Screen 25: every Run button (bottom card, path bar) opens the run sheet instead of
    // starting a build behind the reader's back — the type and the open-after toggle live there.
    fun openRunSheet() {
        if (projectErrors > 0) {
            toast = blockedByErrors(projectErrorList, "running")
            problemSheet.value = problemSheet.value ?: 1
            problemSheetScope = ProblemScope.PROJECT
            activePanel = "Code"
        } else {
            runSheetOpen = true
        }
    }

    // v7: the run card is the same on every side panel — Files, Search, Terminal, Logcat and
    // Problems all float it at the bottom — so it is built once here and handed down.
    val runCard: @Composable () -> Unit = {
        IdeRunCard(
            projectName = projectName,
            branch = gitBranch,
            state = runCardUiOf(
                building = building,
                progress = buildProgress,
                errorCount = projectErrors,
                hasBuilt = hasBuilt,
            ),
            onRun = ::openRunSheet,
        )
    }

    fun selectRailTool(tool: String) {
        dismissKeyboard()
        // A tool panel lives over the editor. Leaving a full page (AI, Logcat, Build, a terminal
        // session) would otherwise keep that page behind the card after the sidebar closes.
        if (tool != "OpenCode" && tool != "Settings") activePanel = "Code"
        when (tool) {
            "Files" -> {
                sidebarTool = null
                explorerTab = FilesTab
                explorerOpen = true
            }
            "GitHub" -> {
                sidebarTool = null
                explorerTab = SourceControlTab
                explorerOpen = true
            }
            // v7: these four are panels of the sidebar, not full screens. The full versions of
            // them stay reachable: Terminal and Logcat from inside their panels, Problems from
            // the Run blockers and the editor.
            "Search" -> {
                sidebarTool = "Search"
                explorerOpen = true
            }
            "Terminal" -> {
                sidebarTool = "Terminal"
                explorerOpen = true
            }
            "Logcat" -> {
                sidebarTool = "Logcat"
                explorerOpen = true
            }
            "Problems" -> {
                sidebarTool = "Problems"
                explorerOpen = true
            }
            "OpenCode" -> {
                // AI is a full page, so the sidebar gets out of its way instead of staying on
                // top of it the way the side panels do.
                explorerOpen = false
                if (activePanel == "AI") activePanel = "Code" else openAiPanel()
            }
            "Settings" -> {
                saveActiveFile(notify = false)
                onOpenSettings()
            }
        }
    }

    // v7 correction 9: the highlighted tool must match the panel that is showing. The sidebar's
    // panels move it — Files, GitHub and now Search, Terminal, Logcat and Problems too; the full
    // surfaces of those tools do not, and neither does the AI page.
    val activeRailTool = when {
        explorerOpen && sidebarTool != null -> sidebarTool
        explorerOpen && explorerTab == FilesTab -> "Files"
        explorerOpen && explorerTab == SourceControlTab -> "GitHub"
        activePanel == "AI" -> "OpenCode"
        else -> null
    }

    IdeWorkspace(
        sidebarOpen = explorerOpen,
        onSidebarOpenChange = { open ->
            explorerOpen = open
            if (open) dismissKeyboard()
        },
        keyboardVisible = imeOpen,
        canScrollLeft = { codeCanScrollLeft.value },
        sidebar = {
            Row(Modifier.fillMaxHeight()) {
                IdeRail(
                    projectName = projectName,
                    activeTool = activeRailTool,
                    problemCount = projectErrors,
                    changeCount = gitLetters.size,
                    onTool = ::selectRailTool,
                    onRun = ::openRunSheet,
                    onSettings = { saveActiveFile(notify = false); onOpenSettings() },
                    onProjectClick = { projectSwitcherOpen = true },
                    onProjectSettings = { projectSettingsOpen = true },
                    onDeveloper = { developerOpen = true },
                )
                // v7: the sidebar's panel is the open tool's own panel. The Files and GitHub
                // tabs stay the explorer; the other four tools are side panels, and the full
                // versions of them stay reachable from inside.
                val sideTool = sidebarTool
                if (sideTool == "Search") {
                    IdeSearchSidePanel(
                        projectId = projectId,
                        files = diskTree,
                        treeVersion = treeVersion,
                        query = findQuery,
                        onQueryChange = { findQuery = it },
                        caseSensitive = findCaseSensitive,
                        regex = findRegex,
                        wholeWord = findWholeWord,
                        onCaseSensitiveChange = { findCaseSensitive = it },
                        onRegexChange = { findRegex = it },
                        onWholeWordChange = { findWholeWord = it },
                        read = { path -> repository.readFile(slug, path) },
                        onOpen = { hit ->
                            diskTree.firstOrNull { it.path == hit.path }?.let { openFile(it) }
                            activePanel = "Code"
                            explorerOpen = false
                            // Back from the opened file returns here, results intact.
                            findReturn = true
                        },
                        runCard = runCard,
                    )
                } else if (sideTool == "Terminal") {
                    IdeTerminalSidePanel(
                        sessions = terminalSessions,
                        onOpen = { number ->
                            terminalSessions.select(number)
                            activePanel = "Terminal"
                            explorerOpen = false
                        },
                        onNew = {
                            terminalSessions.openNew()
                            activePanel = "Terminal"
                            explorerOpen = false
                        },
                        runCard = runCard,
                    )
                } else if (sideTool == "Logcat") {
                    IdeLogcatSidePanel(
                        lines = logLines,
                        filter = logcatFilter,
                        onFilterChange = { logcatFilter = it },
                        onClear = { logLines.clear() },
                        runCard = runCard,
                    )
                } else if (sideTool == "Problems") {
                    IdeProblemsSidePanel(
                        errors = projectErrorList,
                        warnings = projectWarningList,
                        onOpen = { row ->
                            diskTree.firstOrNull { it.name == row.fileName }?.let { file ->
                                openFile(file)
                                val text = liveBuffers[file.path]
                                    ?: diskContents[file.path].orEmpty()
                                caretRequest = offsetOfLine(text, row.diagnostic.line)
                                sourceRevision++
                            }
                            activePanel = "Code"
                            explorerOpen = false
                        },
                        runCard = runCard,
                    )
                } else {
                    IdeExplorer(
                        runCard = runCard,
                        projectName = projectName,
                    onRun = ::openRunSheet,
                    slug = slug,
                    tree = diskTree,
                    activePath = activeFile?.path,
                    revealPath = selectedPath,
                    collapsed = collapsedFolders,
                    query = explorerFilter,
                    onQueryChange = { explorerFilter = it },
                    openTabs = tabs,
                    onOpenTab = { file -> openFile(file); explorerOpen = false },
                    tab = explorerTab,
                    gitRevision = gitRevision,
                    onTab = { explorerTab = it },
                    onCreateFile = { prompt = TreePrompt.New(isFolder = false) },
                    onCreateFolder = { prompt = TreePrompt.New(isFolder = true) },
                    onRefresh = { refreshTree() },
                    onCollapseAll = {
                        collapsedFolders = diskTree
                            .filter { it.kind == FileKind.Folder }
                            .map { it.path }.toSet()
                    },
                    showGenerated = showGenerated,
                    onToggleGenerated = {
                        val next = !showGenerated
                        showGenerated = next
                        kvStore.putBoolean("show_generated_$projectId", next)
                    },
                    gitLetters = gitLetters,
                    dirtyPaths = dirtyPaths,
                    hintVisible = drawerOpens <= 3,
                    // One letter per changed path, so the map's size is the tab's
                    // count bubble: no second source to drift apart from.
                    changeCount = gitLetters.size,
                                            onDiff = { diffPath = it },
                                            onCommitDiff = { id, path -> commitDiff = id to path },
                                            onHistory = { historyOpen = true },
                                            onCommitOpen = { commitOpen = it },
                    onAction = { action, file -> onTreeAction(action, file) },
                    onSelect = { file ->
                        // The row is the working point for the next creation whether it
                        // is a folder or a file, so it is recorded either way; only a
                        // file also becomes the file being read.
                        selectedPath = file.path
                        if (file.kind == FileKind.Folder) {
                            collapsedFolders = toggleCollapsed(collapsedFolders, file.path)
                        } else {
                            openFile(file)
                            explorerOpen = false
                        }
                    },
                    onCloneRequest = { cloneOpen = true },
                    githubTokenEpoch = gitTokenEpoch,
                    projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                    onClose = { explorerOpen = false },
                )
                }
            }
        },
        overlays = {
            if (projectSwitcherOpen) {
                com.closecode.ui.ProjectsSheet(
                    projects = projectRecords,
                    openProjectId = projectId,
                    branchOf = { record -> repository.headBranch(record.slug) },
                    onOpen = { record ->
                        projectSwitcherOpen = false
                        onOpenProject(record)
                    },
                    onNewProject = {
                        projectSwitcherOpen = false
                        onNewProject()
                    },
                    onTemplates = {
                        projectSwitcherOpen = false
                        onNewProject()
                    },
                    onClose = { projectSwitcherOpen = false },
                )
            }
        },
    ) {
        // The sheets pin themselves to the bottom bar's top edge, so its measured height lives
        // at this scope: 0 while the keyboard hides the bar, its real height otherwise.
        var navBarHeightPx by remember { mutableStateOf(0) }
        Column(
            Modifier
                .fillMaxSize()
                // Pinching anywhere on the screen scales the code text. Attaching it here rather
                // than to the code column means a pinch over the top bar, the path bar, the tool row
                // or the bottom bar does the same thing — and a pinch over a sheet, a menu or the
                // toast does nothing at all, because those are siblings of this Column rather than
                // children of it.
                .pinchToZoom { scale -> editorScale = (editorScale * scale).coerceIn(EditorScaleRange) }
        ) {

            when (activePanel) {
                "AI" -> Column(Modifier.weight(1f).fillMaxWidth()) {
                    IdeFullPageBar(title = "AI", onBack = ::handleBackPress)
                    OpenCodeTab(
                        projectName = projectName,
                        projectId = projectId,
                        projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                        onChat = { openCodeChat = true },
                        onAuthenticate = ::authenticateOpenCode,
                        modifier = Modifier.weight(1f),
                    )
                }
                "Logcat" -> IdeLogcatPanel(
                    lines = logLines,
                    onBack = ::handleBackPress,
                    modifier = Modifier.weight(1f),
                    appPackage = lastOpenedPackage
                        ?: (installState as? com.closecode.build.InstallState.Installed)
                            ?.packageName,
                )
                "Build" -> IdeBuildPanel(
                    appName = projectName,
                    packageName = projectPackageName
                        ?: diskTree.firstOrNull { it.path.endsWith("AndroidManifest.xml") }
                            ?.let { manifestPackageOf(diskContents[it.path]) },
                    version = diskTree.filter { it.path.endsWith("build.gradle.kts") }
                        .firstNotNullOfOrNull { gradleVersionOf(diskContents[it.path]) },
                    building = building,
                    errorCount = buildErrorCards.size,
                    warningCount = 0,
                    elapsedMs = buildElapsedMs,
                    phase = BuildPhase.IDLE,
                    fileCount = 0,
                    progress = buildPanel.progress,
                    symbolsMs = 0,
                    filesMs = 0,
                    hasBuilt = hasBuilt,
                    errors = buildErrorCards,
                    blocked = buildBlocked,
                    buildSteps = buildPanel.rows,
                    buildFailed = buildPanel.failed,
                    buildResult = buildPanel.result,
                    buildResultOk = buildPanel.resultOk,
                    primaryLabel = buildPanel.primaryLabel,
                    onCopyBlockedList = {
                        val info = buildBlocked
                        if (info == null) {
                            toast = "Nothing to copy"
                        } else {
                            treeClipboard.setText(
                                androidx.compose.ui.text.AnnotatedString(
                                    buildString {
                                        appendLine(info.title)
                                        info.lines.forEach { appendLine("· $it") }
                                    }.trimEnd(),
                                ),
                            )
                            toast = "List copied"
                        }
                    },
                    onOpenProjectSettings = { projectSettingsOpen = true },
                    onRun = {
                        // Spec 7: build and run are blocked while any error exists.
                        if (projectErrors > 0) {
                            toast = "Fix ${projectErrors} errors before building"
                            activePanel = "Build"
                        } else {
                            // The Build panel's primary button builds; Run installs and opens.
                            autoOpenAfterBuild = false
                            building = true
                        }
                    },
                    onCancel = {
                        buildEngine.cancel()
                        toast = "Cancelling…"
                    },
                    onViewErrors = {
                        problemSheetScope = ProblemScope.PROJECT
                        problemSheet.value = problemSheet.value ?: 1
                    },
                    onOpenLog = { buildLogOpen = true },
                    onOpenFile = { name, line ->
                        diskTree.firstOrNull { it.name == name }?.let { file ->
                            openFile(file)
                            val text = liveBuffers[file.path]
                                ?: diskContents[file.path].orEmpty()
                            caretRequest = offsetOfLine(text, line)
                            sourceRevision++
                            activePanel = "Code"
                        }
                    },
                    onAskAi = { row -> askAiFromDiagnostic(row.fileName, row.diagnostic.line, row.diagnostic.message) },
                    onBack = ::handleBackPress,
                    modifier = Modifier.weight(1f),
                    apkName = builtApk?.let { file ->
                        val kb = (file.length() + 512) / 1024
                        "${file.name} · $kb KB"
                    },
                    installLabel = when (val state = installState) {
                        com.closecode.build.InstallState.Idle -> "Install"
                        is com.closecode.build.InstallState.Installing -> "Installing…"
                        is com.closecode.build.InstallState.Installed -> "Open app"
                        com.closecode.build.InstallState.NeedsPermission -> "Allow install"
                        is com.closecode.build.InstallState.Failed -> "Try install again"
                    },
                    appInstalled = runCatching {
                        val manifest = diskTree.firstOrNull { it.path.endsWith("AndroidManifest.xml") }
                            ?.let { diskContents[it.path] }.orEmpty()
                        val pkg = com.closecode.build.TemplateContract.manifestPackage(manifest)
                            ?: com.closecode.build.TemplateContract.packageForSlug(slug)
                        com.closecode.build.InstallHelper.isInstalled(context, pkg)
                    }.getOrDefault(false),
                    installNote = (installState as? com.closecode.build.InstallState.Failed)?.message,
                    onInstall = {
                        val apk = builtApk ?: return@IdeBuildPanel
                        // A manual install does not auto-open; Open app stays a separate tap.
                        autoOpenAfterInstall = false
                        installNow(apk)
                    },
                    onOpenApp = {
                        val pkg = (installState as? com.closecode.build.InstallState.Installed)
                            ?.packageName
                            ?.takeIf { it.isNotBlank() }
                            ?: packageOfProject()
                        DebugLog.crumb("INSTALL", "open requested · pkg=$pkg")
                        openBuiltApp(pkg)
                    },
                    onUninstall = ((installState as? com.closecode.build.InstallState.Failed)
                        ?.takeIf { it.kind == com.closecode.build.InstallState.FailureKind.Conflict })
                        ?.let {
                            {
                                val pkg = packageOfProject()
                                DebugLog.crumb("INSTALL", "uninstall requested · pkg=$pkg")
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_UNINSTALL_PACKAGE,
                                            android.net.Uri.parse("package:$pkg"),
                                        ),
                                    )
                                }
                            }
                        },
                    onShareApk = {
                        val apk = builtApk ?: return@IdeBuildPanel
                        // Spec 8: the shared file is <appname>-debug-<versionName>.apk,
                        // copied under the provider's cache path instead of exposing the
                        // build folder or handing out a file:// path.
                        val versionName = projectRoot
                            ?.let { com.closecode.build.GradleReader.read(it).versionName }
                            ?: "1.0"
                        val copy = java.io.File(
                            context.cacheDir,
                            "share/" + sharedApkName(projectName, versionName),
                        )
                        val uri = runCatching {
                            copy.parentFile?.mkdirs()
                            apk.copyTo(copy, overwrite = true)
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                copy,
                            )
                        }.getOrNull()
                        if (uri == null) {
                            toast = "Could not prepare the APK to share"
                        } else {
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            runCatching {
                                context.startActivity(
                                    android.content.Intent.createChooser(share, "Share APK"),
                                )
                            }
                        }
                    },
                )
                "Terminal" -> IdeTerminal(
                    projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                    // The UUID is the history key: a rename must not split a project's commands.
                    projectSlug = projectId,
                    projectName = projectName,
                    // The holder came from above the screen switch, so this panel owns the draw but
                    // not the shells: leaving the terminal and coming back finds the same processes
                    // with the same output, and so does opening Settings.
                    sessions = terminalSessions,
                    usePipe = usePipeTerminal,
                    // The project tree, so Tab completes paths from what the project actually
                    // holds rather than by listing a directory the shell may not even be in.
                    tree = diskTree,
                    // `code <file>` ends somewhere the terminal cannot reach on its own:
                    // the editor.
                    onOpenPath = { file ->
                        openFile(file)
                        activePanel = "Code"
                    },
                    // A file made or removed in the terminal is a file the drawer should list,
                    // rather than one that quietly stays wrong until something else refreshes it.
                    onFilesChanged = { refreshTree() },
                    onBack = ::handleBackPress,
                    modifier = Modifier.weight(1f),
                )
                else -> Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    // Code, the block editor and Preview share one path bar, so the way back to the
                    // code is always one `<>` tap away.
                    IdePathBar(
                        filePath = activeFile?.path ?: active,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = { undoRequest++ },
                        onRedo = { redoRequest++ },
                        onRun = ::openRunSheet,
                    )
                    when (activePanel) {
                        "Preview" -> IdePreviewPanel(
                            fileName = activeName,
                            fileKind = activeFile?.kind,
                            projectPath = activeFile?.path.orEmpty(),
                            loader = { path ->
                                runCatching { repository.readFile(slug, path) }.getOrNull()
                            },
                            binaryLoader = { path ->
                                runCatching { repository.readFileBytes(slug, path) }.getOrNull()
                            },
                            diskTree = diskTree,
                            projectRepository = repository,
                            refreshToken = previewRefresh,
                            onRefresh = { previewRefresh++ },
                            projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                            previewSetup = previewSetup,
                            // A console source link closes the preview and lands the caret on
                            // its line, through the same reseed every other jump uses.
                            onOpenFile = { path, line ->
                                diskTree.firstOrNull { it.path == path }?.let { file ->
                                    openFile(file)
                                    val text = liveBuffers[file.path]
                                        ?: diskContents[file.path].orEmpty()
                                    caretRequest = offsetOfLine(text, line)
                                    sourceRevision++
                                    activePanel = "Code"
                                }
                            },
                            // Preview cards fail on project errors, matched by file name the
                            // same way the sweep reports them.
                            previewErrors = projectErrorList,
                            modifier = Modifier.weight(1f),
                        )

                        "Blocks" -> IdeBlockPanel(
                            fileName = activeName,
                            language = language,
                            source = liveBuffers[active] ?: diskContents[active].orEmpty(),
                            onChange = { next ->
                                // The editor owns the text field, so the rewrite is handed over as
                                // a revision it re-seeds from rather than as a mutation it has to
                                // notice mid-edit. A reorder does not care where the caret was, so
                                // the caret falls back to the end of the code.
                                liveBuffers[active] = next
                                noteContent(active)
                                caretRequest = -1
                                sourceRevision++
                            },
                            onAddBlock = { addBlockOpen = true },
                            modifier = Modifier.weight(1f),
                        )

                        // Spec: the drawer and the editor share one row, with the tap-outside scrim
                        // sitting over whatever the editor does not use.
                        //
                        // `fillMaxWidth` is load bearing, not decoration. Both children below ask to
                        // `matchParentSize`, and a Compose child that does that is deliberately
                        // *excluded* from helping decide how big its parent is — it matches the
                        // parent rather than measuring it. `weight` only fills the main axis, which
                        // in a Column is the height. So without this one word the Box had nothing
                        // telling it a width, collapsed to zero, and took the tab strip, the line
                        // numbers, the code and the drawer with it: the editor area went black while
                        // the bars above and below it carried on drawing, because those sit outside
                        // this Box. Deleting this line puts that back exactly as it was.
                        else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                            Row(Modifier.matchParentSize()) {
                                IdeEditorArea(
                                    modifier = Modifier.weight(1f),
                                    tabs = tabs,
                                    active = active,
                                    language = language,
                                    onAskDiagnostic = { name, line, message ->
                                        askAiFromDiagnostic(name, line, message)
                                    },
                                    contentFor = { path -> liveBuffers[path] ?: diskContents[path] ?: "" },
                                    onSelectTab = { path ->
                                        dismissKeyboard()
                                        active = path
                                        selectedPath = path
                                    },
                                    onCloseTab = { path ->
                                        tabs = tabs.filterNot { it.path == path }
                                        if (active == path) {
                                            active = tabs.lastOrNull()?.path.orEmpty()
                                            selectedPath = tabs.lastOrNull()?.path
                                        }
                                    },
                                    onCloseOthers = { path ->
                                        tabs = tabs.filter { it.path == path }
                                        if (active != path) {
                                            active = path
                                            selectedPath = tabs.firstOrNull()?.path
                                        }
                                    },
                                    onCloseAll = {
                                        tabs = emptyList()
                                        active = ""
                                        selectedPath = null
                                    },
                                    onBufferChange = { text ->
                                        // The editor reports its opening seed too. An empty
                                        // report is only the reader's edit when this buffer
                                        // already held text and they deleted all of it. At any
                                        // other time it is a seed, and keeping it would let auto
                                        // save overwrite the file once the read lands.
                                        val edited = liveBuffers[active]?.isNotEmpty() == true
                                        if (text.isNotEmpty() || edited) {
                                            liveBuffers[active] = text
                                            noteContent(active)
                                        }
                                    },
                                    onToast = { text -> toast = text },
                                    dirtyOf = ::isDirtyTab,
                                    problemSheetLine = problemSheet,
                                    activeKind = activeFile?.kind,
                                    formatRequest = formatRequest,
                                    formatSelectionRequest = formatSelectionRequest,
                                    problemSheetScope = problemSheetScope,
                                    projectDiagnostics = projectErrorList,
                                    onOpenProjectFile = { name ->
                                        diskTree.firstOrNull { it.name == name }?.let { openFile(it) }
                                    },
                                    onSelectionFormat = { formatSelectionRequest++ },
                                    undoRequest = undoRequest,
                                    redoRequest = redoRequest,
                                    sourceRevision = sourceRevision,
                                    caretRequest = caretRequest,
                                    onCaretChange = { editorCaret = it },
                                    projectSymbols = projectNames,
                                    onSaveRequest = { saveActiveFile() },
                                    relativePath = activeFile?.path,
                                    projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                                    initialCaret = restoredCaret,
                                    initialScroll = restoredScroll,
                                    onPlaceChange = { caret, scroll ->
                                        kvStore.putString(
                                            "restore_caret_$projectId",
                                            caret.entries.joinToString("") { "${it.key}${it.value}" },
                                        )
                                        kvStore.putString(
                                            "restore_scroll_$projectId",
                                            scroll.entries.joinToString("") {
                                                "${it.key}${it.value.first},${it.value.second}"
                                            },
                                        )
                                    },
                                    onHistoryState = { undo, redo ->
                                        canUndo = undo
                                        canRedo = redo
                                    },
                                    codeScrollLeft = codeCanScrollLeft,
                                    onSuggestionsOpen = { suggestionsOpen = it },
                                    showToolRow = showToolRow(imeOpen, suggestionsOpen),
                                    imeOpen = imeOpen,
                                    editorScale = editorScale,
                                )
                            }

                        }
                    }
                }
            }

        }


        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(motion(160)),
            exit = fadeOut(motion(160)),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            CommandPalette(
                commands = buildList {
                    add(
                        PaletteCommand(
                            label = "Save file",
                            shortcut = "Ctrl S",
                            run = {
                                saveActiveFile()
                                searchOpen = false
                            },
                        ),
                    )
                    add(
                        PaletteCommand("Run app", "Ctrl R", {
                            // Spec 7: running is blocked while the project has errors.
                            if (projectErrors > 0) {
                                toast = blockedByErrors(projectErrorList, "running")
                                problemSheet.value = problemSheet.value ?: 1
                                problemSheetScope = ProblemScope.PROJECT
                            } else {
                                autoOpenAfterBuild = true
                                building = true
                            }
                            searchOpen = false
                        }),
                    )
                    add(
                        PaletteCommand("Build debug APK", null, {
                            if (projectErrors > 0) {
                                toast = blockedByErrors(projectErrorList, "building")
                                problemSheet.value = problemSheet.value ?: 1
                                problemSheetScope = ProblemScope.PROJECT
                            } else {
                                autoOpenAfterBuild = false
                                building = true
                            }
                            searchOpen = false
                        }),
                    )
                    add(PaletteCommand("Format file", null, {
                        formatRequest++
                        searchOpen = false
                    }))
                    add(PaletteCommand("Show problems", null, {
                        problemSheet.value = problemSheet.value ?: 1
                        searchOpen = false
                    }))
                    add(PaletteCommand("Show logcat", null, {
                        activePanel = "Logcat"
                        searchOpen = false
                    }))
                    add(PaletteCommand("Toggle theme", null, {
                        onToggleTheme()
                        searchOpen = false
                    }))
                    // Diagnostics out without Settings, adb or logcat: the back/scroll/keyboard
                    // lines go on the clipboard for pasting into a bug report. Temporary, like
                    // the red line — both leave once the row and Back are proven.
                    add(PaletteCommand("Copy diagnostics log", null, {
                        val report = buildString {
                            appendLine("=== back ===")
                            BackLog.snapshot().forEach { appendLine(it) }
                            appendLine("=== scroll/ime ===")
                            ScrollLog.snapshot().forEach {
                                appendLine(
                                    "scroll ${it.axis} ${it.from}->${it.to} ${it.reason}",
                                )
                            }
                        }
                        treeClipboard.setText(
                            androidx.compose.ui.text.AnnotatedString(report),
                        )
                        toast = "Diagnostics copied"
                        searchOpen = false
                    }))
                    // Spec 5: one "Open <file>" command per file in the project.
                    for (file in diskTree) {
                        if (file.kind == FileKind.Folder) continue
                        add(
                            PaletteCommand(
                                label = "Open ${file.name}",
                                shortcut = null,
                                run = {
                                    // The same path a drawer tap takes, so the palette's read is
                                    // off the main thread too instead of blocking the tap that
                                    // opened the file.
                                    openFile(file)
                                    activePanel = "Code"
                                    searchOpen = false
                                },
                            ),
                        )
                    }
                },
                query = query,
                onQueryChange = { query = it },
                onDismiss = {
                    searchOpen = false
                    query = ""
                },
            )
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(motion(200)),
            exit = fadeOut(motion(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp),
        ) {
            IdeToast(toast.orEmpty())
        }

        if (menuOpen) {
            OverflowMenu(
                // The two rows that act on the file that is open, and nothing else: settings and
                // the theme switch live in the bottom More sheet, so there is one home for each
                // row rather than two menus that overlap.
                actions = listOf(
                    OverflowAction("Search and commands", "⌕") {
                        searchOpen = true
                        menuOpen = false
                    },
                    OverflowAction("Format file", "⌗") {
                        formatRequest++
                        menuOpen = false
                    },
                ),
                // Every row above carries its own click, so this only ever runs for a row added
                // without one. Closing the menu is the right thing to do either way.
                onPick = { menuOpen = false },
                onDismiss = { menuOpen = false },
            )
        }

        // Find in project lives in the sidebar's own Search panel now (IdeSearchSidePanel); the
        // rail toggle and Back-from-a-result route readers there. There is no more sheet.

        if (addBlockOpen) {
            IdeAddBlockSheet(
                onInsert = { template ->
                    val source = liveBuffers[active] ?: diskContents[active].orEmpty()
                    val (next, at) = insertBlockAt(source, editorCaret, template)
                    liveBuffers[active] = next
                    noteContent(active)
                    caretRequest = at
                    sourceRevision++
                    addBlockOpen = false
                    // Straight back to Code, so the caret the sheet just placed is on screen.
                    activePanel = "Code"
                },
                onDismiss = { addBlockOpen = false },
            )
        }

        IdeMoreOverlay(
            open = moreOpen,
            onDismiss = { moreOpen = false },
            onLogcat = { activePanel = "Logcat" },
            onBuild = { activePanel = "Build" },
            onUiInspector = { uiInspectorOpen = true },
            onProjectSettings = { projectSettingsOpen = true },
            onSettings = onOpenSettings,
            onToggleTheme = onToggleTheme,
            onDeveloper = { developerOpen = true },
            versionLabel = BuildInfo.label,
            barHeight = with(LocalDensity.current) { navBarHeightPx.toDp() },
        )

        IdeUiInspectorOverlay(
            open = uiInspectorOpen,
            onDismiss = { uiInspectorOpen = false },
            barHeight = with(LocalDensity.current) { navBarHeightPx.toDp() },
        )

        if (runSheetOpen) {
            IdeRunSheet(
                appName = projectName,
                buildType = projectRecord?.config?.buildType ?: "Debug",
                onBuildType = { next ->
                    val record = projectRecord
                    if (record != null) {
                        projectRecord = repository.updateConfig(
                            slug,
                            record.config.copy(buildType = next),
                        )
                    }
                },
                openAfterInstall = runOpenAfterInstall,
                onOpenAfterInstall = { runOpenAfterInstall = it },
                building = building,
                progress = buildProgress,
                steps = buildPanel.rows,
                canRun = projectErrors == 0,
                onStop = {
                    buildEngine.cancel()
                    toast = "Cancelling…"
                },
                onRun = {
                    runningFromSheet = true
                    // The run follows the sheet's own decisions: the type was chosen there, and
                    // the install opens the app only if the toggle says so.
                    autoOpenAfterBuild = runOpenAfterInstall
                    building = true
                },
                onDismiss = { runSheetOpen = false },
            )
        }
        if (runFinishedOpen) {
            val result = runFinished
            if (result != null) {
                IdeRunFinishedPage(
                    result = result,
                    onBack = { runFinishedOpen = false },
                    onOpenApp = { runFinishedOpen = false; openBuiltApp(packageOfProject()) },
                    onOpenLog = { runFinishedOpen = false; buildLogOpen = true },
                )
            }
        }
        if (projectSettingsOpen) {
            val record = projectRecord
            IdeProjectSettingsSheet(
                imported = record?.imported == true,
                packageName = if (record?.imported == true) {
                    projectPackageName.orEmpty()
                } else {
                    record?.config?.packageName ?: projectPackageName.orEmpty()
                },
                minSdk = record?.config?.minSdk ?: "",
                targetSdk = record?.config?.targetSdk ?: "",
                compileSdk = record?.config?.compileSdk ?: "",
                report = projectReport,
                busy = projectCheckBusy,
                onSave = { newPackage, minSdk, targetSdk, compileSdk ->
                    saveProjectSettings(newPackage, minSdk, targetSdk, compileSdk)
                },
                onDismiss = { projectSettingsOpen = false },
                barHeight = with(LocalDensity.current) { navBarHeightPx.toDp() },
            )
        }

        if (developerOpen) {
            IdeDeveloperSheet(
                debugLogOn = debugLogs,
                usePipe = usePipeTerminal,
                crashAvailable = CrashReport.readLast(context.filesDir) != null,
                debugBuild = BuildConfig.DEBUG,
                onToggleDebugLog = { debugLogs = it },
                onCopyLog = {
                    val text = DebugLog.snapshot().joinToString("\n")
                    if (text.isEmpty()) {
                        toast = "The debug log is empty"
                    } else {
                        treeClipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        toast = "Log copied"
                    }
                },
                onShareLog = {
                    val text = DebugLog.snapshot().joinToString("\n")
                    if (text.isEmpty()) {
                        toast = "The debug log is empty — turn it on first"
                    } else if (!sharePlainText(context, "CloseCode debug log", text)) {
                        toast = "No app accepted the share"
                    }
                },
                onClearLog = {
                    DebugLog.clear()
                    toast = "Debug log cleared"
                },
                onSelfTest = {
                    selfTestFacts = SelfTest.facts(context)
                    selfTestChecks = emptyList()
                    selfTestOpen = true
                },
                onViewCrash = {
                    val report = CrashReport.readLast(context.filesDir)
                    if (report == null) toast = "No crash is recorded" else crashView = report
                },
                onShareReport = {
                    if (!sharePlainText(context, "CloseCode debug report", DebugReport.compose(context))) {
                        toast = "No app accepted the share"
                    }
                },
                onClearBuildCache = { confirmClearCache = true },
                onDeleteTools = { confirmDeleteTools = true },
                onToggleUsePipe = {
                    usePipeTerminal = it
                    kvStore.putInt("terminal_use_pipe", if (it) 1 else 0)
                },
                onCrashTest = {
                    // A breadcrumb first, so the report shows the test was on purpose.
                    DebugLog.crumb("CRASH", "crash test started from More > Developer")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        throw RuntimeException("Crash test from More > Developer")
                    }
                },
                onDismiss = { developerOpen = false },
                barHeight = with(LocalDensity.current) { navBarHeightPx.toDp() },
            )
        }

        if (selfTestOpen) {
            IdeSelfTestSheet(
                facts = selfTestFacts,
                checks = selfTestChecks,
                running = selfTestRunning,
                onRun = {
                    if (!selfTestRunning) {
                        selfTestFacts = SelfTest.facts(context)
                        selfTestChecks = emptyList()
                        selfTestRunning = true
                        fileScope.launch {
                            SelfTest.run(context) { check ->
                                selfTestChecks = selfTestChecks + check
                            }
                            selfTestRunning = false
                        }
                    }
                },
                onCopyReport = {
                    val report = SelfTest.lastReport
                    if (report == null) {
                        toast = "Run the self-test first"
                    } else {
                        treeClipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                        toast = "Self-test report copied"
                    }
                },
                onDismiss = { selfTestOpen = false },
                barHeight = with(LocalDensity.current) { navBarHeightPx.toDp() },
            )
        }

        crashView?.let { report ->
            IdeCrashSheet(
                text = report,
                onCopy = {
                    treeClipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                    crashView = null
                    toast = "Crash report copied"
                },
                onShare = { sharePlainText(context, "CloseCode crash report", report) },
                onDismiss = { crashView = null },
                barHeight = with(LocalDensity.current) { navBarHeightPx.toDp() },
            )
        }

        if (confirmClearCache) {
            IdeConfirmDialog(
                title = "Clear build cache",
                message = "Delete this project's build output? It is always safe — the next check creates it again.",
                confirmLabel = "Clear",
                onConfirm = {
                    confirmClearCache = false
                    val root = runCatching { repository.projectDir(slug) }.getOrNull()
                    val cache = root?.let { java.io.File(it, "build/closecode") }?.takeIf { it.exists() }
                    val bytes = cache?.let { com.closecode.ui.settings.dirSize(it) } ?: 0L
                    val removed = cache?.deleteRecursively() ?: false
                    toast = when {
                        root == null -> "No project is open"
                        cache == null || bytes == 0L -> "Build cache is already empty"
                        removed -> "Build cache cleared · " + com.closecode.ui.settings.humanSize(bytes)
                        else -> "Could not clear the build cache"
                    }
                },
                onDismiss = { confirmClearCache = false },
            )
        }

        if (confirmDeleteTools) {
            IdeConfirmDialog(
                title = "Delete build tools",
                message = "The compiler pack will be deleted. It can be downloaded again from Settings > Build & Toolchain.",
                confirmLabel = "Delete",
                onConfirm = {
                    confirmDeleteTools = false
                    toast = if (com.closecode.build.ToolchainHolder.deleteEverything(context)) {
                        "Build tools deleted"
                    } else {
                        "Could not delete the build tools"
                    }
                },
                onDismiss = { confirmDeleteTools = false },
            )
        }

        when (val current = prompt) {
            null -> Unit
            is TreePrompt.New -> IdeNameDialog(
                title = if (current.isFolder) "New folder" else "New file",
                hint = if (current.isFolder) "feature" else "MainActivity.kt",
                horizontalBias = PROMPT_BIAS,
                onCreate = {
                    prompt = null
                    createEntry(isFolder = current.isFolder, rawName = it)
                },
                onDismiss = { prompt = null },
            )

            is TreePrompt.Rename -> IdeNameDialog(
                title = "Rename",
                hint = current.target.name,
                initial = current.target.name,
                confirmLabel = "Rename",
                horizontalBias = PROMPT_BIAS,
                onCreate = {
                    prompt = null
                    renameEntry(current.target, it)
                },
                onDismiss = { prompt = null },
            )

            is TreePrompt.Transfer -> IdeNameDialog(
                title = if (current.copy) "Copy to" else "Move to",
                hint = "app/src/main/java/Main.kt",
                initial = current.target.path,
                confirmLabel = if (current.copy) "Copy" else "Move",
                horizontalBias = PROMPT_BIAS,
                onCreate = {
                    prompt = null
                    transferEntry(current.target, it, current.copy)
                },
                onDismiss = { prompt = null },
            )
        }

        confirmDelete?.let { target ->
            IdeConfirmDialog(
                title = "Delete ${target.name}?",
                message = if (target.kind == FileKind.Folder) {
                    "Everything inside ${target.path} goes with it. This cannot be undone."
                } else {
                    "${target.path} is removed. This cannot be undone."
                },
                confirmLabel = "Delete",
                onConfirm = {
                    confirmDelete = null
                    deleteEntry(target)
                },
                onDismiss = { confirmDelete = null },
            )
        }

        if (showInstallSheet) {
            IdeConfirmDialog(
                title = "To install apps you build",
                message = "Android needs your permission once. CloseCode hands the finished " +
                    "APK to Android's installer; Android shows its own screen for every " +
                    "install, and CloseCode can never install anything silently.",
                confirmLabel = "Open settings",
                onConfirm = {
                    showInstallSheet = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
                onDismiss = { showInstallSheet = false },
            )
        }

        if (showOverlaySheet) {
            IdeConfirmDialog(
                title = "Back to editor button",
                message = "Allow CloseCode to show a small Back to editor button over your " +
                    "app, so you can return with one tap. If you say no, a notification " +
                    "with the same action is used instead.",
                confirmLabel = "Allow",
                onConfirm = {
                    showOverlaySheet = false
                    overlayAsked = true
                    kvStore.putBoolean("overlay_asked", true)
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
                onDismiss = {
                    // A refusal is still an Open: the app starts, and the fallback
                    // notification carries the way back.
                    showOverlaySheet = false
                    overlayAsked = true
                    kvStore.putBoolean("overlay_asked", true)
                    pendingOverlayOpen?.let { packageName ->
                        pendingOverlayOpen = null
                        launchBuiltApp(packageName)
                        com.closecode.overlay.BackToEditorService.showFallback(context)
                    }
                },
            )
        }

        // Hosted here rather than inside the drawer: it covers the screen, and the drawer
        // collapses to 0.dp on the Files tab — where it would be invisible but still modal.
        if (cloneOpen) {
            val tokenStore = remember { TokenStore(context) }
            GitHubCloneScreen(
                token = remember(gitTokenEpoch) { tokenStore.read() },
                onToken = { fresh ->
                    tokenStore.write(fresh)
                    githubTokenEpoch++
                },
                onDismiss = { cloneOpen = false },
                onCloned = { record ->
                    cloneOpen = false
                    onProjectCloned(record)
                },
            )
        }

        // OpenCode's own web page. It is a browser, so it covers the screen; the back button
        // inside the page goes back inside the page first and only leaves it at the start.
        // The page URL names the project, not OpenCode's global home directory.
        if (openCodeChat) {
            val openCode = com.closecode.ai.OpenCode.state.value
            OpenCodeChatScreen(
                baseUrl = openCode.url,
                projectUrl = openCode.pageUrl,
                projectDirectory = openCode.projectDirectory,
                sessionId = openCode.sessionId,
                authToken = openCode.authToken,
                basicAuth = openCode.basicAuth,
                generation = openCode.generation,
                onBack = { openCodeChat = false },
                onAuthenticate = {
                    openCodeChat = false
                    authenticateOpenCode()
                },
            )
        }

        diffPath?.let { path ->
            DiffScreen(
                projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                path = path,
                onBack = ::handleBackPress,
                onOpenFile = {
                    diskTree.firstOrNull { it.path == path }?.let { file ->
                        openFile(file)
                        diffPath = null
                    }
                },
            )
        }

        commitDiff?.let { (id, path) ->
            DiffScreen(
                projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                path = path,
                onBack = ::handleBackPress,
                commitId = id,
            )
        }

        if (historyOpen) {
            HistoryPage(
                projectName = projectName,
                branch = runCatching {
                    com.closecode.ui.git.GitService(repository.projectDir(slug))
                        .snapshot().getOrNull()?.branch
                }.getOrNull() ?: "main",
                projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                headId = null,
                onBack = ::handleBackPress,
                onCommit = { commitOpen = it },
            )
        }

        commitOpen?.let { id ->
            CommitPage(
                commitId = id,
                projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                revision = gitRevision,
                onBack = ::handleBackPress,
                onDiff = { commitId, path -> commitDiff = commitId to path },
                onChanged = { gitRevision++ },
            )
        }

        if (buildLogOpen) {
            BuildLogScreen(
                errors = projectErrorList,
                warnings = projectWarningList,
                elapsedMs = buildElapsedMs,
                onOpenFile = { name, line ->
                    diskTree.firstOrNull { it.name == name }?.let { file ->
                        openFile(file)
                        val text = liveBuffers[file.path]
                            ?: diskContents[file.path].orEmpty()
                        caretRequest = offsetOfLine(text, line)
                        sourceRevision++
                        activePanel = "Code"
                    }
                    buildLogOpen = false
                },
                onBack = ::handleBackPress,
                onAskAi = { row ->
                    buildLogOpen = false
                    askAiFromDiagnostic(row.fileName, row.diagnostic.line, row.diagnostic.message)
                },
            )
        }

        // Leaving with unsaved work asks first. Composed last so Back dismisses it before
        // anything underneath can act: Cancel stays, Discard leaves, Save writes then leaves
        // (and stays put on a failed write, so nothing is silently lost).
        BackHandler(enabled = exitConfirm) {
            BackLog.press("exit-dialog", "branch=cancel")
            exitConfirm = false
        }
        if (exitConfirm) {
            val c = LocalPageColors.current
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { exitConfirm = false },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.panel)
                        .border(1.dp, c.ln, RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(18.dp),
                ) {
                    Text(
                        text = "Unsaved changes",
                        color = c.tx,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = "Some files have edits that are not saved yet.",
                        color = c.mu,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = Sans,
                    )
                    Row(
                        Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExitDialogButton(label = "Cancel", primary = false) {
                            exitConfirm = false
                        }
                        ExitDialogButton(label = "Discard", primary = false) {
                            exitConfirm = false
                            onExit()
                        }
                        ExitDialogButton(label = "Save", primary = true) {
                            if (saveAllDirty()) {
                                exitConfirm = false
                                onExit()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ExitDialogButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (primary) Modifier.background(c.accent)
                else Modifier.border(1.dp, c.ln, RoundedCornerShape(10.dp)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) Color.White else c.text,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * The header shared by AI and More: both are full pages under the status bar, so neither can
 * borrow the editor's top bar (that bar belongs to a file). The back button runs the same
 * handleBackPress as system Back, so the two can never disagree.
 */
@Composable
private fun IdeFullPageBar(title: String, onBack: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.panel)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp)
            .drawBehind {
                drawLine(
                    color = c.divider,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeaderIconButton(
            icon = CcIcons.ChevronLeft,
            label = "Back",
            tint = c.iconTint,
            onClick = onBack,
        )
        Text(
            text = title,
            color = c.brightText,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IdeTopBar(
    projectName: String,
    dirty: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onDrawer: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTerminal: () -> Unit,
    onRun: () -> Unit,
    onMore: () -> Unit,
    menuActive: Boolean,
) {
    val c = LocalPageColors.current

    Row(
        Modifier
            .fillMaxWidth()
            // Android 15+ draws the window under the phone's status bar. Without this inset the
            // row's buttons sat against it; now the panel colour extends behind the bar and the
            // 52 dp of content starts below it. Where the system already insets the window, the
            // value is zero and nothing moves.
            .background(c.panel)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp)
            .drawBehind {
                drawLine(
                    color = c.divider,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The drawer button opens the file list; the project name fills the free width so the
        // actions sit against the right edge. Going back is the system Back button and gesture,
        // which the screen's own handler owns — no arrow here.
        HeaderIconButton(
            icon = CcIcons.Drawer,
            label = "Menu",
            tint = c.iconTint,
            onClick = onDrawer,
        )

        // The project name fills the free width so the actions sit against the right edge.
        Text(
            text = projectName,
            color = c.brightText,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        HeaderIconButton(
            icon = CcIcons.Undo,
            label = "Undo",
            tint = if (canUndo) c.iconTint else c.iconTint.copy(alpha = 0.35f),
            enabled = canUndo,
            onClick = onUndo,
        )
        HeaderIconButton(
            icon = CcIcons.Redo,
            label = "Redo",
            tint = if (canRedo) c.iconTint else c.iconTint.copy(alpha = 0.35f),
            enabled = canRedo,
            onClick = onRedo,
        )
        // Save carries no tint change: a yellow dot at its top right says unsaved, and nothing
        // else on the button moves.
        Box(
            Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            HeaderIconButton(
                icon = CcIcons.Save,
                label = "Save",
                tint = c.iconTint,
                onClick = onSave,
            )
            if (dirty) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.warnText),
                )
            }
        }
        HeaderIconButton(
            icon = CcIcons.Terminal,
            label = "Terminal",
            tint = c.iconTint,
            onClick = onTerminal,
        )
        // Run is the one filled button in the header: a blue fill with a white play glyph.
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.runFill)
                .clickable(onClick = onRun)
                .androidxSemantics("Run"),
            contentAlignment = Alignment.Center,
        ) {
            // White by spec, not a token: the run fill is the same blue on both themes, so the
            // label that reads on it is the same white on both.
            CcIcon(CcIcons.Play, Color.White, 19.dp)
        }
        HeaderIconButton(
            icon = CcIcons.More,
            label = "More",
            tint = if (menuActive) c.tx else c.iconTint,
            background = if (menuActive) c.panel2 else Color.Transparent,
            onClick = onMore,
        )
    }
}

/**
 * One 34 x 34 header button holding a 19 dp icon. [keepFocusClick] is not used here because the
 * header sits outside the editor, but the icons come from [CcIcons] so they match the prototype's
 * shapes exactly instead of depending on font glyphs.
 */
@Composable
internal fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color? = null,
    enabled: Boolean = true,
    boxSize: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    // Panel actions are 38 x 38 rounded buttons on the field colour. The editor header's
    // undo/redo pass background = Color.Transparent because screen 07 draws them as bare icons.
    val fill = background ?: LocalPageColors.current.mock.field
    Box(
        Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .androidxSemantics(label),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, tint, iconSize)
    }
}

/** Labels a button for accessibility without making it focusable, so the keyboard never moves. */
private fun Modifier.androidxSemantics(label: String): Modifier =
    this.then(
        Modifier.semantics { contentDescription = label },
    )

/**
 * Kinds the preview panel can actually render: the Kotlin phone mock, the local web server, the
 * image decoder and the structured formatters. Gradle, properties and unrecognised text get no
 * button rather than a button that opens onto "no preview".
 *
 * CSS and JS are deliberately absent: the panel renders a whole web *project* from its entry HTML,
 * never a stylesheet or a script on its own, so offering them only ever produced plain text.
 */
internal val PreviewableKinds = setOf(
    FileKind.Kotlin,
    FileKind.Html,
    FileKind.Json,
    FileKind.Xml,
    FileKind.Csv,
    FileKind.Markdown,
    FileKind.Image,
)

private val PreviewableExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

internal fun isPreviewable(kind: FileKind?, name: String): Boolean {
    if (kind != null && kind in PreviewableKinds) return true
    if (kind == FileKind.Folder) return false
    return name.substringAfterLast('.', "").lowercase() in PreviewableExtensions
}

/**
 * The path bar's crumbs: the folders that hold the file, then the file itself. The whole path is
 * shown while it fits, and only a path long enough to be unreadable is shortened — from the left,
 * so the package end and the file name survive. Three is the cap: past that the bar starts eating
 * the buttons next to it.
 *
 * `app/build.gradle.kts` → `app › build.gradle.kts`
 * `app/src/main/java/com/example/myapp/Main.kt` → `… › myapp › Main.kt`
 */
internal fun breadcrumbOf(path: String, maxCrumbs: Int = 3): List<String> {
    val parts = path.split('/').filter { it.isNotBlank() }
    if (parts.isEmpty()) return emptyList()
    if (parts.size <= maxCrumbs) return parts
    return listOf("…") + parts.takeLast(maxCrumbs - 1)
}

/**
 * Every folder that holds [relative], outermost first: `a/b/c.kt` gives `a` and `a/b`, and a file
 * at the root gives nothing. The drawer keys its collapsed set by path, so these are exactly the
 * entries that have to come out of it for a row to be drawn where it belongs.
 */
internal fun ancestorFoldersOf(relative: String): List<String> =
    relative.split("/").dropLast(1)
        .runningFold("") { outer, part -> if (outer.isEmpty()) part else "$outer/$part" }
        .drop(1)

/**
 * The drawer's collapsed set with [path] flipped: an open folder becomes closed and a closed one
 * becomes open.
 *
 * This lives here rather than as two branches at the call site because both branches used to add
 * and remove on the wrong side — `in collapsed` meant add, not in it meant remove — so the set was
 * never changed by tapping at all. Only creating a file, which takes ancestors *out* of the set,
 * ever opened a folder, which is why it looked like folders opened only when you made a file.
 */
internal fun toggleCollapsed(collapsed: Set<String>, path: String): Set<String> =
    if (path in collapsed) collapsed - path else collapsed + path

/**
 * Cuts a long name in the middle so the extension stays visible:
 * `ExampleInstrumentedTest.kt` becomes `ExampleInstrumentedT….kt`.
 */
internal fun middleEllipsis(name: String, head: Int = 20, tail: Int = 10): String {
    if (name.length <= head + tail + 1) return name
    return name.take(head) + "…" + name.takeLast(tail)
}

/**
 * Compact folders: a folder whose only visible child is another folder joins it into
 * one row (`androidTest/java/com/example`), repeated down the chain. Returns display
 * entries carrying the label to draw and the last folder's path, which is what the
 * chevron toggles and the long-press menu acts on. A joined name that is still too
 * long cuts at the start so the last folder stays visible.
 */
internal data class DrawerRow(val file: ProjectFile, val label: String, val togglePath: String)

internal fun compactDrawerRows(rows: List<ProjectFile>): List<DrawerRow> {
    // Children by index, in draw order: the flat list is depth-first, so the rows
    // between a folder and the next row at its depth or shallower are its subtree.
    fun childrenOf(index: Int): List<Int> {
        val parent = rows[index]
        val out = mutableListOf<Int>()
        var i = index + 1
        while (i < rows.size && rows[i].depth > parent.depth) {
            if (rows[i].depth == parent.depth + 1) out.add(i)
            i++
        }
        return out
    }
    fun parentOf(index: Int): Int? {
        val depth = rows[index].depth
        if (depth == 0) return null
        for (i in index - 1 downTo 0) {
            if (rows[i].depth == depth - 1) return i
            if (rows[i].depth < depth - 1) return null
        }
        return null
    }
    val out = mutableListOf<DrawerRow>()
    rows.forEachIndexed { index, file ->
        if (file.kind != FileKind.Folder) {
            out.add(DrawerRow(file, middleEllipsis(file.name), file.path))
            return@forEachIndexed
        }
        // A middle link has a folder parent with no other children: skip it, the chain
        // head draws the whole run.
        val parent = parentOf(index)
        if (parent != null && rows[parent].kind == FileKind.Folder && childrenOf(parent).size == 1) {
            return@forEachIndexed
        }
        // Walk down single-folder chains from here.
        val parts = mutableListOf(file.name)
        var last = index
        while (true) {
            val kids = childrenOf(last)
            if (kids.size != 1 || rows[kids[0]].kind != FileKind.Folder) break
            parts.add(rows[kids[0]].name)
            last = kids[0]
        }
        val lastPath = rows[last].path
        val full = parts.joinToString("/")
        // Too long: cut at the start so the last folder stays visible.
        val label = if (full.length > 40) "…/" + full.takeLast(38) else full
        out.add(DrawerRow(file.copy(path = lastPath), label, lastPath))
    }
    return out
}

/**
 * The one field a tree operation asks for before it happens. Creating starts blank, renaming
 * starts on the leaf and moving starts on the whole path — the same dialog three ways, because
 * they are three ways of typing a name.
 */
private sealed interface TreePrompt {
    data class New(val isFolder: Boolean) : TreePrompt
    data class Rename(val target: ProjectFile) : TreePrompt
    data class Transfer(val target: ProjectFile, val copy: Boolean) : TreePrompt
}

/** What the import picker is offered. Narrow, because "is this text?" is only answerable after. */
private val ImportMimeTypes = arrayOf(
    "text/*",
    "application/json",
    "application/xml",
    "application/javascript",
    "application/octet-stream",
)

/** Matches the editor's own read ceiling: a file this app will not open is not one it imports. */
private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

/** How much of a pick is inspected before deciding it is not text. One page is enough. */
private const val MAX_IMPORT_PROBE = 8 * 1024

/**
 * Name dialogs (new file, rename, move) are asked from the Files panel, so they sit over that
 * panel instead of the center of the screen, where the editor is. Bias -1 pins to the left
 * edge, +1 to the right; -0.22 lands on the panel's middle.
 */
private const val PROMPT_BIAS = -0.22f

/**
 * The name a picker gave the file, if it gave one. The display name is the only reliable source:
 * `uri.lastPathSegment` is a document id on most providers, not something anybody would recognise.
 */
private fun documentName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
}.getOrNull()

/** The size the provider claims, so an oversized pick is refused before it is read into memory. */
private fun documentSize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
}.getOrNull()

/**
 * A name that is not already used in [folder]. An import never overwrites: there is no undo in
 * this editor, so landing on an existing file would destroy it without anything to bring it back.
 */
internal fun uniqueLeafIn(folder: String, wanted: String, taken: (String) -> Boolean): String {
    fun at(leaf: String) = if (folder.isEmpty()) leaf else "$folder/$leaf"
    if (!taken(at(wanted))) return wanted
    val dot = wanted.lastIndexOf('.')
    val base = if (dot > 0) wanted.substring(0, dot) else wanted
    val ext = if (dot > 0) wanted.substring(dot) else ""
    var attempt = 2
    while (taken(at("$base$attempt$ext"))) attempt++
    return "$base$attempt$ext"
}

/**
 * The tab strip's cap. The active tab and any tab with unsaved changes are never dropped, so the
 * cap can never throw away the file the user is in or the file they have not saved yet; when
 * something has to give it is the oldest tab that is neither, because a strip that keeps its
 * newest work is the one that matches what people expect from a tab bar.
 *
 * Original order is preserved — a strip that reshuffles itself as files open is harder to follow
 * than one that only grows from the right.
 */
/**
 * Which tabs survive opening one too many. Identity is the path: two tabs called index.js in
 * different folders are two tabs, and dropping by name would close the wrong one.
 */
internal fun retainTabs(
    tabs: List<ProjectFile>,
    active: String,
    isDirty: (String) -> Boolean,
    limit: Int = 3,
): List<ProjectFile> {
    if (tabs.size <= limit) return tabs
    val keep = tabs
        .filter { it.path == active || isDirty(it.path) }
        .map { it.path }
        .toMutableSet()
    if (keep.size < limit) {
        for (tab in tabs.asReversed()) {
            if (keep.size >= limit) break
            keep += tab.path
        }
    }
    return tabs.filter { it.path in keep }
}

/**
 * The row between the tab strip and the code: where the file lives, plus the buttons that leave
 * the code. It replaces the deleted status bar, and it is the only chrome above the editor body,
 * so it is a fixed 34 dp and never sits over the text.
 */
@Composable
private fun IdePathBar(
    filePath: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRun: () -> Unit,
) {
    val c = LocalPageColors.current
    val fileName = filePath.substringAfterLast('/').ifBlank { "No file" }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(c.mock.content)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fileName,
            color = c.mock.text,
            fontSize = 16.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HeaderIconButton(
            icon = CcIcons.Undo,
            label = "Undo",
            tint = if (canUndo) c.mock.text2 else c.mock.faint.copy(alpha = 0.4f),
            background = Color.Transparent,
            enabled = canUndo,
            boxSize = 34.dp,
            iconSize = 19.dp,
            onClick = onUndo,
        )
        Spacer(Modifier.width(4.dp))
        HeaderIconButton(
            icon = CcIcons.Redo,
            label = "Redo",
            tint = if (canRedo) c.mock.text2 else c.mock.faint.copy(alpha = 0.4f),
            background = Color.Transparent,
            enabled = canRedo,
            boxSize = 34.dp,
            iconSize = 19.dp,
            onClick = onRedo,
        )
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.mock.green)
                .clickable(onClick = onRun),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Play, c.mock.onGreen, 17.dp)
        }
    }
}

/** One 26 dp icon button on the path bar. [selected] is the accent tint, not a fill, because the
 *  bar is only 34 dp tall and a filled chip would read as a second tab. */
@Composable
private fun PathBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) c.mock.card else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, if (selected) c.mock.accent else c.mock.muted, 16.dp)
    }
}

@Composable
private fun IdeExplorer(
    projectName: String,
    // v7: the run card every side panel floats at its bottom. Null when the caller has none.
    runCard: (@Composable () -> Unit)?,
    onRun: () -> Unit,
    slug: String,
    tree: List<ProjectFile>,
    // Highlighted row: the file being read, looked up by path. Two files that share a name are
    // not the same row, so the name alone can never say which one is on screen.
    activePath: String?,
    // Row to scroll into view when it is not already showing. Separate from the highlight because
    // a folder that was just created needs to be revealed without stealing the open file.
    revealPath: String?,
    // Closed folders, by path. The drawer only reads it — opening one happens on the way to a new
    // entry, from the screen that knows what was just created.
    collapsed: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    openTabs: List<ProjectFile>,
    onOpenTab: (ProjectFile) -> Unit,
    tab: String,
    gitRevision: Int,
    onTab: (String) -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onRefresh: () -> Unit,
    onCollapseAll: () -> Unit,
    showGenerated: Boolean,
    onToggleGenerated: () -> Unit,
    // One Git letter per file path, and unsaved files by path, for the row decorations.
    gitLetters: Map<String, com.closecode.ui.components.StatusKind>,
    dirtyPaths: Set<String>,
    // The hint line shows for the drawer's first three opens, then never again.
    hintVisible: Boolean,
    onSelect: (ProjectFile) -> Unit,
    // The long-press menu. It is carried up because every one of its actions needs the repository,
    // the open tabs or a dialog, and the drawer has none of those.
    onAction: (TreeAction, ProjectFile) -> Unit,
    // Carried through to the GitHub tab: it only says *when* a clone should start. The route that
    // runs one is full-screen, so it lives at screen level, and only the screen has room for it.
    onCloneRequest: () -> Unit,
    // Staged plus unstaged plus untracked: the Source Control tab's count bubble.
    changeCount: Int,
    // Opens a unified diff of the tapped change row. The route lives at screen level.
    onDiff: (String) -> Unit = {},
    // Opens a commit's before/after diff of one history file. Same routing.
    onCommitDiff: (commitId: String, path: String) -> Unit = { _, _ -> },
    // Opens the history page and one commit's page. Same routing.
    onHistory: () -> Unit = {},
    onCommitOpen: (commitId: String) -> Unit = {},
    // How many times the stored GitHub token has been replaced. Passed on so the panel re-reads it
    // instead of going on with the copy it took when it was composed.
    githubTokenEpoch: Int,
    projectRoot: File?,
    onClose: () -> Unit = {},
) {
    val c = LocalPageColors.current
    // Screen 10 speaks the spec palette (CcTheme.kt): every colour below comes from here,
    // never from the old `c.mock` tokens.
    val cc = com.closecode.ui.theme.LocalCcColors.current

    val listState = rememberLazyListState()

    val q = query.trim().lowercase()
    val matched = if (q.isEmpty()) tree else tree.filter { it.path.lowercase().contains(q) }
    // A collapsed folder hides its whole subtree, not only its own row. The tree is flat, so the
    // descendants are found by depth: everything below a collapsed depth stops being listed.
    val rows = if (q.isNotEmpty()) {
        matched
    } else {
        val out = mutableListOf<ProjectFile>()
        val hiddenBelow = mutableListOf<Int>()
        for (file in matched) {
            while (hiddenBelow.isNotEmpty() && hiddenBelow.last() >= file.depth) {
                hiddenBelow.removeAt(hiddenBelow.lastIndex)
            }
            if (hiddenBelow.isNotEmpty()) continue
            out += file
            if (file.kind == FileKind.Folder && file.path in collapsed) hiddenBelow += file.depth
        }
        out
    }

    // Display rows: compact folder chains on the full tree, plain middle-ellipsis
    // labels on search hits (which are not hierarchical, so joining them would lie).
    val drawerRows = remember(rows, q) {
        if (q.isEmpty()) compactDrawerRows(rows) else rows.map {
            DrawerRow(it, middleEllipsis(it.name), it.path)
        }
    }

    // Bring the row that was just created or just opened into view. It only fires when the row is
    // off screen, so tapping a file that is already showing never jumps the list, and it waits for
    // the row count to change because a new entry lands in the tree one frame after the tap that
    // asked for it. Without this the file opens in a tab and stays invisible in the drawer, which
    // reads as "nothing happened".
    val headerRows = TreeListHeaderItems
    LaunchedEffect(revealPath, drawerRows.size) {
        val path = revealPath ?: return@LaunchedEffect
        val index = drawerRows.indexOfFirst { it.togglePath == path || it.file.path == path }
        if (index < 0) return@LaunchedEffect
        val position = index + headerRows
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == position }) {
            listState.scrollToItem(position)
        }
    }

    Box(
        Modifier
            .width(234.dp)
            .fillMaxHeight()
            // v7: the panel is a rounded card — BOTH top corners are 26, not only the left one.
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(cc.panel)
            .windowInsetsPadding(WindowInsets.statusBars)
            .clipToBounds(),
    ) {
    Column(Modifier.fillMaxSize()) {
        if (tab == FilesTab) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Files",
                    color = cc.text,
                    fontSize = 21.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = projectName,
                    color = cc.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            HeaderIconButton(
                icon = CcIcons.Plus,
                label = "New file",
                tint = cc.text2,
                background = cc.field,
                iconSize = 18.dp,
                onClick = onCreateFile,
            )
            HeaderIconButton(
                icon = CcIcons.FolderPlus,
                label = "New folder",
                tint = cc.text2,
                background = cc.field,
                iconSize = 18.dp,
                onClick = onCreateFolder,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cc.field)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CcIcon(CcIcons.Search, cc.muted, 16.dp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = cc.text,
                    fontSize = 13.5.sp,
                    fontFamily = Sans,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Filter files",
                            color = cc.muted,
                            fontSize = 13.5.sp,
                            fontFamily = Sans,
                        )
                    }
                    inner()
                },
            )
        }
        if (openTabs.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "OPEN · ${openTabs.size}",
                    color = cc.muted,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(openTabs, key = { it.path }) { file ->
                    Text(
                        text = file.name,
                        color = cc.text2,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 118.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(cc.field)
                            .clickable { onOpenTab(file) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FILES",
                color = cc.muted,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f),
            )
        }

        }
        // Both lists stay composed so the Source Control tab never loses a half-typed commit message
        // or cancels a commit that is already running on disk.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (q.isNotEmpty()) "No files match" else "This folder is empty",
                                color = cc.muted,
                                fontSize = 13.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (q.isNotEmpty()) "Try another name." else "Create a file or a folder.",
                                color = cc.textSoft,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                textAlign = TextAlign.Center,
                            )
                            if (q.isEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                com.closecode.ui.components.OutlineButton(
                                    label = "New file",
                                    onClick = onCreateFile,
                                )
                                Spacer(Modifier.height(8.dp))
                                com.closecode.ui.components.OutlineButton(
                                    label = "Clone a repository",
                                    onClick = onCloneRequest,
                                )
                            }
                        }
                    }
                }
                itemsIndexed(drawerRows, key = { _, r -> "tree-${r.file.path}" }) { _, row ->
                    val file = row.file
                    val open = file.kind == FileKind.Folder
                    val closed = open && file.path in collapsed
                    TreeRow(
                        label = row.label,
                        depth = file.depth + 1,
                        bold = false,
                        icon = com.closecode.ui.model.fileIconName(file.kind),
                        iconTint = when {
                            file.kind == FileKind.Folder -> cc.yellow
                            file.kind == FileKind.Markdown ||
                                file.kind == FileKind.Properties -> cc.muted
                            file.kind == FileKind.Kotlin ||
                                file.kind == FileKind.Xml ||
                                file.kind == FileKind.Gradle ||
                                file.kind == FileKind.Json ||
                                file.kind == FileKind.Css ||
                                file.kind == FileKind.Js ||
                                file.kind == FileKind.Html -> cc.purple
                            else -> cc.blue
                        },
                        chevron = if (open) "cr" else null,
                        chevronOpen = !closed,
                        chevronTint = cc.faint,
                        active = file.path == activePath,
                        gitStatus = gitLetters[file.path],
                        dirty = file.path in dirtyPaths,
                        onClick = { onSelect(file) },
                        actions = actionsForTree(file),
                        onAction = { action -> onAction(action, file) },
                    )
                }
                if (hintVisible && rows.isNotEmpty()) {
                    item(key = "hint") {
                        Text(
                            text = "Tap a file to open it. Long press for Rename, Duplicate, " +
                                "Copy path and Delete. A yellow dot means unsaved changes. " +
                                "The letter on the right is the Git status.",
                            color = cc.faint,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            IdeGitPanel(
                projectName = projectName,
                projectRoot = projectRoot,
                revision = gitRevision,
                onCloneRequest = onCloneRequest,
                tokenEpoch = githubTokenEpoch,
                onDiff = onDiff,
                onCommitDiff = onCommitDiff,
                onHistory = onHistory,
                onCommitOpen = onCommitOpen,
                // One modifier or the other, never both. Appending `size(0.dp)` after
                // `width(...).fillMaxHeight()` could not work: those hand the child an incoming
                // minimum of full width by full height, `size` is enforce-incoming, so the zero was
                // clamped straight back up and this opaque panel covered the file tree on both tabs.
                // On its own `size(0.dp)` really does collapse to nothing — while the panel stays
                // composed, so a half-typed commit message and a commit already running on disk
                // both survive the switch.
                modifier = if (tab == SourceControlTab) {
                    Modifier.fillMaxWidth().fillMaxHeight()
                } else {
                    Modifier.size(0.dp)
                },
            )
        }
        // v7: the run card floats above the panel's bottom; the panel only reserves its
        // strip so the last rows of either tab are never hidden behind it.
        Spacer(Modifier.height(84.dp))
    }
        // Files, like every side panel, floats the run card. The Source Control tab does not
        // (its commit controls own the bottom strip).
        if (tab == FilesTab && runCard != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 10.dp, bottom = 12.dp),
            ) {
                runCard()
            }
        }
    }
}

/**
 * Section labels elsewhere in the UI still use file-kind tiles; the tab strip no longer does.
 * Tabs are plain text with an unsaved dot, so there is nothing here to keep in sync with them.
 */

@Composable
internal fun SectionLabel(text: String) {
    val c = LocalPageColors.current
    Text(
        text = text,
        color = c.mu.copy(alpha = 0.7f),
        fontSize = 10.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 17.dp, top = 13.dp, bottom = 5.dp),
    )
}

/**
 * What the drawer's long-press menu can do to a row. The label carries the action rather than a
 * position in a list, so a folder can offer "New file" and a file can offer "Reveal in file
 * manager" without either one having to remember where it sits.
 */
internal enum class TreeAction(val label: String) {
    NEW_FILE("New file"),
    NEW_FOLDER("New folder"),
    RENAME("Rename"),
    DUPLICATE("Duplicate"),
    COPY_PATH("Copy path"),
    MOVE("Move to…"),
    COPY("Copy to…"),
    IMPORT("Import file…"),
    SHARE("Reveal in file manager"),
    DELETE("Delete"),
}

/** The menu a row offers. A folder is where new things go; a file is the thing itself. */
internal fun actionsForTree(file: ProjectFile): List<TreeAction> =    if (file.kind == FileKind.Folder) {
        listOf(
            TreeAction.NEW_FILE,
            TreeAction.NEW_FOLDER,
            TreeAction.RENAME,
            TreeAction.DUPLICATE,
            TreeAction.DELETE,
        )
    } else {
        listOf(
            TreeAction.RENAME,
            TreeAction.DUPLICATE,
            TreeAction.DELETE,
        )
    }

/** Screen 11 icon set: plus, folder, edit, copy, trash. */
private fun TreeAction.menuIcon(): String = when (this) {
    TreeAction.NEW_FILE -> "plus"
    TreeAction.NEW_FOLDER -> "folder"
    TreeAction.RENAME -> "edit"
    TreeAction.DUPLICATE -> "copy"
    TreeAction.DELETE -> "trash"
    TreeAction.COPY_PATH, TreeAction.MOVE, TreeAction.COPY -> "copy"
    TreeAction.IMPORT -> "file"
    TreeAction.SHARE -> "share"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    label: String,
    depth: Int,
    bold: Boolean,
    icon: String,
    iconTint: Color,
    chevron: String? = null,
    chevronOpen: Boolean = false,
    chevronTint: Color = Color.Unspecified,
    active: Boolean,
    gitStatus: com.closecode.ui.components.StatusKind? = null,
    dirty: Boolean = false,
    onClick: () -> Unit,
    actions: List<TreeAction> = emptyList(),
    onAction: (TreeAction) -> Unit = {},
) {
    val c = LocalPageColors.current
    val cc = com.closecode.ui.theme.LocalCcColors.current
    var menuOpen by remember { mutableStateOf(false) }
    // Screen 11: the menu is anchored under the row it belongs to. Measured against the area
    // under the status bar, which is where CcMenuOverlay starts drawing its dim layer.
    val density = LocalDensity.current
    var anchorTop by remember { mutableStateOf(0.dp) }
    // Status bar height is a composition read; the row's position is not, so measure it here.
    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    // Screen 10: 6 dp + 14 dp per level, no guide lines, 38 dp rows, 11 dp radius.
    val indentStep = 14.dp
    val chevRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (chevron != null && chevronOpen) 90f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "chevron",
    )
    val (gitColor, gitLetter) = when (gitStatus) {
        com.closecode.ui.components.StatusKind.Added -> cc.green to "A"
        com.closecode.ui.components.StatusKind.Modified -> cc.amber to "M"
        com.closecode.ui.components.StatusKind.Deleted -> cc.red to "D"
        com.closecode.ui.components.StatusKind.Untracked -> cc.muted to "U"
        null -> Color.Unspecified to null
    }

    Box {
        Row(
            Modifier
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (active) cc.selectedFile else Color.Transparent)
                .onGloballyPositioned { coords ->
                    anchorTop = with(density) { coords.positionInWindow().y.toDp() } - statusTop
                }
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(start = 6.dp + indentStep * depth, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(13.dp).height(13.dp), contentAlignment = Alignment.Center) {
                if (chevron != null) {
                    com.closecode.ui.CcIcon(
                        chevron,
                        if (chevronTint == Color.Unspecified) cc.faint else chevronTint,
                        13.dp,
                        2.2f,
                        Modifier.rotate(chevRotation),
                    )
                }
            }
            com.closecode.ui.CcIcon(icon, iconTint, 17.dp, 1.7f, Modifier.padding(start = 8.dp, end = 8.dp))
            Text(
                text = label,
                color = cc.text2,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = if (bold || active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (dirty) {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(cc.amber),
                )
            }
            if (gitLetter != null) {
                Text(
                    text = gitLetter,
                    color = gitColor,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (menuOpen) {
            val screenH = LocalConfiguration.current.screenHeightDp.dp
            val menuH = (actions.size * 42 + 12 + if (actions.contains(TreeAction.DELETE)) 9 else 0).dp
            val top = if (anchorTop + 38.dp + menuH + 16.dp > screenH) {
                (anchorTop - menuH).coerceAtLeast(8.dp)
            } else {
                anchorTop + 38.dp
            }
            com.closecode.ui.components.CcMenuOverlay(
                top = top,
                onDismiss = { menuOpen = false },
            ) {
                actions.forEach { action ->
                    if (action == TreeAction.DELETE) {
                        com.closecode.ui.components.CcMenuDivider()
                    }
                    com.closecode.ui.components.CcMenuItem(
                        icon = action.menuIcon(),
                        label = action.label,
                        destructive = action == TreeAction.DELETE,
                        onClick = {
                            menuOpen = false
                            onAction(action)
                        },
                    )
                }
            }
        }
    }
}

/** One undo step: the field restores the display half, the analysis reads the source half. */
internal data class UndoEntry(val field: TextFieldValue, val source: String)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun IdeEditorArea(
    tabs: List<ProjectFile>,
    active: String,
    language: String,
    contentFor: (String) -> String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    // A tab's long-press menu closes more than one file at a time, so these live beside the
    // single close rather than inside it.
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    onSuggestionsOpen: (Boolean) -> Unit = {},
    // Whether the tool row is on screen. It is the only way to reach tab, brace, quote and
    // comment on a soft keyboard, so it follows the keyboard rather than a timer.
    showToolRow: Boolean = true,
    // Whether the soft keyboard is open right now, read live by the screen. The editor uses it
    // to keep the tapped line visible once the keyboard (and the key row) has settled, because
    // at tap time the layout still shows the pre-keyboard size.
    imeOpen: Boolean = false,
    // How far the code text is pinched. The gesture lives on the screen above; this is just the
    // number the text size is drawn from.
    editorScale: Float = 1f,
    onBufferChange: (String) -> Unit = {},
    onToast: (String) -> Unit = {},
    // One cached lookup instead of two map reads and a whole-file compare per tab per frame.
    dirtyOf: (String) -> Boolean = { false },
    // Hoisted so the path bar can open the sheet too, not just taps inside the editor.
    problemSheetLine: MutableState<Int?> = remember { mutableStateOf(null) },
    activeKind: FileKind? = null,
formatRequest: Int = 0,
    // Raised by the selection bar's Format, which re-indents only the selected lines.
    formatSelectionRequest: Int = 0,
    // Which set of problems the sheet shows, and the project-wide set when it does.
    problemSheetScope: ProblemScope = ProblemScope.FILE,
    projectDiagnostics: List<ProjectDiagnostic> = emptyList(),
    // Opens another file by name, for a row in the project-wide list.
    onOpenProjectFile: (String) -> Unit = {},
    // Ask AI on a sheet row: the diagnostic leaves the editor for the agent entirely.
    onAskDiagnostic: (String, Int, String) -> Unit = { _, _, _ -> },
    // The parent owns the counter; the editor owns the buffer. The bar raises the parent's copy.
    onSelectionFormat: () -> Unit = {},
    undoRequest: Int = 0,
    redoRequest: Int = 0,
    // Bumped when the buffer was rewritten from outside — the block editor moves whole line ranges
    // and hands the result back. The field re-seeds on this, and its history with it, so undo can
    // never reach back past a text that no longer exists.
    sourceRevision: Int = 0,
    // Where the next re-seed from [sourceRevision] should leave the caret, or -1 for "wherever the
    // file's code ends". Hoisted because the add-a-block sheet is the one caller that knows.
    caretRequest: Int = -1,
    onCaretChange: (Int) -> Unit = {},
    // Names declared across the whole project, so a sibling file's declaration is not reported
    // as missing here.
    projectSymbols: Set<String> = emptySet(),
    // Ctrl S from a hardware keyboard, which the palette advertises as the save shortcut.
    onSaveRequest: () -> Unit = {},
    onHistoryState: (Boolean, Boolean) -> Unit = { _, _ -> },
    // Fed from the code's sideways scroll: true while the code can still move further left.
    // The sidebar swipe reads it so a swipe over sideways code scrolls the code instead of
    // opening the panel.
    codeScrollLeft: MutableState<Boolean> = mutableStateOf(false),
    // The file's path inside the project, for the version-control margin marks. The tab name is
    // not enough: two folders can hold the same name, and only the path addresses the git object.
    relativePath: String? = null,
    // The project's directory on disk, which the marks above are computed against. Null means
    // the project has no directory (a remote-only view), so the gutter marks nothing.
    projectRoot: java.io.File? = null,
    // Restored places from the last session, keyed by file name. Seeded once: the maps below
    // are the live copy afterwards, reported back through onPlaceChange on every switch.
    initialCaret: Map<String, Int> = emptyMap(),
    initialScroll: Map<String, Pair<Int, Int>> = emptyMap(),
    onPlaceChange: (Map<String, Int>, Map<String, Pair<Int, Int>>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val settings = LocalSettings.current
    val editScope = rememberCoroutineScope()
    // The gutter is 56 dp of line numbers; with the setting off the code moves all the way left
    // instead of sitting behind a blank strip, and every overlay below reads this same value so
    // nothing lands in a column the code is no longer drawn in.
    val columnStart = if (settings.lineNumbers) CodeColumnStart else CodeLeftInset

    // No starter template. A file with nothing in it opens as nothing, and the grey hint drawn in
    // the code column says so: a template would be written straight back by the next save into a
    // file the user never edited. The editor used to seed one and let `LaunchedEffect(active)`
    // below throw it away a frame later, which is why an empty file flickered a Kotlin header.
    // Read on every composition, never remembered behind the `contentFor` lambda. Compose is free
    // to reuse that lambda instance, and when it did, the cache below kept the empty opening seed
    // even after the disk read landed: the file showed "Empty file" with its text sitting in
    // `diskContents`. Reading the map here subscribes this composable to the write instead.
    val fromDisk = contentFor(active)
    // Where each file was left: caret offset and both scroll positions, keyed by file name in
    // plain maps. Plain, because reading them seeds the field below during composition, and
    // mutating snapshot state mid-composition would schedule another one.
    val caretByFile = remember { mutableMapOf<String, Int>().also { it.putAll(initialCaret) } }
    val scrollByFile =
        remember { mutableMapOf<String, Pair<Int, Int>>().also { it.putAll(initialScroll) } }
    // Fold state: source start-lines collapsed, per file. Entries that stop being foldable after
    // an edit are ignored by the map builder rather than cleaned up, so no bookkeeping here can
    // ever hide the wrong lines.
    var foldedFirstLines by remember(active) { mutableStateOf(setOf<Int>()) }
    val activeFolds = if (settings.codeFolding) foldedFirstLines else emptySet<Int>()
    // The file as it is on disk: the analysis, the persistence and every structural edit read
    // this, while the field, the layout and the caret read the folded display text below. The
    // two meet only in commit(), commitSource() and the fold toggle — nowhere else.
    var sourceText by remember(active, language, sourceRevision) { mutableStateOf(fromDisk) }
    // The only thing that may move the view on the reader's behalf, and when. Typing, taps,
    // suggestions, formatting, undo and explicit jumps bump it; restores, analysis, layout
    // changes, popups and the keyboard never do. The follow effects key on this alone, so
    // nothing else can move the view no matter how often it recomposes.
    var followSeq by remember { mutableStateOf(0) }
    // The last sequence the horizontal follow actually handled. A follow that arrives before the
    // renderer has measured the new window leaves this behind and waits for the layout instead of
    // guessing from the previous slice.
    var handledHFollowSeq by remember { mutableStateOf(0) }
    // True while one of this editor's own scroll calls is in flight, so the external-change
    // observer does not mistake our own animation frames for a scroll nobody asked for.
    var selfScrolling by remember { mutableStateOf(false) }
    // True while the horizontal pan handler owns the gesture (raw deltas never mark the state
    // in progress), for the same observer.
    var hPanActive by remember { mutableStateOf(false) }
    val foldMap = remember(sourceText, activeFolds) {
        FoldMap.build(sourceText, activeFolds)
    }
    // Display rows (1-based, gutter numbering) whose source line starts an active fold: the chip
    // rows. The chip sits past the end of the first line, where the hidden body used to continue.
    val foldedDisplayRows = remember(foldMap) {
        foldMap.foldedRanges.mapNotNull { range ->
            val row = foldMap.sourceToDisplayLine.getOrNull(range.first) ?: -1
            if (row >= 0) row + 1 else null
        }.toSet()
    }
    val foldableDisplayRows = remember(sourceText, foldMap) {
        foldableRanges(sourceText).mapNotNull { range ->
            val row = foldMap.sourceToDisplayLine.getOrNull(range.first) ?: -1
            if (row >= 0) row + 1 else null
        }.toSet()
    }
    var fieldValue by remember(active, language, sourceRevision) {
        mutableStateOf(
            TextFieldValue(
                text = foldMap.display,
                // Opens where the file was left: the saved caret is a source offset, mapped into
                // the current display, so tapping back and forth keeps each file's place even
                // across folds opening and closing underneath it.
                selection = run {
                    val saved = caretByFile.remove(active)
                    val displayCaret = saved?.let {
                        foldMap.toDisplay[it.coerceIn(0, sourceText.length)]
                    }
                    when {
                        displayCaret != null ->
                            TextRange(displayCaret.coerceIn(0, foldMap.display.length))
                        caretRequest >= 0 -> TextRange(
                            foldMap.toDisplay[caretRequest.coerceIn(0, sourceText.length)]
                                .coerceIn(0, foldMap.display.length),
                        )
                        else -> TextRange(caretOffsetAtEndOfCode(foldMap.display))
                    }
                },
            ),
        )
    }
    // The disk read can land after this editor has already seeded from an empty buffer. Adopt
    // the text as soon as it arrives, but only while both the buffer and the field are still
    // empty: once the reader has typed, the buffer is theirs and this must never overwrite it.
    LaunchedEffect(active, fromDisk) {
        if (sourceText.isEmpty() && fromDisk.isNotEmpty() && fieldValue.text.isEmpty()) {
            sourceText = fromDisk
            fieldValue = TextFieldValue(
                text = fromDisk,
                selection = TextRange(caretOffsetAtEndOfCode(fromDisk)),
            )
        }
    }
    // Spec 4.1 Undo and Redo. Every text change goes through commit(), which records the value being
    // replaced, so the header can step back through the editor's own history without a native undo
    // manager. Selection-only moves commit too but record nothing, because they are not edits.
    // Entries hold display and source together: the field restores one half and the analysis reads
    // the other, and either half alone would strand the editor showing text its own diagnostics do
    // not describe.
    val undoStack = remember(active, sourceRevision) { mutableStateListOf<UndoEntry>() }
    val redoStack = remember(active, sourceRevision) { mutableStateListOf<UndoEntry>() }

    fun pushUndo() {
        undoStack.add(UndoEntry(fieldValue, sourceText))
        while (undoStack.size > UNDO_HISTORY_LIMIT) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun commit(next: TextFieldValue) {
        // Typing, taps, suggestions, formatting and jumps all come through here, so this is
        // where the view earns the right to follow the cursor. Restores and reseeds assign
        // the field directly and never bump, so they can never drag the view along.
        followSeq++
        if (next.text != fieldValue.text) {
            // Built fresh rather than reading the remembered map: two keystrokes can land in one
            // frame, and the second sees the first's field text with the first's map still
            // waiting for recomposition. Mapping across that mismatch indexed past the end of
            // the map and killed the app on the keystroke.
            val map = FoldMap.build(sourceText, activeFolds)
            val edit = FoldMap.applyDisplayEdit(
                map,
                fieldValue.text,
                next.text,
                next.selection.start,
                next.selection.end,
            )
            pushUndo()
            sourceText = edit.source
            val fresh = FoldMap.build(edit.source, activeFolds)
            fieldValue = TextFieldValue(
                fresh.display,
                TextRange(
                    fresh.toDisplay[edit.caret.coerceIn(0, edit.source.length)]
                        .coerceIn(0, fresh.display.length),
                    fresh.toDisplay[edit.anchor.coerceIn(0, edit.source.length)]
                        .coerceIn(0, fresh.display.length),
                ),
            )
        } else {
            fieldValue = next
        }
    }

    /**
     * Commits an edit computed against the source text — whole-file formatting, import surgery —
     * which the display pipeline can never produce because folded bodies are not in it.
     */
    fun commitSource(newSource: String, sourceCaret: Int, sourceAnchor: Int = sourceCaret) {
        if (newSource == sourceText) return
        // Formatting and import surgery are the reader's own actions: the view may follow.
        followSeq++
        pushUndo()
        sourceText = newSource
        val map = FoldMap.build(newSource, activeFolds)
        fieldValue = TextFieldValue(
            map.display,
            TextRange(
                map.toDisplay[sourceCaret.coerceIn(0, newSource.length)]
                    .coerceIn(0, map.display.length),
                map.toDisplay[sourceAnchor.coerceIn(0, newSource.length)]
                    .coerceIn(0, map.display.length),
            ),
        )
    }

    fun undoOnce() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(UndoEntry(fieldValue, sourceText))
        fieldValue = previous.field
        sourceText = previous.source
        followSeq++
    }

    fun redoOnce() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(UndoEntry(fieldValue, sourceText))
        fieldValue = next.field
        sourceText = next.source
        followSeq++
    }
    // Folding a block hides its interior rows from the display text, and everything downstream —
    // gutter, overlays, caret — reads display coordinates, so no other layer needs to know folds
    // exist. Toggling clears history: entries hold display snapshots, and a fold changes what
    // every display offset means, so popping one now would restore text the map cannot place.
    fun toggleFold(sourceLine: Int) {
        if (!settings.codeFolding) return
        val beforeFolds = foldedFirstLines
        foldedFirstLines = if (sourceLine in foldedFirstLines) {
            foldedFirstLines - sourceLine
        } else {
            foldedFirstLines + sourceLine
        }
        // The caret and anchor travel through a freshly built map, not the remembered one: a
        // tap can follow a keystroke in the same frame, and the remembered map may still
        // describe the text from before it.
        val before = FoldMap.build(sourceText, beforeFolds)
        // The caret and anchor travel through the old map into source offsets, then back out
        // through the new one: a caret inside a region being folded lands on the fold point
        // instead of pointing at a row that no longer exists.
        val caretS = before.sourceAt(fieldValue.selection.start)
        val anchorS = before.sourceAt(fieldValue.selection.end)
        val map = FoldMap.build(sourceText, activeFolds)
        fieldValue = TextFieldValue(
            map.display,
            TextRange(
                map.toDisplay[caretS.coerceIn(0, sourceText.length)]
                    .coerceIn(0, map.display.length),
                map.toDisplay[anchorS.coerceIn(0, sourceText.length)]
                    .coerceIn(0, map.display.length),
            ),
        )
        undoStack.clear()
        redoStack.clear()
    }

    // Display rows (1-based, gutter numbering) whose source line starts an active fold: the chip

    LaunchedEffect(active, undoStack.size, redoStack.size) {
        onHistoryState(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }
    LaunchedEffect(undoRequest, active) {
        if (undoRequest == 0) return@LaunchedEffect
        undoOnce()
    }
    LaunchedEffect(redoRequest, active) {
        if (redoRequest == 0) return@LaunchedEffect
        redoOnce()
    }

    // Opening a file is not an edit, so it replaces the text and starts a fresh history.
    // Both halves are reseeded: the field shows the folded display, the analysis reads source.
    LaunchedEffect(active) {
        val next = contentFor(active)
        undoStack.clear()
        redoStack.clear()
        sourceText = next
        val map = FoldMap.build(next, activeFolds)
        fieldValue = TextFieldValue(
            map.display,
            TextRange(caretOffsetAtEndOfCode(map.display)),
        )
    }
    // One ScrollState per axis. Sharing a single state between verticalScroll and
    // horizontalScroll made the two fight over the same value, so the line-number gutter
    // drifted out of step with the code and error line numbers pointed at the wrong line.
    val codeScroll = rememberScrollState()
    val codeHScroll = rememberScrollState()
    // The workspace's swipe reads this: sideways code eats a right swipe until it has been
    // scrolled back to its left end (the 40 dp edge still opens the panel either way).
    LaunchedEffect(codeHScroll) {
        snapshotFlow { codeHScroll.value > 0 }.collect { codeScrollLeft.value = it }
    }
    val scrollMotion = motion<Float>(200)
    // A scroll nobody asked for — the field's own bring-into-view is the usual suspect — is
    // still caught here, with the timestamp that says which finished job it followed.
    LaunchedEffect(Unit) {
        var lastV = codeScroll.value
        var lastH = codeHScroll.value
        snapshotFlow { codeScroll.value to codeHScroll.value }.collect { (v, h) ->
            if (!codeScroll.isScrollInProgress && !selfScrolling && !hPanActive) {
                if (v != lastV) ScrollLog.record("v", lastV, v, "external")
                if (h != lastH) ScrollLog.record("h", lastH, h, "external")
            }
            lastV = v
            lastH = h
        }
    }
    // The setting sets the base size and pinch zoom scales it on top, which is exactly the
    // relationship the fixed constant had — so zoom keeps working unchanged, and at the default
    // size with nobody pinching this computes the same 13 sp the constant held.
    val codeFontSize = zoomedFontSize(settings.fontSize, editorScale).sp
    // Spec section 3: the row tracks the font at its own 20/13 ratio, so a larger font gets a
    // larger row instead of running two lines of code into each other.
    val codeLineHeight = settings.fontSize.sp * CodeLineHeightRatio * editorScale
    // Spec section 3: the row is exactly 20 dp. A `sp` line height would grow with the system font
    // scale, and every overlay is positioned against this number, so the dp value is converted
    // through the density here and the scale cancels back out.
    val codeLineHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        codeLineHeight.toPx()
    }
    val codeLineHeightTextUnit = with(androidx.compose.ui.platform.LocalDensity.current) {
        codeLineHeightPx.toSp()
    }
    val code = fieldValue.text
    val cursor = fieldValue.selection.start.coerceIn(0, code.length)
    // Identity here is the path (see the screen above); the name is display and diagnostic
    // matching only, since diagnostics arrive keyed by file name.
    val activeName = tabs.firstOrNull { it.path == active }?.name
        ?: active.substringAfterLast('/')
    // One pass over the buffer per text change; every scroll frame below reads from it instead
    // of walking the file (window offsets, the sticky header, the highlight base depth, the
    // brace-guide lines). Rebuilding it is linear, so even a 5,000-line file stays cheap.
    val lineIndex = remember(code) { LineIndex(code) }
    val declarationIndex = remember(code) { DeclarationIndex(code) }
    // Leaving a tab records where it was left; coming back restores caret and both scrolls, so
    // tapping between two files keeps each file's place. The drawer opening a file does not go
    // through here — that path never had a place to keep, and still doesn't.
    val tabListState = rememberLazyListState()
    var tabMenuFor by remember { mutableStateOf<String?>(null) }
    fun forgetPlace(name: String) {
        caretByFile.remove(name)
        scrollByFile.remove(name)
        onPlaceChange(caretByFile.toMap(), scrollByFile.toMap())
    }
    val selectTabKeepingPlace = { path: String ->
        // The caret is stored as a source offset so it survives folds opening and closing
        // underneath it; the scroll is display rows, coerced back into range on restore.
        caretByFile[active] = foldMap.sourceAt(cursor)
        scrollByFile[active] = codeScroll.value to codeHScroll.value
        onPlaceChange(caretByFile.toMap(), scrollByFile.toMap())
        onSelectTab(path)
    }
    LaunchedEffect(active, tabs.size) {
        // The active tab scrolls into view; a file opened off the right edge would otherwise sit
        // there selected with no visible mark of it.
        val index = tabs.indexOfFirst { it.path == active }
        if (index >= 0) tabListState.animateScrollToItem(index)
        // Scroll comes back a frame after the caret because it needs the laid-out column, while
        // the caret is seeded into the field above before first draw.
        val saved = scrollByFile.remove(active)
        if (saved != null) {
            selfScrolling = true
            try {
                ScrollLog.record("v", codeScroll.value, saved.first, "tab-place-restore")
                codeScroll.scrollTo(saved.first.coerceIn(0, codeScroll.maxValue))
                ScrollLog.record("h", codeHScroll.value, saved.second, "tab-place-restore")
                codeHScroll.scrollTo(saved.second.coerceIn(0, codeHScroll.maxValue))
            } finally {
                selfScrolling = false
            }
        }
    }
    // Go-to-line, go-to-error and search-result jumps land here as source offsets. Same-file
    // jumps never reseed (the file is already open), so the caret is placed here directly and
    // the view follows through the sequence below. Declared after the reseed above so that on
    // a file switch the reseed wins the caret and this only adds the follow.
    LaunchedEffect(caretRequest) {
        if (caretRequest < 0) return@LaunchedEffect
        val at = foldMap.toDisplay[caretRequest.coerceIn(0, sourceText.length)]
            .coerceIn(0, code.length)
        commit(TextFieldValue(code, TextRange(at)))
    }
    // Report the source buffer, not the display text: the parent saves it, diffs it for Preview
    // and counts diagnostics project wide, and none of those may ever see folded bodies missing.
    LaunchedEffect(active, sourceText) { onBufferChange(sourceText) }
    val isKotlin = language.contains("Kotlin", true) || active.endsWith(".kt")
    val staticSuggestions = remember(language) { suggestionsFor(language) }
    var selectedSug by remember(language) { mutableStateOf(0) }
    // Back dismisses the suggestion list without moving the caret, mirroring the signature
    // tooltip's suppression: any edit or caret move lifts it on its own.
    var suggestSuppressedAt by remember { mutableStateOf<Pair<String, Int>?>(null) }
    if (suggestSuppressedAt != null && suggestSuppressedAt != (code to cursor)) {
        suggestSuppressedAt = null
    }

    var apiIndex by remember { mutableStateOf(AndroidApiAssets.cached()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AndroidApiAssets.load(context) { loaded ->
            apiIndex = loaded
        }
    }

    var analysis by remember { mutableStateOf<KotlinFileAnalysis?>(null) }
    LaunchedEffect(sourceText, isKotlin, apiIndex, projectSymbols) {
        if (!isKotlin || sourceText.length > MAX_ANALYZED_CHARS) {
            analysis = null
            return@LaunchedEffect
        }
        delay(ANALYSIS_DEBOUNCE_MS)
        val snapshot = sourceText
        val index = apiIndex
        val declared = projectSymbols
        val computed = withContext(Dispatchers.Default) {
            runCatching { KotlinAnalyzer.analyze(snapshot, index, declared) }.getOrNull()
        }
        if (computed != null) analysis = computed
    }

    val codeLines = remember(code) { code.lines() }


    // Which colouring rules this buffer is under. Decided here as well as in the renderer because
    // the markup check below has to know it is looking at a page and not at code.
    val codeLanguage = remember(code) { codeLanguageFor(languageOf(active)) }

    val currentAnalysis = analysis?.takeIf { it.source == sourceText }

    // Diagnostics off means "show me nothing", not "stop analysing": the analysis still runs,
    // because Run and Build gate on its error count and turning the display off must not quietly
    // make an unbuildable project look buildable. Only what is drawn changes.
    // Markup gets its own check on the source text: folded-away tags are still tags, and
    // checking the display text would cry unclosed over every element hidden inside a fold.
    val markupDiagnostics = remember(sourceText, codeLanguage) {
        if (codeLanguage == CodeLanguage.MARKUP) htmlTagDiagnostics(sourceText) else emptyList()
    }

    // Diagnostics arrive in source coordinates and are drawn in display coordinates: the analyzer
    // sees the whole file including folded bodies, while squiggles, pills and gutter bars only
    // exist for visible rows. Anything hidden drops out until it unfolds.
    val displayDiagnostics = remember(currentAnalysis, markupDiagnostics, foldMap) {
        val mapped = ArrayList<Diagnostic>()
        currentAnalysis?.diagnostics.orEmpty().forEach { mapDiagnosticToDisplay(foldMap, it)?.let(mapped::add) }
        markupDiagnostics.forEach { mapDiagnosticToDisplay(foldMap, it)?.let(mapped::add) }
        mapped
    }

    val problems = remember(displayDiagnostics, settings.diagnostics) {
        ProblemsByLine.from(
            if (settings.diagnostics) displayDiagnostics else emptyList()
        )
    }

    val caretLine = remember(code, cursor) { caretPosition(code, cursor).first }
    // Reported on every move so the block sheet can insert at the line the caret is actually on.
    // A plain offset is all the insertion needs, so the whole selection never leaves the editor.
    LaunchedEffect(cursor) { onCaretChange(foldMap.sourceAt(cursor)) }

    var codeLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    // The widest line the visible slice actually needs. The transparent field must be at least
    // this wide or it wraps where the drawn Text does not, and a tap maps to the wrong character.
    var codeContentWidthPx by remember { mutableStateOf(0f) }
    // The slice the current layout was measured from. A tap can bump the follow sequence before
    // the renderer has laid out the new window, and reading the old layout with the new window
    // would clamp the caret to the wrong character and scroll the code sideways for no reason.
    var codeLayoutWindowStart by remember { mutableStateOf(-1) }
    var codeLayoutWindowEnd by remember { mutableStateOf(-1) }
    // The transparent field must be at least as wide as the glyphs it edits. When it is narrower,
    // Compose wraps its text while the visible Text stays unwrapped, and a tap maps to a different
    // character. The field is measured here so a mismatch is visible in the scroll log instead of
    // only as a caret that jumps to the wrong place.
    var fieldWidthPx by remember { mutableStateOf(0) }
    LaunchedEffect(fieldWidthPx, codeLayout) {
        val layout = codeLayout ?: return@LaunchedEffect
        if (fieldWidthPx > 0 && fieldWidthPx < layout.size.width - 1) {
            ScrollLog.record(
                "field",
                fieldWidthPx,
                layout.size.width,
                "field-narrower-than-text",
            )
        }
    }
    var viewportWidthPx by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    // The keyboard inset as the editor sees it, read live for the scroll forensics below. Under
    // adjustResize the window shrinks instead and this reads 0 with the keyboard open — which is
    // itself the fact the log is for.
    val imeBottomPxEditor = WindowInsets.ime.getBottom(LocalDensity.current)

    // Row height measured off the rendered text, so the numbers and the labels cannot drift from
    // the code the way a hand-converted dp/sp value does.
    val fallbackLineHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        codeLineHeight.toPx()
    }
    val lineHeightPx = remember(codeLayout, code) {
        measuredLineHeightPx(codeLayout, code) ?: fallbackLineHeightPx
    }

    // Only the rows that can be on screen are laid out and colourized. A two thousand line file was
    // paying for two thousand lines of measurement and two thousand lines of tokenizing on every
    // keystroke, of which about forty were ever visible.
    //
    // The scroll is read through derivedStateOf so a fling costs this scope one recomposition per
    // row crossed rather than one per pixel, and the window is what tells every overlay where the
    // slice sits — see [CodeWindow].
    val codeRange by remember(codeScroll, lineHeightPx, viewportHeightPx, codeLines) {
        androidx.compose.runtime.derivedStateOf {
            codeWindowRange(
                lineCount = codeLines.size,
                scrollTopPx = codeScroll.value.toFloat(),
                viewportHeightPx = viewportHeightPx,
                lineHeightPx = lineHeightPx,
            )
        }
    }
    val codeWindow = remember(codeRange.first, lineHeightPx, code) {
        CodeWindow(
            firstLine = codeRange.first,
            startOffset = lineIndex.lineStart(codeRange.first + 1),
            topPx = windowTopPx(codeRange.first, lineHeightPx),
        )
    }

    // The function the top of the window is inside, for the sticky header. Null while its own opening
    // line is still on screen: the reader can see it, and a pinned copy above the code is a second
    // thing saying what that line already says. One array slot per row crossed, not one file walk.
    val scopeHeader = remember(code, codeWindow.firstLine) {
        declarationIndex[codeWindow.firstLine]
            ?.takeIf { it.first < codeWindow.firstLine + 1 }
            ?.second
    }
    // highlightCode works one line at a time with nothing carried between them, so colourizing the
    // slice gives exactly the rows the whole file would have produced.
    //
    // The language is the file's, not the slice's. A slice begins wherever the reader has scrolled
    // to, so a page scrolled to its middle has no doctype and no tag in the first line to guess
    // from — and a reader who scrolled down a page expects a tag to stay a tag.
    //
    // Rainbow brackets are a setting and the cycle is theme data. Both are passed into the colour
    // pass and both are remember keys, so switching the setting recolours immediately. Getting this
    // wrong once meant the cycle lived in a global written from an effect, which cannot invalidate
    // the remember that reads it: the setting toggled and nothing happened at all.
    //
    // And the slice starts wherever the reader has scrolled to, so the block depth it starts from is
    // measured from everything above it. Without that, every window coloured from zero no matter how
    // deep in the file it was — the one thing the feature exists to prevent.
    val bracketCycle = if (settings.rainbowBrackets) {
        listOf(c.bracket1, c.bracket2, c.bracket3, c.bracket4, c.bracket5)
    } else {
        emptyList()
    }
    // The symbol the caret sits in, or the single word selected: every other whole-word
    // occurrence lights up behind it. A selected word of any length counts — selecting is
    // deliberate — but a caret merely parked needs two letters, or every `i` and `x` in the
    // file would glow while typing. Long-press and double-tap both select the word, so both
    // get highlights and details with no gesture detection of their own.
    val selectedSymbol = remember(code, fieldValue.selection) {
        val sel = fieldValue.selection
        if (sel.collapsed) {
            null
        } else {
            code.substring(sel.min.coerceIn(0, code.length), sel.max.coerceIn(0, code.length))
                .takeIf { it.isNotEmpty() && it.all(::isWordChar) }
        }
    }
    val occurrenceSymbol = selectedSymbol
        ?: remember(code, cursor) { symbolAt(code, cursor)?.takeIf { it.length >= 2 } }
    val occurrenceOffsets = remember(code, occurrenceSymbol) {
        occurrenceSymbol?.let { symbolOccurrences(code, it) } ?: emptyList()
    }
    // The selected symbol's details, for the selection bar's header: its name, where it is
    // declared, and how many times the file uses it. The declaration reads the source, not the
    // display text — folds hide bodies the symbol may live in — mapped through the fold map
    // from the selected row. Shown only when the selected line declares the name: a usage, or
    // a general multi-word selection, earns the highlight but never the strip. (Long-press and
    // double-tap are indistinguishable here — both select the word — so either may raise it.)
    val symbolDetails = remember(selectedSymbol, fieldValue.selection, code, foldMap, sourceText) {
        val symbol = selectedSymbol ?: return@remember null
        val caret = fieldValue.selection.min.coerceIn(0, code.length)
        val displayLine = caretPosition(code, caret).first
        val sourceLine = foldMap.displayLines.getOrNull(displayLine - 1) ?: displayLine
        val ownLine = sourceText.lines().getOrNull(sourceLine - 1).orEmpty()
        if (!declaresSymbol(ownLine, symbol)) return@remember null
        val decl = declarationOf(ownLine).takeIf { it.contains(symbol) }
            ?: enclosingDeclarationAt(sourceText, sourceLine)?.second
                ?.replace(Regex("\\s+"), " ")?.trim()?.take(SYMBOL_DETAILS_DECL_MAX)
        val count = occurrenceOffsets.size
        val uses = "$count ${if (count == 1) "use" else "uses"}"
        if (decl.isNullOrEmpty()) "$symbol · $uses" else "$symbol · $decl · $uses"
    }
    val highlighted = remember(codeRange, code, c, codeLanguage, bracketCycle, occurrenceSymbol) {
        val slice = sliceOfLines(codeLines, codeRange)
        // The base depth used to be a substring of everything above the window plus a scan of
        // it — a copy and a walk of up to the whole file on every row crossed. The index
        // already holds that depth: same scan, done once per text change.
        val base = highlightCode(
            slice,
            c,
            codeLanguage,
            bracketCycle,
            lineIndex.depthAtLine0(codeRange.first),
        )
        // Other occurrences of the caret's symbol glow behind the text, so a tap on a name
        // shows everywhere that name is used. Spans join the slice's own highlighting, which
        // is why only matches inside the visible slice are drawn — the list itself still
        // counts the whole display text, folded bodies excluded, since folds never reach it.
        val symbol = occurrenceSymbol
        if (symbol == null || occurrenceOffsets.isEmpty()) {
            base
        } else {
            val sliceStart = codeWindow.startOffset.coerceIn(0, code.length)
            val sel = fieldValue.selection
            val mark = androidx.compose.ui.text.SpanStyle(
                background = c.accent.copy(alpha = 0.22f),
            )
            androidx.compose.ui.text.AnnotatedString.Builder().apply {
                append(base)
                var drawn = 0
                for (range in occurrenceOffsets) {
                    if (drawn >= MAX_OCCURRENCE_SPANS) break
                    // The selected word itself is skipped: the selection highlight already owns it.
                    if (!sel.collapsed && range.first == sel.min && range.last + 1 == sel.max) {
                        continue
                    }
                    if (range.last < sliceStart || range.first >= sliceStart + slice.length) {
                        continue
                    }
                    addStyle(
                        mark,
                        (range.first - sliceStart).coerceAtLeast(0),
                        (range.last + 1 - sliceStart).coerceAtMost(slice.length),
                    )
                    drawn++
                }
            }.toAnnotatedString()
        }
    }

    // Pairs are found in the whole buffer rather than the window, because a block can open above
    // the slice the renderer is holding; the layer decides for each pair whether either end is on
    // screen. The setting is read here so switching it off stops the scan, not just the drawing.
    // Structural scans read the source: folded bodies still open blocks, pair braces and
    // indent lines, and none of that is visible in the display text. Their results are mapped
    // back into display coordinates for drawing, dropping anything the folds hide.
    // All three run off the main thread: on a big file the walk is the typing lag, and the layers
    // read the last finished result while the next one computes — never a half-built structure.
    val braceGuidesSrc by produceState(emptyList<IntRange>(), sourceText, settings.bracketGuides) {
        value = withContext(Dispatchers.Default) {
            if (settings.bracketGuides) bracePairs(sourceText) else emptyList()
        }
    }
    val indentColumnsSrc by produceState(IntArray(0), sourceText, settings.tabSize) {
        value = withContext(Dispatchers.Default) { indentColumnsOf(sourceText, settings.tabSize) }
    }
    val bracketPairsAllSrc by produceState(emptyList<IntRange>(), sourceText) {
        value = withContext(Dispatchers.Default) { allBracketPairs(sourceText) }
    }
    fun FoldMap.displayRange(sourceRange: IntRange): IntRange? {
        val a = toDisplay[sourceRange.first.coerceIn(0, sourceText.length)]
        val b = toDisplay[sourceRange.last.coerceIn(0, sourceText.length)]
        // Both ends must land exactly: a clamped end would underline whatever character the
        // fold point happens to sit on.
        if (toSource[a.coerceIn(0, display.length)] != sourceRange.first) return null
        if (toSource[b.coerceIn(0, display.length)] != sourceRange.last) return null
        return a..b
    }
    val braceGuides = remember(braceGuidesSrc, foldMap) {
        braceGuidesSrc.mapNotNull { foldMap.displayRange(it) }
    }
    val indentColumns = remember(indentColumnsSrc, foldMap) {
        IntArray(foldMap.displayLines.size) { row ->
            indentColumnsSrc.getOrElse(foldMap.displayLines[row] - 1) { 0 }
        }
    }
    val bracketPairsAll = remember(bracketPairsAllSrc, foldMap) {
        bracketPairsAllSrc.mapNotNull { foldMap.displayRange(it) }
    }
    // The one pair the caret is in, resolved once and read by the brace underline. Any of the
    // three bracket kinds counts, and a lone bracket with no partner is reported separately so it
    // can be underlined in red. All display coordinates: the pairs above already crossed over.
    val activeBracketPair = remember(bracketPairsAll, code, cursor) {
        activeBraces(bracketPairsAll, code, cursor)
    }
    val unmatchedBracket = remember(bracketPairsAll, code, cursor) {
        unmatchedBracketAt(code, cursor, bracketPairsAll)
    }
    var quickFixLine by remember { mutableStateOf<Int?>(null) }
    var quickFixIndex by remember { mutableStateOf(0) }

    val quickFixActions = remember(quickFixLine, problems, currentAnalysis, code, settings.quickFixes) {
        if (!settings.quickFixes) emptyList()
        else buildQuickFixActions(quickFixLine, problems, currentAnalysis, code)
    }

    fun openQuickFix(line: Int) {
        if (!settings.quickFixes) return
        quickFixLine = line
        quickFixIndex = 0
        problemSheetLine.value = null
    }

    fun openProblemSheet(line: Int) {
        problemSheetLine.value = line
        quickFixLine = null
    }


    /** Character offset of a 1-based column on a 1-based line, for text-layout lookups. */
    fun offsetOfLineColumn(line: Int, column: Int): Int = lineIndex.offsetOf(line, column)

    val kotlinItems = remember(code, cursor, isKotlin, currentAnalysis, foldMap) {
        val live = currentAnalysis
        if (!isKotlin || live == null) {
            null
        } else {
            // The analysis reads source text, so the display caret is mapped before it goes in:
            // with folds above, the two disagree by exactly the hidden span.
            val sourceCursor = foldMap.toSource[cursor.coerceIn(0, code.length)]
            runCatching {
                if (live.prefixAt(sourceCursor).isNotEmpty() || live.isMemberAccess(sourceCursor)) {
                    live.completionsAt(sourceCursor, limit = 25)
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())
        }
    }

    // A tag in progress takes over the list. The generic fallback matched on the word before
    // the caret, so typing `<di` suggested `div` only by accident of the word being `di` — and
    // typing `<` or `<button ` suggested nothing at all, which is the exact moment a reader
    // writing a page needs the list most.
    val htmlContext = remember(code, cursor, codeLanguage) {
        if (codeLanguage == CodeLanguage.MARKUP) htmlTagContextAt(code, cursor) else null
    }
    val htmlItems = remember(htmlContext, settings.autocomplete) {
        if (!settings.autocomplete) emptyList()
        else when {
            htmlContext == null -> emptyList()
            htmlContext.afterName -> htmlAttributeSuggestions(
                tagName = htmlContext.tagName,
                partial = code.substring(
                    htmlContextOffset(code, cursor),
                    cursor,
                ),
            ).map {
                com.closecode.ui.ide.model.Suggestion(
                    glyph = "A",
                    name = it,
                    detail = "attribute",
                )
            }
            else -> htmlTagSuggestions(htmlContext.tagName).map {
                com.closecode.ui.ide.model.Suggestion(
                    glyph = "T",
                    name = it,
                    detail = "element",
                )
            }
        }
    }

    // Names accepted from this list, newest first, capped. A suggestion picked once is the
    // likeliest answer the next time its prefix is typed, so recent picks sort above untouched
    // names that merely match.
    val recentSuggestions = remember { mutableStateListOf<String>() }
    fun noteSuggestionAccepted(name: String) {
        recentSuggestions.remove(name)
        recentSuggestions.add(0, name)
        while (recentSuggestions.size > 20) recentSuggestions.removeAt(recentSuggestions.size - 1)
    }

    val kotlinPrefix = if (kotlinItems != null) {
        val sourceCursor = foldMap.toSource[cursor.coerceIn(0, code.length)]
        runCatching { currentAnalysis?.prefixAt(sourceCursor) }.getOrNull().orEmpty()
    } else {
        ""
    }

    val visibleSuggestions = remember(
        kotlinItems,
        htmlItems,
        staticSuggestions,
        code,
        isKotlin,
        settings.autocomplete,
        suggestSuppressedAt,
    ) {
        // A space ends the word being typed, so the list that was completing it has nothing to say
        // anymore. Without this the list lingered over the fresh word until the next keystroke gave
        // it a reason to leave.
        val typedSpace = cursor > 0 && cursor <= code.length && code[cursor - 1] == ' '
        when {
            // Dismissed by Back: stays down until the code or the cursor moves on.
            suggestSuppressedAt != null -> emptyList()
            // Checked first so a reader cannot mistake "switched off" for "nothing matched".
            !settings.autocomplete -> emptyList()
            typedSpace -> emptyList()
            htmlContext != null -> htmlItems
            kotlinItems != null -> sortSuggestions(
                kotlinItems.map { it.toSuggestion() },
                prefix = kotlinPrefix,
                recent = recentSuggestions,
            )
            else -> {
                val tail = code.takeLastWhile { it.isLetterOrDigit() }
                if (tail.isEmpty()) {
                    emptyList()
                } else {
                    sortSuggestions(
                        staticSuggestions.filter { it.name.startsWith(tail) },
                        prefix = tail,
                        recent = recentSuggestions,
                    )
                }
            }
        }
    }

    LaunchedEffect(visibleSuggestions) { selectedSug = 0 }

    LaunchedEffect(visibleSuggestions.isNotEmpty()) {
        onSuggestionsOpen(visibleSuggestions.isNotEmpty())
    }

    // Leaving the line that owns an open menu dismisses it. Otherwise a stale list from an old
    // line would offer actions against text that has already moved.
    LaunchedEffect(caretLine) {
        if (quickFixLine != null && quickFixLine != caretLine) quickFixLine = null
    }

    /**
     * Applies a quick fix and returns the text plus where the caret should land. Keeping this
     * outside composition means it can be tested and reused by both entry points.
     */
    fun applyQuickFix(action: QuickFixAction): Boolean {
        val target = quickFixActions.firstOrNull { it.label == action.label } ?: return false
        val line = target.target.toIntOrNull()
        when (target.kind) {
            // Import surgery reads the whole file including folded bodies, so it runs on source
            // and commits through the source path. Running it on display text would duplicate
            // imports it cannot see.
            QuickFixKind.ADD_IMPORT -> {
                val next = SourceEdits.addImport(sourceText, target.target)
                if (next == sourceText) return false
                commitSource(
                    next,
                    SourceEdits.caretAfterAddImport(sourceText, target.target) ?: next.length,
                )
            }

            QuickFixKind.REMOVE_IMPORT -> {
                val next = SourceEdits.removeImport(sourceText, target.target)
                if (next == sourceText) return false
                val caretS = foldMap.sourceAt(cursor)
                commitSource(next, caretS.coerceAtMost(next.length))
            }

            // Spec 6.6: E0101 deletes the offending token and leaves the caret where it began,
            // so a replacement can be typed straight into the gap.
            QuickFixKind.REMOVE_TOKEN -> {
                val diagnostic = problems.on(line ?: -1)
                    .firstOrNull { it.code == DiagnosticCodes.E0101_UNRESOLVED }
                    ?: return false
                val next = SourceEdits.removeToken(code, diagnostic.offset, diagnostic.length)
                    ?: return false
                commit(TextFieldValue(next, TextRange(diagnostic.offset.coerceIn(0, next.length))))
            }

            QuickFixKind.ADD_SEMICOLON -> {
                if (line == null) return false
                val next = SourceEdits.addSemicolon(code, line) ?: return false
                commit(TextFieldValue(next, TextRange(next.length.coerceAtMost(next.length))))
            }

            QuickFixKind.NO_FIX -> return false

            QuickFixKind.TOGGLE_COMMENT -> {
                if (line == null) return false
                val next = SourceEdits.toggleLineComment(code, line)
                if (next == code) return false
                commit(TextFieldValue(next, TextRange(cursor.coerceAtMost(next.length))))
            }

            QuickFixKind.MOVE_LINE_UP, QuickFixKind.MOVE_LINE_DOWN -> {
                if (line == null) return false
                // Moving a folded header alone would orphan its body a row away: unfold first so
                // the move carries what the reader sees as one block. Unfolding leaves the line
                // number itself alone, so the move below still aims at the right row.
                foldMap.displayLines.getOrNull(line - 1)?.let { sourceLine ->
                    if (sourceLine in foldedFirstLines) toggleFold(sourceLine)
                }
                val fresh = fieldValue.text
                val freshCursor = fieldValue.selection.start.coerceIn(0, fresh.length)
                val down = target.kind == QuickFixKind.MOVE_LINE_DOWN
                val next =
                    if (down) SourceEdits.moveLineDown(fresh, line) else SourceEdits.moveLineUp(fresh, line)
                if (next == fresh) {
                    return false
                } else {
                    val (_, freshColumn) = caretPosition(fresh, freshCursor)
                    commit(
                        TextFieldValue(
                            next,
                            TextRange(SourceEdits.caretAfterLineMove(fresh, line, freshColumn, down)),
                        ),
                    )
                }
            }
        }
        return true
    }

    // Keeps the caret on screen. Compose does not do this for a BasicTextField that sits inside a
    // manually scrolled column, so without this the caret can walk off the bottom while typing.
    // The row maths reuses rowTopPx, the same function the gutter draws its numbers with, so the
    // scroll follows the numbers rather than a second set of arithmetic.
    val autoDensity = androidx.compose.ui.platform.LocalDensity.current
    val topPaddingPx = with(autoDensity) { EditorTopPadding.toPx() }
    // The gutter is not part of the code column, so the popup's width and the tap area's minimum
    // width both measure the code area on its own.
    val gutterWidthPx = with(autoDensity) {
        (if (settings.lineNumbers) GutterWidth else 0.dp).toPx()
    }
    val codeWidthPx = (viewportWidthPx - gutterWidthPx).coerceAtLeast(0f)
    // The pill layer sits at the code column's text, not at the editor's left edge, so the room it
    // has on screen starts past the gutter and the column's own inset. Measuring from the editor's
    // edge accepted a pill that ran off the right side of the screen.
    val codeColumnOriginPx = with(autoDensity) { columnStart.toPx() }
    val pillViewportWidthPx = (viewportWidthPx - codeColumnOriginPx).coerceAtLeast(0f)
    // The whole scrollable height, measured once and shared: the caret's scroll maths and the code
    // column's own height have to agree, because they are the two ends of the same scroller. Both
    // are built from the row model rather than from what the renderer measured, so the number the
    // scroller clamps against cannot move when a slice is laid out.
    val contentHeightPx =
        topPaddingPx + codeLines.size * lineHeightPx + with(autoDensity) { 40.dp.toPx() }
    // "Scroll the cursor into view" — and only this. It runs after the reader's own typing
    // or cursor movement and after explicit jumps (the only things that bump the sequence),
    // only when the cursor sits outside the visible band, never mid-drag or mid-fling, and
    // never twice for the same place. Analysis finishing, layouts changing, popups opening
    // and the keyboard animating cannot retrigger it, because none of them is a key.
    LaunchedEffect(followSeq) {
        if (followSeq == 0) return@LaunchedEffect
        if (codeScroll.isScrollInProgress) {
            ScrollLog.record("v", codeScroll.value, codeScroll.value, "follow-skipped-dragging")
            return@LaunchedEffect
        }
        val margin = with(autoDensity) { 24.dp.toPx() }
        val rowTop = rowTopPx(caretLine, lineHeightPx, topPaddingPx)
        val rowBottom = rowTop + lineHeightPx
        val maxScroll = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
        // Scroll forensics, temporary: every call logs the cursor line's bottom, the visible
        // area's bottom, the keyboard and key row heights, and whether it scrolled.
        val rowHeightPx = with(autoDensity) { EditorKeyRowHeight.toPx() }
        fun forensics(scrolled: Boolean) =
            "follow-cursor cursorBottom=${rowBottom.toInt()} " +
                "visibleBottom=${(codeScroll.value + viewportHeightPx).toInt()} " +
                "kb=$imeBottomPxEditor row=${if (showToolRow) rowHeightPx.toInt() else 0} " +
                "scrolled=$scrolled"
        val target = when {
            rowTop - margin < codeScroll.value -> (rowTop - margin).coerceAtLeast(0f)
            rowBottom + margin > codeScroll.value + viewportHeightPx ->
                (rowBottom + margin - viewportHeightPx).coerceIn(0f, maxScroll)
            else -> {
                ScrollLog.record("v", codeScroll.value, codeScroll.value, forensics(false))
                return@LaunchedEffect
            }
        }
        val to = target.toInt().coerceAtLeast(0)
        if (to == codeScroll.value) {
            ScrollLog.record("v", codeScroll.value, to, forensics(false))
            return@LaunchedEffect
        }
        selfScrolling = true
        try {
            ScrollLog.record("v", codeScroll.value, to, forensics(true))
            codeScroll.animateScrollTo(to)
        } finally {
            selfScrolling = false
        }
    }

    // The same job sideways. Compose will not scroll a BasicTextField horizontally either, so the
    // caret can walk off the right of a long line while the user is still typing on it.
    //
    // The margin is what keeps this from being a nuisance: inside the band the answer is "stay",
    // so typing in the middle of a line that is already on screen costs nothing, and only a caret
    // that has reached one of the two edges moves the view — just far enough to put the margin
    // back between the caret and the edge. A big jump, which is what a tap at the far end of a long
    // line produces, is animated so the eye can follow where it went; the small corrections typing
    // makes are applied straight away, because an animation restarted on every keystroke never
    // settles and the text ends up swimming.
    //
    // A follow only runs once the measured layout belongs to the current window and contains the
    // caret. Tapping a line opens the keyboard at the same moment the follow sequence changes, so
    // the first pass can still see the previous slice; waiting for that layout to land keeps the
    // caret's real x instead of clamping it to the end of an old window and scrolling the code away.
    LaunchedEffect(
        followSeq,
        codeLayout,
        codeLayoutWindowStart,
        codeLayoutWindowEnd,
        codeWindow.startOffset,
        codeWidthPx,
    ) {
        if (followSeq == 0 || followSeq == handledHFollowSeq) return@LaunchedEffect
        if (codeHScroll.isScrollInProgress) {
            ScrollLog.record("h", codeHScroll.value, codeHScroll.value, "follow-skipped-dragging")
            return@LaunchedEffect
        }
        if (codeWidthPx <= 0f) return@LaunchedEffect
        val layout = codeLayout ?: return@LaunchedEffect
        if (codeLayoutWindowStart != codeWindow.startOffset) return@LaunchedEffect
        if (cursor !in codeLayoutWindowStart until codeLayoutWindowEnd) return@LaunchedEffect
        handledHFollowSeq = followSeq
        val caretContent = caretX(layout, cursor, codeWindow) ?: return@LaunchedEffect
        val margin = with(autoDensity) { CaretHScrollMargin.toPx() }
        val now = codeHScroll.value.toFloat()
        val wanted = targetHScroll(caretContent, now, codeWidthPx, margin)
        if (wanted == now) return@LaunchedEffect
        selfScrolling = true
        try {
            if (abs(wanted - now) > codeWidthPx / 2f) {
                ScrollLog.record("h", now.toInt(), wanted.roundToInt(), "follow-cursor-far")
                codeHScroll.animateScrollTo(wanted.roundToInt())
            } else {
                ScrollLog.record("h", now.toInt(), wanted.roundToInt(), "follow-cursor-near")
                codeHScroll.scrollTo(wanted.roundToInt())
            }
        } finally {
            selfScrolling = false
        }
    }

    // The tapped line stays visible when the keyboard opens. At tap time the layout still shows
    // the pre-keyboard size, so this waits for the settled one: the measured code height after
    // the keyboard (and key row) animation, debounced past the last frame of it. Keyed on both
    // the live keyboard flag and the height — a flow keyed on Unit froze the flag at its first
    // value and the pass never ran. Then, if the cursor line is not fully visible in the area
    // above the keys, the view moves by the smallest amount that shows it whole with one extra
    // line of space below, over 200 ms — and not at all when the line is already fully visible,
    // or when the keyboard is closing. Tapping while open and typing on the last visible line
    // land here the same way, through the height they change. Opening the keyboard by tapping
    // is the reader's own action, so this scroll is allowed under the follow rule. The
    // last-seen pair lives outside the effect so restarts compare against the previous run,
    // not the first composition.
    var lastHeightSeen by remember { mutableStateOf(viewportHeightPx) }
    var lastImeSeen by remember { mutableStateOf(imeOpen) }
    LaunchedEffect(imeOpen, viewportHeightPx) {
        // Coalesce the animation frames: each new height restarts the wait, so the check runs
        // once, 50 ms after the last frame, against the final layout.
        kotlinx.coroutines.delay(50)
        val height = viewportHeightPx
        val ime = imeOpen
        val opened = ime && !lastImeSeen
        val resizedOpen = ime && height != lastHeightSeen
        lastHeightSeen = height
        lastImeSeen = ime
        if (!opened && !resizedOpen) return@LaunchedEffect
                if (codeScroll.isScrollInProgress) {
                    ScrollLog.record(
                        "v",
                        codeScroll.value,
                        codeScroll.value,
                        "keyboard-settle-skipped-dragging",
                    )
                    return@LaunchedEffect
                }
                val viewTop = codeScroll.value.toFloat()
                val viewBottom = viewTop + height.toFloat()
                val rowTop = rowTopPx(caretLine, lineHeightPx, topPaddingPx)
                val rowBottom = rowTop + lineHeightPx
                val maxScroll = (contentHeightPx - height.toFloat()).coerceAtLeast(0f)
                val rowHeightPx = with(autoDensity) { EditorKeyRowHeight.toPx() }
                fun forensics(scrolled: Boolean) =
                    "keyboard-settle cursorBottom=${rowBottom.toInt()} " +
                        "visibleBottom=${viewBottom.toInt()} kb=$imeBottomPxEditor " +
                        "row=${if (showToolRow) rowHeightPx.toInt() else 0} scrolled=$scrolled"
                val target = when {
                    rowTop < viewTop -> (rowTop - lineHeightPx).coerceAtLeast(0f)
                    rowBottom + lineHeightPx > viewBottom ->
                        (rowBottom + lineHeightPx - height.toFloat()).coerceIn(0f, maxScroll)
                    else -> {
                        ScrollLog.record("v", codeScroll.value, codeScroll.value, forensics(false))
                        return@LaunchedEffect
                    }
                }
                val to = target.toInt().coerceAtLeast(0)
                if (to == codeScroll.value) {
                    ScrollLog.record("v", codeScroll.value, to, forensics(false))
                    return@LaunchedEffect
                }
                selfScrolling = true
                try {
                    ScrollLog.record("v", codeScroll.value, to, forensics(true))
                    codeScroll.animateScrollTo(to, animationSpec = scrollMotion)
                } finally {
                    selfScrolling = false
                }
    }

val applySuggestion: (Int) -> Unit = { index ->
        val live = fieldValue
        val liveText = live.text
        val liveCursor = live.selection.start.coerceIn(0, liveText.length)
        val liveItems = kotlinItems
        val insert = liveItems?.getOrNull(index)?.insertText
            ?: visibleSuggestions.getOrNull(index)?.name
        if (insert != null) {
            // The prefix is read from the analysis source, so the caret crosses into source
            // coordinates first; its length applies back in display coordinates because the
            // caret's own line reads identically in both.
            val sourceCursor = foldMap.sourceAt(liveCursor)
            val prefix = if (liveItems != null) currentAnalysis?.prefixAt(sourceCursor).orEmpty() else ""
            val start = (liveCursor - prefix.length).coerceAtLeast(0)
            val item = liveItems?.getOrNull(index)
            if (item != null) {
                val sourceStart = (sourceCursor - prefix.length).coerceAtLeast(0)
                var next = sourceText.substring(0, sourceStart) + insert +
                    sourceText.substring(sourceCursor)
                var caret = sourceStart + insert.length
                if (settings.autoImport &&
                    (item.kind == CompletionKind.CLASS || item.kind == CompletionKind.STDLIB_CLASS)
                ) {
                    val fqn = item.subtitle?.let { subtitle ->
                        if (subtitle.endsWith(".${item.label}")) subtitle
                        else if (subtitle.isNotBlank()) "$subtitle.${item.label}"
                        else null
                    }
                    if (fqn != null && fqn.contains('.')) {
                        val before = next
                        val imported = SourceEdits.addImport(next, fqn)
                        if (imported != before) {
                            next = imported
                            caret = SourceEdits.caretAfterAddImport(before, fqn) ?: caret
                        }
                    }
                }
                commitSource(next, caret.coerceIn(0, next.length))
            } else {
                val next = liveText.substring(0, start) + insert + liveText.substring(liveCursor)
                commit(TextFieldValue(next, TextRange(start + insert.length)))
            }
            visibleSuggestions.getOrNull(index)?.name?.let { noteSuggestionAccepted(it) }
        }
    }

    /** Types [insert] at the caret and leaves the caret after it. */
    fun typeAtCaret(insert: String) {
        val live = fieldValue
        val at = live.selection.start.coerceIn(0, live.text.length)
        val next = live.text.substring(0, at) + insert + live.text.substring(at)
        commit(TextFieldValue(next, TextRange(at + insert.length)))
    }

    /** Moves the caret one line up or down, keeping the column where the line is long enough. */
    fun moveCaretVertically(delta: Int) {
        val live = fieldValue
        val text = live.text
        val at = live.selection.start.coerceIn(0, text.length)
        val (line, column) = caretPosition(text, at)
        // The caret can reach its own row even when that row is still blank — that is exactly what
        // pressing Enter produces — but never a blank row below it, which would read as a bug.
        val lastVisible = gutterRowCount(text.lines(), caretLine = line)
        val target = (line + delta).coerceIn(1, lastVisible)
        if (target == line) return
        val lineStart = offsetOfLineColumn(text, target, 1)
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val caret = (lineStart + column - 1).coerceIn(lineStart, lineEnd)
        commit(TextFieldValue(text, TextRange(caret)))
    }

    /**
 * Re-indents only the lines the reader has selected, and says so when there is nothing to do.
 *
 * Separate from the whole-file formatter because that one rebuilds the file from brace depth: run on
 * part of a file it would re-indent every line outside the selection too, which is the opposite of
 * what selecting three lines asked for.
 */
    fun formatSelection() {
        val live = fieldValue
        val from = live.selection.min
        val to = live.selection.max
        if (from >= to) {
            onToast("Select the lines to format")
            return
        }
        // Whole lines belong to the selection; a partial line belongs to the file. Re-indenting
        // half a line would move code the reader did not select. The whole file is formatted
        // from source: folded bodies still count braces, and the display text cannot see them.
        if (!SourceEdits.selectionCoversWholeLines(live.text, from, to)) {
            val sourceCaret = foldMap.toSource[cursor.coerceIn(0, code.length)]
            val formatted = if (activeKind == FileKind.Kotlin || active.endsWith(".kt", true)) {
                SourceEdits.formatKotlin(sourceText)
            } else {
                SourceEdits.normalizeTrailingWhitespace(sourceText)
            }
            if (formatted == sourceText) {
                onToast("Already formatted")
                return
            }
            val (line, column) = caretPosition(sourceText, sourceCaret)
            val caret = offsetOfLineColumn(formatted, line, column).coerceIn(0, formatted.length)
            commitSource(formatted, caret)
            onToast("Formatted $active")
            return
        }
        val formatted = SourceEdits.formatRange(live.text, from, to, settings.tabSize)
        if (formatted == live.text) {
            onToast("Already formatted")
            return
        }
        commit(TextFieldValue(formatted, TextRange(from)))
        onToast("Formatted ${to - from} chars")
    }

    /** Copies the selection, or the caret's whole line when nothing is selected. */
    fun copySelectionOrLine() {
        val live = fieldValue
        val text = live.text
        val start = live.selection.min.coerceIn(0, text.length)
        val end = live.selection.max.coerceIn(0, text.length)
        val payload = if (end > start) {
            text.substring(start, end)
        } else {
            val (line, _) = caretPosition(text, start)
            text.substring(offsetOfLineColumn(text, line, 1)).substringBefore('\n')
        }
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(payload))
        onToast("Copied")
    }

    val onToolAction: (ToolAction) -> Unit = { action ->
        val live = fieldValue
        val liveText = live.text
        val at = live.selection.start.coerceIn(0, liveText.length)
        val (liveLine, _) = caretPosition(liveText, at)
        when (action) {
            // Spec 6.8: Tab accepts the first suggestion when the popup is open, else indents.
            ToolAction.TAB -> {
                if (visibleSuggestions.isNotEmpty()) {
                    applySuggestion(0)
                } else {
                    typeAtCaret(" ".repeat(settings.tabSize.coerceAtLeast(1)))
                }
            }

            ToolAction.COMMENT -> {
                val next = SourceEdits.toggleLineComment(liveText, liveLine)
                if (next != liveText) {
                    commit(TextFieldValue(next, TextRange(at.coerceAtMost(next.length))))
                }
            }

            ToolAction.LINE_UP -> moveCaretVertically(-1)
            ToolAction.LINE_DOWN -> moveCaretVertically(1)
            ToolAction.COPY -> copySelectionOrLine()

            // The Problems tab was removed, so the triangle opens the problem sheet instead.
            ToolAction.PROBLEMS -> when {
                // Say what is actually true rather than "code is clean", which would be a lie
                // told about a file whose problems are simply not being drawn.
                !settings.diagnostics -> onToast("Diagnostics are off in Settings")
                problems.all.isNotEmpty() -> openProblemSheet(problems.all.first().line)
                else -> onToast("No problems. Code is clean.")
            }

            else -> ToolRowInserts[action]?.let { typeAtCaret(it) }
        }
    }

    // Header and palette format requests land here because only the editor owns the text field.
    // Formatting keeps the same line and column when possible; the line count never changes.
    LaunchedEffect(formatSelectionRequest) {
        if (formatSelectionRequest != 0) formatSelection()
    }

    LaunchedEffect(formatRequest) {
        if (formatRequest == 0) return@LaunchedEffect
        // Whole-file formatting reads the source: the display text is missing folded bodies,
        // and re-indenting from it would stack every line under a fold one level too deep.
        val sourceCaret = foldMap.toSource[cursor.coerceIn(0, code.length)]
        val formatted = if (activeKind == FileKind.Kotlin || active.endsWith(".kt", true)) {
            SourceEdits.formatKotlin(sourceText)
        } else {
            SourceEdits.normalizeTrailingWhitespace(sourceText)
        }
        if (formatted == sourceText) {
            onToast("Already formatted")
            return@LaunchedEffect
        }
        val (line, column) = caretPosition(sourceText, sourceCaret)
        val caret = offsetOfLineColumn(formatted, line, column).coerceIn(0, formatted.length)
        commitSource(formatted, caret)
        onToast("Formatted $active")
    }

    Column(
        modifier
            .fillMaxHeight()
            .background(c.editor)
    ) {
        // The declaration the top of the window is inside, pinned above the code. It appears only
        // once its own opening line has scrolled away, and disappears again when it comes back.
        if (scopeHeader != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.panel2)
                    .padding(start = columnStart, end = 12.dp)
                    .height(SCOPE_HEADER_HEIGHT)
                    .drawBehind {
                        drawLine(
                            color = c.ln,
                            start = Offset(0f, size.height - 0.5f),
                            end = Offset(size.width, size.height - 0.5f),
                            strokeWidth = 1f,
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = scopeHeader,
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // File chips. The active file is a blurple pill; the rest are quiet grey pills.
        Row(
            Modifier
                .fillMaxWidth()
                .height(TAB_STRIP_HEIGHT)
                .background(c.editorBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawWithCache {
                        val fadeWidth = minOf(TAB_EDGE_FADE_WIDTH.toPx(), size.width)
                        val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to c.editorBg.copy(alpha = 0f),
                            1f to c.editorBg,
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = brush,
                                topLeft = androidx.compose.ui.geometry.Offset(size.width - fadeWidth, 0f),
                                size = androidx.compose.ui.geometry.Size(fadeWidth, size.height),
                            )
                        }
                    },
                state = tabListState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(tabs, key = { _, t -> "tab-${t.path}" }) { _, tab ->
                    val isActive = tab.path == active
                    val dirty = dirtyOf(tab.path)
                    val duplicates = tabs.count { it.name == tab.name } > 1
                    val tabLabel = if (duplicates) {
                        tab.path.substringBeforeLast('/').substringAfterLast('/')
                            .takeIf { it.isNotEmpty() }?.let { "$it/${tab.name}" } ?: tab.name
                    } else {
                        tab.name
                    }
                    Box {
                        Row(
                            Modifier
                                .height(TAB_HEIGHT)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (isActive) c.mock.accent else c.mock.field)
                                .combinedClickable(
                                    onClick = { selectTabKeepingPlace(tab.path) },
                                    onLongClick = { tabMenuFor = tab.path },
                                )
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (dirty) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) Color.White else c.mock.warning,
                                        ),
                                )
                            }
                            Text(
                                text = ellipsizeMiddle(tabLabel),
                                color = if (isActive) Color.White else c.mock.text2,
                                fontSize = 12.5.sp,
                                fontFamily = Sans,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = tabMenuFor == tab.path,
                            onDismissRequest = { tabMenuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Close", fontFamily = Sans) },
                                onClick = {
                                    tabMenuFor = null
                                    forgetPlace(tab.path)
                                    onCloseTab(tab.path)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Close others", fontFamily = Sans) },
                                onClick = {
                                    tabMenuFor = null
                                    tabs.forEach { forgetPlace(it.path) }
                                    onCloseOthers(tab.path)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Close all", fontFamily = Sans) },
                                onClick = {
                                    tabMenuFor = null
                                    tabs.forEach { forgetPlace(it.path) }
                                    onCloseAll()
                                },
                            )
                        }
                    }
                }
            }
        }
        val popupDensity = androidx.compose.ui.platform.LocalDensity.current
        // The signature tooltip is computed here, above the pill, because the pill
        // hides while the tooltip is open: reading the tooltip's answer after the
        // pill would draw both on the same frame.
        val caretTopContentEarly = lineTopPx(codeLayout, code, caretLine, codeWindow)
            ?: pillTopPx(caretLine, lineHeightPx)
        val popupEdgePxEarly = with(popupDensity) { POPUP_EDGE_INSET.toPx() }
        val signatureOptions = remember(code, cursor, settings.parameterHints) {
            if (!settings.parameterHints) emptyList()
            else signatureHelpAll(code, cursor, AndroidApiAssets.cached())
        }
        var sigChoice by remember(code, cursor) { mutableIntStateOf(0) }
        val signatureInfo = signatureOptions.getOrNull(
            sigChoice.coerceIn(0, (signatureOptions.size - 1).coerceAtLeast(0)),
        )
        val sigWidthPx = with(popupDensity) { 272.dp.toPx() }
        val sigGapPx = with(popupDensity) { 4.dp.toPx() }
        val sigLeft = with(popupDensity) { 62.dp.toPx() }
            .coerceIn(
                with(popupDensity) { 8.dp.toPx() },
                (viewportWidthPx - sigWidthPx - with(popupDensity) { 8.dp.toPx() })
                    .coerceAtLeast(with(popupDensity) { 8.dp.toPx() }),
            )
        val signatureHeightPx = with(popupDensity) {
            (SignatureRowHeight + SignatureDescriptionHeight + SignatureBoxVerticalPadding * 2)
                .toPx()
        }
        // Everything below this line reads the scroll position, and a scroll read in
        // composition subscribes this whole scope to every pixel of movement — which used to
        // recompose the entire editor on every frame of a fling. Gated on a signature actually
        // showing: while it is null none of this runs and scrolling pays nothing; open, the
        // tooltip tracks the caret as it should.
        val (signatureShownTop, signatureBelowTop) =
            if (signatureInfo == null) {
                null to null
            } else {
                val caretTopViewport = topPaddingPx + caretTopContentEarly - codeScroll.value
                val above = signatureTopPx(
                    caretTopPx = caretTopViewport,
                    tooltipHeightPx = signatureHeightPx,
                    gapPx = sigGapPx,
                    viewportHeightPx = viewportHeightPx,
                    edgePx = popupEdgePxEarly,
                )
                if (above != null) {
                    above to null
                } else {
                    val below = tooltipBelowTopPx(
                        caretTopPx = caretTopViewport,
                        lineHeightPx = lineHeightPx,
                        gapPx = sigGapPx,
                        tooltipHeightPx = signatureHeightPx,
                        viewportHeightPx = viewportHeightPx,
                        edgePx = popupEdgePxEarly,
                    )
                    below to below
                }
            }
        var sigMeasuredH by remember { mutableStateOf(0f) }
        val caretRowOnScreenEarly = if (signatureInfo == null) {
            true
        } else {
            val caretTopViewport = topPaddingPx + caretTopContentEarly - codeScroll.value
            caretTopViewport + lineHeightPx > 0f && caretTopViewport < viewportHeightPx
        }
        // Back dismisses the tooltip without moving the caret: any edit lifts the
        // suppression on its own, because the code or the cursor it was recorded
        // against is gone.
        var sigSuppressedAt by remember { mutableStateOf<Pair<String, Int>?>(null) }
        if (sigSuppressedAt != null && sigSuppressedAt != (code to cursor)) {
            sigSuppressedAt = null
        }
        val sigVisible = signatureInfo != null &&
            signatureShownTop != null &&
            caretRowOnScreenEarly &&
            quickFixLine == null &&
            fieldValue.selection.max == fieldValue.selection.min &&
            problemSheetLine.value == null &&
            sigSuppressedAt == null
        // Inside the editor, Back walks down its own stack first: suggestions, tooltip,
        // selection collapse, quick fix. Composed deeper than the screen chain, so it wins
        // while armed and the screen never sees the press.
        val suggestionsVisible = visibleSuggestions.isNotEmpty()
        val hasSelection = fieldValue.selection.max > fieldValue.selection.min
        BackHandler(
            enabled = suggestionsVisible || sigVisible || hasSelection || quickFixLine != null ||
                tabMenuFor != null,
        ) {
            when {
                suggestionsVisible -> {
                    BackLog.press("editor", "branch=suggestions")
                    suggestSuppressedAt = code to cursor
                }
                sigVisible -> {
                    BackLog.press("editor", "branch=signature")
                    sigSuppressedAt = code to cursor
                }
                hasSelection -> {
                    BackLog.press("editor", "branch=selection")
                    commit(
                        fieldValue.copy(selection = TextRange(fieldValue.selection.start)),
                    )
                }
                quickFixLine != null -> {
                    BackLog.press("editor", "branch=quickfix")
                    quickFixLine = null
                }
                else -> {
                    BackLog.press("editor", "branch=tabmenu")
                    tabMenuFor = null
                }
            }
        }

        // Milliseconds per frame over the code, for the scroll report. Off unless the Frame
        // stats toggle in Settings says otherwise; while on, a frame clock keeps the last 120
        // frames and reads out average, 95th percentile and worst, plus how many blew the 32 ms
        // budget — updated every 30 frames so the readout itself costs nothing worth measuring.
        if (settings.frameStats) {
            var frameStats by remember { mutableStateOf(Triple(0.0, 0.0, 0.0)) }
            var slowFrames by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                val ring = DoubleArray(120)
                var filled = 0
                var cursor = 0
                var last = withFrameNanos { it }
                var n = 0
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = (now - last) / 1_000_000.0
                    last = now
                    ring[cursor] = dt
                    cursor = (cursor + 1) % ring.size
                    if (filled < ring.size) filled++
                    if (dt > 32.0) slowFrames++
                    n++
                    if (n >= 30) {
                        val sorted = ring.take(filled).sorted()
                        val avg = sorted.average()
                        val p95 = sorted[((sorted.size * 0.95).toInt()).coerceIn(0, sorted.size - 1)]
                        frameStats = Triple(avg, p95, sorted.last())
                        n = 0
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.panel2)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "%.1f avg · %.1f p95 · %.1f max".format(
                        frameStats.first,
                        frameStats.second,
                        frameStats.third,
                    ),
                    color = c.brightText,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
                Text(
                    text = "${codeRange.last - codeRange.first + 1} lines · " +
                        "${lineIndex.lineCount} total · $slowFrames > 32 ms",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .onSizeChanged {
                    viewportWidthPx = it.width.toFloat()
                    viewportHeightPx = it.height.toFloat()
                },
        ) {
            // One vertical scroll for the whole editor. The line numbers and the code are both
            // children of this box, so they share a coordinate space and cannot drift apart.
            // A press can land between characters, so the offset settles onto whole
            // characters when the finger leaves: the spec clips the code exactly at the gutter
            // edge, and a half-character offset would put half a glyph under that clip.
            fun snapHScroll() {
                val w = charWidthPx(codeLayout) ?: return
                val target = snapScrollToCharacter(codeHScroll.value.toFloat(), w)
                if (target == codeHScroll.value) return
                selfScrolling = true
                editScope.launch {
                    try {
                        ScrollLog.record("h", codeHScroll.value, target, "snap-char")
                        codeHScroll.scrollTo(target)
                    } finally {
                        selfScrolling = false
                    }
                }
            }
            LaunchedEffect(codeHScroll.isScrollInProgress) {
                // Flings and programmatic scrolls end here; finger pans report through onRelease
                // below, because raw deltas never mark the state in progress.
                if (!codeHScroll.isScrollInProgress) snapHScroll()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(codeScroll)
                    // Spec 6.3: the one top padding the editor has. The gutter is a sibling inside
                    // this same container and adds nothing of its own, so a number, its dot and its
                    // code line always share one top edge and one row height.
                    .padding(top = EditorTopPadding, bottom = 40.dp),
            ) {
                // The cursor row, full width from x = 0 including the gutter. Drawn first so it
                // sits behind everything; the gutter paints its own 56 dp of the same row over it,
                // which is what makes the band read as one.
                val caretRowTop =
                    lineTopPx(codeLayout, code, caretLine, codeWindow)
                        ?: ((caretLine - 1).coerceAtLeast(0) * lineHeightPx)
                Box(
                    Modifier
                        .offset { IntOffset(0, (topPaddingPx + caretRowTop).roundToInt()) }
                        .fillMaxWidth()
                        .height(with(autoDensity) { lineHeightPx.toDp() })
                        .background(c.activeLineBg),
                )
                Row(
                    Modifier
                        .horizontalScroll(codeHScroll)
                        .panOnHorizontalDrag(
                            state = codeHScroll,
                            flingBehavior = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
                            scope = editScope,
                            slopPx = with(autoDensity) { 8.dp.toPx() },
                            onPanActive = { hPanActive = it },
                            onRelease = ::snapHScroll,
                        )
                        // The code is clipped exactly at the gutter edge, so it never shows under
                        // the gutter or gets cut halfway through a character at the left edge.
                        .drawWithContent {
                            clipRect(
                                left = with(autoDensity) { GutterWidth.toPx() },
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) {
                    // The renderer only lays out the slice that can be on screen now, so the text is
                    // no longer what makes this column tall — the row model is. Pinning it here is
                    // what keeps the column, the gutter and the scroller agreeing on where the file
                    // ends, and it is the same number the caret's scroll maths clamps against.
                    val codeColumnHeight = with(autoDensity) {
                        (codeLines.size * lineHeightPx).toDp()
                    }
                    Box(
                        Modifier
                            // The code area is at least as wide as the visible column, so a tap on
                            // the empty space to the right of a short line still reaches the text
                            // field and focuses the editor.
                            .width(
                                with(androidx.compose.ui.platform.LocalDensity.current) {
                                    maxOf(
                                        codeWidthPx,
                                        codeContentWidthPx +
                                            columnStart.toPx() +
                                            CodeLeftInset.toPx(),
                                    ).toDp()
                                },
                            )
                            .height(codeColumnHeight)
                            // Spec 6.3: the code column starts one inset past the gutter divider,
                            // and every overlay inside it reads x from that same origin.
                            .padding(
                                start = columnStart,
                                end = CodeLeftInset,
                            ),
                    ) {
                        // Drawn first, so every rule sits under the glyphs rather than across
                        // them. The two ends come from the renderer; the column is arithmetic over
                        // the file's own indents, times one measured character width.
                        BracketGuideLayer(
                            pairs = braceGuides,
                            source = code,
                            // One pass over the buffer, remembered with it, so changing the tab
                            // size is the only thing that re-reads it.
                            indents = indentColumns,
                            tabSize = settings.tabSize,
                            charWidthPx = charWidthPx(codeLayout) ?: 0f,
                            layout = codeLayout,
                            lineHeightPx = lineHeightPx,
                            window = codeWindow,
                            lineIndex = lineIndex,
                            modifier = Modifier.matchParentSize(),
                        )
                        // A line under each of the active pair's braces, drawn after the guides so
                        // it sits on top of the glyph rather than under it — plus a red one under a
                        // lone bracket, so an unclosed opener says so where it stands.
                        MatchingBraceLayer(
                            active = activeBracketPair,
                            layout = codeLayout,
                            window = codeWindow,
                            modifier = Modifier.matchParentSize(),
                            unmatched = unmatchedBracket,
                        )
                        // One grey line where the file's first line would be, for a buffer with
                        // nothing in it — a new file, a file we cannot read, or the editor with no
                        // tab open at all. It is drawn, never typed: the buffer stays empty, so a
                        // save that nobody touched writes nothing back.
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = "Empty file",
                                color = c.mu,
                                fontSize = codeFontSize,
                                style = androidx.compose.ui.text.TextStyle(
                                    lineHeight = codeLineHeightTextUnit,
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                                    ),
                                ),
                                fontFamily = Mono,
                                // Word wrap off means lines run long, not that lines 2..n vanish:
                                // only softWrap toggles. maxLines stays unbounded, because one
                                // line here is the whole visible slice and clipping it to a single
                                // row renders exactly one line of the file.
                                softWrap = settings.wordWrap,
                                maxLines = Int.MAX_VALUE,
                            )
                        }
                        Text(
                            text = highlighted,
                            color = c.tx,
                            fontSize = codeFontSize,
                            // Centring the glyphs in the fixed row is what lets the gutter number
                            // share the row's baseline.
                            style = androidx.compose.ui.text.TextStyle(
                                lineHeight = codeLineHeightTextUnit,
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                                ),
                            ),
                            fontFamily = Mono,
                            // Same as the field above: wrap off clips nothing, it only unwraps.
                            softWrap = settings.wordWrap,
                            maxLines = Int.MAX_VALUE,
                            // The slice is drawn at the absolute row it starts on, so every line in
                            // it lands on the same y it had when the whole file was one Text — and
                            // the transparent field, the gutter and the overlays all still agree.
                            modifier = Modifier.offset {
                                IntOffset(0, codeWindow.topPx.roundToInt())
                            },
                            // The layout comes from *this* Text, the one whose glyphs the user
                            // actually sees. Taking it from the transparent BasicTextField below
                            // gave a different line advance, which is what made the gutter rows,
                            // pills and bulb drift away from the code.
                            onTextLayout = {
                                codeLayout = it
                                codeContentWidthPx = it.size.width.toFloat()
                                codeLayoutWindowStart = codeWindow.startOffset
                                codeLayoutWindowEnd = codeWindow.startOffset + it.layoutInput.text.length
                            },
                        )
                        // The editor draws its own selection bar, so Android's own Copy/Paste
                        // menu is switched off at this field: two toolbars is one too many.
                        CompositionLocalProvider(LocalTextToolbar provides NoSystemTextToolbar) {
                        BasicTextField(
                            value = fieldValue,
                            onValueChange = { incoming ->
                                // Spec 6.3: Enter carries the indentation over. The IME's own key
                                // handling is left alone, so Enter still works with the popup open.
                                val caret = incoming.selection.start.coerceIn(0, incoming.text.length)
                                val indented = if (settings.autoIndent) {
                                    autoIndentAfterEnter(
                                        fieldValue.text,
                                        incoming.text,
                                        caret,
                                        settings.tabSize,
                                    )
                                } else {
                                    null
                                }
                                val adjusted = if (indented == null) {
                                    incoming
                                } else {
                                    TextFieldValue(indented.first, TextRange(indented.second))
                                }
                                // Pairing runs after Enter has had its say and only ever on a single
                                // typed character, so a paste or a multi-line edit is left alone.
                                val closed = if (settings.autoCloseBrackets) {
                                    autoClosePair(fieldValue.text, adjusted.text, adjusted.selection.start)
                                } else {
                                    null
                                }
                                // Typing `</` completes the tag that is still open, so the reader
                                // writes the first two characters of a closer and gets the rest.
                                // Runs after bracket pairing, which has no opinion about a slash.
                                val tagClosed = closeTagAt(fieldValue.text, adjusted.text, adjusted.selection.start)
                                commit(
                                    when {
                                        tagClosed != null ->
                                            TextFieldValue(tagClosed.first, TextRange(tagClosed.second))
                                        closed != null ->
                                            TextFieldValue(closed.first, TextRange(closed.second))
                                        else ->
                                            adjusted
                                    }
                                )
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.Transparent,
                                fontSize = codeFontSize,
                                // Same row metrics as the Text above, so the caret lands on the same
                                // row the glyphs are drawn on.
                                lineHeight = codeLineHeightTextUnit,
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                                ),
                                fontFamily = Mono,
                            ),
                            cursorBrush = SolidColor(c.ac),
                            // ImeAction.Default with no KeyboardActions, so Enter always inserts a
                            // newline even while the suggestion popup is open. Only Tab or tapping
                            // a row accepts a suggestion. No capitals or corrections: code must
                            // arrive as typed, like terminal commands.
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Default,
                            ),
                            modifier = Modifier
                                // Match the column that the visible Text measured, not the viewport.
                                // With word wrap off the Text is wider than the screen; a field
                                // pinned to the viewport would wrap its own copy and put every tap
                                // after the first screenful on the wrong character.
                                .matchParentSize()
                                .onSizeChanged { fieldWidthPx = it.width }
                                // Spec 4.1: the palette advertises Ctrl S, and the reference binds
                                // the hardware keys for save, undo and redo. Enter is owned here
                                // too, ahead of the IME, so one press can never produce two rows.
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when {
                                        event.isCtrlPressed && event.key == Key.S -> {
                                            onSaveRequest()
                                            true
                                        }

                                        event.isCtrlPressed && event.key == Key.Z -> {
                                            if (event.isShiftPressed) redoOnce() else undoOnce()
                                            true
                                        }

                                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                                            // Spec 6.3: one press, one row down. Own the newline so
                                            // the IME cannot hand back two of them for one key.
                                            val current = fieldValue
                                            val (next, caret) = enterEdit(
                                                current.text,
                                                minOf(current.selection.start, current.selection.end),
                                                maxOf(current.selection.start, current.selection.end),
                                                settings.tabSize,
                                                settings.autoIndent,
                                            )
                                            commit(TextFieldValue(next, TextRange(caret)))
                                            true
                                        }

                                        event.key == Key.Tab -> {
                                            onToolAction(ToolAction.TAB)
                                            true
                                        }

                                        else -> false
                                    }
                                },
                        )
                        }
                        // The error pill, when the tooltip is closed: one pill for the cursor
                        // line's own error, on the line above it — or below it when the line above
                        // is too long to hold it.
                        val pillError = problems.on(caretLine)
                            .firstOrNull { it.severity == Severity.ERROR }
                        if (pillError != null && !sigVisible) {
                            val charW = charWidthPx(codeLayout) ?: 0f
                            val pillGapPx = with(popupDensity) { 12.dp.toPx() }
                            // Icon, gap, 22 characters of message and padding: the width the pill
                            // needs before it may start.
                            val pillNeedPx = with(popupDensity) { 12.dp.toPx() } +
                                with(popupDensity) { 4.dp.toPx() } +
                                22 * charW +
                                with(popupDensity) { 14.dp.toPx() }
                            val viewportRightPx = codeHScroll.value + viewportWidthPx
                            val aboveLine = caretLine - 1
                            val aboveEnd = if (aboveLine >= 1) {
                                endOfLineX(codeLayout, code, aboveLine, codeWindow)
                            } else {
                                null
                            }
                            // Above when it fits, below when it does not, above anyway when there
                            // is no below: a pill off the right edge helps nobody, and the layer
                            // below would draw it there without a word.
                            val pillLine = if (
                                aboveEnd == null ||
                                aboveEnd + pillGapPx + pillNeedPx > viewportRightPx
                            ) {
                                caretLine + 1
                            } else {
                                aboveLine
                            }.takeIf { it in 1..codeLines.size } ?: aboveLine.coerceAtLeast(1)
                            val pillX = (endOfLineX(codeLayout, code, pillLine, codeWindow) ?: 0f) +
                                pillGapPx
                            val pillTop =
                                (lineTopPx(codeLayout, code, pillLine, codeWindow)
                                    ?: pillTopPx(pillLine, lineHeightPx)) -
                                    with(popupDensity) { 1.dp.toPx() }
                            InlineProblemLayer(
                                problems = ProblemsByLine.from(listOf(pillError)),
                                lineHeightPx = lineHeightPx,
                                gapPx = 0f,
                                anchorX = { pillX },
                                yOfLine = { pillTop + with(popupDensity) { 1.dp.toPx() } },
                                onTap = { diagnostic ->
                                    commit(
                                        TextFieldValue(
                                            code,
                                            TextRange(offsetOfLineColumn(code, diagnostic.line, diagnostic.column)),
                                        ),
                                    )
                                    openProblemSheet(diagnostic.line)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth(),
                            )
                        }
                        if (foldedDisplayRows.isNotEmpty()) {
                            // The ellipsis chips after folded first lines, in the same content
                            // coordinates as pills so they travel sideways with their rows.
                            val chipGapPx = with(popupDensity) { 6.dp.toPx() }
                            FoldChipLayer(
                                chips = foldedDisplayRows.sorted().mapNotNull { row ->
                                    foldMap.displayLines.getOrNull(row - 1)?.let { source ->
                                        FoldChip(displayLine = row, sourceLine = source)
                                    }
                                },
                                lineEndX = { row ->
                                    endOfLineX(codeLayout, code, row, codeWindow)
                                },
                                lineTop = { row ->
                                    lineTopPx(codeLayout, code, row, codeWindow)
                                },
                                gapPx = chipGapPx,
                                onTap = { sourceLine -> toggleFold(sourceLine) },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth(),
                            )
                        }
                        if (problems.byLine.isNotEmpty()) {
                            // Wavy lines under the exact bad characters. Drawn before the labels
                            // so the labels stay readable on top of them. Only the window's own
                            // diagnostics reach the draw scope: the filter runs once per row
                            // crossed, not once per frame drawn.
                            val visibleSquiggles = remember(problems, codeWindow) {
                                val sliceEnd = lineIndex.lineStart(codeRange.last + 2)
                                problems.all.filter { diagnostic ->
                                    diagnostic.offset >= codeWindow.startOffset &&
                                        diagnostic.offset <= sliceEnd
                                }
                            }
                            SquiggleLayer(
                                problems = visibleSquiggles,
                                layout = codeLayout,
                                window = codeWindow,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxSize(),
                            )
                        }
                        }
            }

                // Drawn last with an opaque background: code scrolled sideways passes underneath.
                // The marks compare the live buffer to HEAD, so they include unsaved edits, and are
                // keyed by source line — the gutter looks them up by the number it draws, which is
                // why folding never moves a mark onto the wrong row. Recomputed only when the file
                // or its text changes, never on scroll: the layer reads the scroll itself.
                // JGit opens the repository, reads HEAD and diffs the whole buffer. Doing that in
                // composition on every keystroke was enough work to stall the editor. Wait for a
                // short pause in typing, then run it off the main thread; a new keystroke cancels
                // the old job before it starts.
                val gitMarks by produceState<Map<Int, GitMark>>(
                    initialValue = emptyMap(),
                    relativePath,
                    sourceText,
                    projectRoot,
                ) {
                    val path = relativePath
                    val root = projectRoot
                    if (path == null || root == null) {
                        value = emptyMap()
                        return@produceState
                    }
                    delay(180)
                    value = withContext(Dispatchers.Default) {
                        com.closecode.ui.git.GitService(root).bufferMarks(path, sourceText)
                            .getOrElse { emptyMap() }
                            .mapValues { (_, kind) -> GitMark.valueOf(kind.name) }
                    }
                }
                if (settings.lineNumbers) {
                    LineNumberLayer(
                        lineCount = gutterRowCount(codeLines, caretLine),
                        lineHeightPx = lineHeightPx,
                        problems = problems,
                        caretLine = caretLine,
                        gitMarks = emptyMap(),
                        sourceLines = foldMap.displayLines,
                        // Rows come from the layout that drew the glyphs, not from a second row model.
                        layout = codeLayout,
                        // ...and from the slice that layout describes, so a number stays on its own
                        // row even though the text is no longer one Text for the whole file.
                        window = codeWindow,
                        foldLine = 0,
                        foldableLines = foldableDisplayRows,
                        foldedLines = foldedDisplayRows,
                        onFoldTap = { displayRow ->
                            foldMap.displayLines.getOrNull(displayRow - 1)?.let { toggleFold(it) }
                        },
                        // The bulb is drawn by the gutter now, so it needs no layout of its own and
                        // cannot land on top of the caret line's text. It appears for the caret line
                        // whenever that line has something to offer to fix and no card is open.
                        bulbLine = if (
                            settings.quickFixes &&
                            problems.on(caretLine).isNotEmpty() && quickFixLine == null
                        ) {
                            caretLine
                        } else {
                            null
                        },
                        onBulbTap = { openQuickFix(caretLine) },
                        // Only the rows that can be on screen are composed. The scroll is read inside
                        // the layer, so scrolling it does not recompose the editor around it.
                        scrollState = codeScroll,
                        viewportHeightPx = viewportHeightPx,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }

            val menuDensity = androidx.compose.ui.platform.LocalDensity.current
            val menuGapPx = with(menuDensity) { 6.dp.toPx() }
            val menuOffsetX = with(menuDensity) { columnStart.toPx() }
            androidx.compose.animation.AnimatedVisibility(
                visible = quickFixLine != null && quickFixActions.isNotEmpty(),
                enter = fadeIn(motion(120)),
                exit = fadeOut(motion(120)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val line = quickFixLine ?: 1
                        // The card lives in the viewport, the layout in the scrolled content, so the
                        // scroll offset has to come off before the card can sit on its line.
                        val contentTop = lineTopPx(codeLayout, code, line, codeWindow)
                            ?: pillTopPx(line, lineHeightPx)
                        val top = topPaddingPx + contentTop - codeScroll.value
                        androidx.compose.ui.unit.IntOffset(
                            menuOffsetX.toInt(),
                            (top + lineHeightPx + menuGapPx).toInt(),
                        )
                    }
                    .padding(end = 12.dp),
            ) {
                IdeActionCard(
                    rows = quickFixActions.map { it.label to null },
                    selected = quickFixIndex,
                    onSelect = { index ->
                        quickFixIndex = index
                        quickFixActions.getOrNull(index)?.let { action ->
                            applyQuickFix(action).let { applied ->
                                if (applied) {
                                    quickFixLine = null
                                    problemSheetLine.value = null
                                }
                            }
                        }
                    },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = problemSheetLine.value != null,
                enter = slideInVertically(motion(160)) { it } + fadeIn(motion(160)),
                exit = slideOutVertically(motion(160)) { it } + fadeOut(motion(160)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            ) {
                ProblemSheet(
                    // Either the open file's own problems, or every one in the project — which is
                    // what a reader who was just blocked from running needs to see.
                    problems = if (problemSheetScope == ProblemScope.PROJECT) {
                        projectDiagnostics
                    } else {
                        problems.all.map { ProjectDiagnostic(activeName, it) }
                    },
                    scope = problemSheetScope,
                    fileLabel = activeName,
                    onSelect = { row ->
                        // Tapping a row in project scope opens the file it came from, then puts the
                        // caret on its line. Jumping to a line number in a file that is not open
                        // would be a dead end.
                        // A row from another file has to open that file first: jumping to a line
                        // number in a file that is not open would be a dead end. Diagnostics are
                        // keyed by name, so twins resolve to the first match.
                        if (row.fileName != activeName) onOpenProjectFile(row.fileName)
                        val offset = offsetOfLineColumn(code, row.diagnostic.line, row.diagnostic.column)
                        commit(TextFieldValue(code, TextRange(offset)))
                        problemSheetLine.value = null
                    },
                    onDismiss = { problemSheetLine.value = null },
                    onAskAi = { row ->
                        problemSheetLine.value = null
                        onAskDiagnostic(row.fileName, row.diagnostic.line, row.diagnostic.message)
                    },
                )
            }

            // Anchored on the rendered caret line, never on an assumed row height. The popup sits on the row
            // below the line being typed so that line stays readable while typing, and flips above it
            // when the keyboard leaves no room below. It never overlaps the caret line or its pill.
            val popupGapPx = with(popupDensity) { POPUP_GAP.toPx() }
            val popupRowHeightPx = with(popupDensity) { POPUP_ROW_HEIGHT.toPx() }
            val popupBorderPx = with(popupDensity) { 1.dp.toPx() }
            val popupEdgePx = with(popupDensity) { POPUP_EDGE_INSET.toPx() }
            val popupWidthPx = popupWidthPx(
                maxWidthPx = with(popupDensity) { POPUP_MAX_WIDTH.toPx() },
                viewportWidthPx = viewportWidthPx,
                gutterWidthPx = gutterWidthPx,
                insetPx = popupEdgePx,
            ).coerceAtLeast(with(popupDensity) { 120.dp.toPx() })
            val shownSuggestions = visibleSuggestions.take(MAX_VISIBLE_SUGGESTIONS)
            // The placement reads both scroll positions, and a scroll read in composition
            // subscribes this whole scope to every pixel of movement — the same per-frame
            // recompose the tooltip had. Gated on the popup existing: empty 99% of the time,
            // scrolling pays nothing for it; open, it tracks like everything else.
            // Triple: caret top in viewport pixels, placement (top, rows), left edge.
            val placement: Triple<Float, Pair<Float, Int>?, Float>? =
                if (shownSuggestions.isEmpty()) {
                    null
                } else {
                    val caretTopContent = lineTopPx(codeLayout, code, caretLine, codeWindow)
                        ?: pillTopPx(caretLine, lineHeightPx)
                    val caretTopViewport = topPaddingPx + caretTopContent - codeScroll.value
                    Triple(
                        caretTopViewport,
                        popupPlacement(
                            caretTopPx = caretTopViewport,
                            lineHeightPx = lineHeightPx,
                            gapPx = popupGapPx,
                            rowHeightPx = popupRowHeightPx,
                            borderPx = popupBorderPx,
                            viewportHeightPx = viewportHeightPx,
                            edgePx = popupEdgePx,
                            maxRows = shownSuggestions.size.coerceAtLeast(1),
                        ),
                        popupLeftPx(
                            wordX = xForOffset(
                                codeLayout,
                                wordStartOffset(code, cursor.coerceIn(0, code.length)),
                                codeWindow,
                            ) ?: 0f,
                            columnStartPx = with(popupDensity) { columnStart.toPx() },
                            hScrollPx = codeHScroll.value,
                            popupWidthPx = popupWidthPx,
                            codeWidthPx = codeWidthPx,
                            insetPx = popupEdgePx,
                        ),
                    )
                }
            // Spec 6.7: the popup hides when the caret line scrolls out of view, so it never floats
            // over unrelated code, and comes back when the line is on screen again.
            val caretRowVisible = placement?.let { (top, _, _) ->
                top + lineHeightPx > 0f && top < viewportHeightPx
            } == true
            // Where it goes *and* how much of it the space allows, answered together: when the room
            // under the caret line cannot take the whole list, the row count gives way rather than
            // the line the user is typing on. Null means not even one row fits either side, and a
            // popup that cannot keep the caret line clear has no business being on screen.
            val popupAt = placement?.second
            val popupTop = popupAt?.first ?: 0f
            val popupRows = popupAt?.second ?: 1
            val popupLeft = placement?.third ?: 0f
            androidx.compose.animation.AnimatedVisibility(
                visible = sigVisible,
                enter = fadeIn(motion(120)),
                exit = fadeOut(motion(120)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            sigLeft.toInt(),
                            (signatureShownTop ?: 0f).toInt(),
                        )
                    },
            ) {
                signatureInfo?.let {
                    SignatureHelpTooltip(
                        info = it,
                        overloadLabel = signatureOptions.size.takeIf { n -> n > 1 }
                            ?.let { n -> "${sigChoice.coerceIn(0, n - 1) + 1}/$n" },
                        onOverloadTap = {
                            if (signatureOptions.size > 1) {
                                sigChoice = (sigChoice + 1) % signatureOptions.size
                            }
                        },
                        modifier = Modifier
                            .width(with(popupDensity) { sigWidthPx.toDp() })
                            .onSizeChanged { sigMeasuredH = it.height.toFloat() },
                    )
                }
            }

            // When the tooltip sits below the caret, the list moves beneath the tooltip's
            // measured height rather than a guess, so a wrapped signature still clears it.
            val popupTopAdj = if (signatureBelowTop != null && sigMeasuredH > 0f) {
                maxOf(popupTop, signatureBelowTop + sigMeasuredH + popupGapPx)
            } else {
                popupTop
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = quickFixLine == null &&
                    shownSuggestions.isNotEmpty() &&
                    caretRowVisible &&
                    popupAt != null,
                enter = fadeIn(motion(120)),
                exit = fadeOut(motion(120)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(popupLeft.toInt(), popupTopAdj.toInt())
                    },
            ) {
                SuggestionPopup(
                    suggestions = shownSuggestions,
                    rows = popupRows,
                    selected = selectedSug.coerceIn(0, shownSuggestions.lastIndex.coerceAtLeast(0)),
                    onSelect = { selectedSug = it },
                    onAccept = applySuggestion,
                    // Spec 6.7: min(430 dp, editor width - 16 dp).
                    modifier = Modifier.width(with(popupDensity) { popupWidthPx.toDp() }),
                )
            }

            // A selection earns its own bar: 8 dp above the selection's start, left edge on
            // it, kept 8 dp inside the screen edges — or below the handles with 16 dp of
            // clearance when nothing fits above, so the handles never overlap it. It stands
            // down while the suggestion popup or a quick fix owns the space — two floating
            // panels over the same caret is two panels fighting.
            val selectedRange = fieldValue.selection
            val hasSelection = selectedRange.max > selectedRange.min
            if (hasSelection) {
                val selectedLine = caretPosition(code, selectedRange.min).first
                val selectedTopContent =
                    lineTopPx(codeLayout, code, selectedLine, codeWindow)
                        ?: pillTopPx(selectedLine, lineHeightPx)
                val selectedTopViewport = topPaddingPx + selectedTopContent - codeScroll.value
                val selectedEndLine = caretPosition(code, selectedRange.max).first
                val selectedEndBottomViewport = topPaddingPx +
                    (lineTopPx(codeLayout, code, selectedEndLine, codeWindow)
                        ?: pillTopPx(selectedEndLine, lineHeightPx)) +
                    lineHeightPx - codeScroll.value
                // Full row when the screen minus 16 dp holds it, else Cut/Copy/Paste plus the
                // overflow menu. The width feeds the placement below, so the bar never hangs
                // off the edge it was measured against.
                val barWidthDp = if (with(popupDensity) { viewportWidthPx.toDp() } - 16.dp >=
                    SELECTION_FULL_WIDTH_DP
                ) {
                    SELECTION_FULL_WIDTH_DP
                } else {
                    SELECTION_COMPACT_WIDTH_DP
                }
                // Taller on a single-word selection: the bar grows a details header, and the
                // positioned box must grow with it or the buttons would hang off its bottom.
                val selectionBarHeight = if (symbolDetails != null) {
                    SELECTION_BAR_HEIGHT + SELECTION_BAR_DETAILS_HEIGHT
                } else {
                    SELECTION_BAR_HEIGHT
                }
                val selectionBarHeightPx =
                    with(popupDensity) { selectionBarHeight.toPx() }
                val selectionBarWidthPx =
                    with(popupDensity) { barWidthDp.toPx() }
                val aboveTop = selectedTopViewport -
                    with(popupDensity) { 8.dp.toPx() } - selectionBarHeightPx
                val selectionBarTop = if (aboveTop >= 0f) {
                    aboveTop
                } else {
                    // No room above: below the handles with 16 dp of clearance, clamped on
                    // screen so a selection at the very bottom still owns a reachable bar.
                    (selectedEndBottomViewport + with(popupDensity) { 16.dp.toPx() })
                        .coerceIn(0f, (viewportHeightPx - selectionBarHeightPx).coerceAtLeast(0f))
                }
                val selectionBarLeft = popupLeftPx(
                    wordX = xForOffset(codeLayout, selectedRange.min, codeWindow) ?: 0f,
                    columnStartPx = with(popupDensity) { columnStart.toPx() },
                    hScrollPx = codeHScroll.value,
                    popupWidthPx = selectionBarWidthPx,
                    codeWidthPx = viewportWidthPx,
                    insetPx = with(popupDensity) { 8.dp.toPx() },
                )
                val selectionRowVisible = selectedTopViewport + lineHeightPx > 0f &&
                    selectedTopViewport < viewportHeightPx
                val selectedText = fieldValue.text.substring(selectedRange.min, selectedRange.max)
                // Whether Paste does anything right now, read when the selection appears: the
                // clipboard is not observable, so this is a snapshot rather than a subscription,
                // and a fresh selection re-takes it.
                var pasteReady by remember(hasSelection) { mutableStateOf(false) }
                LaunchedEffect(hasSelection) {
                    pasteReady = clipboard.getText()?.text?.isNotEmpty() == true
                }
                // The bar stands down while the code moves under it and comes back about 200 ms
                // after it stops: a bar that stays up during a scroll points at a selection the
                // reader can no longer see. A drag through the handles fires one selection change
                // per frame, a tap or long-press fires one: the first change shows the bar at
                // once, a second within 300 ms means a drag is in progress, so the bar stands
                // down until 200 ms after the last change.
                var toolbarAllowed by remember { mutableStateOf(true) }
                LaunchedEffect(codeScroll.isScrollInProgress, codeHScroll.isScrollInProgress) {
                    if (codeScroll.isScrollInProgress || codeHScroll.isScrollInProgress) {
                        toolbarAllowed = false
                    } else {
                        delay(200)
                        toolbarAllowed = true
                    }
                }
                var selectionSettled by remember { mutableStateOf(true) }
                var lastSelChangeMs by remember { mutableStateOf(0L) }
                LaunchedEffect(selectedRange) {
                    val now = System.currentTimeMillis()
                    if (!selectedRange.collapsed && now - lastSelChangeMs < 300) {
                        selectionSettled = false
                    }
                    lastSelChangeMs = now
                    delay(200)
                    selectionSettled = true
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectionRowVisible &&
                        toolbarAllowed &&
                        selectionSettled &&
                        quickFixLine == null &&
                        visibleSuggestions.isEmpty() &&
                        problemSheetLine.value == null,
                    enter = fadeIn(motion(120)),
                    exit = fadeOut(motion(120)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(selectionBarLeft.toInt(), selectionBarTop.toInt())
                        },
                ) {
                    SelectionBar(
                        details = symbolDetails,
                        widthDp = barWidthDp,
                        cutEnabled = hasSelection,
                        copyEnabled = hasSelection,
                        onCut = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(selectedText))
                            commit(
                                TextFieldValue(
                                    text = fieldValue.text.substring(0, selectedRange.min) +
                                        fieldValue.text.substring(selectedRange.max),
                                    selection = TextRange(selectedRange.min),
                                ),
                            )
                            onToast("Cut ${selectedText.length} chars")
                        },
                        onFormat = onSelectionFormat,
                        pasteEnabled = pasteReady,
                        onPaste = {
                            val pasted = clipboard.getText()?.text
                            if (pasted.isNullOrEmpty()) {
                                onToast("Clipboard is empty")
                            } else {
                                commit(
                                    TextFieldValue(
                                        text = fieldValue.text
                                            .replaceRange(selectedRange.min, selectedRange.max, pasted),
                                        selection = TextRange(selectedRange.min + pasted.length),
                                    ),
                                )
                            }
                        },
                        onSelectAll = {
                            commit(fieldValue.copy(selection = TextRange(0, fieldValue.text.length)))
                        },
                        onCopy = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(selectedText))
                            onToast("Copied ${selectedText.length} chars")
                        },
                        onComment = {
                            // Every line the selection touches, top to bottom, so a selection that
                            // starts mid-line still comments the line the reader is on.
                            val first = caretPosition(code, selectedRange.min).first
                            val last = caretPosition(code, selectedRange.max).first
                            val next = (first..last).fold(fieldValue.text) { acc, lineNo ->
                                SourceEdits.toggleLineComment(acc, lineNo)
                            }
                            commit(TextFieldValue(next, fieldValue.selection))
                        },
                    )
                }
            }

            // The scrollbars are children of the same box that owns both scrollers, so they float
            // over the code instead of costing it a column of width, and they line up with the
            // axis they report on.
            EditorScrollbar(
                codeHScroll,
                viewportWidthPx,
                horizontal = true,
                onRelease = { snapHScroll() },
            )
            EditorScrollbar(
                codeScroll,
                viewportHeightPx,
                horizontal = false,
                // Errors above and below the window, marked on the scrollbar so a two-thousand-line
                // file still answers "is it above me or below me".
                ticks = errorTickFractions(
                    errorLines = problems.linesWithErrors,
                    lineCount = codeLines.size,
                    visibleTop = codeWindow.firstLine + 1,
                    // How many rows are actually on screen. This was the whole file's line count,
                    // which on a long file put the bottom of the window below every error in it, so
                    // no error was ever judged "off screen" and no tick was ever drawn.
                    visibleBottom = codeWindow.firstLine +
                        (codeRange.last - codeRange.first + 1),
                ),
            )
        }

        // The keyboard key row, shown only while the keyboard is up. It still owns its height
        // when shown, so the code area grows rather than slides. The 100 ms matches the
        // hide budget: a closed keyboard leaves the bar, never a lingering row.
        val toolRowHeight by animateDpAsState(
            targetValue = if (showToolRow) EditorKeyRowHeight else 0.dp,
            animationSpec = motion(100),
            label = "tool-row-height",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(toolRowHeight)
                // A no-op while adjustResize is in effect (the window already ends above the
                // keyboard), and the thing that keeps the row visible on windows where it
                // does not: the row always sits exactly on the keyboard's top edge.
                .imePadding()
                .clipToBounds(),
        ) {
            EditorKeyRow(
                onTab = { onToolAction(ToolAction.TAB) },
                onKey = { glyph -> typeAtCaret(glyph) },
            )
        }
    }
}


private val SCROLLBAR_THICKNESS = 3.dp

/**
 * How much room around the visible bar a finger has to hit it.
 *
 * A 3 dp bar is a 3 dp target, and nothing that small is reliably tappable with a thumb. The extra
 * room is transparent and sits outside the bar, so the editor looks exactly as it did — only the
 * target is bigger.
 */
private val SCROLLBAR_TOUCH_SLOP = 28.dp

/** The pinned declaration's own row, small enough that it never looks like another tab strip. */
private val SCOPE_HEADER_HEIGHT = 24.dp

/** The error mark on the scrollbar: the bar's own width, short enough not to read as a thumb. */
private val ERROR_TICK_WIDTH = 3.dp
private val ERROR_TICK_HEIGHT = 3.dp

/**
 * How far a pinch may take the editor away from the size the settings chose: far enough to read a
 * long line without scrolling sideways, not so far that a phone screen holds three characters.
 */
internal val EditorScaleRange = 0.6f..2.4f

/** How far in from the left edge a pull open may start. A region of the screen, not a tolerance. */
internal val DrawerEdgeBand = 28.dp

/**
 * True when a drag started inside the left-edge band and travelled far enough right to be a pull
 * open. Both halves matter: a drag that starts clear of the edge is the editor's own scroll, and a
 * nudge that never leaves the band is a mis-tap on the side of the phone.
 */
internal fun shouldOpenFromSwipe(startXPx: Float, edgePx: Float, dxPx: Float, slopPx: Float): Boolean =
    startXPx <= edgePx && dxPx >= slopPx

/**
 * Pulling right from the left edge opens the drawer, the way a phone's own panels do.
 *
 * Armed only while [enabled], so the same drag over an open drawer is left to the scrim that
 * closes it. Nothing is consumed until the pull is recognised — a tap near the edge is still just
 * a tap — and a second finger is the pinch's business, not the drawer's, so a two-finger gesture
 * resets the start point rather than opening anything.
 */
private fun Modifier.edgeSwipeToOpenDrawer(
    enabled: Boolean,
    edgePx: Float,
    slopPx: Float,
    onOpen: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        var startX = Float.NaN
        var opened = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.filter { it.pressed }.singleOrNull()
            if (change == null) {
                startX = Float.NaN
                continue
            }
            if (startX.isNaN()) startX = change.position.x
            if (!opened && shouldOpenFromSwipe(startX, edgePx, change.position.x - startX, slopPx)) {
                opened = true
                onOpen()
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * The font a pinch leaves the editor at — the size the settings picked, multiplied by however far
 * the fingers have spread, in the whole points a font is actually drawn in. At the default size
 * with nobody pinching it is the same 13 the fixed constant used to hold.
 */
internal fun zoomedFontSize(baseSp: Int, scale: Float): Int = (baseSp * scale).roundToInt()

/**
 * Which way a drag has committed to, from its travelled distance on each axis.
 *
 * A diagonal drag belongs to one axis, not both: the first axis past slop that also leads wins,
 * and the loser never starts. Zero means undecided — neither axis has earned the gesture yet.
 */
internal fun panDirectionLocked(travelX: Float, travelY: Float, slopPx: Float): Int = when {
    kotlin.math.abs(travelX) > slopPx && kotlin.math.abs(travelX) > kotlin.math.abs(travelY) -> 1
    kotlin.math.abs(travelY) > slopPx && kotlin.math.abs(travelY) > kotlin.math.abs(travelX) -> -1
    else -> 0
}

/**
 * A one-finger horizontal drag pans the code column instead of selecting text.
 *
 * Why this has to exist: the code is drawn by a `Text` with a transparent `BasicTextField` laid over
 * it, and that field is what actually takes focus and the IME. A horizontal drag inside it is
 * claimed for *text selection*, so on a phone the one gesture that would let you pan along a long
 * line did nothing at all — the line simply ran off the right edge and stayed there. Vertical drag
 * and long-press still select; only the sideways pan is taken here.
 *
 * Four details are load bearing:
 *
 * - It listens on [PointerEventPass.Initial]. Pointer events reach ancestors before children on
 *   that pass, so this sees the drag before the text field does and consuming there takes the pan
 *   cleanly, instead of losing the race to a child that would already have consumed it.
 * - It commits on an 8 dp slop with a dominant-axis lock, so a tap never pans and a vertical or
 *   diagonal drag — which belongs to the surrounding `verticalScroll` — is left entirely alone.
 * - It abandons the gesture the moment a second pointer arrives, which is what keeps two-finger
 *   pinch-zoom working: that handler is an ancestor of this one and only acts on two pointers, so
 *   neither can starve the other.
 * - Release with speed flings through the state's own fling behaviour and snaps to a character
 *   when it settles; a slow release snaps straight away.
 */
private fun Modifier.panOnHorizontalDrag(
    state: androidx.compose.foundation.ScrollState,
    flingBehavior: androidx.compose.foundation.gestures.FlingBehavior,
    scope: kotlinx.coroutines.CoroutineScope,
    slopPx: Float,
    onPanActive: (Boolean) -> Unit = {},
    onRelease: () -> Unit = {},
): Modifier =
    pointerInput(state, flingBehavior, slopPx) {
        awaitEachGesture {
            // One pointer only, and unconsumed: a gesture some other handler has already claimed
            // (a second finger for zoom, the editor's own drawer pull) is none of our business.
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            var travelX = 0f
            var travelY = 0f
            var locked = 0
            var panning = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                // Released, lifted, or a second finger joined: hand the gesture back.
                if (change == null || !change.pressed || event.changes.count { it.pressed } > 1) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()
                if (!panning) {
                    // Only sideways travel commits, and only when it also leads: a tap, and a
                    // drag that turns out to be vertical, never get here with anything consumed,
                    // so the text field keeps both.
                    travelX += delta.x
                    travelY += delta.y
                    locked = panDirectionLocked(travelX, travelY, slopPx)
                    if (locked < 0) break
                    if (locked > 0) {
                        panning = true
                        onPanActive(true)
                    }
                }
                if (panning) {
                    change.consume()
                    state.dispatchRawDelta(-delta.x)
                }
            }
            // The finger left while panning: speed flings, then the column snaps to a character.
            if (panning) {
                onPanActive(false)
                val velocity = tracker.calculateVelocity().x
                if (kotlin.math.abs(velocity) > HORIZONTAL_FLING_MIN_VELOCITY) {
                    scope.launch {
                        try {
                            state.scroll(androidx.compose.foundation.MutatePriority.UserInput) {
                                with(flingBehavior) { performFling(-velocity) }
                            }
                        } finally {
                            onRelease()
                        }
                    }
                } else {
                    onRelease()
                }
            }
        }
    }

/** Fling floor for a horizontal pan release, in px/s: below this the column snaps in place. */
private const val HORIZONTAL_FLING_MIN_VELOCITY = 500f

/**
 * The end of Android's own text toolbar inside the editor: Copy, Paste, Cut and Select all
 * from the system would sit directly above this editor's own selection bar, doubling every
 * action. A toolbar that never shows and is never shown ends the ActionMode before it starts.
 */
private object NoSystemTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun showMenu(
        rect: androidx.compose.ui.geometry.Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit

    override fun hide() = Unit
}

/**
 * Which horizontal offset a press settles on: the nearest whole character.
 *
 * A press can land between characters, and the code is clipped exactly at the gutter edge — a
 * half-character offset would put half a glyph under that clip. Rounding, not flooring: the offset
 * the finger chose is the honest answer, and the nearest character is the closest honest rendering
 * of it. A non-positive character width means nothing measured yet, so the offset stands.
 */
internal fun snapScrollToCharacter(valuePx: Float, charWidthPx: Float): Int {
    if (charWidthPx <= 0f) return valuePx.roundToInt()
    return ((valuePx / charWidthPx).roundToInt() * charWidthPx).roundToInt()
}

/**
 * Pinching anywhere scales the editor's text. Two fingers only, and the event is read on the
 * [PointerEventPass.Initial] pass so it is heard before the scrollers underneath it: consuming
 * there is what stops a pinch from also dragging the code sideways and down while it zooms. A
 * single finger is never consumed, so taps, drags and scrolling reach whatever is below.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var distanceAtLastChange = 0f
        var wasZooming = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val positions = event.changes.filter { it.pressed }.map { it.position }
            if (positions.size >= 2) {
                val distance = (positions[0] - positions[1]).getDistance()
                if (wasZooming && distanceAtLastChange > 0f) {
                    onZoom(distance / distanceAtLastChange)
                }
                distanceAtLastChange = distance
                wasZooming = true
                event.changes.forEach { it.consume() }
            } else {
                distanceAtLastChange = 0f
                wasZooming = false
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * The row height as a multiple of the font size, taken from the spec's own 13 sp / 20 dp pair.
 * A setting changes the font size, so the row has to follow it rather than stay at 20.
 */
private val CodeLineHeightRatio = CodeLineHeight.value / CodeFontSize.value

/** How long typing has to stop before Auto save writes the buffer out. */
private const val AUTOSAVE_DELAY_MS = 900L

/** The floating bar a selection earns: height of the button row. */
private val SELECTION_BAR_HEIGHT = 34.dp
// The symbol-details line above the selection bar's buttons, when the selection is one word.
private val SELECTION_BAR_DETAILS_HEIGHT = 20.dp
// How many occurrence glows are drawn; the count in the details line still totals the file.
private const val MAX_OCCURRENCE_SPANS = 200
// How much of a declaration the details line may quote before cutting it off.
private const val SYMBOL_DETAILS_DECL_MAX = 48

/** Fixed widths for the selection bar's buttons, in dp: each holds its full word at 12sp. */
private const val SELECTION_CUT_WIDTH = 56
private const val SELECTION_COPY_WIDTH = 64
private const val SELECTION_PASTE_WIDTH = 68
private const val SELECTION_SELECT_ALL_WIDTH = 96
private const val SELECTION_COMMENT_WIDTH = 80
private const val SELECTION_FORMAT_WIDTH = 72
private const val SELECTION_MORE_WIDTH = 44

/** Full-row width: every button plus the five 1 dp separators between them. */
private val SELECTION_FULL_WIDTH_DP =
    (SELECTION_CUT_WIDTH + SELECTION_COPY_WIDTH + SELECTION_PASTE_WIDTH +
        SELECTION_SELECT_ALL_WIDTH + SELECTION_COMMENT_WIDTH + SELECTION_FORMAT_WIDTH + 5).dp

/** Compact width: Cut, Copy, Paste, the overflow button and three separators. */
private val SELECTION_COMPACT_WIDTH_DP =
    (SELECTION_CUT_WIDTH + SELECTION_COPY_WIDTH + SELECTION_PASTE_WIDTH +
        SELECTION_MORE_WIDTH + 3).dp

/**
 * The editor's own selection toolbar — the only one: Android's menu is switched off at the
 * field. Cut, Copy, Paste, Select all, Comment, Format, each wide enough for its full word;
 * past the screen minus 16 dp the last three move behind a ⋯ overflow menu rather than
 * squeezing into initials.
 */
@Composable
private fun SelectionBar(
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onComment: () -> Unit,
    onFormat: () -> Unit,
    cutEnabled: Boolean = true,
    copyEnabled: Boolean = true,
    pasteEnabled: Boolean = true,
    // The selected symbol's details — name, declaration, use count — shown as a header row
    // when a long-pressed name declares something. Null draws the button row alone, as before.
    details: String? = null,
    widthDp: androidx.compose.ui.unit.Dp = SELECTION_FULL_WIDTH_DP,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val compact = widthDp < SELECTION_FULL_WIDTH_DP
    Box(modifier) {
        Column(
            Modifier
                .width(widthDp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.toolbarBg)
                .border(1.dp, c.toolbarBorder, RoundedCornerShape(10.dp)),
        ) {
            // One line, muted and cut off: it names the symbol, it does not explain it.
            if (details != null) {
                Text(
                    text = details,
                    color = c.popupMuted,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SELECTION_BAR_DETAILS_HEIGHT)
                        .padding(horizontal = 12.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.toolbarBorder),
                )
            }
            Row(
                Modifier.height(SELECTION_BAR_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionBarButton("Cut", SELECTION_CUT_WIDTH.dp, onCut, enabled = cutEnabled)
                SelectionBarSeparator()
                SelectionBarButton("Copy", SELECTION_COPY_WIDTH.dp, onCopy, enabled = copyEnabled)
                SelectionBarSeparator()
                SelectionBarButton(
                    "Paste",
                    SELECTION_PASTE_WIDTH.dp,
                    onPaste,
                    enabled = pasteEnabled,
                )
                if (!compact) {
                    SelectionBarSeparator()
                    SelectionBarButton("Select all", SELECTION_SELECT_ALL_WIDTH.dp, onSelectAll)
                    SelectionBarSeparator()
                    SelectionBarButton("Comment", SELECTION_COMMENT_WIDTH.dp, onComment)
                    SelectionBarSeparator()
                    SelectionBarButton(
                        "Format",
                        SELECTION_FORMAT_WIDTH.dp,
                        onFormat,
                        accent = true,
                    )
                } else {
                    SelectionBarSeparator()
                    SelectionBarGlyph("⋯", "More actions", SELECTION_MORE_WIDTH.dp) {
                        menuOpen = true
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Select all", fontFamily = Sans) }, onClick = {
                menuOpen = false
                onSelectAll()
            })
            DropdownMenuItem(text = { Text("Comment", fontFamily = Sans) }, onClick = {
                menuOpen = false
                onComment()
            })
            DropdownMenuItem(text = { Text("Format", fontFamily = Sans) }, onClick = {
                menuOpen = false
                onFormat()
            })
        }
    }
}

/** One fixed-width button with its full word: 56 dp at the narrowest, never an initial. */
@Composable
private fun RowScope.SelectionBarButton(
    label: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = (if (accent) c.toolbarAccent else c.text).let {
                if (enabled) it else it.copy(alpha = 0.5f)
            },
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            // A backstop that never fires at the default text size: the widths above hold
            // every word whole, and only a enlarged system font may outgrow them.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.SelectionBarSeparator() {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(c.toolbarBorder),
    )
}

@Composable
private fun RowScope.SelectionBarGlyph(
    glyph: String,
    label: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = c.mu, fontSize = 15.sp, fontFamily = Sans, maxLines = 1)
    }
}

/**
 * The thumb for one axis of the editor, drawn over the code rather than beside it so that
 * scrolling never costs a column of width.
 *
 * A `ScrollState` never exposes the content size, only `maxValue`, so the geometry is derived from
 * that: the content is viewport + hidden extent, the thumb is the viewport's share of it, and
 * dragging the thumb by a slice of its travel moves the scroll by the same slice of `maxValue`.
 * Nothing is drawn while the axis already fits, because a thumb that cannot move reads as a defect.
 */
@Composable
private fun BoxScope.EditorScrollbar(
    state: ScrollState,
    viewportPx: Float,
    horizontal: Boolean,
    // Pixel offsets down the vertical track where a mark belongs. Nothing is marked on the
    // horizontal bar: it reports sideways position, and sideways position has no errors of its own.
    ticks: List<Float> = emptyList(),
    // Called when a scrollbar drag ends, so the caller can settle the offset — the horizontal bar
    // snaps to whole characters.
    onRelease: () -> Unit = {},
) {
    if (state.maxValue <= 0 || viewportPx <= 1f) return
    val c = LocalPageColors.current
    val density = LocalDensity.current
    val contentPx = viewportPx + state.maxValue
    val thumbPx = (viewportPx * viewportPx / contentPx).coerceAtLeast(1f)
    val travelPx = (viewportPx - thumbPx).coerceAtLeast(0f)
    val progress = state.value.toFloat() / state.maxValue
    val thumbDp = with(density) { thumbPx.toDp() }
    // The thumb shows only while scrolling, then fades. A permanent bar is chrome the reader
    // never asked about; the drag target underneath stays, so nothing about dragging changes.
    var thumbVisible by remember(horizontal) { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, state.value) {
        if (state.isScrollInProgress) {
            thumbVisible = true
        } else {
            delay(900)
            thumbVisible = false
        }
    }
    val thumbAlpha by animateFloatAsState(
        targetValue = if (thumbVisible) 1f else 0f,
        animationSpec = motion(200),
        label = "scrollbar-fade",
    )
    val track = if (horizontal) {
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    } else {
        Modifier.align(Alignment.CenterEnd).fillMaxHeight()
    }
    val slop = if (horizontal) {
        Modifier.height(SCROLLBAR_TOUCH_SLOP)
    } else {
        Modifier.width(SCROLLBAR_TOUCH_SLOP)
    }
    val bar = if (horizontal) {
        Modifier.fillMaxWidth().height(SCROLLBAR_THICKNESS)
    } else {
        Modifier.fillMaxHeight().width(SCROLLBAR_THICKNESS)
    }
    // Two boxes, and the order matters. The outer one is only a hit area — sized so a finger can
    // actually land on it — and the inner one is the bar the reader sees, still 6 dp, still
    // rounded, still carrying the drag. Padding on a single box instead would have inflated the
    // visible bar along with the target, because the thumb fills whatever box it is in.
    Box(track.then(slop), contentAlignment = if (horizontal) Alignment.BottomCenter else Alignment.CenterEnd) {
        Box(
            bar
                .clip(RoundedCornerShape(SCROLLBAR_THICKNESS / 2))
                .alpha(thumbAlpha)
                .pointerInput(horizontal, viewportPx, state.maxValue) {
                    detectDragGestures(
                        onDragEnd = { onRelease() },
                    ) { change, dragAmount ->
                        change.consume()
                        val dragged = if (horizontal) dragAmount.x else dragAmount.y
                        // One pixel of thumb to the same slice of the content, which is why the
                        // enlarged target above does not also change the scroll's feel.
                        state.dispatchRawDelta(dragged * contentPx / viewportPx)
                    }
                },
        ) {
            // The marks sit beside the thumb, not under it, and are drawn in the error red rather
            // than the thumb's grey: a grey mark on a grey bar is something only the code could see.
            if (!horizontal && ticks.isNotEmpty()) {
                ticks.forEach { at ->
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(ERROR_TICK_WIDTH)
                            .height(ERROR_TICK_HEIGHT)
                            .offset { IntOffset(0, at.toInt()) }
                            .background(c.error, RoundedCornerShape(1.dp)),
                    )
                }
            }
            Box(
                Modifier
                    .then(
                        if (horizontal) {
                            Modifier.fillMaxHeight().width(thumbDp)
                        } else {
                            Modifier.fillMaxWidth().height(thumbDp)
                        },
                    )
                    .offset {
                        val along = (progress * travelPx).toInt()
                        IntOffset(if (horizontal) along else 0, if (horizontal) 0 else along)
                    }
                    .background(c.scrollThumb, RoundedCornerShape(SCROLLBAR_THICKNESS / 2)),
            )
        }
    }
}

/**
 * Which badge letter a suggestion's glyph maps to: F for functions, P for properties and
 * parameters, M for everything else. The engine's glyph set is wider than the spec's three
 * letters, so anything the table does not name reads as a member rather than inventing a fourth.
 */
internal fun suggestionBadge(glyph: String): Char = when (glyph) {
    "ƒ" -> 'F'
    "P", "p" -> 'P'
    else -> 'M'
}

/**
 * Orders suggestions the way a reader scans them: exact prefix matches first, then names picked
 * recently, then everything else in the order it arrived. [recent] is newest-first; anything not
 * in it keeps its relative order, so the engine's own ranking survives underneath.
 */
internal fun sortSuggestions(
    items: List<com.closecode.ui.ide.model.Suggestion>,
    prefix: String,
    recent: List<String>,
): List<com.closecode.ui.ide.model.Suggestion> {
    if (items.size < 2 || (prefix.isEmpty() && recent.isEmpty())) return items
    return items.sortedWith(
        compareBy(
            { if (prefix.isNotEmpty() && it.name.startsWith(prefix)) 0 else 1 },
            {
                if (prefix.isNotEmpty() && it.name.startsWith(prefix, ignoreCase = true)) 0 else 1
            },
            {
                val at = recent.indexOf(it.name)
                if (at < 0) Int.MAX_VALUE else at
            },
        ),
    )
}

@Composable
private fun SuggestionPopup(
    suggestions: List<com.closecode.ui.ide.model.Suggestion>,
    rows: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAccept: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val listState = rememberLazyListState()
    val shown = rows.coerceIn(1, suggestions.size.coerceAtLeast(1))

    // The caller has already worked out how much room there is, so the list is exactly that tall
    // and the rest scrolls. Shrinking the popup to fit used to mean dropping suggestions on the
    // floor; this way one that falls off the bottom is a keypress away rather than gone.
    LaunchedEffect(selected, shown) {
        if (selected in suggestions.indices) listState.animateScrollToItem(selected)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                // 196 dp wide, 26 dp rows, radius 8, 1 dp border. The caller supplies the clamped
                // width; this guarantees the corner radius and exactly the height the room allowed.
                .height(POPUP_ROW_HEIGHT * shown)
                .clip(RoundedCornerShape(8.dp))
                .background(c.popupBg)
                .border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)),
        ) {
        itemsIndexed(suggestions) { index, item ->
            val isSel = index == selected
            val badge = suggestionBadge(item.glyph)
            val badgeColor = when (badge) {
                'F' -> c.badgeF
                'P' -> c.badgeP
                else -> c.badgeM
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(POPUP_ROW_HEIGHT)
                    // The selected row is the popup's own highlight with white text, not the
                    // app's purple: the list must read as one surface, not a slice of another.
                    .background(if (isSel) c.popupSelected else Color.Transparent)
                    .clickable {
                        onSelect(index)
                        onAccept(index)
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                Text(
                    text = item.name,
                    color = if (isSel) Color.White else c.popupText,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val type = item.detail.takeIf { it.isNotBlank() }
                if (type != null) {
                    Text(
                        text = type,
                        color = if (isSel) Color.White.copy(alpha = 0.75f) else c.popupMuted,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    // A 3 dp thumb that exists only while the list moves. The list is at most four rows tall, so
    // the thumb answers the only question it can: whether there is more below.
    if (suggestions.size > shown && listState.isScrollInProgress) {
        val trackH = POPUP_ROW_HEIGHT * shown
        val thumbH = trackH * (shown.toFloat() / suggestions.size)
        val travelPx = with(density) { (trackH - thumbH).toPx() }
        val progress = if (suggestions.size - shown <= 0) {
            0f
        } else {
            (listState.firstVisibleItemIndex.toFloat() / (suggestions.size - shown)).coerceIn(0f, 1f)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .height(trackH)
                .width(3.dp)
                .padding(vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, (travelPx * progress).toInt()) }
                    .width(3.dp)
                    .height(thumbH)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(c.scrollThumb),
            )
        }
    }
    }
}

/** "1 error" and "0 errors", so a count never reads "1 errors". */
internal fun pluralProblems(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

/**
 * The bottom bar. Three equal doors: Search and More open a sheet and hand the screen straight
 * back, while AI is the only one that takes the pane — and even it leaves the editor one tap away.
 * The counts that used to sit in the status bar live on the editor's badge, because the status bar
 * is gone and a badge on the bar would be a fourth thing to look at.
 */
@Composable
private fun IdeNav(
    active: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current

    Row(
        modifier
            .fillMaxWidth()
            .background(c.panel)
            // Spec 4.2: a 1 dp top border, no pill or indicator behind the active item.
            .border(1.dp, c.ln, RoundedCornerShape(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdeNavItems.forEach { item ->
            val isActive = item == active
            Box(
                Modifier
                    // Three equal-width items. The height comes from the 9 dp vertical padding
                    // and the label, not a fixed bar height.
                    .weight(1f)
                    .clickable { onSelect(item) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
                    color = if (isActive) c.ac else c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun IdeToast(text: String) {
    val c = LocalPageColors.current

    Text(
        text = text,
        color = c.panel,
        fontSize = 12.sp,
        fontFamily = Sans,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.tx)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}


private const val MAX_ANALYZED_CHARS = 200_000

/** Files larger than this are skipped by find-in-project; their content would swamp the sheet. */
internal const val MAX_SEARCH_FILE_CHARS = 200_000
/** How long a burst of keystrokes is left alone before the per-file pass runs. */
private const val ANALYSIS_DEBOUNCE_MS = 200L

/** Project-wide totals run over every Kotlin file, so they wait longer and stop at a file cap. */
private const val PROJECT_ANALYSIS_DEBOUNCE_MS = 400L
private const val PROJECT_ANALYSIS_MAX_FILES = 60

/**
 * The longest build time the panel will report.
 *
 * A number this far from the truth helps nobody, and it only gets there when a device is being
 * starved of CPU — so past ten seconds the panel says "10.0 s" rather than claiming a minute it did
 * not take.
 */
private const val MAX_REPORTED_BUILD_MS = 10_000L

/**
 * The popup shows at most four rows. Four is what a thumb can cover on a phone — a fifth row sits
 * under where the hand already is, and the list scrolls anyway, so a suggestion is never lost, only
 * moved one swipe away.
 */
internal const val MAX_VISIBLE_SUGGESTIONS = 4

/** Exposed for the geometry tests. */
internal const val MAX_VISIBLE_SUGGESTIONS_FOR_TEST = MAX_VISIBLE_SUGGESTIONS

/** Spec popup metrics: 196 dp wide, 26 dp rows, at most four rows (104 dp tall). */
private val POPUP_ROW_HEIGHT = 26.dp
private val POPUP_MAX_WIDTH = 196.dp
private val POPUP_EDGE_INSET = 8.dp

/** Spec 6.7: the gap between the caret line and the popup, whichever side it opens on. */
private val POPUP_GAP = 6.dp

/**
 * Every item the header shows, in order. This list is the whole contract: the header composes from
 * it and the unit test pins it, so a button cannot be added to or removed from the row without the
 * test noticing. The file drawer is deliberately absent, because it opens from the chevron at the
 * left of the tab strip and a second entry point duplicated it.
 */
enum class HeaderItem {
    MENU,
    PROJECT_NAME,
    UNDO,
    REDO,
    SAVE,
    TERMINAL,
    RUN,
    OVERFLOW,
}

internal val HeaderItems: List<HeaderItem> = listOf(
    HeaderItem.MENU,
    HeaderItem.PROJECT_NAME,
    HeaderItem.UNDO,
    HeaderItem.REDO,
    HeaderItem.SAVE,
    HeaderItem.TERMINAL,
    HeaderItem.RUN,
    HeaderItem.OVERFLOW,
)

/**
 * The panels that cover the editor rather than sitting beside it. Back has to know what was
 * underneath them rather than always dropping the reader on Code and losing a Preview they had
 * open, so these are the panels that do not become the answer to that question.
 */
internal val ToolPanels = setOf("Logcat", "Build", "Terminal")

/**
 * The drawer's first tab, named for what it holds rather than for the service half of it: this
 * panel is local version control first — branches, changes, commits, stash — and GitHub is a
 * section underneath that. The account, clone and publish controls all live here and none of them
 * need a repository of its own, which is why it opens first.
 */
internal const val SourceControlTab = "Source Control"

/** The drawer's Files tab. Stated once so the segmented control and the default cannot drift. */
internal const val FilesTab = "Files"

/** The drawer's tabs. The first is the default; the segmented control shows Files first. */
internal val ExplorerTabs: List<String> = listOf(SourceControlTab, FilesTab)

/** Spec 4.1: how many edits Undo can step back through before the oldest one is dropped. */
private const val UNDO_HISTORY_LIMIT = 100

/**
 * The rows the drawer's list puts above every file: none anymore. The project root row and the
 * PROJECT label were removed (the header row carries the project name), so a tree index already
 * means a LazyColumn index. The constant stays so the reveal math keeps a name for its offset.
 */
internal const val TreeListHeaderItems = 0

/**
 * Height of the file tab strip, 34 dp. It has to be stated rather than wrapped around the tabs,
 * because a Row inside a Column hands its children the height that is still free, so an unbounded
 * child would otherwise stretch this strip over the whole editor and squeeze the code area out of
 * existence.
 */
internal val TAB_STRIP_HEIGHT = 40.dp

/** Spec 6.1: the tab's own height inside the strip. */
internal val TAB_HEIGHT = 30.dp

/**
 * The rule under the open tab.
 *
 * Two dp for the same reason the brace mark is two dp and the scrollbar's thumb is six: the marks
 * along an edge should read as one family, and a one dp rule on a 1 dp border is just a second
 * border.
 */
internal val TAB_UNDERLINE_THICKNESS = 2.dp

/**
 * How wide the fade over the tab strip's overflowing edge is, 36 dp.
 *
 * Tabs used to stop dead at the panel's edge, so a file name was cut in half mid-letter with nothing
 * to say there was more. A fade says "there is more over there" without a scrollbar or a chevron to
 * take the eye, and costs nothing to draw.
 */
internal val TAB_EDGE_FADE_WIDTH = 36.dp

/** Exposed for the geometry tests: the spec's 53 dp row, not the 46 dp that clipped the package. */
internal val POPUP_ROW_HEIGHT_FOR_TEST = POPUP_ROW_HEIGHT

private fun caretPosition(code: String, cursor: Int): Pair<Int, Int> {
    val safe = cursor.coerceIn(0, code.length)
    var line = 1
    var lineStart = 0
    for (i in 0 until safe) {
        if (code[i] == '\n') {
            line++
            lineStart = i + 1
        }
    }
    return line to (safe - lineStart + 1)
}

private fun CompletionItem.toSuggestion() = com.closecode.ui.ide.model.Suggestion(
    glyph = when (kind) {
        CompletionKind.CLASS -> "C"
        CompletionKind.MEMBER -> "M"
        CompletionKind.FUNCTION -> "ƒ"
        CompletionKind.PROPERTY -> "P"
        CompletionKind.PARAMETER -> "p"
        CompletionKind.LOCAL -> "L"
        CompletionKind.KEYWORD -> "K"
        CompletionKind.ENUM_ENTRY -> "E"
        CompletionKind.STDLIB_CLASS -> "C"
        CompletionKind.STDLIB_FUNCTION -> "ƒ"
        CompletionKind.STDLIB_MEMBER -> "M"
    },
    name = label,
    detail = detail.orEmpty(),
    subtitle = subtitle,
)
