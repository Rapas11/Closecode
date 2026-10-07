package com.closecode.ui.ide

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.project.PreviewServer
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Extensions Preview renders as a bitmap rather than as text. */
private val ImageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/**
 * The conventional two-tone board behind a decoded bitmap, so a transparent PNG reads as
 * transparent instead of taking on whatever the page background happens to be.
 */
private fun DrawScope.checkerboard(even: Color, odd: Color) {
    val cell = 14.dp.toPx()
    var row = 0
    var y = 0f
    while (y < size.height) {
        var col = 0
        var x = 0f
        while (x < size.width) {
            val square = if ((row + col) % 2 == 0) even else odd
            drawRect(
                color = square,
                topLeft = Offset(x, y),
                size = Size(minOf(cell, size.width - x), minOf(cell, size.height - y)),
            )
            x += cell
            col++
        }
        y += cell
        row++
    }
}

/** Above this, images are downscaled before decoding so a large photo cannot exhaust memory. */
const val PREVIEW_MAX_IMAGE_EDGE = 2048

private fun extensionOf(name: String): String =
    name.substringAfterLast('.', "").lowercase()

/**
 * Chooses the HTML entry shown by live web preview. A root `index.html` wins; otherwise the open
 * HTML file wins; otherwise the first HTML file wins. Null means this project has no live page.
 */
internal fun webPreviewEntry(diskTree: List<ProjectFile>, activePath: String): String? {
    val byPath = diskTree.associateBy { it.path }
    if (byPath["index.html"]?.kind == FileKind.Html) return "index.html"
    val active = byPath[activePath]
    if (active?.kind == FileKind.Html) return active.path
    return diskTree.firstOrNull { it.kind == FileKind.Html }?.path
}

/** Which preview the panel draws. */
internal enum class PreviewRoute {
    /** A live page served from the project directory. */
    Web,

    /** A live page the panel cannot serve: the project's own entry exists but its root does not. */
    WebUnavailable,

    /** Nothing to show, because no tab is open and this project has no page of its own. */
    NoFile,

    /** The spec's static phone mock, for a Kotlin file. */
    KotlinMock,

    /** The ordinary file preview: image, JSON, XML, CSV, plain text. */
    Static,
}

/**
 * The order the panel tries things in, decided in one place so the order itself can be pinned.
 *
 * The web entry comes first on purpose. A project's own `index.html` is a preview whether or not
 * anyone has a file open, and telling someone to open a file so they can see a page that already
 * exists is a gate guarding nothing — the first thing on screen for a web project is the page.
 * Everything after it does need a file, because each of those previews *is* one.
 */
internal fun previewRoute(
    webEntry: String?,
    projectRootPresent: Boolean,
    fileName: String,
    fileKind: FileKind?,
): PreviewRoute = when {
    webEntry != null ->
        if (projectRootPresent) PreviewRoute.Web else PreviewRoute.WebUnavailable

    fileName.isBlank() -> PreviewRoute.NoFile

    fileKind == FileKind.Kotlin || fileName.endsWith(".kt", ignoreCase = true) ->
        PreviewRoute.KotlinMock

    else -> PreviewRoute.Static
}

internal data class ComposePreviewContent(
    val title: String,
    val subtitle: String?,
    val button: String?,
)

/**
 * Reads representative UI strings from Kotlin source for the static phone mock.
 *
 * This intentionally parses only enough Compose to find `Text("...")` labels and a `Button`
 * label. It is a preview aid, not a compiler; anything it cannot understand is omitted rather
 * than guessed.
 */
internal fun extractComposePreview(source: String, fileName: String): ComposePreviewContent {
    val texts = extractComposeTexts(source, limit = 5)
    val button = findComposeButtonLabel(source)
    val body = texts.filterNot { it == button }
    return ComposePreviewContent(
        title = body.firstOrNull()?.ifBlank { fileName } ?: fileName,
        subtitle = body.getOrNull(1),
        button = button ?: body.getOrNull(2),
    )
}

