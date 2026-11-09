package com.closecode.ui.ide

import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.closecode.R
import com.closecode.lang.AndroidApiAssets
import com.closecode.lang.CompletionItem as SoraCompletionModel
import com.closecode.lang.CompletionKind
import com.closecode.lang.Diagnostic
import com.closecode.lang.KotlinAnalyzer
import com.closecode.lang.KotlinFileAnalysis
import com.closecode.lang.Severity
import com.closecode.lang.SourceEdits
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.ide.model.suggestionsFor
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.git.GitService
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.PageColors
import com.closecode.ui.theme.Sans
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val SoraHighlightLimit = 200_000
private const val SoraHighlightDelayMs = 120L
private const val SoraDiagnosticsLimit = 200_000
private const val SoraDiagnosticsDelayMs = 250L
private const val SoraWindowPrefetchAbove = 200
private const val SoraWindowPrefetchBelow = 400
private const val SoraWindowRefreshMargin = 100

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SoraIdeEditorArea(
    tabs: List<ProjectFile>,
    active: String,
    language: String,
    contentFor: (String) -> String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    onSuggestionsOpen: (Boolean) -> Unit = {},
    showToolRow: Boolean = true,
    imeOpen: Boolean = false,
    editorScale: Float = 1f,
    onBufferChange: (String) -> Unit = {},
    onToast: (String) -> Unit = {},
    dirtyOf: (String) -> Boolean = { false },
    problemSheetLine: MutableState<Int?> = remember { mutableStateOf(null) },
    activeKind: FileKind? = null,
    formatRequest: Int = 0,
    formatSelectionRequest: Int = 0,
    problemSheetScope: ProblemScope = ProblemScope.FILE,
    projectDiagnostics: List<ProjectDiagnostic> = emptyList(),
    onOpenProjectFile: (String) -> Unit = {},
    onAskDiagnostic: (String, Int, String) -> Unit = { _, _, _ -> },
    onSelectionFormat: () -> Unit = {},
    undoRequest: Int = 0,
    redoRequest: Int = 0,
    sourceRevision: Int = 0,
    caretRequest: Int = -1,
    onCaretChange: (Int) -> Unit = {},
    projectSymbols: Set<String> = emptySet(),
    onSaveRequest: () -> Unit = {},
    onHistoryState: (Boolean, Boolean) -> Unit = { _, _ -> },
    relativePath: String? = null,
    projectRoot: java.io.File? = null,
    initialCaret: Map<String, Int> = emptyMap(),
    initialScroll: Map<String, Pair<Int, Int>> = emptyMap(),
    onPlaceChange: (Map<String, Int>, Map<String, Pair<Int, Int>>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = LocalPageColors.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var appliedSourceRevision by remember { mutableIntStateOf(-1) }
    var textRevision by remember { mutableIntStateOf(0) }
    var windowState by remember { mutableStateOf(IntRange.EMPTY) }
    var apiIndex by remember { mutableStateOf(AndroidApiAssets.cached()) }
    var tabMenuFor by remember { mutableStateOf<String?>(null) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportTick by remember { mutableIntStateOf(0) }
    var caretLine by remember { mutableIntStateOf(1) }
    var hasSelection by remember { mutableStateOf(false) }
    var autoCompleteOpen by remember { mutableStateOf(false) }
    var problems by remember { mutableStateOf(ProblemsByLine()) }
    var currentAnalysis by remember { mutableStateOf<KotlinFileAnalysis?>(null) }
    var quickFixLine by remember { mutableStateOf<Int?>(null) }
    var quickFixIndex by remember { mutableIntStateOf(0) }
    var pendingProblemJump by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var signatureOptions by remember { mutableStateOf<List<SignatureInfo>>(emptyList()) }
    var signatureChoice by remember { mutableIntStateOf(0) }
    var signatureMeasuredHeight by remember { mutableIntStateOf(0) }
    val latestSource = remember { AtomicReference("") }
    val tabListState = rememberLazyListState()
    val caretByFile = remember { mutableMapOf<String, Int>().also { it.putAll(initialCaret) } }
    val scrollByFile = remember { mutableMapOf<String, Pair<Int, Int>>() }
    val latestActive by rememberUpdatedState(active)
    val latestBuffer by rememberUpdatedState(onBufferChange)
    val latestCaret by rememberUpdatedState(onCaretChange)
    val latestHistory by rememberUpdatedState(onHistoryState)
    val latestPlace by rememberUpdatedState(onPlaceChange)
    val latestSave by rememberUpdatedState(onSaveRequest)
    val latestToast by rememberUpdatedState(onToast)
    val latestSuggestionsOpen by rememberUpdatedState(onSuggestionsOpen)

    val highlightGeneration = remember { AtomicInteger(0) }
    val diagnosticGeneration = remember { AtomicInteger(0) }
    val highlightLanguage = remember(language) { codeLanguageFor(language) }
    val scheme = remember(colors) { soraColorScheme(colors) }
    val palette = remember(colors) { soraPalette(colors) }
    val completionProvider = remember(
        language,
        activeKind,
        settings.autocomplete,
        apiIndex,
        projectSymbols,
    ) {
        { source: String, offset: Int ->
            soraCompletionModels(
                source = source,
                offset = offset,
                language = language,
                activeKind = activeKind,
                enabled = settings.autocomplete,
                api = apiIndex,
                projectSymbols = projectSymbols,
            )
        }
    }
    val sourceProvider = remember { { latestSource.get() } }
    val codeFont = remember(settings.codeFont) {
        if (settings.codeFont == "System") Typeface.MONOSPACE
        else runCatching { context.resources.getFont(R.font.jetbrains_mono_regular) }
            .getOrDefault(Typeface.MONOSPACE)
    }
    val editor = remember(context) {
        CodeEditor(context).also { view ->
            view.setTypefaceText(codeFont)
            view.setTypefaceLineNumber(Typeface.MONOSPACE)
            view.setTextSize(zoomedFontSize(settings.fontSize, editorScale).toFloat())
            view.setLineSpacing(0f, 1.12f)
            view.setLineNumberEnabled(settings.lineNumbers)
            view.setLineNumberAlign(Paint.Align.RIGHT)
            view.setLineNumberMarginLeft(20f * context.resources.displayMetrics.density)
            view.setFirstLineNumberAlwaysVisible(true)
            view.setWordwrap(settings.wordWrap)
            view.setTabWidth(settings.tabSize)
            view.setScalable(false)
            view.setUndoEnabled(true)
            view.setHighlightCurrentLine(true)
            view.setBlockLineEnabled(settings.bracketGuides)
            view.setHighlightBracketPair(true)
            view.setDiagnosticIndicatorStyle(DiagnosticIndicatorStyle.WAVY_LINE)
            view.props.drawSideBlockLine = settings.bracketGuides
            view.props.highlightMatchingDelimiters = true
            view.props.stickyScroll = false
            view.setColorScheme(scheme)
        }
    }

    fun forgetPlace(name: String) {
        caretByFile.remove(name)
        scrollByFile.remove(name)
        latestPlace(caretByFile.toMap(), scrollByFile.toMap())
    }

    DisposableEffect(editor) {
        val view = editor
        val autoComplete = view.getComponent(EditorAutoCompletion::class.java)
        fun refreshAutoCompleteOpen() {
            view.postDelayed({
                val shown = runCatching { autoComplete?.isShowing == true }.getOrDefault(false)
                autoCompleteOpen = shown
                latestSuggestionsOpen(shown)
            }, 60L)
        }
        val subscriptions = listOf(
            view.subscribeEvent(ContentChangeEvent::class.java, EventReceiver { _, _ ->
                textRevision++
                viewportTick++
                val text = view.text.toString()
                latestSource.set(text)
                latestBuffer(text)
                latestCaret(view.cursor.getLeft())
                caretByFile[latestActive] = view.cursor.getLeft()
                caretLine = view.cursor.leftLine + 1
                hasSelection = view.cursor.isSelected
                latestHistory(view.canUndo(), view.canRedo())
                refreshAutoCompleteOpen()
            }),
            view.subscribeEvent(SelectionChangeEvent::class.java, EventReceiver { _, _ ->
                viewportTick++
                val caret = view.cursor.getLeft()
                latestCaret(caret)
                caretByFile[latestActive] = caret
                caretLine = view.cursor.leftLine + 1
                hasSelection = view.cursor.isSelected
                refreshAutoCompleteOpen()
            }),
            view.subscribeEvent(ScrollEvent::class.java, EventReceiver { event, _ ->
                viewportTick++
                val lastLine = (view.text.lineCount - 1).coerceAtLeast(0)
                scrollByFile[latestActive] = view.firstVisibleLine.coerceIn(0, lastLine) to view.offsetX
                if (view.text.length > SoraHighlightLimit) {
                    windowState = soraHighlightWindow(
                        view.firstVisibleLine,
                        view.lastVisibleLine,
                        view.text.lineCount,
                        windowState,
                    )
                }
                refreshAutoCompleteOpen()
            }),
            view.subscribeEvent(EditorKeyEvent::class.java, EventReceiver { event, _ ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    event.isCtrlPressed &&
                    event.keyCode == KeyEvent.KEYCODE_S
                ) {
                    event.intercept()
                    latestSave()
                }
            }),
        )
        onDispose {
            subscriptions.forEach { it.unsubscribe() }
            latestPlace(caretByFile.toMap(), scrollByFile.toMap())
        }
    }

    LaunchedEffect(
        editor,
        settings.autoCloseBrackets,
        settings.autoIndent,
        settings.tabSize,
        settings.autoImport,
        completionProvider,
        sourceProvider,
    ) {
        editor.setEditorLanguage(
            CloseCodeLanguage(
                tabWidth = settings.tabSize,
                autoClose = settings.autoCloseBrackets,
                autoIndent = settings.autoIndent,
                autoImport = settings.autoImport,
                sourceProvider = sourceProvider,
                completionProvider = completionProvider,
            ),
        )
    }

    LaunchedEffect(
        editor,
        settings.fontSize,
        settings.lineNumbers,
        settings.wordWrap,
        settings.bracketGuides,
        settings.codeFont,
        editorScale,
        scheme,
    ) {
        editor.setTypefaceText(if (settings.codeFont == "System") Typeface.MONOSPACE else codeFont)
        editor.setTextSize(zoomedFontSize(settings.fontSize, editorScale).toFloat())
        editor.setLineNumberEnabled(settings.lineNumbers)
        editor.setLineNumberAlign(Paint.Align.RIGHT)
        editor.setLineNumberMarginLeft(20f * context.resources.displayMetrics.density)
        editor.setFirstLineNumberAlwaysVisible(true)
        editor.setWordwrap(settings.wordWrap)
        editor.setTabWidth(settings.tabSize)
        editor.setBlockLineEnabled(settings.bracketGuides)
        editor.props.drawSideBlockLine = settings.bracketGuides
        editor.setColorScheme(scheme)
    }


    LaunchedEffect(editor, active, sourceRevision) {
        val view = editor
        val text = contentFor(active)
        val force = sourceRevision != appliedSourceRevision
        if (view.text.toString() != text || force) {
            view.setText(text)
            appliedSourceRevision = sourceRevision
        }
        latestSource.set(view.text.toString())
        viewportTick++
        val offset = (caretByFile[active] ?: 0).coerceIn(0, text.length)
        val position = view.text.indexer.getCharPosition(offset)
        view.setSelection(position.line, position.column)
        val scroll = scrollByFile[active]
        val lastLine = (view.text.lineCount - 1).coerceAtLeast(0)
        val targetLine = scroll?.first?.coerceIn(0, lastLine) ?: 0
        if (targetLine > 0) view.jumpToLine(targetLine)
        else view.ensurePositionVisible(position.line, position.column)
        windowState = if (view.text.length > SoraHighlightLimit) {
            soraHighlightWindow(targetLine, targetLine, view.text.lineCount, IntRange.EMPTY)
        } else {
            IntRange.EMPTY
        }
        latestCaret(offset)
        caretLine = view.cursor.leftLine + 1
        hasSelection = view.cursor.isSelected
        latestHistory(view.canUndo(), view.canRedo())
        latestPlace(caretByFile.toMap(), scrollByFile.toMap())
    }

    LaunchedEffect(caretRequest) {
        if (caretRequest < 0) return@LaunchedEffect
        val view = editor
        val offset = caretRequest.coerceIn(0, editor.text.length)
        val position = view.text.indexer.getCharPosition(offset)
        view.setSelection(position.line, position.column)
        view.ensurePositionVisible(position.line, position.column)
    }

    LaunchedEffect(undoRequest) {
        if (undoRequest == 0) return@LaunchedEffect
        editor.undo()
        latestHistory(editor.canUndo(), editor.canRedo())
    }

    LaunchedEffect(redoRequest) {
        if (redoRequest == 0) return@LaunchedEffect
        editor.redo()
        latestHistory(editor.canUndo(), editor.canRedo())
    }

    LaunchedEffect(formatRequest) {
        if (formatRequest == 0) return@LaunchedEffect
        val view = editor
        val text = view.text.toString()
        val caret = view.cursor.getLeft()
        val formatted = if (activeKind == FileKind.Kotlin || active.endsWith(".kt", true)) {
            SourceEdits.formatKotlin(text)
        } else {
            SourceEdits.normalizeTrailingWhitespace(text)
        }
        if (formatted == text) {
            latestToast("Already formatted")
            return@LaunchedEffect
        }
        val position = view.text.indexer.getCharPosition(caret.coerceIn(0, editor.text.length))
        view.setText(formatted)
        val line = position.line.coerceIn(0, view.text.lineCount - 1)
        val column = position.column.coerceIn(0, view.text.getColumnCount(line))
        val newCaret = view.text.getCharIndex(line, column)
        val newPosition = view.text.indexer.getCharPosition(newCaret)
        view.setSelection(newPosition.line, newPosition.column)
        latestCaret(newCaret)
        latestBuffer(formatted)
        latestHistory(view.canUndo(), view.canRedo())
        latestToast("Formatted $active")
    }

    LaunchedEffect(formatSelectionRequest) {
        if (formatSelectionRequest == 0) return@LaunchedEffect
        val view = editor
        val text = view.text.toString()
        val from = view.cursor.left
        val to = view.cursor.right
        if (from >= to) {
            latestToast("Select the lines to format")
            return@LaunchedEffect
        }
        val formatted = if (SourceEdits.selectionCoversWholeLines(text, from, to)) {
            SourceEdits.formatRange(text, from, to, settings.tabSize)
        } else if (activeKind == FileKind.Kotlin || active.endsWith(".kt", true)) {
            SourceEdits.formatKotlin(text)
        } else {
            SourceEdits.normalizeTrailingWhitespace(text)
        }
        if (formatted == text) {
            latestToast("Already formatted")
            return@LaunchedEffect
        }
        val caret = from.coerceIn(0, formatted.length)
        view.setText(formatted)
        val position = view.text.indexer.getCharPosition(caret)
        view.setSelection(position.line, position.column)
        latestSource.set(formatted)
        latestBuffer(formatted)
        latestCaret(caret)
        latestHistory(view.canUndo(), view.canRedo())
        latestToast("Formatted selection")
    }

    LaunchedEffect(
        active,
        textRevision,
        editor,
        highlightLanguage,
        colors,
        palette,
        windowState,
        settings.autoCloseBrackets,
        settings.autoIndent,
        settings.tabSize,
        settings.rainbowBrackets,
    ) {
        val view = editor
        val generation = highlightGeneration.incrementAndGet()
        val requestedWindow = windowState
        delay(if (textRevision == 0) 0 else SoraHighlightDelayMs)
        val snapshot = view.text.toString()
        val lineCount = view.text.lineCount
        val windowed = snapshot.length > SoraHighlightLimit
        val bracketCycle = if (settings.rainbowBrackets) {
            listOf(colors.bracket1, colors.bracket2, colors.bracket3, colors.bracket4, colors.bracket5)
        } else {
            emptyList()
        }
        val result = withContext(Dispatchers.Default) {
            if (!windowed) {
                val annotated = highlightCode(snapshot, colors, highlightLanguage, bracketCycle)
                Triple(0, buildSoraLines(annotated, snapshot, palette), snapshot.length)
            } else {
                val starts = lineStarts(snapshot)
                val lastLine = (starts.size - 1).coerceAtLeast(0)
                val requested = if (requestedWindow == IntRange.EMPTY) {
                    0..lastLine.coerceAtMost(600)
                } else {
                    requestedWindow
                }
                val start = requested.first.coerceIn(0, lastLine)
                val end = requested.last.coerceIn(start, lastLine)
                val startOffset = starts.getOrElse(start) { snapshot.length }
                val endOffset = if (end + 1 < starts.size) starts[end + 1] else snapshot.length
                val slice = snapshot.substring(startOffset, endOffset)
                val contexts = highlightContexts(snapshot, highlightLanguage)
                val context = contexts.getOrNull(start) ?: HighlightContext()
                val depth = if (bracketCycle.isEmpty()) {
                    0
                } else {
                    openBlockDepth(snapshot.substring(0, startOffset))
                }
                val annotated = highlightCode(
                    slice,
                    colors,
                    highlightLanguage,
                    bracketCycle = bracketCycle,
                    initialDepth = depth,
                    initialContext = context,
                )
                Triple(start, buildSoraLines(annotated, slice, palette), snapshot.length)
            }
        }
        if (generation == highlightGeneration.get() &&
            result.third == view.text.length &&
            snapshot == view.text.toString()
        ) {
            val (start, built, _) = result
            val spans = HashMap<Int, List<Span>>(built.size * 2)
            built.forEachIndexed { index, line ->
                if (line.isNotEmpty()) {
                    spans[start + index] = line.map { SpanFactory.obtain(it.column, it.style) }
                }
            }
            view.setStyles(Styles(SoraSpans(spans, view.text.lineCount)))
        }
    }

    LaunchedEffect(active) {
        editor.setDiagnostics(null)
    }

    LaunchedEffect(Unit) {
        AndroidApiAssets.load(context) { apiIndex = it }
    }

    LaunchedEffect(
        active,
        textRevision,
        activeKind,
        highlightLanguage,
        settings.diagnostics,
        apiIndex,
        projectSymbols,
    ) {
        val view = editor
        val generation = diagnosticGeneration.incrementAndGet()
        val isKotlin = language.contains("Kotlin", true) ||
            active.endsWith(".kt", true) ||
            activeKind == FileKind.Kotlin
        val isMarkup = highlightLanguage == CodeLanguage.MARKUP ||
            activeKind == FileKind.Html ||
            activeKind == FileKind.Xml ||
            active.endsWith(".html", true) ||
            active.endsWith(".htm", true) ||
            active.endsWith(".xml", true)
        if (!isKotlin && !isMarkup) {
            view.setDiagnostics(null)
            currentAnalysis = null
            problems = ProblemsByLine()
            return@LaunchedEffect
        }
        if (!settings.diagnostics) view.setDiagnostics(null)
        delay(if (textRevision == 0) 0 else SoraDiagnosticsDelayMs)
        val snapshot = view.text.toString()
        if (snapshot.length > SoraDiagnosticsLimit) {
            view.setDiagnostics(null)
            currentAnalysis = null
            problems = ProblemsByLine()
            return@LaunchedEffect
        }
        val analysis = withContext(Dispatchers.Default) {
            if (isKotlin) {
                runCatching { KotlinAnalyzer.analyze(snapshot, apiIndex, projectSymbols) }.getOrNull()
            } else {
                null
            }
        }
        val diagnostics = analysis?.diagnostics ?: withContext(Dispatchers.Default) {
            htmlTagDiagnostics(snapshot)
        }
        if (generation != diagnosticGeneration.get() || snapshot != view.text.toString()) {
            return@LaunchedEffect
        }
        currentAnalysis = analysis
        problems = ProblemsByLine.from(if (settings.diagnostics) diagnostics else emptyList())
        if (!settings.diagnostics) {
            view.setDiagnostics(null)
            return@LaunchedEffect
        }
        val container = DiagnosticsContainer()
        diagnosticMarks(snapshot.length, diagnostics).forEachIndexed { index, mark ->
            container.addDiagnostic(
                DiagnosticRegion(
                    mark.start,
                    mark.end,
                    mark.severity,
                    index.toLong(),
                    DiagnosticDetail(mark.message, mark.message, emptyList(), mark.code),
                ),
            )
        }
        view.setDiagnostics(container)
    }

    LaunchedEffect(
        active,
        textRevision,
        caretLine,
        hasSelection,
        autoCompleteOpen,
        settings.parameterHints,
        problemSheetLine.value,
        quickFixLine,
    ) {
        if (!settings.parameterHints || hasSelection || autoCompleteOpen ||
            problemSheetLine.value != null || quickFixLine != null
        ) {
            signatureOptions = emptyList()
            return@LaunchedEffect
        }
        delay(80)
        val source = editor.text.toString()
        val cursor = editor.cursor.getLeft().coerceIn(0, source.length)
        val found = withContext(Dispatchers.Default) {
            runCatching { signatureHelpAll(source, cursor, apiIndex) }.getOrDefault(emptyList())
        }
        if (source != editor.text.toString() || cursor != editor.cursor.getLeft()) {
            return@LaunchedEffect
        }
        signatureOptions = found
        if (signatureChoice >= found.size) signatureChoice = 0
    }

    val quickFixActions = remember(
        quickFixLine,
        problems,
        currentAnalysis,
        textRevision,
        settings.quickFixes,
    ) {
        val line = quickFixLine
        val source = latestSource.get()
        val analysis = currentAnalysis?.takeIf { it.source == source }
        if (!settings.quickFixes || line == null || analysis == null) {
            emptyList()
        } else {
            buildQuickFixActions(line, problems, analysis, source)
        }
    }
    val caretQuickFixActions = remember(
        caretLine,
        problems,
        currentAnalysis,
        textRevision,
        settings.quickFixes,
    ) {
        val source = latestSource.get()
        val analysis = currentAnalysis?.takeIf { it.source == source }
        if (!settings.quickFixes || analysis == null) {
            emptyList()
        } else {
            buildQuickFixActions(caretLine, problems, analysis, source)
        }
    }

    fun applyQuickFix(action: QuickFixAction) {
        val target = quickFixActions.firstOrNull { it.label == action.label } ?: return
        val view = editor
        val source = view.text.toString()
        val caret = view.cursor.getLeft().coerceIn(0, source.length)
        val line = target.target.toIntOrNull()
        val result: Pair<String, Int>? = when (target.kind) {
            QuickFixKind.ADD_IMPORT -> {
                val next = SourceEdits.addImport(source, target.target)
                if (next == source) null
                else next to (SourceEdits.caretAfterAddImport(source, target.target) ?: next.length)
            }
            QuickFixKind.REMOVE_IMPORT -> {
                val next = SourceEdits.removeImport(source, target.target)
                if (next == source) null else next to caret.coerceAtMost(next.length)
            }
            QuickFixKind.REMOVE_TOKEN -> {
                val diagnostic = problems.on(line ?: -1)
                    .firstOrNull { it.code == com.closecode.lang.DiagnosticCodes.E0101_UNRESOLVED }
                if (diagnostic == null) {
                    null
                } else {
                    SourceEdits.removeToken(source, diagnostic.offset, diagnostic.length)
                        ?.let { it to diagnostic.offset.coerceIn(0, it.length) }
                }
            }
            QuickFixKind.ADD_SEMICOLON -> line?.let { SourceEdits.addSemicolon(source, it) }
                ?.let { it to it.length }
            QuickFixKind.TOGGLE_COMMENT -> line?.let {
                val next = SourceEdits.toggleLineComment(source, it)
                if (next == source) null else next to caret.coerceAtMost(next.length)
            }
            QuickFixKind.MOVE_LINE_UP, QuickFixKind.MOVE_LINE_DOWN -> line?.let {
                val down = target.kind == QuickFixKind.MOVE_LINE_DOWN
                val next = if (down) SourceEdits.moveLineDown(source, it) else SourceEdits.moveLineUp(source, it)
                if (next == source) {
                    null
                } else {
                    val column = view.cursor.leftColumn
                    next to SourceEdits.caretAfterLineMove(source, it, column, down)
                        .coerceIn(0, next.length)
                }
            }
            QuickFixKind.NO_FIX -> null
        }
        if (result == null) {
            quickFixLine = null
            return
        }
        val (next, newCaret) = result
        view.setText(next)
        val position = view.text.indexer.getCharPosition(newCaret.coerceIn(0, next.length))
        view.setSelection(position.line, position.column)
        latestSource.set(next)
        latestBuffer(next)
        latestCaret(newCaret)
        latestHistory(view.canUndo(), view.canRedo())
        quickFixLine = null
    }

    val gitMarks by produceState<Map<Int, GitMark>>(
        initialValue = emptyMap(),
        relativePath,
        textRevision,
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
            val marks: Map<Int, GitChangeKind> =
                GitService(root).bufferMarks(path, latestSource.get())
                    .getOrElse { emptyMap() }
            marks.mapValues { (_, kind) -> GitMark.valueOf(kind.name) }
        }
    }

    val sheetProblems = if (problemSheetScope == ProblemScope.PROJECT) {
        projectDiagnostics
    } else {
        val name = active.substringAfterLast('/')
        problems.all.map { ProjectDiagnostic(name, it) }
    }
    val visibleProblemCount = if (problemSheetScope == ProblemScope.PROJECT) {
        projectDiagnostics.size
    } else {
        problems.all.size
    }
    val firstProblem = problems.all.firstOrNull()

    LaunchedEffect(caretLine, quickFixLine) {
        if (quickFixLine != null && quickFixLine != caretLine) quickFixLine = null
    }

    LaunchedEffect(active, textRevision, pendingProblemJump) {
        val jump = pendingProblemJump ?: return@LaunchedEffect
        if (active.substringAfterLast('/') != jump.first) return@LaunchedEffect
        delay(80)
        val text = editor.text
        val line = (jump.second - 1).coerceIn(0, text.lineCount - 1)
        val column = (jump.third - 1).coerceIn(0, text.getColumnCount(line))
        editor.setSelection(line, column)
        editor.ensurePositionVisible(line, column)
        pendingProblemJump = null
    }

    LaunchedEffect(Unit) { onSuggestionsOpen(false) }

    LaunchedEffect(active, tabs.size) {
        val index = tabs.indexOfFirst { it.path == active }
        if (index >= 0) tabListState.animateScrollToItem(index)
    }

    Column(
        modifier
            .fillMaxHeight()
            .background(colors.editor),
    ) {
        if (tabs.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TAB_STRIP_HEIGHT)
                    .background(colors.editorBg)
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    state = tabListState,
                    contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(tabs, key = { _, tab -> "sora-tab-${tab.path}" }) { _, tab ->
                        val isActive = tab.path == active
                        val duplicates = tabs.count { it.name == tab.name } > 1
                        val tabLabel = if (duplicates) {
                            tab.path.substringBeforeLast('/').substringAfterLast('/')
                                .takeIf { it.isNotEmpty() }?.let { "$it/${tab.name}" } ?: tab.name
                        } else {
                            tab.name
                        }
                        Box {
                            Box(
                                Modifier
                                    .height(TAB_HEIGHT)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(if (isActive) colors.mock.accent else colors.mock.field)
                                    .combinedClickable(
                                        onClick = {
                                            caretByFile[active] = editor.cursor.getLeft()
                                            scrollByFile[active] =
                                                editor.firstVisibleLine to editor.offsetX
                                            onSelectTab(tab.path)
                                        },
                                        onLongClick = { tabMenuFor = tab.path },
                                    )
                                    .padding(horizontal = 15.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ellipsizeMiddle(tabLabel),
                                    color = if (isActive) Color.White else colors.mock.textSoft,
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
                if (visibleProblemCount > 0) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                problemSheetLine.value = firstProblem?.line ?: 1
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon(
                            CcIcons.Warning,
                            colors.errorPillTx,
                            18.dp,
                        )
                    }
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { editor.beginSearchMode() },
                            onLongClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Search, colors.mock.textSoft, 18.dp)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { editorSize = it },
        ) {
            AndroidView(
                factory = { editor },
                update = { },
                onRelease = { it.release() },
                modifier = Modifier.fillMaxSize(),
            )

            val rowHeight = editor.rowHeight.toFloat()
            val codeLeftPx = editor.measureTextRegionOffset().coerceAtLeast(0f)
            val gapPx = with(density) { 6.dp.toPx() }
            fun rowTop(line: Int): Float {
                val zero = (line - 1).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
                val index = runCatching { editor.text.getCharIndex(zero, 0) }.getOrDefault(0)
                val row = runCatching { editor.layout.getRowIndexForPosition(index) }.getOrDefault(zero)
                return row * rowHeight - editor.offsetY
            }
            val caretTop = remember(viewportTick, editorSize, caretLine) { rowTop(caretLine) }
            val quickTop = remember(viewportTick, editorSize, quickFixLine) {
                quickFixLine?.let { rowTop(it) } ?: 0f
            }

            if (gitMarks.isNotEmpty()) {
                Canvas(
                    Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .align(Alignment.TopStart),
                ) {
                    val first = (editor.firstVisibleLine - 2).coerceAtLeast(0)
                    val last = (editor.lastVisibleLine + 3).coerceAtMost(editor.text.lineCount)
                    for (line in first until last) {
                        val mark = gitMarks[line + 1] ?: continue
                        val top = rowTop(line + 1)
                        if (top > size.height || top + rowHeight < 0f) continue
                        when (mark) {
                            GitMark.Added -> drawRect(
                                color = colors.gitAdded,
                                topLeft = Offset(0f, top + 1f),
                                size = androidx.compose.ui.geometry.Size(size.width, (rowHeight - 2f).coerceAtLeast(1f)),
                            )
                            GitMark.Modified -> drawRect(
                                color = colors.gitModified,
                                topLeft = Offset(0f, top + 1f),
                                size = androidx.compose.ui.geometry.Size(size.width, (rowHeight - 2f).coerceAtLeast(1f)),
                            )
                            GitMark.Deleted -> {
                                val path = Path().apply {
                                    moveTo(0f, top + rowHeight - 5f)
                                    lineTo(size.width * 2f, top + rowHeight)
                                    lineTo(0f, top + rowHeight + 5f)
                                    close()
                                }
                                drawPath(path, colors.gitDeleted)
                            }
                        }
                    }
                }
            }

            if (quickFixLine == null && caretQuickFixActions.isNotEmpty() && !hasSelection) {
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                x = with(density) { 2.dp.roundToPx() },
                                y = (caretTop + (rowHeight - with(density) { 20.dp.toPx() }) / 2f).toInt(),
                            )
                        }
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.mock.panel)
                        .clickable {
                            quickFixIndex = 0
                            quickFixLine = caretLine
                            problemSheetLine.value = null
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(CcIcons.Bulb, colors.bulbGlyph, 14.dp)
                }
            }

            if (quickFixLine != null && quickFixActions.isNotEmpty()) {
                val cardWidth = with(density) {
                    minOf(300.dp.toPx(), (editorSize.width - codeLeftPx - 16.dp.toPx()).coerceAtLeast(120.dp.toPx()))
                }
                IdeActionCard(
                    rows = quickFixActions.map { it.label to null },
                    selected = quickFixIndex.coerceIn(0, quickFixActions.lastIndex),
                    onSelect = { index ->
                        quickFixIndex = index
                        quickFixActions.getOrNull(index)?.let(::applyQuickFix)
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = codeLeftPx.toInt(),
                                y = (quickTop + rowHeight + gapPx)
                                    .coerceAtMost(editorSize.height - with(density) { 120.dp.toPx() })
                                    .toInt(),
                            )
                        }
                        .width(with(density) { cardWidth.toDp() }),
                )
            }

            val signatureInfo = signatureOptions.getOrNull(
                signatureChoice.coerceIn(0, signatureOptions.lastIndex.coerceAtLeast(0)),
            )
            val signatureVisible = settings.parameterHints &&
                signatureInfo != null &&
                !hasSelection &&
                !autoCompleteOpen &&
                problemSheetLine.value == null &&
                quickFixLine == null
            if (signatureVisible) {
                val signatureWidth = with(density) {
                    minOf(430.dp.toPx(), (editorSize.width - codeLeftPx - 16.dp.toPx()).coerceAtLeast(140.dp.toPx()))
                }
                val measured = signatureMeasuredHeight.toFloat()
                val below = caretTop + rowHeight + gapPx
                val above = caretTop - measured - gapPx
                val top = if (measured > 0f && below + measured > editorSize.height - gapPx) {
                    above.coerceAtLeast(gapPx)
                } else {
                    below
                }
                SignatureHelpTooltip(
                    info = signatureInfo!!,
                    overloadLabel = signatureOptions.size.takeIf { it > 1 }
                        ?.let { n -> "${signatureChoice.coerceIn(0, n - 1) + 1}/$n" },
                    onOverloadTap = {
                        if (signatureOptions.size > 1) {
                            signatureChoice = (signatureChoice + 1) % signatureOptions.size
                        }
                    },
                    modifier = Modifier
                        .offset { IntOffset(codeLeftPx.toInt(), top.toInt()) }
                        .width(with(density) { signatureWidth.toDp() })
                        .onSizeChanged { signatureMeasuredHeight = it.height },
                )
            }

            if (problemSheetLine.value != null) {
                ProblemSheet(
                    problems = sheetProblems,
                    scope = problemSheetScope,
                    fileLabel = active.substringAfterLast('/'),
                    onSelect = { row ->
                        if (row.fileName != active.substringAfterLast('/')) {
                            pendingProblemJump = Triple(
                                row.fileName,
                                row.diagnostic.line,
                                row.diagnostic.column,
                            )
                            onOpenProjectFile(row.fileName)
                        } else {
                            val text = editor.text
                            val line = (row.diagnostic.line - 1).coerceIn(0, text.lineCount - 1)
                            val column = (row.diagnostic.column - 1)
                                .coerceIn(0, text.getColumnCount(line))
                            editor.setSelection(line, column)
                            editor.ensurePositionVisible(line, column)
                        }
                        problemSheetLine.value = null
                    },
                    onDismiss = { problemSheetLine.value = null },
                    onAskAi = { row ->
                        problemSheetLine.value = null
                        onAskDiagnostic(row.fileName, row.diagnostic.line, row.diagnostic.message)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                )
            }
        }

        if (showToolRow) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(EditorKeyRowHeight)
                    .imePadding(),
            ) {
                EditorKeyRow(
                    onKey = { key ->
                        editor.requestFocus()
                        when {
                            key.moveRight -> {
                                val line = editor.cursor.leftLine
                                val column = (editor.cursor.leftColumn + 1)
                                    .coerceAtMost(editor.text.getColumnCount(line))
                                editor.setSelection(line, column)
                            }
                            key.label == "Tab" -> editor.commitText(" ".repeat(settings.tabSize))
                            key.insert.isNotEmpty() -> {
                                editor.commitText(key.insert)
                                if (key.caretBack > 0) {
                                    val target = (editor.cursor.getLeft() - key.caretBack)
                                        .coerceIn(0, editor.text.length)
                                    val position = editor.text.indexer.getCharPosition(target)
                                    editor.setSelection(position.line, position.column)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

internal fun soraHighlightWindow(
    firstVisible: Int,
    lastVisible: Int,
    lineCount: Int,
    current: IntRange,
): IntRange {
    if (lineCount <= 0) return 0..0
    val first = firstVisible.coerceIn(0, lineCount - 1)
    val last = lastVisible.coerceIn(first, lineCount - 1)
    val needsRefresh = current == IntRange.EMPTY ||
        first < current.first + SoraWindowRefreshMargin ||
        last > current.last - SoraWindowRefreshMargin
    if (!needsRefresh) return current
    val start = (first - SoraWindowPrefetchAbove).coerceAtLeast(0)
    val end = (last + SoraWindowPrefetchBelow).coerceAtMost(lineCount - 1)
    return start..end
}

internal data class DiagnosticMark(
    val start: Int,
    val end: Int,
    val severity: Short,
    val message: String,
    val code: String,
)

internal fun diagnosticMarks(
    textLength: Int,
    diagnostics: List<Diagnostic>,
): List<DiagnosticMark> = diagnostics.mapNotNull { diagnostic ->
    val start = diagnostic.offset.coerceIn(0, textLength)
    val end = (start + diagnostic.length.coerceAtLeast(1)).coerceAtMost(textLength)
    if (end <= start) return@mapNotNull null
    DiagnosticMark(
        start = start,
        end = end,
        severity = if (diagnostic.severity == Severity.ERROR) {
            DiagnosticRegion.SEVERITY_ERROR
        } else {
            DiagnosticRegion.SEVERITY_WARNING
        },
        message = diagnostic.message,
        code = diagnostic.code,
    )
}

private class SoraSpans(
    private val lines: Map<Int, List<Span>>,
    private val lineCount: Int,
) : Spans {
    private val defaultSpan = SpanFactory.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
    private val defaultSpans = listOf(defaultSpan)

    private fun spansFor(line: Int): List<Span> =
        lines[line]?.takeIf { it.isNotEmpty() } ?: defaultSpans

    override fun adjustOnInsert(start: CharPosition, end: CharPosition) = Unit

    override fun adjustOnDelete(start: CharPosition, end: CharPosition) = Unit

    override fun read(): Spans.Reader = object : Spans.Reader {
        private var line = 0
        override fun moveToLine(line: Int) {
            this.line = line
        }

        override fun getSpanCount(): Int = spansFor(line).size
        override fun getSpanAt(index: Int): Span = spansFor(line).getOrElse(index) { defaultSpan }
        override fun getSpansOnLine(line: Int): List<Span> = spansFor(line)
    }

    override fun supportsModify(): Boolean = false
    override fun modify(): Spans.Modifier = throw UnsupportedOperationException()
    override fun getLineCount(): Int = lineCount
}

private class CloseCodeLanguage(
    private val tabWidth: Int,
    private val autoClose: Boolean,
    private val autoIndent: Boolean,
    private val autoImport: Boolean,
    private val sourceProvider: () -> String,
    private val completionProvider: (String, Int) -> List<SoraCompletionModel>,
) : EmptyLanguage() {
    override fun getSymbolPairs(): SymbolPairMatch =
        if (autoClose) SymbolPairMatch.DefaultSymbolPairs() else EMPTY_SYMBOL_PAIRS

    override fun getIndentAdvance(ref: ContentReference, line: Int, column: Int): Int {
        if (!autoIndent || line <= 0) return 0
        val previous = ref.getLine(line - 1).trimEnd()
        if (!previous.endsWith("{") && !previous.endsWith("(") && !previous.endsWith("[")) return 0
        val current = ref.getLine(line).take(column).trimStart()
        val closing = current.startsWith("}") || current.startsWith(")") || current.startsWith("]")
        return if (closing) 0 else tabWidth
    }

    override fun requireAutoComplete(
        ref: ContentReference,
        pos: CharPosition,
        publisher: CompletionPublisher,
        environment: android.os.Bundle,
    ) {
        val source = sourceProvider()
        val offset = pos.index.coerceIn(0, source.length)
        val models = completionProvider(source, offset)
        if (models.isEmpty()) return
        var start = offset
        while (start > 0) {
            val ch = source[start - 1]
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-') start-- else break
        }
        val prefixLength = offset - start
        val items = models.map { model ->
            soraEditorCompletionItem(model, prefixLength, autoImport)
        }
        publisher.addItems(items)
        publisher.updateList()
    }

    override fun useTab(): Boolean = false
}

private class SoraEditorCompletionItem(
    private val model: SoraCompletionModel,
    prefixLength: Int,
    autoImport: Boolean,
) : CompletionItem(model.label, model.detail ?: model.subtitle) {
    private val importFqn: String? = if (
        autoImport &&
        (model.kind == CompletionKind.CLASS || model.kind == CompletionKind.STDLIB_CLASS)
    ) {
        model.subtitle?.let { subtitle ->
            when {
                subtitle.endsWith(".${model.label}") -> subtitle
                subtitle.isNotBlank() -> "$subtitle.${model.label}"
                else -> null
            }
        }
    } else {
        null
    }

    init {
        this.prefixLength = prefixLength
        kind = when (model.kind) {
            CompletionKind.CLASS, CompletionKind.STDLIB_CLASS -> CompletionItemKind.Class
            CompletionKind.FUNCTION, CompletionKind.STDLIB_FUNCTION -> CompletionItemKind.Function
            CompletionKind.PROPERTY, CompletionKind.STDLIB_MEMBER, CompletionKind.MEMBER ->
                CompletionItemKind.Property
            CompletionKind.PARAMETER, CompletionKind.LOCAL -> CompletionItemKind.Variable
            CompletionKind.ENUM_ENTRY -> CompletionItemKind.EnumMember
            CompletionKind.KEYWORD -> CompletionItemKind.Keyword
        }
    }

    override fun performCompletion(
        editor: CodeEditor,
        content: io.github.rosemoe.sora.text.Content,
        line: Int,
        column: Int,
    ) {
        val source = content.toString()
        val caret = content.getCharIndex(line, column).coerceIn(0, source.length)
        val start = (caret - prefixLength).coerceAtLeast(0)
        var next = source.substring(0, start) + model.insertText + source.substring(caret)
        var newCaret = start + model.insertText.length
        val fqn = importFqn
        if (fqn != null && fqn.contains('.')) {
            val imported = SourceEdits.addImport(next, fqn)
            if (imported != next) {
                newCaret = SourceEdits.caretAfterAddImport(next, fqn) ?: newCaret
                next = imported
            }
        }
        if (next == source) return
        content.replace(0, content.length, next)
        val position = editor.text.indexer.getCharPosition(newCaret.coerceIn(0, next.length))
        editor.setSelection(position.line, position.column)
    }
}

private fun soraEditorCompletionItem(
    model: SoraCompletionModel,
    prefixLength: Int,
    autoImport: Boolean,
): CompletionItem = SoraEditorCompletionItem(model, prefixLength, autoImport)

internal fun soraCompletionModels(
    source: String,
    offset: Int,
    language: String,
    activeKind: FileKind?,
    enabled: Boolean,
    api: com.closecode.lang.AndroidApiIndex,
    projectSymbols: Set<String>,
): List<SoraCompletionModel> {
    if (!enabled) return emptyList()
    val at = offset.coerceIn(0, source.length)
    val isKotlin = language.contains("Kotlin", true) ||
        activeKind == FileKind.Kotlin
    if (isKotlin) {
        val analysis = runCatching { KotlinAnalyzer.analyze(source, api, projectSymbols) }.getOrNull()
            ?: return emptyList()
        val prefix = runCatching { analysis.prefixAt(at) }.getOrDefault("")
        val member = runCatching { analysis.isMemberAccess(at) }.getOrDefault(false)
        if (prefix.isEmpty() && !member) return emptyList()
        return runCatching { analysis.completionsAt(at, limit = 25) }.getOrDefault(emptyList())
    }
    val markup = language.contains("HTML", true) ||
        activeKind == FileKind.Html ||
        activeKind == FileKind.Xml
    if (markup) {
        val context = runCatching { htmlTagContextAt(source, at) }.getOrNull()
        if (context != null) {
            val start = htmlContextOffset(source, at)
            val partial = source.substring(start.coerceIn(0, at), at)
            return if (context.afterName) {
                htmlAttributeSuggestions(context.tagName, partial).map {
                    SoraCompletionModel(it, CompletionKind.PROPERTY, "attribute", it)
                }
            } else {
                htmlTagSuggestions(partial).map {
                    SoraCompletionModel(it, CompletionKind.KEYWORD, "element", it)
                }
            }
        }
    }
    val tail = source.take(at).takeLastWhile { it.isLetterOrDigit() }
    if (tail.isEmpty()) return emptyList()
    return suggestionsFor(language)
        .filter { it.name.startsWith(tail, ignoreCase = true) }
        .take(20)
        .map { SoraCompletionModel(it.name, CompletionKind.KEYWORD, it.detail, it.name) }
}

private data class LineSpan(val column: Int, val style: Long)

private data class SpanSegment(val start: Int, val end: Int, val style: androidx.compose.ui.text.SpanStyle)

private data class Boundary(val column: Int, val add: Boolean, val style: androidx.compose.ui.text.SpanStyle, val order: Int)

private fun buildSoraLines(
    annotated: androidx.compose.ui.text.AnnotatedString,
    text: String,
    palette: Map<Int, Int>,
): List<List<LineSpan>> {
    val starts = lineStarts(text)
    val segments = Array(starts.size) { mutableListOf<SpanSegment>() }
    annotated.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, text.length)
        val end = range.end.coerceIn(start, text.length)
        if (start == end) return@forEach
        val firstLine = lineIndexAt(starts, start)
        val lastLine = lineIndexAt(starts, (end - 1).coerceAtLeast(0))
        for (line in firstLine..lastLine) {
            val lineStart = starts[line]
            val lineEnd = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
            val segmentStart = start.coerceAtLeast(lineStart) - lineStart
            val segmentEnd = end.coerceAtMost(lineEnd) - lineStart
            if (segmentEnd > segmentStart) {
                segments[line].add(SpanSegment(segmentStart, segmentEnd, range.item))
            }
        }
    }

    return segments.mapIndexed { line, lineSegments ->
        if (lineSegments.isEmpty()) return@mapIndexed emptyList()
        val lineStart = starts[line]
        val lineEnd = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
        val lineLength = (lineEnd - lineStart).coerceAtLeast(0)
        val boundaries = ArrayList<Boundary>(lineSegments.size * 2)
        var order = 0
        lineSegments.forEach { segment ->
            boundaries.add(Boundary(segment.start, true, segment.style, order++))
            boundaries.add(Boundary(segment.end, false, segment.style, order++))
        }
        boundaries.sortWith(
            compareBy<Boundary> { it.column }.thenBy { it.add }.thenBy { it.order },
        )
        val active = ArrayList<androidx.compose.ui.text.SpanStyle>()
        val out = ArrayList<LineSpan>()
        var previous = -1L
        var index = 0
        while (index < boundaries.size) {
            val column = boundaries[index].column
            while (index < boundaries.size && boundaries[index].column == column) {
                val boundary = boundaries[index]
                if (boundary.add) active.add(boundary.style) else active.remove(boundary.style)
                index++
            }
            val style = combinedStyle(active, palette)
            if (column < lineLength && style != previous) out.add(LineSpan(column, style))
            previous = style
        }
        out
    }
}

private fun combinedStyle(
    active: List<androidx.compose.ui.text.SpanStyle>,
    palette: Map<Int, Int>,
): Long {
    var colorId = EditorColorScheme.TEXT_NORMAL
    var bold = false
    var italic = false
    active.forEach { style ->
        val color = style.color
        if (color != Color.Unspecified) palette[color.toArgb()]?.let { id -> colorId = id }
        val weight = style.fontWeight?.weight ?: 400
        if (weight >= 500) bold = true
        if (style.fontStyle == FontStyle.Italic) italic = true
    }
    var result = TextStyle.makeStyle(colorId)
    if (bold) result = result or TextStyle.BOLD_BIT
    if (italic) result = result or TextStyle.ITALICS_BIT
    return result
}

private fun soraPalette(c: PageColors): Map<Int, Int> = mapOf(
    c.codeComment.toArgb() to EditorColorScheme.COMMENT,
    c.codeString.toArgb() to EditorColorScheme.LITERAL,
    c.codeFunction.toArgb() to EditorColorScheme.FUNCTION_NAME,
    c.codeKeyword.toArgb() to EditorColorScheme.KEYWORD,
    c.codeType.toArgb() to EditorColorScheme.IDENTIFIER_NAME,
    c.codeNumber.toArgb() to EditorColorScheme.LITERAL,
    c.codeVariable.toArgb() to EditorColorScheme.IDENTIFIER_VAR,
    c.mock.muted.toArgb() to EditorColorScheme.OPERATOR,
)

private fun soraColorScheme(c: PageColors): EditorColorScheme = EditorColorScheme().apply {
    applyDefault()
    setColor(EditorColorScheme.WHOLE_BACKGROUND, c.editor.toArgb())
    setColor(EditorColorScheme.TEXT_NORMAL, c.tx.toArgb())
    setColor(EditorColorScheme.CURRENT_LINE, c.activeLineBg.toArgb())
    setColor(EditorColorScheme.LINE_NUMBER, c.lineNumber.toArgb())
    setColor(EditorColorScheme.LINE_NUMBER_CURRENT, c.lineNumberActive.toArgb())
    setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, c.editor.toArgb())
    setColor(EditorColorScheme.LINE_DIVIDER, c.divider.toArgb())
    setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, c.textSelection.toArgb())
    setColor(EditorColorScheme.SCROLL_BAR_TRACK, c.editor.toArgb())
    setColor(EditorColorScheme.SCROLL_BAR_THUMB, c.scrollThumb.toArgb())
    setColor(EditorColorScheme.BLOCK_LINE, c.indentGuide.toArgb())
    setColor(EditorColorScheme.BLOCK_LINE_CURRENT, c.accent.toArgb())
    setColor(EditorColorScheme.SIDE_BLOCK_LINE, c.indentGuide.toArgb())
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, c.accent.copy(alpha = 0.20f).toArgb())
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, c.accent.toArgb())
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, c.tx.toArgb())
    setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, c.accent.toArgb())
    setColor(EditorColorScheme.LINE_NUMBER_PANEL, c.popupBg.toArgb())
    setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, c.popupText.toArgb())
    setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, c.popupBg.toArgb())
    setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, c.popupText.toArgb())
    setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, c.popupMuted.toArgb())
    setColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED, c.accent.toArgb())
    setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, c.popupSelected.toArgb())
    setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, c.popupBg.toArgb())
    setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, c.popupText.toArgb())
    setColor(EditorColorScheme.PROBLEM_ERROR, c.red.toArgb())
    setColor(EditorColorScheme.PROBLEM_WARNING, c.amber.toArgb())
    setColor(EditorColorScheme.PROBLEM_TYPO, c.purple.toArgb())
    setColor(EditorColorScheme.SELECTION_HANDLE, c.accent.toArgb())
    setColor(EditorColorScheme.SELECTION_INSERT, c.accent.toArgb())
    setColor(EditorColorScheme.CURRENT_ROW_BORDER, c.accent.toArgb())
    setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, c.accent.copy(alpha = 0.20f).toArgb())
    setColor(EditorColorScheme.MATCHED_TEXT_BORDER, c.accent.toArgb())
    setColor(EditorColorScheme.COMMENT, c.codeComment.toArgb())
    setColor(EditorColorScheme.KEYWORD, c.codeKeyword.toArgb())
    setColor(EditorColorScheme.LITERAL, c.codeString.toArgb())
    setColor(EditorColorScheme.FUNCTION_NAME, c.codeFunction.toArgb())
    setColor(EditorColorScheme.IDENTIFIER_NAME, c.codeType.toArgb())
    setColor(EditorColorScheme.IDENTIFIER_VAR, c.codeVariable.toArgb())
    setColor(EditorColorScheme.ANNOTATION, c.codeFunction.toArgb())
    setColor(EditorColorScheme.OPERATOR, c.mock.muted.toArgb())
    setColor(EditorColorScheme.HTML_TAG, c.codeKeyword.toArgb())
    setColor(EditorColorScheme.ATTRIBUTE_NAME, c.codeVariable.toArgb())
    setColor(EditorColorScheme.ATTRIBUTE_VALUE, c.codeString.toArgb())
}

private fun lineStarts(text: String): IntArray {
    val starts = ArrayList<Int>()
    starts.add(0)
    for (index in text.indices) if (text[index] == '\n') starts.add(index + 1)
    return starts.toIntArray()
}

private fun lineIndexAt(starts: IntArray, offset: Int): Int {
    var low = 0
    var high = starts.lastIndex
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (starts[mid] <= offset) low = mid + 1 else high = mid - 1
    }
    return high.coerceAtLeast(0)
}
