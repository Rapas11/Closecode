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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onSizeChanged
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
import com.closecode.ui.ide.model.suggestionsFor
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.project.ProjectPathException
import com.closecode.ui.project.languageOf
import com.closecode.ui.project.ProjectSafety
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.model.projectTree
import com.closecode.ui.model.recentFiles
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File

@Composable
internal fun IdeScreen(
    projectName: String,
    // Held by MainActivity rather than created here, so the shells outlive the settings screen.
    terminalSessions: TerminalSessions,
    templateName: String, // kept for the project record; the header shows the project name
    slug: String,
    repository: ProjectRepository,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    // A clone performed inside the IDE finishes with a record for a *different* project. The
    // caller decides where that lands; this screen never navigates itself.
    onProjectCloned: (ProjectRecord) -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    BackHandler { onExit() }
    // The one place the settings are read from for this screen: saving and the editor both honour
    // them, and reading them once keeps a row changed on the settings screen in force immediately.
    val settings = LocalSettings.current
    val context = LocalContext.current
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
    var collapsedFolders by remember(slug) { mutableStateOf(setOf<String>()) }
    val diskContents = remember(slug) { mutableStateMapOf<String, String>() }
    var activePanel by remember(slug) { mutableStateOf("Code") }
    var explorerOpen by remember { mutableStateOf(false) }
    // The drawer's width, kept here rather than inside the drawer so the scrim that closes it
    // starts exactly where the drawer ends. Two numbers computed apart drift the moment either one
    // animates, and a scrim that overlaps the drawer would swallow the file list. The drawer takes
    // most of the width so files are readable, while the tab strip's chevron and the first
    // characters of code stay visible behind it.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val drawerFullWidth = if (screenWidthDp >= 760) 320.dp else (screenWidthDp * 0.72f).dp
    val drawerWidth by animateDpAsState(
        targetValue = if (explorerOpen) drawerFullWidth else 0.dp,
        animationSpec = tween(200),
        label = "explorer-width",
    )
    // Which half of the drawer is showing. Source Control opens first — it holds the account,
    // clone and publish controls, none of which need this project to have a repository of its own.
    var explorerTab by remember { mutableStateOf(ExplorerTabs.first()) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
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
    // Written by that route when it re-authenticates. The drawer caches the token for as long as
    // it is composed, and this is how it is told to read it again rather than go on with the copy
    // it took when it was opened.
    var githubTokenEpoch by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var findQuery by remember { mutableStateOf("") }
    // The search sheet's two switches. They hold while the sheet is open so a pattern survives a
    // detour to look at a result and back.
    var findCaseSensitive by remember { mutableStateOf(false) }
    var findRegex by remember { mutableStateOf(false) }
    var explorerFilter by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }
    // The build and logcat streams. The log still carries nothing but what a build reports; the
    // build now reports a real sweep of the project's own files.
    val logLines = remember { mutableStateListOf<LogLine>() }
    // True while the suggestion popup is up. It is the tool row's other reason to hide: the popup
    // covers the row, and a row under a popup only steals taps.
    var suggestionsOpen by remember { mutableStateOf(false) }
    // The soft keyboard is what the tool row exists for — tab, brace, quote and comment are not on
    // a phone keyboard — so its presence decides whether the row is there. Reading the inset rather
    // than watching a timer is what fixes the row disappearing four seconds into a thought while
    // the keyboard is still up.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // How far the code text is pinched away from the size the settings chose. The gesture is heard
    // on the whole screen — a pinch over the top bar or the tool row zooms the editor too — so the
    // state lives here rather than down in the editor that only reads it.
    var editorScale by remember { mutableFloatStateOf(1f) }
    // How far in from the left edge a pull open may start, and how far that pull has to travel.
    // The system's own slop is used for the second so the gesture feels like every other panel on
    // the phone; the band is fixed, because it is a region of the screen rather than a tolerance.
    val drawerEdgePx = with(LocalDensity.current) { DrawerEdgeBand.toPx() }
    val drawerSlopPx = LocalViewConfiguration.current.touchSlop
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
    // Logcat, Build and Terminal cover the editor, so back has to know what was underneath them
    // rather than always dropping the reader on Code and losing a Preview they had open.
    var panelBeforeTool by remember { mutableStateOf("Code") }

    LaunchedEffect(activePanel) {
        if (activePanel !in ToolPanels) panelBeforeTool = activePanel
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
    LaunchedEffect(slug, treeVersion) {
        val files = withContext(Dispatchers.IO) { repository.tree(slug) }
        diskTree = files
        if (treeVersion != 0) return@LaunchedEffect
        val sources = files.filter { it.kind != FileKind.Folder }.take(3)
        tabs = sources
        active = sources.firstOrNull()?.name.orEmpty()
        selectedPath = sources.firstOrNull()?.path
        val loaded = withContext(Dispatchers.IO) {
            sources.mapNotNull { file ->
                runCatching { repository.readFile(slug, file.path) }.getOrNull()
                    ?.let { file.name to it }
            }
        }
        loaded.forEach { (name, text) ->
            diskContents[name] = text
            noteContent(name)
        }
    }

    val activeFile = tabs.firstOrNull { it.name == active }
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

    // Every tree mutation ends here, so a file created from the drawer, a file created from the
    // command palette and a file deleted from the shell all repaint the drawer the same way.
    fun refreshTree() {
        treeVersion++
        gitRevision++
    }

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

    /** Opens a file in a tab, reading it only when it has not been read before. */
    fun openFile(file: ProjectFile) {
        if (file.kind == FileKind.Folder) return
        active = file.name
        selectedPath = file.path
        if (tabs.none { it.name == file.name }) tabs = tabs + file
        tabs = retainTabs(tabs, active, ::isDirtyTab)
        if (diskContents[file.name] == null) {
            // Off the main thread: this runs from a tap on a drawer row, and a cold read of a big
            // file used to be the frame that opened it. The tab shows nothing until it lands rather
            // than freezing — and a freshly opened buffer is never dirty, so no dot flashes.
            val name = file.name
            val path = file.path
            fileScope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { repository.readFile(slug, path) }.getOrNull()
                }
                if (text != null && diskContents[name] == null) {
                    diskContents[name] = text
                    noteContent(name)
                    // The editor seeds from an empty buffer while this read is in flight, so tell
                    // it to seed again now that there is something to seed with. A tab the reader
                    // has already typed into is left alone: that buffer is theirs, not ours.
                    if (active == name && liveBuffers[name] == null) sourceRevision++
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
     * Where a renamed or moved entry leaves the editor. Tabs hold the path, buffers and dirty flags
     * are keyed by the name, and the drawer highlights by path — all three have to follow the file,
     * or the tab keeps drawing a path that no longer exists and the next save writes to the old
     * place.
     */
    fun retargetPath(from: ProjectFile, to: ProjectFile) {
        tabs = tabs.map { if (it.path == from.path) to else it }
        if (from.name != to.name) {
            liveBuffers.remove(from.name)?.let { liveBuffers[to.name] = it }
            diskContents.remove(from.name)?.let { diskContents[to.name] = it }
            dirtyTabs.remove(from.name)?.let { dirtyTabs[to.name] = it }
            if (active == from.name) active = to.name
        }
        if (selectedPath == from.path) selectedPath = to.path
    }

    /**
     * Whether some other file already goes by [name]. Buffers are keyed by name, so a rename that
     * lands on one does not overwrite it — it makes two tabs draw the same text. Refused here,
     * before anything on disk moves.
     */
    fun nameIsHeldByAnother(name: String, path: String): Boolean {
        val holders = tabs.filter { it.name == name }
        if (holders.any { it.path != path }) return true
        return holders.isEmpty() && liveBuffers.containsKey(name)
    }

    fun renameEntry(target: ProjectFile, newName: String) {
        if (nameIsHeldByAnother(newName, target.path)) {
            toast = "A file called $newName is already open"
            return
        }
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
        if (!copy && target.kind != FileKind.Folder) {
            val leaf = destination.trim().removePrefix("/").substringAfterLast('/')
            if (nameIsHeldByAnother(leaf, target.path)) {
                toast = "A file called $leaf is already open"
                return
            }
        }
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
                    liveBuffers.remove(file.name)
                    diskContents.remove(file.name)
                    dirtyTabs.remove(file.name)
                }
                tabs = tabs.filterNot(::inside)
                if (gone.any { it.name == active }) active = tabs.lastOrNull()?.name.orEmpty()
                if (selectedPath?.let { it == target.path || it.startsWith(prefix) } == true) {
                    selectedPath = tabs.firstOrNull { it.name == active }?.path
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
            .map { it.name to (liveBuffers[it.name] ?: diskContents[it.name].orEmpty()) }
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
    // A real build: the app's own Kotlin analyser is run across the project's files and what it finds
    // is reported. There is no Gradle daemon inside an Android app, so this is a compile check and
    // the panel says so — but every number here is measured rather than timed out on a schedule.
    //
    // The whole sweep runs on Dispatchers.Default. It was on the main thread while the panel showed
    // a timer, where it was harmless because the work was fake; with real work over up to sixty
    // files it would have frozen the editor for as long as it took.
    LaunchedEffect(building) {
        if (!building) return@LaunchedEffect
        val sources = diskTree
            .filter { it.kind == FileKind.Kotlin }
            .take(PROJECT_ANALYSIS_MAX_FILES)
            .map { it.name to (liveBuffers[it.name] ?: diskContents[it.name].orEmpty()) }
        buildFileCount = sources.size
        buildPhase = BuildPhase.SYMBOLS
        buildProgress = 0f
        if (sources.isEmpty()) {
            // Nothing to check is not the same as a project that compiles, so the panel is left in
            // the idle state rather than being handed a clean bill of health it never earned.
            building = false
            buildPhase = BuildPhase.IDLE
            buildProgress = 0f
            buildElapsedMs = 0L
            buildErrorCount = 0
            buildWarningCount = 0
            toast = "No Kotlin files to build"
            return@LaunchedEffect
        }
        val started = System.nanoTime()
        val result = withContext(Dispatchers.Default) {
            buildPhase = BuildPhase.FILES
            val index = AndroidApiAssets.cached()
            val declared = KotlinAnalyzer.projectSymbols(sources.map { it.second })
            var errors = 0
            var warnings = 0
            // One file at a time, so the bar moves as the work is done rather than jumping at the
            // end — and so a slow file cannot make the panel look hung.
            for ((fileIndex, source) in sources.withIndex()) {
                val text = source.second
                if (text.isNotBlank() && text.length <= MAX_ANALYZED_CHARS) {
                    val analysis = runCatching {
                        KotlinAnalyzer.analyze(text, index, declared)
                    }.getOrNull()
                    analysis?.diagnostics?.forEach { d ->
                        when (d.severity) {
                            Severity.ERROR -> errors++
                            Severity.WARNING -> warnings++
                        }
                    }
                }
                buildProgress = (fileIndex + 1).toFloat() / sources.size.toFloat()
            }
            Triple(errors, warnings, (System.nanoTime() - started) / 1_000_000)
        }
        val (errors, warnings, elapsedMs) = result
        buildElapsedMs = elapsedMs.coerceAtMost(MAX_REPORTED_BUILD_MS)
        buildErrorCount = errors
        buildWarningCount = warnings
        buildProgress = 1f
        buildPhase = BuildPhase.DONE
        building = false
        if (errors > 0) {
            logLines.appendCapped(
                LogLine(
                    LogLevel.ERROR,
                    "Build",
                    "BUILD FAILED · $errors errors, $warnings warnings in ${formatBuildTime(buildElapsedMs)}",
                ),
            )
            toast = "Build failed · $errors errors"
        } else {
            logLines.appendCapped(
                LogLine(
                    LogLevel.INFO,
                    "Build",
                    "BUILD SUCCESSFUL · 0 errors in ${formatBuildTime(buildElapsedMs)}",
                ),
            )
            toast = "Build successful · 0 errors"
        }
    }
    // The Android back button closes what is open, newest first, and only leaves the project when
    // there is nothing left to close. Composed after the exit handler above so it wins while armed.
    BackHandler(
        enabled = moreOpen || findOpen || searchOpen || menuOpen ||
            prompt != null || confirmDelete != null || explorerOpen || addBlockOpen,
    ) {
        when {
            addBlockOpen -> addBlockOpen = false
            prompt != null -> prompt = null
            confirmDelete != null -> confirmDelete = null
            moreOpen -> moreOpen = false
            findOpen -> {
                findOpen = false
                findQuery = ""
            }

            searchOpen -> {
                searchOpen = false
                query = ""
            }

            menuOpen -> menuOpen = false
            explorerOpen -> explorerOpen = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Pinching anywhere on the screen scales the code text. Attaching it here rather
                // than to the code column means a pinch over the top bar, the path bar, the tool row
                // or the bottom bar does the same thing — and a pinch over a sheet, a menu or the
                // toast does nothing at all, because those are siblings of this Column rather than
                // children of it.
                .pinchToZoom { scale -> editorScale = (editorScale * scale).coerceIn(EditorScaleRange) }
                // Pulling right from the left edge opens the drawer. Armed only while it is shut,
                // so the same drag over an open drawer is left to the scrim below it.
                .edgeSwipeToOpenDrawer(
                    enabled = !explorerOpen,
                    edgePx = drawerEdgePx,
                    slopPx = drawerSlopPx,
                    onOpen = { explorerOpen = true },
                ),
        ) {

            val activePath = activeFile?.path ?: active
            IdeTopBar(
                projectName = projectName,
                dirty = isDirtyTab(active),
                canUndo = canUndo,
                canRedo = canRedo,
                onDrawer = { explorerOpen = !explorerOpen },
                onSave = { saveActiveFile() },
                onUndo = { undoRequest++ },
                onRedo = { redoRequest++ },
                onTerminal = { activePanel = "Terminal" },
                onRun = {
                    // Spec 7: running is blocked while the project has compile errors, and jumps
                    // to the problem sheet instead.
                    if (projectErrors > 0) {
                        toast = blockedByErrors(projectErrorList, "running")
                        problemSheet.value = problemSheet.value ?: 1
                        problemSheetScope = ProblemScope.PROJECT
                        activePanel = "Code"
                    } else {
                        building = true
                    }
                },
                onMore = { menuOpen = true },
                menuActive = menuOpen,
            )

            when (activePanel) {
                "AI" -> AgentPanel(projectName = projectName, modifier = Modifier.weight(1f))
                "Logcat" -> IdeLogcatPanel(
                    lines = logLines,
                    onBack = { activePanel = panelBeforeTool },
                    modifier = Modifier.weight(1f),
                )
                "Build" -> IdeBuildPanel(
                    building = building,
                    errorCount = buildErrorCount,
                    warningCount = buildWarningCount,
                    elapsedMs = buildElapsedMs,
                    phase = buildPhase,
                    fileCount = buildFileCount,
                    progress = buildProgress,
                    onRun = {
                        // Spec 7: build and run are blocked while any error exists.
                        if (projectErrors > 0) {
                            toast = "Fix ${projectErrors} errors before building"
                            activePanel = "Build"
                        } else {
                            building = true
                        }
                    },
                    onBack = { activePanel = panelBeforeTool },
                    modifier = Modifier.weight(1f),
                )
                "Terminal" -> IdeTerminal(
                    projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                    // The slug is the history key: commands are remembered per project, and the
                    // store needs something to tell one project's list from another's.
                    projectSlug = slug,
                    // The holder came from above the screen switch, so this panel owns the draw but
                    // not the shells: leaving the terminal and coming back finds the same processes
                    // with the same output, and so does opening Settings.
                    sessions = terminalSessions,
                    // The project tree, so Tab completes paths from what the project actually
                    // holds rather than by listing a directory the shell may not even be in.
                    tree = diskTree,
                    // `code <file>` and the two Git chips both end somewhere the terminal cannot
                    // reach on its own: the editor, and the drawer's source control.
                    onOpenPath = { file ->
                        openFile(file)
                        activePanel = "Code"
                    },
                    onOpenSourceControl = {
                        explorerTab = SourceControlTab
                        explorerOpen = true
                    },
                    // A file made or removed in the terminal is a file the drawer should list,
                    // rather than one that quietly stays wrong until something else refreshes it.
                    onFilesChanged = { refreshTree() },
                    onBack = { activePanel = panelBeforeTool },
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    // Code, the block editor and Preview share one path bar, so the way back to the
                    // code is always one `<>` tap away.
                    IdePathBar(
                        filePath = activeFile?.path ?: active,
                        previewable = isPreviewable(activeFile?.kind, active),
                        // Every file can be projected, not just Kotlin: non-Kotlin sources have no
                        // live projection, so the panel renders the honest empty state instead of
                        // hiding the button that would have explained why.
                        blockable = true,
                        previewActive = activePanel == "Preview",
                        blocksActive = activePanel == "Blocks",
                        onCode = { activePanel = "Code" },
                        onBlocks = { activePanel = "Blocks" },
                        onPreview = {
                            val buffer = liveBuffers[active]
                            val saved = diskContents[active]
                            if (buffer != null && buffer != saved) saveActiveFile()
                            activePanel = "Preview"
                        },
                    )
                    when (activePanel) {
                        "Preview" -> IdePreviewPanel(
                            fileName = active,
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
                            modifier = Modifier.weight(1f),
                        )

                        "Blocks" -> IdeBlockPanel(
                            fileName = active,
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
                                IdeExplorer(
                                    expanded = explorerOpen,
                                    fullWidth = drawerFullWidth,
                                    width = drawerWidth,
                                    projectName = projectName,
                                    slug = slug,
                                    tree = diskTree,
                                    activePath = activeFile?.path,
                                    revealPath = selectedPath,
                                    collapsed = collapsedFolders,
                                    query = explorerFilter,
                                    tab = explorerTab,
                                    gitRevision = gitRevision,
                                    onTab = { explorerTab = it },
                                    onCreateFile = { prompt = TreePrompt.New(isFolder = false) },
                                    onCreateFolder = { prompt = TreePrompt.New(isFolder = true) },
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
                                    githubTokenEpoch = githubTokenEpoch,
                                    projectRoot = runCatching { repository.projectDir(slug) }.getOrNull(),
                                )

                                IdeEditorArea(
                                    modifier = Modifier.weight(1f),
                                    tabs = tabs,
                                    active = active,
                                    language = language,
                                    contentFor = { name -> liveBuffers[name] ?: diskContents[name] ?: "" },
                                    onSelectTab = { name ->
                                        active = name
                                        selectedPath = tabs.firstOrNull { it.name == name }?.path
                                    },
                                    onCloseTab = { name ->
                                        tabs = tabs.filterNot { it.name == name }
                                        if (active == name) {
                                            active = tabs.lastOrNull()?.name.orEmpty()
                                            selectedPath = tabs.lastOrNull()?.path
                                        }
                                    },
                                    onCloseOthers = { name ->
                                        tabs = tabs.filter { it.name == name }
                                        if (active != name) {
                                            active = name
                                            selectedPath = tabs.firstOrNull()?.path
                                        }
                                    },
                                    onCloseAll = {
                                        tabs = emptyList()
                                        active = ""
                                        selectedPath = null
                                    },
                                    onBufferChange = { text ->
                                        liveBuffers[active] = text
                                        noteContent(active)
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
                                    onHistoryState = { undo, redo ->
                                        canUndo = undo
                                        canRedo = redo
                                    },
                                    onSuggestionsOpen = { suggestionsOpen = it },
                                    showToolRow = showToolRow(imeVisible, suggestionsOpen),
                                    editorScale = editorScale,
                                )
                            }

                            // The drawer's own shadow. It starts where the drawer ends, so the
                            // file list underneath stays tappable, and a tap anywhere else closes
                            // the drawer — the gesture that opened it has no mirror on the way back.
                            if (explorerOpen) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(start = drawerWidth)
                                        .background(c.scrim)
                                        .clickable { explorerOpen = false },
                                )
                            }
                        }
                    }
                }
            }

            IdeNav(
                active = when {
                    findOpen -> "Search"
                    moreOpen -> "More"
                    activePanel == "AI" -> "AI"
                    else -> ""
                },
                onSelect = { item ->
                    // Spec item 4's save-on-Preview now lives on the path bar, because Preview is
                    // no longer something the bottom bar selects directly.
                    when (item) {
                        "Search" -> findOpen = true
                        "AI" -> activePanel = if (activePanel == "AI") "Code" else "AI"
                        "More" -> moreOpen = true
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
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
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
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

        // Find in project. The sheet stays up while results are tapped so the match and the code
        // it came from are on screen together; the scrim is what closes it.
        if (findOpen) {
            // Reading every file in the project is disk work, and inside `remember` it ran on the
            // composition path — one full project read between frames. `produceState` moves the read
            // to Dispatchers.IO and hands the list back when it lands; the sheet simply shows no
            // hits until it does.
            val searchSources by produceState(
                initialValue = emptyList(),
                key1 = slug,
                key2 = treeVersion,
            ) {
                val files = diskTree.filter { it.kind != FileKind.Folder }
                value = withContext(Dispatchers.IO) {
                    files.mapNotNull { file ->
                        val text = runCatching { repository.readFile(slug, file.path) }.getOrNull()
                        if (text == null || text.length > MAX_SEARCH_FILE_CHARS) null
                        else file to text
                    }
                }
            }
            val hits = remember(findQuery, searchSources) {
                findInSources(
            findQuery,
            searchSources,
            caseSensitive = findCaseSensitive,
            regex = findRegex,
        )
            }
            IdeSearchSheet(
                query = findQuery,
                onQueryChange = { findQuery = it },
                hits = hits,
                caseSensitive = findCaseSensitive,
                regex = findRegex,
                onCaseSensitiveChange = { findCaseSensitive = it },
                onRegexChange = { findRegex = it },
                onOpen = { hit ->
                    diskTree.firstOrNull { it.path == hit.path }?.let { openFile(it) }
                    activePanel = "Code"
                },
                onDismiss = {
                    findOpen = false
                    findQuery = ""
                },
            )
        }

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

        if (moreOpen) {
            IdeMoreSheet(
                actions = listOf(
                    MoreAction("Logcat", "≡") { activePanel = "Logcat" },
                    MoreAction("Build", "✓") { activePanel = "Build" },
                    MoreAction("Settings", "⚙") { onOpenSettings() },
                    MoreAction("Toggle theme", "◐") { onToggleTheme() },
                ),
                onDismiss = { moreOpen = false },
            )
        }

        when (val current = prompt) {
            null -> Unit
            is TreePrompt.New -> IdeNameDialog(
                title = if (current.isFolder) "New folder" else "New file",
                hint = if (current.isFolder) "feature" else "MainActivity.kt",
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

        // Hosted here rather than inside the drawer: it covers the screen, and the drawer
        // collapses to 0.dp on the Files tab — where it would be invisible but still modal.
        if (cloneOpen) {
            val tokenStore = remember { TokenStore(context) }
            GitHubCloneScreen(
                token = remember(githubTokenEpoch) { tokenStore.read() },
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
            .height(52.dp)
            .background(c.panel)
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
        // The one door into the drawer. No logo tile beside it: the bar shows the project name,
        // never the file name and never a file path, and nothing else competes for the left edge.
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
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .androidxSemantics(label),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, tint, 19.dp)
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
internal fun retainTabs(
    tabs: List<ProjectFile>,
    active: String,
    isDirty: (String) -> Boolean,
    limit: Int = 3,
): List<ProjectFile> {
    if (tabs.size <= limit) return tabs
    val keep = tabs
        .filter { it.name == active || isDirty(it.name) }
        .map { it.name }
        .toMutableSet()
    if (keep.size < limit) {
        for (tab in tabs.asReversed()) {
            if (keep.size >= limit) break
            keep += tab.name
        }
    }
    return tabs.filter { it.name in keep }
}

/**
 * The row between the tab strip and the code: where the file lives, plus the buttons that leave
 * the code. It replaces the deleted status bar, and it is the only chrome above the editor body,
 * so it is a fixed 34 dp and never sits over the text.
 */
@Composable
private fun IdePathBar(
    filePath: String,
    previewable: Boolean,
    blockable: Boolean,
    previewActive: Boolean,
    blocksActive: Boolean,
    onCode: () -> Unit,
    onBlocks: () -> Unit,
    onPreview: () -> Unit,
) {
    val c = LocalPageColors.current
    val crumbs = breadcrumbOf(filePath)
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(0.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One text, one ellipsis. This used to be three sibling Texts with only the folder part
        // weighted, which meant the separator and the file name were measured first and the folders
        // got whatever was left — on a narrow phone that could be nothing, and the whole point of
        // the bar is to say where the file is. As a single AnnotatedString the path truncates as a
        // unit, and breadcrumbOf has already capped it at three crumbs, so it almost never has to.
        val parts = crumbs.ifEmpty { listOf("No file") }
        val path = buildAnnotatedString {
            parts.forEachIndexed { index, crumb ->
                if (index > 0) append(" › ")
                val leaf = index == parts.lastIndex
                withStyle(
                    SpanStyle(
                        color = when {
                            !leaf -> c.mu
                            previewActive || blocksActive -> c.ac
                            else -> c.tx
                        },
                        fontWeight = if (leaf) FontWeight.Medium else FontWeight.Normal,
                    ),
                ) {
                    append(crumb)
                }
            }
        }
        Text(
            text = path,
            fontSize = 12.sp,
            fontFamily = Mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The three doors out of the code: stay on it, reshape it as blocks, or run it.
        PathBarButton(CcIcons.Code, "Code", selected = !previewActive && !blocksActive, onClick = onCode)
        if (blockable) {
            PathBarButton(CcIcons.Layers, "Blocks", selected = blocksActive, onClick = onBlocks)
        }
        if (previewable) {
            PathBarButton(CcIcons.Image, "Preview", selected = previewActive, onClick = onPreview)
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
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) c.panel else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, if (selected) c.ac else c.mu, 16.dp)
    }
}

@Composable
private fun IdeExplorer(
    expanded: Boolean,
    // The width the drawer is open at, and the width it is drawn at this frame. Both are computed
    // up on the screen so the scrim that closes it can start exactly where the drawer ends.
    fullWidth: Dp,
    width: Dp,
    projectName: String,
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
    tab: String,
    gitRevision: Int,
    onTab: (String) -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onSelect: (ProjectFile) -> Unit,
    // The long-press menu. It is carried up because every one of its actions needs the repository,
    // the open tabs or a dialog, and the drawer has none of those.
    onAction: (TreeAction, ProjectFile) -> Unit,
    // Carried through to the GitHub tab: it only says *when* a clone should start. The route that
    // runs one is full-screen, so it lives at screen level, and only the screen has room for it.
    onCloneRequest: () -> Unit,
    // How many times the stored GitHub token has been replaced. Passed on so the panel re-reads it
    // instead of going on with the copy it took when it was composed.
    githubTokenEpoch: Int,
    projectRoot: File?,
) {
    val c = LocalPageColors.current

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

    // Bring the row that was just created or just opened into view. It only fires when the row is
    // off screen, so tapping a file that is already showing never jumps the list, and it waits for
    // the row count to change because a new entry lands in the tree one frame after the tap that
    // asked for it. Without this the file opens in a tab and stays invisible in the drawer, which
    // reads as "nothing happened".
    val headerRows = TreeListHeaderItems
    LaunchedEffect(revealPath, rows.size) {
        val path = revealPath ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.path == path }
        if (index < 0) return@LaunchedEffect
        val position = index + headerRows
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == position }) {
            listState.scrollToItem(position)
        }
    }

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(c.panel)
            .clipToBounds(),
    ) {
        Row(
            Modifier
                .width(if (expanded) fullWidth else 0.dp)
                .height(50.dp)
                .border(1.dp, c.ln, RoundedCornerShape(0.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EXPLORER",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            HeaderIconButton(
                icon = CcIcons.FilePlus,
                label = "New file",
                tint = c.mu,
                onClick = onCreateFile,
            )
            HeaderIconButton(
                icon = CcIcons.FolderPlus,
                label = "New folder",
                tint = c.mu,
                onClick = onCreateFolder,
            )
        }

        Row(
            Modifier
                .width(if (expanded) fullWidth else 0.dp)
                .height(36.dp)
                .border(1.dp, c.ln, RoundedCornerShape(0.dp)),
        ) {
            ExplorerTabs.forEach { name ->
                ExplorerTab(name, tab == name) { onTab(name) }
            }
        }

        // Both lists stay composed so the Source Control tab never loses a half-typed commit message
        // or cancels a commit that is already running on disk.
        Box(
            Modifier
                .weight(1f)
                .width(if (expanded) fullWidth else 0.dp)
                .fillMaxHeight(),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "root") {
                    TreeRow(
                        label = projectName,
                        depth = 0,
                        bold = true,
                        icon = CcIcons.FolderOpen,
                        iconTint = c.ac,
                        active = false,
                        onClick = {},
                    )
                }

                item(key = "sec-project") { SectionLabel("PROJECT") }

                itemsIndexed(rows, key = { _, f -> "tree-${f.path}" }) { _, file ->
                    val open = file.kind == FileKind.Folder
                    TreeRow(
                        label = file.name,
                        depth = file.depth + 1,
                        bold = false,
                        icon = if (open) CcIcons.FolderOpen else CcIcons.Document,
                        iconTint = kindColor(file.kind, c),
                        // The chevron points where the row is: right when there is something
                        // behind it that is not being shown, down when it is being shown.
                        chevron = when {
                            !open -> null
                            file.path in collapsed -> CcIcons.Chevron
                            else -> CcIcons.Down
                        },
                        chevronTint = c.mu,
                        active = file.path == activePath,
                        // Both kinds of row report the tap: opening a folder and selecting it as
                        // the place a new file lands are the same gesture, so the screen that owns
                        // the collapsed set decides which one happened.
                        onClick = { onSelect(file) },
                        actions = actionsForTree(file),
                        onAction = { action -> onAction(action, file) },
                    )
                }
            }

            IdeGitPanel(
                projectName = projectName,
                projectRoot = projectRoot,
                revision = gitRevision,
                onCloneRequest = onCloneRequest,
                tokenEpoch = githubTokenEpoch,
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
    }
}

/** One half of the drawer's Files / GitHub switch. */
@Composable
private fun RowScope.ExplorerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) c.tx else c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.4.sp,
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.ac),
            )
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
    MOVE("Move to…"),
    COPY("Copy to…"),
    IMPORT("Import file…"),
    SHARE("Reveal in file manager"),
    DELETE("Delete"),
}

/** The menu a row offers. A folder is where new things go; a file is the thing itself. */
internal fun actionsForTree(file: ProjectFile): List<TreeAction> =
    if (file.kind == FileKind.Folder) {
        listOf(
            TreeAction.NEW_FILE,
            TreeAction.NEW_FOLDER,
            TreeAction.RENAME,
            TreeAction.MOVE,
            TreeAction.COPY,
            TreeAction.IMPORT,
            TreeAction.DELETE,
        )
    } else {
        listOf(
            TreeAction.RENAME,
            TreeAction.MOVE,
            TreeAction.COPY,
            TreeAction.SHARE,
            TreeAction.DELETE,
        )
    }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    label: String,
    depth: Int,
    bold: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    // A folder says whether it is open; a file has nothing to say, and the slot it would have
    // used stays empty so its label still lines up under the label of the folder above it.
    chevron: androidx.compose.ui.graphics.vector.ImageVector? = null,
    chevronTint: Color = Color.Unspecified,
    active: Boolean,
    onClick: () -> Unit,
    actions: List<TreeAction> = emptyList(),
    onAction: (TreeAction) -> Unit = {},
) {
    val c = LocalPageColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (active) c.selection else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(start = 9.dp + 17.dp * depth),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(15.dp).height(15.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (chevron != null) CcIcon(chevron, chevronTint, 14.dp)
            }
            CcIcon(icon, iconTint, 15.dp, Modifier.padding(start = 2.dp, end = 7.dp))
            Text(
                text = label,
                color = if (active) c.tx else c.tx.copy(alpha = 0.86f),
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (actions.isNotEmpty()) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = action.label,
                                fontFamily = Sans,
                                color = if (action == TreeAction.DELETE) c.red else c.tx,
                            )
                        },
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
    val fromDisk = remember(active, contentFor) { contentFor(active) }
    // Where each file was left: caret offset and both scroll positions, keyed by file name in
    // plain maps. Plain, because reading them seeds the field below during composition, and
    // mutating snapshot state mid-composition would schedule another one.
    val caretByFile = remember { mutableMapOf<String, Int>() }
    val scrollByFile = remember { mutableMapOf<String, Pair<Int, Int>>() }
    var fieldValue by remember(active, language, sourceRevision) {
        mutableStateOf(
            TextFieldValue(
                text = fromDisk,
                // Opens where the file was left, when it was left somewhere: tapping back and
                // forth between two files no longer drops the caret at the end of the code each
                // time. Otherwise the last line with code, unless a caller asked for a spot.
                selection = run {
                    val saved = caretByFile.remove(active)
                    when {
                        saved != null -> TextRange(saved.coerceIn(0, fromDisk.length))
                        caretRequest >= 0 -> TextRange(caretRequest.coerceIn(0, fromDisk.length))
                        else -> TextRange(caretOffsetAtEndOfCode(fromDisk))
                    }
                },
            ),
        )
    }
    // Spec 4.1 Undo and Redo. Every text change goes through commit(), which records the value being
    // replaced, so the header can step back through the editor's own history without a native undo
    // manager. Selection-only moves commit too but record nothing, because they are not edits.
    val undoStack = remember(active, sourceRevision) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(active, sourceRevision) { mutableStateListOf<TextFieldValue>() }

    fun commit(next: TextFieldValue) {
        if (next.text != fieldValue.text) {
            undoStack.add(fieldValue)
            while (undoStack.size > UNDO_HISTORY_LIMIT) undoStack.removeAt(0)
            redoStack.clear()
        }
        fieldValue = next
    }

    fun undoOnce() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(fieldValue)
        fieldValue = previous
    }

    fun redoOnce() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(fieldValue)
        fieldValue = next
    }

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
    LaunchedEffect(active) {
        val next = contentFor(active)
        undoStack.clear()
        redoStack.clear()
        fieldValue = TextFieldValue(next, TextRange(caretOffsetAtEndOfCode(next)))
    }
    // One ScrollState per axis. Sharing a single state between verticalScroll and
    // horizontalScroll made the two fight over the same value, so the line-number gutter
    // drifted out of step with the code and error line numbers pointed at the wrong line.
    val codeScroll = rememberScrollState()
    val codeHScroll = rememberScrollState()
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
    // Leaving a tab records where it was left; coming back restores caret and both scrolls, so
    // tapping between two files keeps each file's place. The drawer opening a file does not go
    // through here — that path never had a place to keep, and still doesn't.
    val tabListState = rememberLazyListState()
    var tabMenuFor by remember { mutableStateOf<String?>(null) }
    fun forgetPlace(name: String) {
        caretByFile.remove(name)
        scrollByFile.remove(name)
    }
    val selectTabKeepingPlace = { name: String ->
        caretByFile[active] = cursor
        scrollByFile[active] = codeScroll.value to codeHScroll.value
        onSelectTab(name)
    }
    LaunchedEffect(active, tabs.size) {
        // The active tab scrolls into view; a file opened off the right edge would otherwise sit
        // there selected with no visible mark of it.
        val index = tabs.indexOfFirst { it.name == active }
        if (index >= 0) tabListState.animateScrollToItem(index)
        // Scroll comes back a frame after the caret because it needs the laid-out column, while
        // the caret is seeded into the field above before first draw.
        val saved = scrollByFile.remove(active)
        if (saved != null) {
            codeScroll.scrollTo(saved.first)
            codeHScroll.scrollTo(saved.second)
        }
    }
    // Report the buffer for every mutation path, not just typing: quick fixes, suggestion
    // acceptance and tab switches all rewrite the field without going through onValueChange.
    // The parent needs this to save before Preview and to count diagnostics project wide.
    LaunchedEffect(active, code) { onBufferChange(code) }
    val isKotlin = language.contains("Kotlin", true) || active.endsWith(".kt")
    val staticSuggestions = remember(language) { suggestionsFor(language) }
    var selectedSug by remember(language) { mutableStateOf(0) }

    var apiIndex by remember { mutableStateOf(AndroidApiAssets.cached()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AndroidApiAssets.load(context) { loaded ->
            apiIndex = loaded
        }
    }

    var analysis by remember { mutableStateOf<KotlinFileAnalysis?>(null) }
    LaunchedEffect(code, isKotlin, apiIndex, projectSymbols) {
        if (!isKotlin || code.length > MAX_ANALYZED_CHARS) {
            analysis = null
            return@LaunchedEffect
        }
        delay(ANALYSIS_DEBOUNCE_MS)
        val snapshot = code
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

    // Spec 6.3: the first import line carries the fold affordance. Zero means there is none.
    val firstImportLine = remember(code) {
        codeLines.indexOfFirst { it.trimStart().startsWith("import ") } + 1
    }

    val currentAnalysis = analysis?.takeIf { it.source == code }

    // Diagnostics off means "show me nothing", not "stop analysing": the analysis still runs,
    // because Run and Build gate on its error count and turning the display off must not quietly
    // make an unbuildable project look buildable. Only what is drawn changes.
    // Markup gets its own check. The Kotlin analyser knows nothing about tags, so a page
    // previously had no diagnostics of any kind — no squiggle, no bar in the margin, no count
    // anywhere — and an unclosed element was something only the browser would complain about, in
    // a preview the reader has to switch to in order to find out.
    val markupDiagnostics = remember(code, codeLanguage) {
        if (codeLanguage == CodeLanguage.MARKUP) htmlTagDiagnostics(code) else emptyList()
    }

    val problems = remember(currentAnalysis, markupDiagnostics, settings.diagnostics) {
        ProblemsByLine.from(
            if (settings.diagnostics) {
                currentAnalysis?.diagnostics.orEmpty() + markupDiagnostics
            } else {
                emptyList()
            }
        )
    }

    val caretLine = remember(code, cursor) { caretPosition(code, cursor).first }
    // Reported on every move so the block sheet can insert at the line the caret is actually on.
    // A plain offset is all the insertion needs, so the whole selection never leaves the editor.
    LaunchedEffect(cursor) { onCaretChange(cursor) }

    var codeLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var viewportWidthPx by remember { mutableStateOf(0f) }
    var viewportHeightPx by remember { mutableStateOf(0f) }

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
            startOffset = offsetOfLineColumn(code, codeRange.first + 1, 1),
            topPx = windowTopPx(codeRange.first, lineHeightPx),
        )
    }

    // The function the top of the window is inside, for the sticky header. Null while its own opening
    // line is still on screen: the reader can see it, and a pinned copy above the code is a second
    // thing saying what that line already says.
    val scopeHeader = remember(code, codeWindow.firstLine) {
        enclosingDeclarationAt(code, codeWindow.firstLine + 1)
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
    val highlighted = remember(codeRange, code, c, codeLanguage, bracketCycle) {
        val above = code.substring(0, codeWindow.startOffset.coerceIn(0, code.length))
        highlightCode(
            sliceOfLines(codeLines, codeRange),
            c,
            codeLanguage,
            bracketCycle,
            openBlockDepth(above),
        )
    }

    // Pairs are found in the whole buffer rather than the window, because a block can open above
    // the slice the renderer is holding; the layer decides for each pair whether either end is on
    // screen. The setting is read here so switching it off stops the scan, not just the drawing.
    val braceGuides = remember(code, settings.bracketGuides) {
        if (settings.bracketGuides) bracePairs(code) else emptyList()
    }
    // Every line's indent, read once per buffer. This is what places a guide: the column is
    // arithmetic over these numbers, so the rule can never land on top of the code it annotates.
    val indentColumns = remember(code, settings.tabSize) {
        indentColumnsOf(code, settings.tabSize)
    }
    // The one pair the caret is in, resolved once and read by the brace underline. Any of the
    // three bracket kinds counts, and a lone bracket with no partner is reported separately so it
    // can be underlined in red.
    val bracketPairsAll = remember(code) { allBracketPairs(code) }
    val activeBracketPair = remember(bracketPairsAll, code, cursor) {
        activeBraces(bracketPairsAll, code, cursor)
    }
    val unmatchedBracket = remember(bracketPairsAll, code, cursor) {
        unmatchedBracketAt(code, cursor, bracketPairsAll)
    }
    var quickFixLine by remember { mutableStateOf<Int?>(null) }
    var quickFixIndex by remember { mutableStateOf(0) }

    val quickFixActions = remember(quickFixLine, problems, currentAnalysis, code) {
        buildQuickFixActions(quickFixLine, problems, currentAnalysis, code)
    }

    fun openQuickFix(line: Int) {
        quickFixLine = line
        quickFixIndex = 0
        problemSheetLine.value = null
    }

    fun openProblemSheet(line: Int) {
        problemSheetLine.value = line
        quickFixLine = null
    }


    /** Character offset of a 1-based column on a 1-based line, for text-layout lookups. */
    fun offsetOfLineColumn(line: Int, column: Int): Int {
        if (line < 1 || column < 1) return 0
        var offset = 0
        var currentLine = 1
        var i = 0
        while (i < code.length && currentLine < line) {
            if (code[i] == '\n') currentLine++
            i++
            offset++
        }
        return (offset + (column - 1)).coerceIn(0, code.length)
    }

    val kotlinItems = remember(code, cursor, isKotlin, currentAnalysis) {
        val live = currentAnalysis
        if (!isKotlin || live == null) {
            null
        } else {
            runCatching {
                if (live.prefixAt(cursor).isNotEmpty() || live.isMemberAccess(cursor)) {
                    live.completionsAt(cursor, limit = 25)
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

    val visibleSuggestions = remember(
        kotlinItems,
        htmlItems,
        staticSuggestions,
        code,
        isKotlin,
        settings.autocomplete,
    ) {
        when {
            // Checked first so a reader cannot mistake "switched off" for "nothing matched".
            !settings.autocomplete -> emptyList()
            htmlContext != null -> htmlItems
            kotlinItems != null -> kotlinItems.map { it.toSuggestion() }
            else -> {
                val tail = code.takeLastWhile { it.isLetterOrDigit() }
                if (tail.isEmpty()) emptyList() else staticSuggestions.filter { it.name.startsWith(tail) }
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
    fun applyQuickFix(action: QuickFixAction): Pair<String, Int>? {
        val target = quickFixActions.firstOrNull { it.label == action.label } ?: return null
        val line = target.target.toIntOrNull()
        val (_, column) = caretPosition(code, cursor)
        return when (target.kind) {
            QuickFixKind.ADD_IMPORT -> {
                val next = SourceEdits.addImport(code, target.target)
                if (next == code) {
                    null
                } else {
                    next to (SourceEdits.caretAfterAddImport(code, target.target) ?: code.length)
                }
            }

            QuickFixKind.REMOVE_IMPORT -> {
                val next = SourceEdits.removeImport(code, target.target)
                if (next == code) null else next to cursor.coerceAtMost(next.length)
            }

            // Spec 6.6: E0101 deletes the offending token and leaves the caret where it began,
            // so a replacement can be typed straight into the gap.
            QuickFixKind.REMOVE_TOKEN -> {
                val diagnostic = problems.on(line ?: -1)
                    .firstOrNull { it.code == DiagnosticCodes.E0101_UNRESOLVED }
                if (diagnostic == null) return null
                val next = SourceEdits.removeToken(code, diagnostic.offset, diagnostic.length)
                    ?: return null
                next to diagnostic.offset.coerceIn(0, next.length)
            }

            QuickFixKind.ADD_SEMICOLON -> {
                if (line == null) return null
                val next = SourceEdits.addSemicolon(code, line) ?: return null
                next to next.length.coerceAtMost(next.length)
            }

            QuickFixKind.NO_FIX -> null

            QuickFixKind.TOGGLE_COMMENT -> {
                if (line == null) return null
                val next = SourceEdits.toggleLineComment(code, line)
                if (next == code) null else next to cursor.coerceAtMost(next.length)
            }

            QuickFixKind.MOVE_LINE_UP, QuickFixKind.MOVE_LINE_DOWN -> {
                if (line == null) return null
                val down = target.kind == QuickFixKind.MOVE_LINE_DOWN
                val next =
                    if (down) SourceEdits.moveLineDown(code, line) else SourceEdits.moveLineUp(code, line)
                if (next == code) {
                    null
                } else {
                    next to SourceEdits.caretAfterLineMove(code, line, column, down)
                }
            }
        }
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
    LaunchedEffect(caretLine, viewportHeightPx, lineHeightPx) {
        val margin = with(autoDensity) { 24.dp.toPx() }
        val rowTop = rowTopPx(caretLine, lineHeightPx, topPaddingPx)
        val rowBottom = rowTop + lineHeightPx
        val maxScroll = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
        val target = when {
            rowTop - margin < codeScroll.value -> (rowTop - margin).coerceAtLeast(0f)
            rowBottom + margin > codeScroll.value + viewportHeightPx ->
                (rowBottom + margin - viewportHeightPx).coerceIn(0f, maxScroll)
            else -> codeScroll.value
        }
        codeScroll.animateScrollTo(target.toInt().coerceAtLeast(0))
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
    LaunchedEffect(cursor, codeWidthPx, viewportWidthPx) {
        val caretContent = caretX(codeLayout, cursor, codeWindow) ?: return@LaunchedEffect
        val margin = with(autoDensity) { CaretHScrollMargin.toPx() }
        val now = codeHScroll.value.toFloat()
        val wanted = targetHScroll(caretContent, now, codeWidthPx, margin)
        if (wanted == now) return@LaunchedEffect
        if (abs(wanted - now) > codeWidthPx / 2f) {
            codeHScroll.animateScrollTo(wanted.roundToInt())
        } else {
            codeHScroll.scrollTo(wanted.roundToInt())
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
            val prefix = if (liveItems != null) currentAnalysis?.prefixAt(liveCursor).orEmpty() else ""
            val start = (liveCursor - prefix.length).coerceAtLeast(0)
            val next = liveText.substring(0, start) + insert + liveText.substring(liveCursor)
            commit(TextFieldValue(next, TextRange(start + insert.length)))
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
        val formatted = if (activeKind == FileKind.Kotlin || active.endsWith(".kt", true)) {
            SourceEdits.formatKotlin(code)
        } else {
            SourceEdits.normalizeTrailingWhitespace(code)
        }
        if (formatted == code) {
            onToast("Already formatted")
            return@LaunchedEffect
        }
        val (line, column) = caretPosition(code, cursor)
        val caret = offsetOfLineColumn(formatted, line, column).coerceIn(0, formatted.length)
        commit(TextFieldValue(formatted, TextRange(caret)))
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

        // The tab strip holds only tabs. The drawer opens from the top bar's Menu, so the
        // chevron cell that used to sit here duplicated that door and ate 44 dp of file names.
        Row(
            Modifier
                .fillMaxWidth()
                // The strip needs a bounded height. A Row inside a Column hands its children the
                // height that is still free, so an unbounded child would otherwise stretch this
                // strip over the whole editor and squeeze the code area out of existence.
                .height(TAB_STRIP_HEIGHT)
                .background(c.editorBg)
                // A single 1 dp bottom border. The strip is not a box, and the tabs are not boxes.
                .drawBehind {
                    val y = size.height - 0.5f
                    drawLine(
                        color = c.divider,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The fade over the overflowing edge, drawn over the list's own right-hand
                    // edge. It used to stop dead there, so a file name was cut in half mid-letter
                    // with nothing to say there was more of it.
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
                // Tabs breathe: 12 dp on each side, nothing above or below, in a 34 dp strip.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(tabs, key = { _, t -> "tab-${t.name}" }) { _, tab ->
                    val isActive = tab.name == active
                    // The unsaved dot shows only while the buffer differs from last saved.
                    val dirty = dirtyOf(tab.name)
                    val tabDensity = androidx.compose.ui.platform.LocalDensity.current
                    Box {
                        Row(
                            Modifier
                                .height(TAB_HEIGHT)
                                .drawBehind {
                                    if (!isActive) return@drawBehind
                                    val y = size.height - 1f
                                    drawLine(
                                        color = c.accent,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = with(tabDensity) { TAB_UNDERLINE_THICKNESS.toPx() },
                                    )
                                }
                                .combinedClickable(
                                    onClick = { selectTabKeepingPlace(tab.name) },
                                    onLongClick = { tabMenuFor = tab.name },
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // The dot comes first: it marks the tab, and a mark reads before a name.
                            if (dirty) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(c.warnText),
                                )
                            }
                            Text(
                                text = tab.name,
                                color = if (isActive) c.brightText else c.hintText,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp),
                            )
                        }
                        // No close button on the tab itself. A long press is the deliberate gesture;
                        // a visible × is a mis-tap away from closing the file being read.
                        DropdownMenu(
                            expanded = tabMenuFor == tab.name,
                            onDismissRequest = { tabMenuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Close", fontFamily = Sans) },
                                onClick = {
                                    tabMenuFor = null
                                    forgetPlace(tab.name)
                                    onCloseTab(tab.name)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Close others", fontFamily = Sans) },
                                onClick = {
                                    tabMenuFor = null
                                    tabs.forEach { forgetPlace(it.name) }
                                    onCloseOthers(tab.name)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Close all", fontFamily = Sans) },
                                onClick = {
                                    tabMenuFor = null
                                    tabs.forEach { forgetPlace(it.name) }
                                    onCloseAll()
                                },
                            )
                        }
                    }
                }
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
                if (target != codeHScroll.value) {
                    editScope.launch { codeHScroll.scrollTo(target) }
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
                        .panOnHorizontalDrag(codeHScroll, onRelease = ::snapHScroll)
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
                            .widthIn(min = with(androidx.compose.ui.platform.LocalDensity.current) { codeWidthPx.toDp() })
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
                                softWrap = settings.wordWrap,
                                maxLines = if (settings.wordWrap) Int.MAX_VALUE else 1,
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
                            softWrap = settings.wordWrap,
                            maxLines = if (settings.wordWrap) Int.MAX_VALUE else 1,
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
                            onTextLayout = { codeLayout = it },
                        )
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
                            // a row accepts a suggestion.
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            modifier = Modifier
                                .fillMaxSize()
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
                        // The air a problem pill and the quick-fix bulb leave after a line's own
                        // text, measured off the rendered layout rather than guessed: two
                        // characters' worth. Both draw on the same side of a line, so they share
                        // one number and can never drift apart.
                        val twoCharGapPx = 2f * (charWidthPx(codeLayout) ?: 0f)
                        if (problems.byLine.isNotEmpty()) {
                            // Wavy lines under the exact bad characters. Drawn before the labels
                            // so the labels stay readable on top of them.
                            SquiggleLayer(
                                problems = problems,
                                layout = codeLayout,
                                window = codeWindow,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxSize(),
                            )
                            InlineProblemLayer(
                                problems = problems,
                                lineHeightPx = lineHeightPx,
                                // Spec 6.5: two characters past the end of the line's text, both
                                // measured off the rendered layout. The line's own newline has no
                                // width, so the anchor is its right edge, never the character.
                                gapPx = twoCharGapPx,
                                anchorX = { line -> endOfLineX(codeLayout, code, line, codeWindow) },
                                yOfLine = { line -> lineTopPx(codeLayout, code, line, codeWindow) },
                                // Spec 7: a pill is one of the four ways into the Problems sheet.
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
                        }
            }

                // Drawn last with an opaque background: code scrolled sideways passes underneath.
                if (settings.lineNumbers) {
                    LineNumberLayer(
                        lineCount = gutterRowCount(codeLines, caretLine),
                        lineHeightPx = lineHeightPx,
                        problems = problems,
                        caretLine = caretLine,
                        // Rows come from the layout that drew the glyphs, not from a second row model.
                        layout = codeLayout,
                        // ...and from the slice that layout describes, so a number stays on its own
                        // row even though the text is no longer one Text for the whole file.
                        window = codeWindow,
                        // Spec 6.3: the first import line gets the fold affordance.
                        foldLine = firstImportLine,
                        onFoldTap = { _ -> onToast("Code folding is not available yet") },
                        // The bulb is drawn by the gutter now, so it needs no layout of its own and
                        // cannot land on top of the caret line's text. It appears for the caret line
                        // whenever that line has something to offer to fix and no card is open.
                        bulbLine = if (
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
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
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
                            applyQuickFix(action)?.let { (text, caret) ->
                                commit(TextFieldValue(text, TextRange(caret.coerceIn(0, text.length))))
                                quickFixLine = null
                                problemSheetLine.value = null
                            }
                        }
                    },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = problemSheetLine.value != null,
                enter = slideInVertically(tween(160)) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
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
                        problems.all.map { ProjectDiagnostic(active, it) }
                    },
                    scope = problemSheetScope,
                    fileLabel = active,
                    onSelect = { row ->
                        // Tapping a row in project scope opens the file it came from, then puts the
                        // caret on its line. Jumping to a line number in a file that is not open
                        // would be a dead end.
                        // A row from another file has to open that file first: jumping to a line
                        // number in a file that is not open would be a dead end.
                        if (row.fileName != active) onOpenProjectFile(row.fileName)
                        val offset = offsetOfLineColumn(code, row.diagnostic.line, row.diagnostic.column)
                        commit(TextFieldValue(code, TextRange(offset)))
                        problemSheetLine.value = null
                    },
                    onDismiss = { problemSheetLine.value = null },
                )
            }

            // Anchored on the rendered caret line, never on an assumed row height. The popup sits on the row
            // below the line being typed so that line stays readable while typing, and flips above it
            // when the keyboard leaves no room below. It never overlaps the caret line or its pill.
            val popupDensity = androidx.compose.ui.platform.LocalDensity.current
            val popupGapPx = with(popupDensity) { POPUP_GAP.toPx() }
            val popupRowHeightPx = with(popupDensity) { POPUP_ROW_HEIGHT.toPx() }
            val popupBorderPx = with(popupDensity) { 3.dp.toPx() }
            val popupEdgePx = with(popupDensity) { POPUP_EDGE_INSET.toPx() }
            val popupWidthPx = popupWidthPx(
                maxWidthPx = with(popupDensity) { POPUP_MAX_WIDTH.toPx() },
                viewportWidthPx = viewportWidthPx,
                gutterWidthPx = gutterWidthPx,
                insetPx = popupEdgePx,
            ).coerceAtLeast(with(popupDensity) { 120.dp.toPx() })
            val shownSuggestions = visibleSuggestions.take(MAX_VISIBLE_SUGGESTIONS)
            val caretTopContent = lineTopPx(codeLayout, code, caretLine, codeWindow)
                ?: pillTopPx(caretLine, lineHeightPx)
            val caretTopViewport = topPaddingPx + caretTopContent - codeScroll.value
            // Spec 6.7: the popup hides when the caret line scrolls out of view, so it never floats
            // over unrelated code, and comes back when the line is on screen again.
            val caretRowVisible = caretTopViewport + lineHeightPx > 0f &&
                caretTopViewport < viewportHeightPx
            // Where it goes *and* how much of it the space allows, answered together: when the room
            // under the caret line cannot take the whole list, the row count gives way rather than
            // the line the user is typing on. Null means not even one row fits either side, and a
            // popup that cannot keep the caret line clear has no business being on screen.
            val popupAt = popupPlacement(
                caretTopPx = caretTopViewport,
                lineHeightPx = lineHeightPx,
                gapPx = popupGapPx,
                rowHeightPx = popupRowHeightPx,
                borderPx = popupBorderPx,
                viewportHeightPx = viewportHeightPx,
                edgePx = popupEdgePx,
                maxRows = shownSuggestions.size.coerceAtLeast(1),
            )
            val popupTop = popupAt?.first ?: 0f
            val popupRows = popupAt?.second ?: 1
            val popupLeft = popupLeftPx(
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
            )
            // The signature tooltip. It shares the suggestion popup's measurements but sits above
            // the caret line instead of below it, because below is where the list of names goes —
            // one box each side is the only way a reader can see both while typing an argument.
            val signatureInfo = remember(code, cursor) {
                signatureHelp(code, cursor, AndroidApiAssets.cached())
            }
            val signatureHeightPx = with(popupDensity) {
                (SignatureRowHeight + SignatureDescriptionHeight + SignatureBoxVerticalPadding * 2)
                    .toPx()
            }
            val signatureTop = signatureTopPx(
                caretTopPx = caretTopViewport,
                tooltipHeightPx = signatureHeightPx,
                gapPx = popupGapPx,
                viewportHeightPx = viewportHeightPx,
                edgePx = popupEdgePx,
            )?.takeIf { signatureInfo != null }
            val signatureLeft = popupLeftPx(
                wordX = xForOffset(codeLayout, cursor.coerceIn(0, code.length), codeWindow) ?: 0f,
                columnStartPx = with(popupDensity) { columnStart.toPx() },
                hScrollPx = codeHScroll.value,
                popupWidthPx = popupWidthPx,
                codeWidthPx = codeWidthPx,
                insetPx = popupEdgePx,
            )
            val caretRowOnScreen = caretTopViewport + lineHeightPx > 0f &&
                caretTopViewport < viewportHeightPx
            androidx.compose.animation.AnimatedVisibility(
                visible = signatureInfo != null &&
                    signatureTop != null &&
                    caretRowOnScreen &&
                    quickFixLine == null &&
                    fieldValue.selection.max == fieldValue.selection.min &&
                    problemSheetLine.value == null,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            signatureLeft.toInt(),
                            (signatureTop ?: 0f).toInt(),
                        )
                    },
            ) {
                signatureInfo?.let {
                    SignatureHelpTooltip(
                        info = it,
                        modifier = Modifier.width(with(popupDensity) { popupWidthPx.toDp() }),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = quickFixLine == null &&
                    shownSuggestions.isNotEmpty() &&
                    caretRowVisible &&
                    popupAt != null,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        androidx.compose.ui.unit.IntOffset(popupLeft.toInt(), popupTop.toInt())
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

            // A selection earns its own bar. It is placed with the same helpers the suggestion
            // popup uses, so it anchors to the rendered row rather than an assumed row height and
            // flips above the selection when the keyboard leaves no room below. It stands down
            // while the suggestion popup or a quick fix owns the space — two floating panels
            // over the same caret is two panels fighting.
            val selectedRange = fieldValue.selection
            val hasSelection = selectedRange.max > selectedRange.min
            if (hasSelection) {
                val selectedLine = caretPosition(code, selectedRange.min).first
                val selectedTopContent =
                    lineTopPx(codeLayout, code, selectedLine, codeWindow)
                        ?: pillTopPx(selectedLine, lineHeightPx)
                val selectedTopViewport = topPaddingPx + selectedTopContent - codeScroll.value
                val selectionBarHeightPx =
                    with(popupDensity) { SELECTION_BAR_HEIGHT.toPx() }
                val selectionBarWidthPx =
                    with(popupDensity) { SELECTION_BAR_WIDTH.toPx() }
                val selectionBarTop = popupTopPx(
                    caretTopPx = selectedTopViewport,
                    lineHeightPx = lineHeightPx,
                    gapPx = popupGapPx,
                    popupHeightPx = selectionBarHeightPx,
                    viewportHeightPx = viewportHeightPx,
                    edgePx = popupEdgePx,
                )
                val selectionBarLeft = popupLeftPx(
                    wordX = xForOffset(codeLayout, selectedRange.min, codeWindow) ?: 0f,
                    columnStartPx = with(popupDensity) { columnStart.toPx() },
                    hScrollPx = codeHScroll.value,
                    popupWidthPx = selectionBarWidthPx,
                    codeWidthPx = codeWidthPx,
                    insetPx = popupEdgePx,
                )
                val selectionRowVisible = selectedTopViewport + lineHeightPx > 0f &&
                    selectedTopViewport < viewportHeightPx
                val selectedText = fieldValue.text.substring(selectedRange.min, selectedRange.max)
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectionRowVisible &&
                        quickFixLine == null &&
                        visibleSuggestions.isEmpty() &&
                        problemSheetLine.value == null,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(selectionBarLeft.toInt(), selectionBarTop.toInt())
                        },
                ) {
                    SelectionBar(
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
                        onInfo = {
                            val lines = selectedText.count { it == '\n' } + 1
                            onToast("${selectedText.length} chars · $lines lines")
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
                        onDuplicate = {
                            val next = fieldValue.text.substring(0, selectedRange.max) +
                                selectedText +
                                fieldValue.text.substring(selectedRange.max)
                            commit(
                                TextFieldValue(
                                    text = next,
                                    selection = TextRange(selectedRange.min, selectedRange.max + selectedText.length),
                                ),
                            )
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

        // Spec 6.8's tool row, shown only while the keyboard is up. It still owns its 46 dp when
        // shown, so the code area grows rather than slides.
        val toolRowHeight by animateDpAsState(
            targetValue = if (showToolRow) ToolRowHeight else 0.dp,
            animationSpec = tween(160),
            label = "tool-row-height",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(toolRowHeight)
                .clipToBounds(),
        ) {
            IdeToolRow(onAction = onToolAction)
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
 * A one-finger horizontal drag pans the code column instead of selecting text.
 *
 * Why this has to exist: the code is drawn by a `Text` with a transparent `BasicTextField` laid over
 * it, and that field is what actually takes focus and the IME. A horizontal drag inside it is
 * claimed for *text selection*, so on a phone the one gesture that would let you pan along a long
 * line did nothing at all — the line simply ran off the right edge and stayed there. Vertical drag
 * and long-press still select; only the sideways pan is taken here.
 *
 * Three details are load bearing:
 *
 * - It listens on [PointerEventPass.Initial]. Pointer events reach ancestors before children on
 *   that pass, so this sees the drag before the text field does and consuming there takes the pan
 *   cleanly, instead of losing the race to a child that would already have consumed it.
 * - It waits for horizontal slop before committing, so a vertical drag — which belongs to the
 *   surrounding `verticalScroll` — is left entirely alone.
 * - It abandons the gesture the moment a second pointer arrives, which is what keeps two-finger
 *   pinch-zoom working: that handler is an ancestor of this one and only acts on two pointers, so
 *   neither can starve the other.
 */
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

private fun Modifier.panOnHorizontalDrag(
    state: androidx.compose.foundation.ScrollState,
    onRelease: () -> Unit = {},
): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            // One pointer only, and unconsumed: a gesture some other handler has already claimed
            // (a second finger for zoom, the editor's own drawer pull) is none of our business.
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val slop = viewConfiguration.touchSlop
            var travelled = 0f
            var panning = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                // Released, lifted, or a second finger joined: hand the gesture back.
                if (change == null || !change.pressed || event.changes.count { it.pressed } > 1) break
                val delta = change.positionChange()
                if (!panning) {
                    // Only sideways travel commits. A tap, and a drag that turns out to be vertical,
                    // never get here with anything consumed, so the text field keeps both.
                    travelled += delta.x
                    if (kotlin.math.abs(travelled) > slop) panning = true
                }
                if (panning) {
                    change.consume()
                    state.dispatchRawDelta(-delta.x)
                }
            }
            // The finger left while panning: the scroll settles here, so this is where it snaps.
            if (panning) onRelease()
        }
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

/** The floating bar a selection earns: wide enough for the two words plus the two glyphs. */
private val SELECTION_BAR_WIDTH = 240.dp
private val SELECTION_BAR_HEIGHT = 40.dp

/**
 * The four actions that reach for selected text: paste over it, stretch the selection to the whole
 * buffer, read what was selected, and the rest behind a menu. The bar is a fixed size so its
 * caller can clamp it into the viewport without measuring it, which is the same deal the
 * suggestion popup makes.
 */
@Composable
/**
 * The bar that appears over selected text.
 *
 * Five actions in the order a reader reaches for them: Cut, Copy, Paste, Comment, Format. Cut and
 * Copy used to sit inside a `⋯` menu with Duplicate, which made the two actions every text editor
 * puts first the two hardest to reach — and there was no Cut at all, so the only way to remove
 * selected text was to copy it and hold backspace, which is a poor answer on a phone where the
 * backspace key is small.
 *
 * Format is last and in the accent colour because it is the one here that changes more than the
 * text: it is not undone by a second tap, and most selections do not want it. Select all, the
 * selection's own measurements and Duplicate stay in the overflow, where a deliberate reach finds
 * them.
 */
private fun SelectionBar(
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onComment: () -> Unit,
    onFormat: () -> Unit,
    onSelectAll: () -> Unit,
    onInfo: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .width(SELECTION_BAR_WIDTH)
                .height(SELECTION_BAR_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionBarLabel("Cut", Modifier.weight(1f), onCut)
            SelectionBarLabel("Copy", Modifier.weight(1f), onCopy)
            SelectionBarLabel("Paste", Modifier.weight(1f), onPaste)
            SelectionBarLabel("Comment", Modifier.weight(1f), onComment)
            SelectionBarLabel("Format", Modifier.weight(1f), onFormat, accent = true)
            SelectionBarGlyph("⋯", "More actions") { menuOpen = true }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Copy", fontFamily = Sans) }, onClick = {
                menuOpen = false
                onCopy()
            })
            DropdownMenuItem(text = { Text("Comment", fontFamily = Sans) }, onClick = {
                menuOpen = false
                onComment()
            })
            DropdownMenuItem(text = { Text("Duplicate", fontFamily = Sans) }, onClick = {
                menuOpen = false
                onDuplicate()
            })
        }
    }
}

@Composable
private fun RowScope.SelectionBarLabel(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (accent) c.ac else c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun RowScope.SelectionBarGlyph(glyph: String, label: String, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(SELECTION_BAR_HEIGHT)
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
        animationSpec = tween(200),
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

    LazyColumn(
        state = listState,
        modifier = modifier
            // Spec 6.7: width min(430, editor width - 16), radius 16, 1.5 dp border. The caller
            // supplies the clamped width, so this only guarantees the corner radius and the height
            // the room allowed.
            .height(POPUP_ROW_HEIGHT * shown)
            .clip(RoundedCornerShape(16.dp))
            .background(c.panel)
            .border(1.5.dp, c.ln, RoundedCornerShape(16.dp)),
    ) {
        itemsIndexed(suggestions) { index, item ->
            val isSel = index == selected
            val package_ = item.subtitle?.takeIf { it.isNotBlank() }
            val type = item.detail.takeIf { it.isNotBlank() }
            Column(
                Modifier
                    .fillMaxWidth()
                    // Spec 6.7: every row is 53 dp so the package line under the name is never
                    // squeezed against the row below it.
                    .height(POPUP_ROW_HEIGHT)
                    .drawBehind {
                        if (index != suggestions.lastIndex) {
                            val y = size.height - 0.5f
                            drawLine(
                                color = c.ln.copy(alpha = 0.45f),
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(size.width, y),
                                strokeWidth = 1f,
                            )
                        }
                    }
                    // Spec 6.7: the selected row is purple with white text.
                    .background(if (isSel) c.purple else Color.Transparent)
                    .clickable {
                        onSelect(index)
                        onAccept(index)
                    }
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) c.autoIconBgSelected else c.autoIconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.glyph.ifBlank { "C" }.take(2),
                            color = if (isSel) Color.White else c.autoIconTx,
                            fontSize = 13.sp,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = item.name,
                        color = if (isSel) Color.White else c.tx,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The type rides on the same row as the name, at the right. It used to be
                    // dropped for Android members in favour of the package line, which is the one
                    // fact about a member you can never act on — the type is what tells you whether
                    // the call you are about to accept can compile.
                    if (type != null) {
                        Text(
                            text = type,
                            color = if (isSel) Color.White.copy(alpha = 0.75f) else c.mu,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (package_ != null) {
                    // The package stays on its own dimmer line under the name, indented past the
                    // badge so it reads as a continuation of the name rather than of the row.
                    Text(
                        text = package_,
                        color = if (isSel) Color.White.copy(alpha = 0.75f) else c.mu,
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        modifier = Modifier.padding(start = 42.dp, top = 1.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
private fun IdeNav(active: String, onSelect: (String) -> Unit) {
    val c = LocalPageColors.current

    Row(
        Modifier
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
private const val MAX_SEARCH_FILE_CHARS = 200_000
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

/** Spec 6.7 popup metrics. */
private val POPUP_ROW_HEIGHT = 53.dp
private val POPUP_MAX_WIDTH = 430.dp
private val POPUP_EDGE_INSET = 6.dp

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

/** The drawer's tabs, in the order they are shown. Stated once so the strip's order and the
 *  default cannot drift apart. */
internal val ExplorerTabs: List<String> = listOf(SourceControlTab, "Files")

/** Spec 4.1: how many edits Undo can step back through before the oldest one is dropped. */
private const val UNDO_HISTORY_LIMIT = 100

/**
 * The rows the drawer's list puts above every file: the project root and the PROJECT label. A
 * tree index has to be offset by them before it means anything to LazyColumn, and the number lives
 * here so the offset and the two `item` blocks above cannot drift apart.
 */
internal const val TreeListHeaderItems = 2

/**
 * Height of the file tab strip, 34 dp. It has to be stated rather than wrapped around the tabs,
 * because a Row inside a Column hands its children the height that is still free, so an unbounded
 * child would otherwise stretch this strip over the whole editor and squeeze the code area out of
 * existence.
 */
internal val TAB_STRIP_HEIGHT = 34.dp

/** Spec 6.1: the tab's own height inside the strip. */
internal val TAB_HEIGHT = 34.dp

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