private fun extractComposeTexts(source: String, limit: Int): List<String> {
    val out = ArrayList<String>()
    var search = 0
    while (out.size < limit) {
        val call = findCallStart(source, "Text", search)
        if (call < 0) break
        val arguments = parseDelimitedRange(source, call + "Text".length, '(', ')')
        if (arguments == null) {
            search = call + 1
            continue
        }
        firstStringLiteralInRange(source, arguments)?.let { out.add(it) }
        search = arguments.last + 1
    }
    return out
}

private fun findComposeButtonLabel(source: String): String? {
    var search = 0
    while (true) {
        val call = findCallStart(source, "Button", search)
        if (call < 0) return null
        val arguments = parseDelimitedRange(source, call + "Button".length, '(', ')')
        if (arguments == null) {
            search = call + 1
            continue
        }
        var next = skipTriviaAndComments(source, arguments.last + 1)
        if (next < source.length && source[next] == '{') {
            val body = parseDelimitedRange(source, next, '{', '}')
            if (body != null) {
                firstTextLiteralAfter(source, body.first, body.last)?.let { return it }
            }
        }
        search = arguments.last + 1
    }
}

private fun firstTextLiteralAfter(source: String, from: Int, to: Int): String? {
    var search = from.coerceIn(0, source.length)
    val end = to.coerceIn(0, source.length)
    while (search <= end) {
        val call = findCallStart(source, "Text", search)
        if (call < 0 || call > end) return null
        val arguments = parseDelimitedRange(source, call + "Text".length, '(', ')')
        if (arguments == null) {
            search = call + 1
            continue
        }
        firstStringLiteralInRange(source, arguments.first.coerceAtLeast(0)..arguments.last.coerceAtMost(end))
            ?.let { return it }
        search = arguments.last + 1
    }
    return null
}

private fun isKotlinIdentifierChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'

private fun findCallStart(source: String, name: String, from: Int): Int {
    var i = from.coerceIn(0, source.length)
    var blockDepth = 0
    var inRawString = false
    var inDouble = false
    var inSingle = false
    var inBacktick = false
    var escaped = false
    while (i + name.length <= source.length) {
        if (inRawString) {
            if (source.startsWith("\"\"\"", i)) {
                inRawString = false
                i += 3
            } else {
                i++
            }
            continue
        }
        val ch = source[i]
        if (escaped) {
            escaped = false
            i++
            continue
        }
        if (inDouble) {
            if (ch == '\\') escaped = true
            else if (ch == '"') inDouble = false
            i++
            continue
        }
        if (inSingle) {
            if (ch == '\\') escaped = true
            else if (ch == '\'') inSingle = false
            i++
            continue
        }
        if (inBacktick) {
            if (ch == '`') inBacktick = false
            i++
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) source.length else newline + 1
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        if (source.startsWith("\"\"\"", i)) {
            inRawString = true
            i += 3
            continue
        }
        if (ch == '"') {
            inDouble = true
            i++
            continue
        }
        if (ch == '\'') {
            inSingle = true
            i++
            continue
        }
        if (ch == '`') {
            inBacktick = true
            i++
            continue
        }
        if (source.startsWith(name, i) && (i == 0 || !isKotlinIdentifierChar(source[i - 1]))) {
            var j = i + name.length
            while (j < source.length && source[j].isWhitespace()) j++
            if (j < source.length && source[j] == '(') return i
        }
        i++
    }
    return -1
}

private fun parseDelimitedRange(source: String, openIndex: Int, open: Char, close: Char): IntRange? {
    var i = openIndex.coerceIn(0, source.length)
    while (i < source.length && source[i].isWhitespace()) i++
    if (i >= source.length || source[i] != open) return null
    var depth = 0
    var blockDepth = 0
    var inRawString = false
    var inDouble = false
    var inSingle = false
    var inBacktick = false
    var escaped = false
    while (i < source.length) {
        if (inRawString) {
            if (source.startsWith("\"\"\"", i)) {
                inRawString = false
                i += 3
            } else {
                i++
            }
            continue
        }
        val ch = source[i]
        if (escaped) {
            escaped = false
            i++
            continue
        }
        if (inDouble) {
            if (ch == '\\') escaped = true
            else if (ch == '"') inDouble = false
            i++
            continue
        }
        if (inSingle) {
            if (ch == '\\') escaped = true
            else if (ch == '\'') inSingle = false
            i++
            continue
        }
        if (inBacktick) {
            if (ch == '`') inBacktick = false
            i++
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) source.length else newline + 1
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        if (source.startsWith("\"\"\"", i)) {
            inRawString = true
            i += 3
            continue
        }
        if (ch == '"') {
            inDouble = true
            i++
            continue
        }
        if (ch == '\'') {
            inSingle = true
            i++
            continue
        }
        if (ch == '`') {
            inBacktick = true
            i++
            continue
        }
        if (ch == open) depth++
        if (ch == close) {
            depth--
            if (depth == 0) return (openIndex + 1)..(i - 1)
        }
        i++
    }
    return null
}

private fun firstStringLiteralInRange(source: String, range: IntRange): String? {
    var i = range.first.coerceAtLeast(0)
    val end = range.last.coerceAtMost(source.length - 1)
    var blockDepth = 0
    while (i <= end) {
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) end + 1 else (newline + 1).coerceAtMost(end + 1)
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("\"\"\"", i)) {
            val close = source.indexOf("\"\"\"", i + 3)
            if (close < 0 || close > end) return null
            return source.substring(i + 3, close)
        }
        if (source[i] == '"') {
            return readKotlinStringLiteral(source, i)?.first
        }
        i++
    }
    return null
}

private fun readKotlinStringLiteral(source: String, start: Int): Pair<String, Int>? {
    if (start >= source.length || source[start] != '"') return null
    val out = StringBuilder()
    var i = start + 1
    while (i < source.length) {
        val ch = source[i]
        if (ch == '\\' && i + 1 < source.length) {
            when (val next = source[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                '\'', '"', '\\', '$' -> out.append(next)
                'u' -> {
                    val hex = source.substring(i + 2, (i + 6).coerceAtMost(source.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: next)
                    i += 6
                    continue
                }
                else -> {
                    out.append(ch)
                    out.append(next)
                }
            }
            i += 2
            continue
        }
        if (ch == '"') return out.toString() to (i + 1)
        if (ch == '\n') return null
        out.append(ch)
        i++
    }
    return null
}

private fun skipTriviaAndComments(source: String, from: Int): Int {
    var i = from.coerceIn(0, source.length)
    var blockDepth = 0
    while (i < source.length) {
        val ch = source[i]
        if (ch.isWhitespace()) {
            i++
            continue
        }
        if (blockDepth > 0) {
            if (source.startsWith("/*", i)) {
                blockDepth++
                i += 2
            } else if (source.startsWith("*/", i)) {
                blockDepth--
                i += 2
            } else {
                i++
            }
            continue
        }
        if (source.startsWith("//", i)) {
            val newline = source.indexOf('\n', i)
            i = if (newline < 0) source.length else newline + 1
            continue
        }
        if (source.startsWith("/*", i)) {
            blockDepth++
            i += 2
            continue
        }
        return i
    }
    return i
}

/** Calculate the sample size for downsampling an image. */
fun sampleSizeFor(
    srcWidth: Int,
    srcHeight: Int,
    maxEdge: Int = PREVIEW_MAX_IMAGE_EDGE,
): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var longest = maxOf(srcWidth, srcHeight)
    var sample = 1
    while (longest / 2 >= maxEdge) {
        longest /= 2
        sample *= 2
    }
    return sample
}

/** A decoded bitmap plus a note when it had to be downscaled. */
class PreviewImage(val bitmap: ImageBitmap, val note: String? = null)

/** Two-pass decode: bounds first, then a sampled decode. Never runs on the main thread. */
fun decodeScaled(bytes: ByteArray, maxEdge: Int = PREVIEW_MAX_IMAGE_EDGE): PreviewImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalArgumentException("Not a decodable image")
    val note = if (sample > 1) {
        "Downscaled to ${bitmap.width}x${bitmap.height} (original ${bounds.outWidth}x${bounds.outHeight})."
    } else {
        null
    }
    return PreviewImage(bitmap.asImageBitmap(), note)
}

/**
 * Hybrid preview: web projects get a live page from a local HTTP server, Kotlin files get the
 * spec's static phone mock, and all other assets keep the existing static file preview.
 */
@Composable
fun IdePreviewPanel(
    fileName: String,
    fileKind: FileKind?,
    projectPath: String,
    loader: (String) -> String?,
    binaryLoader: (String) -> ByteArray?,
    diskTree: List<ProjectFile>,
    projectRepository: ProjectRepository,
    refreshToken: Int = 0,
    onRefresh: () -> Unit = {},
    projectRoot: java.io.File? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val extension = remember(fileName) { extensionOf(fileName) }
    val isImage = extension in ImageExtensions
    val webEntry = remember(diskTree, projectPath) { webPreviewEntry(diskTree, projectPath) }
    // Hoisted out of WebPreview so the toolbar can hand the live page to the device's browser.
    var webUrl by remember(webEntry) { mutableStateOf<String?>(null) }
    val startingWeb = webEntry != null && projectRoot != null && webUrl == null

    Column(
        modifier
            .fillMaxSize().background(c.bg)) {
        PreviewToolbar(
            loading = startingWeb,
            canOpenInBrowser = webUrl != null,
            onRefresh = onRefresh,
            onOpenInBrowser = {
                webUrl?.let { url ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                        )
                    }
                }
            },
        )

        val route = previewRoute(
            webEntry = webEntry,
            projectRootPresent = projectRoot != null,
            fileName = fileName,
            fileKind = fileKind,
        )
        when (route) {
            PreviewRoute.WebUnavailable -> PreviewMessage("Preview is unavailable for this project.")

            PreviewRoute.Web -> {
                // previewRoute only says Web when both are there; the pair is split so the branch
                // does not have to convince the compiler of that a second time.
                val root = projectRoot
                val entry = webEntry
                if (root != null && entry != null) {
                    WebPreview(
                        projectRoot = root,
                        entryPath = entry,
                        refreshToken = refreshToken,
                        onUrl = { webUrl = it },
                    )
                }
            }

            PreviewRoute.NoFile -> PreviewMessage("Open a file to preview it.")

            PreviewRoute.KotlinMock -> KotlinPhoneMock(
                fileName = fileName,
                projectPath = projectPath,
                loader = loader,
                refreshToken = refreshToken,
            )

            PreviewRoute.Static -> StaticPreview(
                fileName = fileName,
                fileKind = fileKind,
                projectPath = projectPath,
                loader = loader,
                binaryLoader = binaryLoader,
                isImage = isImage,
                refreshToken = refreshToken,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ColumnScope.WebPreview(
    projectRoot: java.io.File,
    entryPath: String,
    refreshToken: Int,
    onUrl: (String) -> Unit,
) {
    var previewUrl by remember(projectRoot, entryPath) { mutableStateOf<String?>(null) }
    var loadedToken by remember(projectRoot, entryPath) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(projectRoot, entryPath) {
        var server: PreviewServer? = null
        val job = scope.launch(Dispatchers.IO) {
            val started = PreviewServer(projectRoot)
            started.start()
            server = started
            withContext(Dispatchers.Main) {
                previewUrl = "${started.baseUrl()}/$entryPath"
                loadedToken = null
            }
        }
        onDispose {
            job.cancel()
            runCatching { server?.stop() }
        }
    }

    val url = previewUrl
    if (url == null) {
        // The toolbar already owns the loading state, so there is nothing left to draw here.
        return
    }
    LaunchedEffect(url) { onUrl(url) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url?.substringBefore('#') != url) {
                view.clearCache(true)
                view.loadUrl(url)
                loadedToken = refreshToken
            } else if (loadedToken != refreshToken) {
                view.clearCache(true)
                view.reload()
                loadedToken = refreshToken
            }
        },
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.KotlinPhoneMock(
    fileName: String,
    projectPath: String,
    loader: (String) -> String?,
    refreshToken: Int = 0,
) {
    val source by produceState<String?>(initialValue = null, fileName, projectPath, refreshToken) {
        value = withContext(Dispatchers.IO) { loader(projectPath) }
    }
    val content = remember(source, fileName) {
        extractComposePreview(source.orEmpty(), fileName.ifBlank { "Preview" })
    }
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(LocalPageColors.current.panel2),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(230.dp)
                    .heightIn(min = 440.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .border(7.dp, Color(0xFF202733), RoundedCornerShape(32.dp))
                    .padding(top = 16.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .width(54.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF202733)),
                    )
                    Text(
                        text = content.title,
                        color = Color(0xFF101826),
                        fontSize = 20.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    content.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = Color(0xFF66728A),
                            fontSize = 14.sp,
                            fontFamily = Sans,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    content.button?.let { label ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF2457FF))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Static preview for images, markdown, JSON, XML, CSV, Kotlin files */
@Composable
private fun StaticPreview(
    fileName: String,
    fileKind: FileKind?,
    projectPath: String,
    loader: (String) -> String?,
    binaryLoader: (String) -> ByteArray?,
    isImage: Boolean,
    refreshToken: Int = 0,
) {
    val c = LocalPageColors.current
    val extension = remember(fileName) { extensionOf(fileName) }
    val isImage = extension in ImageExtensions

    // Off the main thread: reading is IO, and JSON/XML formatting is CPU bound.
    val text by produceState<String?>(initialValue = null, fileName, projectPath, refreshToken) {
        value = if (isImage) {
            null
        } else {
            withContext(Dispatchers.IO) { loader(projectPath) }
        }
    }
    val image by produceState<PreviewImage?>(initialValue = null, fileName, projectPath, refreshToken) {
        value = if (!isImage) {
            null
        } else {
            withContext(Dispatchers.IO) {
                binaryLoader(projectPath)?.let { bytes ->
                    runCatching { decodeScaled(bytes) }.getOrNull()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize().background(c.bg)) {
        if (fileName.isBlank()) {
            PreviewMessage("Open a file to preview it.")
            return@StaticPreview
        }

        if (isImage) {
            when {
                image == null -> PreviewMessage("No preview available for this image.")
                else -> {
                    val bmp = image!!.bitmap
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Sized to the picture, so the board sits exactly under the pixels and not
                        // in the letterbox around them.
                        Box(
                            Modifier
                                .padding(12.dp)
                                .aspectRatio(
                                    bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f),
                                )
                                .drawBehind { checkerboard(c.bg, c.panel) },
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = fileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            if (image?.note != null) {
                PreviewMessage(image!!.note!!)
            }
            return@StaticPreview
        }

        when {
            text == null -> PreviewMessage("Loading…")
            fileKind == FileKind.Markdown -> MarkdownPreview(text!!)
            fileKind == FileKind.Json -> PrettyPreview(text!!, isJson = true)
            fileKind == FileKind.Xml -> PrettyPreview(text!!, isJson = false)
            fileKind == FileKind.Csv -> CsvPreview(text!!)
            else -> PrettyPreview(text!!, isJson = false, plain = true)
        }
    }
}

/**
 * The preview's own chrome, and the whole of it: a re-read and — when the preview is a live page —
 * a way out to the device's browser, both at the top right.
 *
 * No filename here. The path bar and the tab strip already say which file this is, and repeating it
 * spent a third of the strip's width on something nobody had to be told twice; what is left is a
 * slim header whose left side is simply empty.
 */
@Composable
private fun PreviewToolbar(
    loading: Boolean,
    canOpenInBrowser: Boolean,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(0.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PreviewAction(CcIcons.Refresh, "Refresh preview", onClick = onRefresh)
            if (canOpenInBrowser) {
                PreviewAction(CcIcons.External, "Open in browser", onClick = onOpenInBrowser)
            }
        }
        // The wait belongs to the header rather than to a second strip beneath it: one slim bar at
        // the top of the preview, not two stacked ones eating the viewport.
        if (loading) PreviewLoadingBar()
    }
}

@Composable
private fun PreviewAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, c.mu, 17.dp)
    }
}

/**
 * Shown while the local preview server comes up. A bar rather than a message: the wait is short
 * and happens every time the panel opens, so it should read as progress, not as text to dismiss.
 *
 * It hangs off the bottom of the header rather than sitting as a strip of its own, so the wait
 * costs the viewport nothing and there is still only one thing at the top of the preview.
 */
@Composable
private fun PreviewLoadingBar() {
    val c = LocalPageColors.current
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = c.ac,
        trackColor = c.ln,
    )
}

@Composable
private fun PreviewMessage(text: String) {
    val c = LocalPageColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = c.mu,
            fontSize = 13.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ColumnScope.PrettyPreview(source: String, isJson: Boolean, plain: Boolean = false) {
    val c = LocalPageColors.current
    val formatted by produceState<String?>(initialValue = null, source, isJson, plain) {
        value = withContext(Dispatchers.Default) {
            if (plain) source else prettyFormat(source, isJson)
        }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(
            text = formatted.orEmpty(),
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Mono,
        )
    }
}

@Composable
private fun ColumnScope.MarkdownPreview(source: String) {
    val c = LocalPageColors.current
    val parsed by produceState<List<MarkdownBlock>>(initialValue = emptyList(), source) {
        value = withContext(Dispatchers.Default) { parseMarkdown(source) }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (block in parsed) {
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    color = c.tx,
                    fontSize = (20 - block.level * 2).coerceAtLeast(13).sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )

                is MarkdownBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.panel2)
                        .padding(10.dp),
                ) {
                    Text(
                        text = block.text,
                        color = c.string,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                    )
                }

                is MarkdownBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = c.mu, fontSize = 13.sp, fontFamily = Mono)
                    Text(
                        text = block.text,
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )

                is MarkdownBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(c.ln)
                        .height(1.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CsvPreview(source: String) {
    val c = LocalPageColors.current
    val rows by produceState<List<List<String>>>(initialValue = emptyList(), source) {
        value = withContext(Dispatchers.Default) { parseCsv(source) }
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (cell in row) {
                    Text(
                        text = cell,
                        color = if (rowIndex == 0) c.tx else c.mu,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp).padding(end = 8.dp),
                    )
                }
            }
            if (rowIndex == 0) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.ln))
            }
        }
    }
}

// -- formatting helpers, all pure so they can be unit tested without Compose -------------------

/** Indents JSON or XML. JSON keeps its key order and string contents untouched. */
fun prettyFormat(source: String, isJson: Boolean): String =
    if (isJson) prettyJson(source) else prettyXml(source)

private const val INDENT = "  "

/**
 * JSON formatter that walks the source rather than rebuilding it from a map, so duplicate keys,
 * key order and unusual number formatting all survive a round trip.
 */
private fun prettyJson(source: String): String {
    val out = StringBuilder()
    var i = 0

    fun skipWhitespace() {
        while (i < source.length && source[i].isWhitespace()) i++
    }

    fun readString(): String {
        val sb = StringBuilder()
        sb.append(source[i]) // opening quote
        i++
        while (i < source.length) {
            val ch = source[i]
            sb.append(ch)
            i++
            if (ch == '\\' && i < source.length) {
                sb.append(source[i])
                i++
                continue
            }
            if (ch == '"') break
        }
        return sb.toString()
    }

    fun readValue(depth: Int) {
        skipWhitespace()
        if (i >= source.length) return
        val ch = source[i]
        if (ch == '"') {
            out.append(readString())
            return
        }
        if (ch != '{' && ch != '[') {
            val start = i
            while (i < source.length && !source[i].isWhitespace() && source[i] !in ",}]") i++
            out.append(source, start, i)
            return
        }
        val close = if (ch == '{') '}' else ']'
        out.append(ch)
        i++
        skipWhitespace()
        // An empty object or array stays on one line.
        if (i < source.length && source[i] == close) {
            out.append(close)
            i++
            return
        }
        out.append('\n')
        var first = true
        while (i < source.length && source[i] != close) {
            skipWhitespace()
            if (!first) {
                if (source[i] == ',') i++ else break
                out.append(',')
                skipWhitespace()
                out.append('\n')
            }
            out.append(INDENT.repeat(depth + 1))
            first = false
            if (i < source.length && source[i] == '"') {
                out.append(readString())
                skipWhitespace()
                if (i < source.length && source[i] == ':') {
                    out.append(": ")
                    i++
                }
            }
            readValue(depth + 1)
        }
        skipWhitespace()
        out.append('\n').append(INDENT.repeat(depth)).append(close)
        if (i < source.length && source[i] == close) i++
    }

    readValue(0)
    return out.toString().trim()
}

/** XML formatter driven by tag boundaries, so nesting follows the document rather than whitespace. */
private fun prettyXml(source: String): String {
    val out = StringBuilder()
    var depth = 0
    var i = 0
    fun breakLine() {
        if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
    }
    while (i < source.length) {
        if (source[i] == '<') {
            val close = source.indexOf('>', i)
            if (close < 0) {
                out.append(source.substring(i))
                break
            }
            val tag = source.substring(i, close + 1)
            val isClosing = tag.startsWith("</")
            val isSelfClosing = tag.endsWith("/>")
            val isDeclaration = tag.startsWith("<?") || tag.startsWith("<!")
            if (isClosing) depth--
            breakLine()
            out.append(INDENT.repeat(depth.coerceAtLeast(0))).append(tag)
            if (!isClosing && !isSelfClosing && !isDeclaration) depth++
            i = close + 1
            continue
        }
        val next = source.indexOf('<', i)
        val text = if (next < 0) source.substring(i) else source.substring(i, next)
        if (text.isNotBlank()) {
            if (out.isNotEmpty() && !out.endsWith("\n") && !out.endsWith(" ")) out.append(' ')
            out.append(text.trim())
        }
        i = if (next < 0) source.length else next
    }
    return out.toString().trim()
}

/** Markdown blocks Preview knows how to draw. Anything unsupported falls back to a paragraph. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/**
 * A deliberately small Markdown reader: headings, bullets, fenced code, horizontal rules and
 * paragraphs. Anything else is kept as text rather than guessed at.
 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = ArrayList<MarkdownBlock>()
    val paragraph = StringBuilder()
    var inCode = false
    val code = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(MarkdownBlock.Paragraph(paragraph.toString().trim()))
        paragraph.clear()
    }

    for (raw in source.lines()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(raw).append('\n')
            continue
        }
        val trimmed = line.trimStart()
        when {
            trimmed.isEmpty() -> flushParagraph()

            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Rule)
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks.add(MarkdownBlock.Heading(level, trimmed.drop(level).trim()))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                {
                    flushParagraph()
                    blocks.add(MarkdownBlock.Bullet(trimmed.drop(2).trim()))
                }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    if (inCode && code.isNotBlank()) blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
    flushParagraph()
    return blocks
}

/** Splits CSV rows on commas, honouring double quoted cells. */
fun parseCsv(source: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < source.length) {
        val ch = source[index]
        when {
            inQuotes && ch == '"' && source.getOrNull(index + 1) == '"' -> {
                cell.append('"')
                index++
            }

            ch == '"' -> inQuotes = !inQuotes

            inQuotes -> cell.append(ch)

            ch == ',' -> {
                row.add(cell.toString())
                cell.clear()
            }

            ch == '\n' -> {
                row.add(cell.toString())
                cell.clear()
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = ArrayList()
            }

            ch != '\r' -> cell.append(ch)
        }
        index++
    }
    row.add(cell.toString())
    if (row.any { it.isNotBlank() }) rows.add(row)
    return rows
}