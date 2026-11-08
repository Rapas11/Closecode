package com.closecode.ui.ide

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.closecode.ui.CcIcon
import com.closecode.ui.components.CcTextField
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One launcher/project icon read from the open project. */
data class ProjectIconUi(
    val fileName: String,
    val sizeDp: Int,
    val vector: ImageVector?,
    val pathData: String? = null,
    val style: String? = null,
    val viewBox: String? = null,
)

/**
 * One icon inside a downloaded library pack. The vector is built on first draw: a pack holds
 * thousands of icons, and turning every path into an ImageVector up front made selecting a pack
 * stall and held all of them in memory even though only a gridful is visible.
 */
class LibIconUi(
    val name: String,
    private val providedVector: ImageVector? = null,
    val pathData: String? = null,
    val style: String? = null,
    val viewBox: String? = null,
) {
    val vector: ImageVector? by lazy {
        providedVector ?: pathData?.let { parseVector(it, style, viewBox) }
    }
}

/** One saved icon shown on the Code tab. */
data class SavedIconUi(
    val title: String,
    val usedIn: Int?,
    val vector: ImageVector?,
    val pathData: String? = null,
    val style: String? = null,
    val viewBox: String? = null,
)

internal fun displayIconTitle(fileName: String): String {
    val stem = fileName.substringBeforeLast('.')
    return stem.removePrefix("ic_").replace('_', ' ').trim().ifBlank { stem }.lowercase()
}

internal fun iconComposeName(fileName: String): String {
    val parts = fileName
        .substringBeforeLast('.')
        .removePrefix("ic_")
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotEmpty() }
    return parts.joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }.ifBlank { "Icon" }
}

internal fun composeIconCode(icon: SavedIconUi): String {
    val pathData = icon.pathData ?: return ""
    val name = iconComposeName(icon.title)
    val vb = (icon.viewBox ?: "0 0 24 24")
        .split(' ', ',')
        .mapNotNull { it.trim().toFloatOrNull() }
    val minX = vb.getOrNull(0) ?: 0f
    val minY = vb.getOrNull(1) ?: 0f
    val vw = vb.getOrNull(2)?.takeIf { it > 0f } ?: 24f
    val vh = vb.getOrNull(3)?.takeIf { it > 0f } ?: 24f
    val paint = if (icon.style == "stroke") {
        "stroke = defaultColors[0], strokeLineWidth = 2f"
    } else {
        "fill = defaultColors[0]"
    }
    val draw = if (minX == 0f && minY == 0f) {
        """    .addPath(
        pathData = PathParser().parsePathString("$pathData").toNodes(),
        $paint
    )"""
    } else {
        """    .apply {
        addGroup(translationX = ${-minX}f, translationY = ${-minY}f) {
            addPath(
                pathData = PathParser().parsePathString("$pathData").toNodes(),
                $paint
            )
        }
    }"""
    }
    return """val AppIcons.$name: ImageVector by lazy {
    ImageVector.Builder("$name", 24.dp, 24.dp, ${vw}f, ${vh}f)
$draw
    .build()
}""".trimIndent()
}

sealed interface PackState {
    object NotLoaded : PackState
    data class Downloading(val fraction: Float, val doneText: String) : PackState
    object Ready : PackState
    data class Failed(val message: String) : PackState
}

data class PackUi(
    val id: String,
    val name: String,
    val countText: String,
    val sizeText: String,
    val state: PackState,
    val url: String = "",
    val style: String? = null,
)

/** Shared shell for screens 52-57: header + segmented control. */
@Composable
fun IconsShell(
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    footer: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalCcColors.current
    Column(Modifier.fillMaxSize().background(c.content)) {
        ScreenHeader(
            origin = "Project settings",
            title = "Icons",
            onBack = onBack,
        )
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(c.rail)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("This project", "Browse", "Code").forEachIndexed { i, t ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (i == tab) c.toggleOff else Color.Transparent)
                        .clickable { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t,
                        color = if (i == tab) Color.White else c.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (footer != null) 96.dp else 24.dp),
            content = content,
        )
        if (footer != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = footer,
            )
        }
    }
}

/** Rounded group card used by the This project and Browse tabs. */
@Composable
fun IconGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalCcColors.current
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.field),
        content = content,
    )
}

/** One row inside a group: tile, title, sub, trailing. */
@Composable
fun IconRow(
    title: String,
    sub: String,
    last: Boolean,
    onClick: () -> Unit,
    tile: Color = LocalCcColors.current.toggleOff,
    tileIcon: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {
        CcIcon("cr", LocalCcColors.current.faint, 15.dp, 2.2f)
    },
) {
    val c = LocalCcColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tile),
                contentAlignment = Alignment.Center,
            ) {
                tileIcon()
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = c.text,
                    fontSize = 14.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sub,
                    color = c.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 16.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            trailing()
        }
        if (!last) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.toggleOff),
            )
        }
    }
}

/** Logo card shown at the top of the This project tab. */
@Composable
fun LogoCard(logo: ImageBitmap?, onClick: () -> Unit) {
    val c = LocalCcColors.current
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.panel)
            .border(1.dp, c.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logo != null) {
            androidx.compose.foundation.Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                    .background(c.faint.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon("image", c.faint, 32.dp, 1.6f)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (logo != null) "Your logo" else "No logo yet",
                color = c.text,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (logo != null) {
                    "Shown on the phone home screen and in the Play Store."
                } else {
                    "Your app uses the default Android icon."
                },
                color = c.muted,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.accent)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CcIcon("image", Color.White, 15.dp, 2f)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (logo != null) "Change logo" else "Choose your logo",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Empty state shown when there are no project icons (screen 53). */
@Composable
fun NoProjectIcons(onAddSvg: () -> Unit) {
    val c = LocalCcColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No icons in this project",
            color = c.text,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Add an SVG file, or open Browse to take icons from a pack.",
            color = c.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 19.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            Modifier
                .padding(top = 16.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.field)
                .clickable(onClick = onAddSvg)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcIcon("plus", c.text2, 16.dp, 2f)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Add an SVG file",
                color = c.text2,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Screen 52: This project tab - logo card + project icon list + Add SVG row. */
@Composable
fun IconsProjectTab(
    logo: ImageBitmap?,
    icons: List<ProjectIconUi>,
    error: String?,
    onLogo: () -> Unit,
    onIcon: (ProjectIconUi) -> Unit,
    onAddSvg: () -> Unit,
) {
    val c = LocalCcColors.current
    LogoCard(logo, onLogo)
    if (error != null) {
        Text(
            text = error,
            color = c.red,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
    if (icons.isEmpty()) {
        NoProjectIcons(onAddSvg)
        return
    }
    Text(
        text = "Icons in this project · ${icons.size}",
        color = c.muted,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
    )
    IconGroup {
        icons.forEachIndexed { index, i ->
            IconRow(
                title = displayIconTitle(i.fileName),
                sub = "Vector · ${i.sizeDp} dp",
                last = false,
                onClick = { onIcon(i) },
                tileIcon = {
                    if (i.vector != null) {
                        androidx.compose.material3.Icon(
                            imageVector = i.vector,
                            contentDescription = null,
                            tint = c.text2,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
            )
            if (index != icons.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.toggleOff),
                )
            }
        }
        IconRow(
            title = "Add an SVG file",
            sub = "Turns it into an Android vector",
            last = true,
            onClick = onAddSvg,
            tile = c.accent.copy(alpha = 0.20f),
            tileIcon = { CcIcon("plus", c.accent, 19.dp, 1.8f) },
            trailing = { },
        )
    }
}

/** Screen 52-57: the Icons full screen, opened from Project settings > Icons. */
@Composable
fun IdeIconsScreen(
    projectRoot: File?,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    onChangeLogo: () -> Unit = { },
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val c = LocalCcColors.current

    val logo by remember(projectRoot) { mutableStateOf(readProjectLogo(projectRoot)) }
    var icons by remember(projectRoot) { mutableStateOf(readProjectIcons(projectRoot)) }
    var packs by remember(context) { mutableStateOf(loadCachedPackIndex(context)) }
    val packStates = remember { mutableStateMapOf<String, PackState>() }
    val packJobs = remember { mutableMapOf<String, kotlinx.coroutines.Job>() }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var browsePackId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val libraryIcons = remember { mutableStateListOf<LibIconUi>() }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var useSheetOpen by remember { mutableStateOf(false) }
    var useSheetColorIndex by remember { mutableIntStateOf(0) }
    var useSheetSizeIndex by remember { mutableIntStateOf(1) }
    var useSheetFileName by remember { mutableStateOf("") }
    var useSheetNameError by remember { mutableStateOf<String?>(null) }
    var usePackName by remember { mutableStateOf("") }
    var codeSelected by remember { mutableIntStateOf(0) }
    val codeSaved = remember { mutableStateListOf<SavedIconUi>() }
    var codeText by remember { mutableStateOf("") }

    val defaultColors = listOf(c.text2, c.accent, c.green, c.amber, c.red, c.purple)
    var svgError by remember { mutableStateOf<String?>(null) }
    val svgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            svgError = null
            scope.launch {
                val stream = context.contentResolver.openInputStream(uri) ?: return@launch
                val svgText = withContext(Dispatchers.IO) {
                    stream.bufferedReader().readText()
                }
                val colorHex = defaultColors[0].value.toInt().and(0xFFFFFF).toString(16).padStart(6, '0')
                val xml = svgToVectorXml(svgText, colorHex, 24)
                withContext(Dispatchers.Main) {
                    if (xml == null) {
                        svgError = "The SVG could not be converted (no usable path data)."
                    } else {
                        val root = projectRoot ?: return@withContext
                        var target = File(root, "app/src/main/res/drawable/ic_custom.xml")
                        var name = "ic_custom"
                        while (target.exists()) {
                            name = "ic_custom${(Math.random() * 100).toInt()}"
                            target = File(root, "app/src/main/res/drawable/$name.xml")
                        }
                        target.parentFile?.mkdirs()
                        target.writeText(xml)
                        val vector = parseVector(Regex("pathData=\"([^\"]*)\"").find(xml)?.groupValues?.get(1) ?: "")
                        icons = listOf(ProjectIconUi(name, 24, vector)) + icons
                    }
                }
            }
        }
    }

    // The index is remote; the cached copy above is shown immediately so Browse works offline.
    LaunchedEffect(Unit) {
        fetchPackIndex(context).onSuccess { packs = it }
    }

    // A pack that is already on disk is ready, not "Get": the install survives a restart.
    LaunchedEffect(packs) {
        packStates.clear()
        packs.forEach { pack ->
            val file = File(context.filesDir, "iconpacks/${pack.id}.json")
            packStates[pack.id] = if (file.isFile) PackState.Ready else PackState.NotLoaded
        }
        if (packStates[selectedPackId] != PackState.Ready) {
            selectedPackId = packs.firstOrNull { packStates[it.id] == PackState.Ready }?.id
        }
    }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // Packs ship inside the APK, so Get is an install rather than a network download: the
    // bundled JSON is copied to files with real progress, then the library opens on it.
    fun onGetPack(pack: PackUi) {
        packJobs.remove(pack.id)?.cancel()
        packStates[pack.id] = PackState.Downloading(0f, "Installing…")
        val out = File(context.filesDir, "iconpacks/${pack.id}.json")
        packJobs[pack.id] = scope.launch {
            val outcome = installBundledPack(context, pack.id, out) { fraction ->
                scope.launch {
                    packStates[pack.id] = PackState.Downloading(
                        fraction,
                        "${(fraction * 100).toInt()}%",
                    )
                }
            }
            packJobs.remove(pack.id)
            val icons = if (outcome.isSuccess) readPackFile(context, pack.id) else null
            packStates[pack.id] = when {
                !outcome.isSuccess -> PackState.Failed(
                    outcome.exceptionOrNull()?.message ?: "Install failed",
                )
                icons.isNullOrEmpty() -> PackState.Failed("The bundled pack was empty.")
                else -> PackState.Ready
            }
            if (icons?.isNotEmpty() == true) {
                selectedPackId = pack.id
                browsePackId = pack.id
                usePackName = pack.name
                query = ""
            }
        }
    }

    fun onStopPack(pack: PackUi) {
        packJobs.remove(pack.id)?.cancel()
        File(context.filesDir, "iconpacks/${pack.id}.json.part").delete()
        packStates[pack.id] = PackState.NotLoaded
    }

    val readyPacks = packs.filter { packStates[it.id] == PackState.Ready }

    LaunchedEffect(readyPacks) {
        if (readyPacks.isNotEmpty() && packStates[selectedPackId] != PackState.Ready) {
            selectedPackId = readyPacks.first().id
            usePackName = readyPacks.first().name
        }
    }

    // Library: load the selected pack's icons and filter by query.
    LaunchedEffect(selectedPackId, query) {
        // Typing a name should not re-read a two-megabyte pack on every keystroke.
        delay(150)
        libraryIcons.clear()
        selectedIcon = null
        val id = selectedPackId
        if (id != null) {
            val loaded = withContext(Dispatchers.IO) {
                readPackFile(context, id)
            } ?: emptyList()
            val q = query.lowercase()
            libraryIcons.addAll(loaded.filter { it.name.lowercase().contains(q) })
        }
    }

    // Use icon sheet defaults.
    var useIcon by remember { mutableStateOf<LibIconUi?>(null) }

    fun openUseSheet(icon: LibIconUi, packName: String) {
        useIcon = icon
        usePackName = packName
        val base = "ic_${icon.name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")}"
        useSheetFileName = base
        useSheetNameError = null
        useSheetOpen = true
    }

    fun saveUseSheet() {
        val icon = useIcon ?: return
        val pathData = icon.pathData ?: return
        val style = icon.style ?: "fill"
        val colorHex = defaultColors[useSheetColorIndex].value.toInt().and(0xFFFFFF).toString(16).padStart(6, '0')
        val sizeDp = when (useSheetSizeIndex) { 0 -> 24; 1 -> 32; else -> 48 }
        val fileName = useSheetFileName.trim()
        val target = File(projectRoot ?: return, "app/src/main/res/drawable/$fileName.xml")
        val outcome = runCatching {
            if (target.exists()) error("A file with this name already exists")
            target.parentFile?.mkdirs()
            val vb = (icon.viewBox ?: "0 0 24 24")
                .split(' ', ',')
                .mapNotNull { it.trim().toFloatOrNull() }
            val minX = vb.getOrNull(0) ?: 0f
            val minY = vb.getOrNull(1) ?: 0f
            val vw = vb.getOrNull(2)?.takeIf { it > 0f } ?: 24f
            val vh = vb.getOrNull(3)?.takeIf { it > 0f } ?: 24f
            val pathXml = if (style == "fill") {
                """<path android:fillColor="#$colorHex" android:pathData="$pathData"/>"""
            } else {
                """<path android:fillColor="#00000000" android:strokeColor="#$colorHex" android:strokeWidth="2" android:strokeLineCap="round"
        android:strokeLineJoin="round" android:pathData="$pathData"/>"""
            }
            val body = if (minX == 0f && minY == 0f) {
                pathXml
            } else {
                """<group android:translateX="${-minX}" android:translateY="${-minY}">$pathXml</group>"""
            }
            val xml = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${sizeDp}dp" android:height="${sizeDp}dp" android:viewportWidth="$vw" android:viewportHeight="$vh">
    $body
</vector>""".trimIndent()
            target.writeText(xml)
        }
        if (outcome.isSuccess) {
            icons = listOf(
                ProjectIconUi(
                    fileName,
                    sizeDp,
                    parseVector(pathData, style, icon.viewBox),
                    pathData,
                    style,
                    icon.viewBox,
                ),
            ) + icons
            useSheetOpen = false
            toast("Saved")
        } else {
            useSheetNameError = outcome.exceptionOrNull()?.message
        }
    }

    // Code tab: build saved icons + code for selected.
    LaunchedEffect(icons, codeSelected) {
        val saved = icons.map { i ->
            SavedIconUi(
                displayIconTitle(i.fileName),
                countIconUses(projectRoot, i.fileName),
                i.vector,
                i.pathData,
                i.style,
                i.viewBox,
            )
        }
        codeSaved.clear()
        codeSaved.addAll(saved)
        if (codeSelected !in saved.indices) codeSelected = 0
        codeText = saved.getOrNull(codeSelected)?.let(::composeIconCode).orEmpty()
    }

    fun copyCode(text: String, message: String) {
        if (text.isBlank()) {
            toast("Nothing to copy")
            return
        }
        clipboard.setText(AnnotatedString(text))
        toast(message)
    }

    IconsShell(
        tab = tab,
        onTab = onTab,
        onBack = onBack,
        footer = if (tab == 2 && codeSaved.isNotEmpty()) {
            {
                BigButton(
                    text = "Copy code for ${codeSaved.size} icons",
                    onClick = {
                        copyCode(
                            codeSaved.joinToString("\n\n") { composeIconCode(it) },
                            "Code copied",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = "copy",
                    bg = c.field,
                    fg = c.text2,
                )
            }
        } else {
            null
        },
    ) {
        when (tab) {
            0 -> IconsProjectTab(
                logo = logo?.asImageBitmap(),
                icons = icons,
                error = svgError,
                onLogo = { onChangeLogo() },
                onIcon = { icon ->
                    openUseSheet(
                        LibIconUi(
                            icon.fileName,
                            icon.vector,
                            icon.pathData,
                            icon.style,
                            icon.viewBox,
                        ),
                        "This project",
                    )
                },
                onAddSvg = { svgLauncher.launch(arrayOf("image/svg+xml", "*/*")) },
            )
            1 -> {
                val currentPack = packs.firstOrNull { it.id == browsePackId }
                if (currentPack == null) {
                    IconsBrowseTab(
                        packs = packs,
                        readyPacks = readyPacks,
                        onGet = { pack ->
                            val current = packStates[pack.id]
                            if (current is PackState.Downloading) onStopPack(pack) else onGetPack(pack)
                        },
                        onStop = { onStopPack(it) },
                        onOpenPack = { pack ->
                            selectedPackId = pack.id
                            browsePackId = pack.id
                            usePackName = pack.name
                            query = ""
                        },
                    )
                } else {
                    IconsLibraryTab(
                        packName = currentPack.name,
                        packs = readyPacks,
                        total = libraryIcons.size,
                        query = query,
                        icons = libraryIcons,
                        selected = selectedIcon,
                        onPickPack = { pack ->
                            selectedPackId = pack.id
                            browsePackId = pack.id
                            usePackName = pack.name
                            query = ""
                        },
                        onQuery = { query = it },
                        onTap = { icon ->
                            selectedIcon = icon.name
                            usePackName = currentPack.name
                            openUseSheet(icon, currentPack.name)
                        },
                    )
                }
            }
            else -> IconsCodeTab(
                code = codeText,
                saved = codeSaved,
                selected = codeSelected,
                onSelect = { codeSelected = it },
            )
        }
    }

    // Use icon sheet (screen 56).
    if (useSheetOpen && useIcon != null) {
        UseIconSheet(
            icon = useIcon!!,
            packName = usePackName,
            colors = defaultColors,
            colorIndex = useSheetColorIndex,
            sizeIndex = useSheetSizeIndex,
            fileName = useSheetFileName,
            nameError = useSheetNameError,
            onColor = { useSheetColorIndex = it },
            onSize = { useSheetSizeIndex = it },
            onName = { useSheetFileName = it; useSheetNameError = null },
            onCopy = {
                copyCode(
                    composeIconCode(
                        SavedIconUi(
                            useSheetFileName,
                            null,
                            useIcon!!.vector,
                            useIcon!!.pathData,
                            useIcon!!.style,
                            useIcon!!.viewBox,
                        ),
                    ),
                    "Code copied",
                )
            },
            onSave = { saveUseSheet() },
            onDismiss = { useSheetOpen = false },
        )
    }
}
@Composable
fun IconsBrowseTab(
    packs: List<PackUi>,
    readyPacks: List<PackUi>,
    onGet: (PackUi) -> Unit,
    onStop: (PackUi) -> Unit,
    onOpenPack: (PackUi) -> Unit,
) {
    val c = LocalCcColors.current
    var pickMenuFor by remember { mutableStateOf<String?>(null) }
    Text(
        text = "Get a pack once. It stays on this phone, so it works with no internet.",
        color = c.muted,
        fontSize = 12.5.sp,
        fontFamily = Sans,
        lineHeight = 19.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp),
    )
    Text(
        text = "Icon packs",
        color = c.muted,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
    )
    IconGroup {
        packs.forEachIndexed { index, p ->
            val dl = p.state as? PackState.Downloading
            IconRow(
                title = p.name,
                sub = "${p.countText} · ${p.sizeText}",
                last = index == packs.lastIndex && dl == null,
                onClick = {
                    when (val s = p.state) {
                        is PackState.Downloading -> onStop(p)
                        PackState.Ready -> pickMenuFor = p.id
                        else -> onGet(p)
                    }
                },
                tileIcon = { CcIcon("layers", c.text2, 19.dp, 1.8f) },
                trailing = when (val s = p.state) {
                    is PackState.Downloading -> {
                        { PillButton("Stop", c.red, null) { onStop(p) } }
                    }
                    PackState.Ready -> {
                        {
                            Box {
                                PillButton("Pick", c.accent, "check") { pickMenuFor = p.id }
                                DropdownMenu(
                                    expanded = pickMenuFor == p.id,
                                    onDismissRequest = { pickMenuFor = null },
                                ) {
                                    readyPacks.forEach { ready ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = ready.name,
                                                    color = c.text,
                                                    fontSize = 14.sp,
                                                    fontFamily = Sans,
                                                )
                                            },
                                            onClick = {
                                                pickMenuFor = null
                                                onOpenPack(ready)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        { PillButton(if (s is PackState.Failed) "Retry" else "Get", c.accent, "down") { onGet(p) } }
                    }
                },
            )
            if (index != packs.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.toggleOff),
                )
            }
            if (dl != null) {
                Column(
                    Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                ) {
                    ProgressLine(
                        text = "Downloading ${(dl.fraction * 100).toInt()}%",
                        fraction = dl.fraction,
                        doneText = dl.doneText,
                    )
                }
            }
            (p.state as? PackState.Failed)?.let {
                Text(
                    text = it.message,
                    color = c.red,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 16.8.sp,
                    modifier = Modifier.padding(start = 62.dp, bottom = 10.dp),
                )
            }
        }
        if (packs.isEmpty()) {
            Text(
                text = "No packs found. Check your connection and try again.",
                color = c.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Small outlined pill button used by the Browse tab. */
@Composable
private fun PillButton(
    text: String,
    color: Color,
    icon: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, color, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CcIcon(icon, color, 14.dp, 2.2f)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Download progress bar (7 dp track, accent fill). */
@Composable
fun ProgressLine(
    text: String,
    fraction: Float,
    doneText: String,
) {
    val c = LocalCcColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = c.accent,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = doneText,
            color = c.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
        )
    }
    Box(
        Modifier
            .padding(top = 7.dp)
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.rail),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(7.dp)
                .background(c.accent),
        )
    }
}

/** Screen 55: Library tab - pack selector, find field, count, icon grid. */
@Composable
fun IconsLibraryTab(
    packName: String,
    packs: List<PackUi>,
    total: Int,
    query: String,
    icons: List<LibIconUi>,
    selected: String?,
    onPickPack: (PackUi) -> Unit,
    onQuery: (String) -> Unit,
    onTap: (LibIconUi) -> Unit,
) {
    val c = LocalCcColors.current
    var packMenuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
            .fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.field)
                .clickable { packMenuOpen = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pack",
                color = c.muted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = packName,
                color = c.text,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            CcIcon("cd", c.muted, 16.dp, 2.2f)
        }
        DropdownMenu(
            expanded = packMenuOpen,
            onDismissRequest = { packMenuOpen = false },
        ) {
            packs.forEach { pack ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = pack.name,
                            color = c.text,
                            fontSize = 14.sp,
                            fontFamily = Sans,
                        )
                    },
                    onClick = {
                        packMenuOpen = false
                        onPickPack(pack)
                    },
                )
            }
        }
    }
    Box(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        CcTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = "Find an icon",
            leadingIcon = "search",
        )
    }
    val shown = if (query.isNotBlank()) icons.size else total
    Text(
        text = "%,d".format(shown) + " icons · tap one to use it",
        color = c.muted,
        fontSize = 12.sp,
        fontFamily = Sans,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    )
    if (icons.isEmpty() && query.isNotBlank()) {
        Text(
            text = "No icon named \"$query\"",
            color = c.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(32.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        Modifier
            .padding(horizontal = 16.dp)
            .heightIn(max = 560.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.field)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(icons, key = { it.name }) { i ->
            val sel = i.name == selected
            Box(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (sel) {
                            Modifier
                                .background(c.accent.copy(alpha = 0.20f))
                                .border(2.dp, c.accent, RoundedCornerShape(14.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onTap(i) },
                contentAlignment = Alignment.Center,
            ) {
                val iconVector = i.vector
                if (iconVector != null) {
                    androidx.compose.material3.Icon(
                        imageVector = iconVector,
                        contentDescription = i.name,
                        tint = c.text2,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/** Screen 56: Use this icon bottom sheet. */
@Composable
fun UseIconSheet(
    icon: LibIconUi,
    packName: String,
    colors: List<Color>,
    colorIndex: Int,
    sizeIndex: Int,
    fileName: String,
    nameError: String?,
    onColor: (Int) -> Unit,
    onSize: (Int) -> Unit,
    onName: (String) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalCcColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.panel)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        ) {
            Text(
                text = "Use this icon",
                color = c.text,
                fontSize = 19.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.field),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconVector = icon.vector
                if (iconVector != null) {
                    androidx.compose.material3.Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = colors[colorIndex],
                        modifier = Modifier.size(52.dp),
                    )
                }
                Column {
                    Text(
                        text = icon.name.replace('_', ' ')
                            .replaceFirstChar { c: Char -> c.uppercaseChar() },
                        color = c.text,
                        fontSize = 17.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = packName,
                        color = c.muted,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(
                Modifier
                    .padding(top = 14.dp, bottom = 14.dp, start = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                colors.forEachIndexed { i, col ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .then(
                                if (i == colorIndex) {
                                    Modifier
                                        .border(2.dp, c.accent, CircleShape)
                                        .padding(2.dp)
                                } else {
                                    Modifier
                                },
                            )
                            .clip(CircleShape)
                            .background(col)
                            .clickable { onColor(i) },
                        contentAlignment = Alignment.Center,
                    ) {}
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.rail)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("24 dp", "32 dp", "48 dp").forEachIndexed { i, t ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (i == sizeIndex) c.toggleOff else Color.Transparent,
                            )
                            .clickable { onSize(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = t,
                            color = if (i == sizeIndex) Color.White else c.muted,
                            fontSize = 12.5.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            CcTextField(
                value = fileName,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                label = "File name",
                mono = true,
                error = nameError,
            )
            if (nameError == null) {
                Text(
                    text = "Saved in res/drawable",
                    color = c.muted,
                    fontSize = 11.5.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                )
            }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigButton(
                    text = "Copy code",
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    icon = "copy",
                    bg = c.field,
                    fg = c.text2,
                )
                BigButton(
                    text = "Save to project",
                    onClick = onSave,
                    modifier = Modifier.weight(1.4f),
                    icon = "check",
                    enabled = nameError == null && fileName.isNotBlank(),
                )
            }
        }
    }
}

/** Screen 57: Code tab - code block + saved icons list. */
@Composable
fun IconsCodeTab(
    code: String,
    saved: List<SavedIconUi>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val c = LocalCcColors.current
    if (saved.isEmpty()) {
        Text(
            text = "Icons you save appear here as code.",
            color = c.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
        )
        return
    }
    Text(
        text = "Compose code for the icons you saved.",
        color = c.muted,
        fontSize = 12.5.sp,
        fontFamily = Sans,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp),
    )
    Text(
        text = code,
        color = c.text2,
        fontSize = 10.8.sp,
        fontFamily = Mono,
        lineHeight = 17.sp,
        softWrap = false,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.rail)
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
    )
    Text(
        text = "Saved icons · ${saved.size}",
        color = c.muted,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
    )
    IconGroup {
        saved.forEachIndexed { index, s ->
            IconRow(
                title = s.title,
                sub = if (s.usedIn != null) "Used in ${s.usedIn} files" else "",
                last = index == saved.lastIndex,
                onClick = { onSelect(index) },
                tileIcon = {
                    if (s.vector != null) {
                        androidx.compose.material3.Icon(
                            imageVector = s.vector,
                            contentDescription = null,
                            tint = c.text2,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                },
                trailing = {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(c.green),
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIcon("check", Color.White, 13.dp, 3f)
                    }
                },
            )
            if (index != saved.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.toggleOff),
                )
            }
        }
    }
}

/** Reads the project's launcher icon bitmap (same source as screen 46). */
fun readProjectLogo(root: File?): Bitmap? {
    val file = root?.let { File(it, ProjectIconFiles.LAUNCHER) } ?: return null
    if (!file.isFile) return null
    return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
}

/** Reads vector drawables from res/drawable as project icons. */
fun readProjectIcons(root: File?): List<ProjectIconUi> {
    val dir = root?.let { File(it, "app/src/main/res/drawable") } ?: return emptyList()
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles()
        .orEmpty()
        .filter { it.extension == "xml" }
        .mapNotNull { file ->
            runCatching {
                val text = file.readText()
                val pathData = Regex("pathData=\"([^\"]*)\"").find(text)?.groupValues?.get(1)
                    ?: Regex("android:pathData=\"([^\"]*)\"").find(text)?.groupValues?.get(1)
                val width = Regex("android:width=\"(\\d+)\"").find(text)?.groupValues?.get(1)?.toInt()
                    ?: Regex("width=\"(\\d+)\"").find(text)?.groupValues?.get(1)?.toInt()
                    ?: 24
                val height = Regex("android:height=\"(\\d+)\"").find(text)?.groupValues?.get(1)?.toInt()
                    ?: Regex("height=\"(\\d+)\"").find(text)?.groupValues?.get(1)?.toInt()
                    ?: 24
                val style = if (text.contains("android:strokeColor")) "stroke" else "fill"
                val vw = Regex("android:viewportWidth=\"([0-9.]+)\"").find(text)
                    ?.groupValues?.get(1) ?: "24"
                val vh = Regex("android:viewportHeight=\"([0-9.]+)\"").find(text)
                    ?.groupValues?.get(1) ?: vw
                val groupX = Regex("android:translateX=\"(-?[0-9.]+)\"").find(text)
                    ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val groupY = Regex("android:translateY=\"(-?[0-9.]+)\"").find(text)
                    ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val viewBox = "0 0 $vw $vh"
                    .let { if (groupX == 0f && groupY == 0f) it else "${-groupX} ${-groupY} $vw $vh" }
                val vector = pathData?.let { parseVector(it, style, viewBox) }
                ProjectIconUi(
                    fileName = file.nameWithoutExtension,
                    sizeDp = maxOf(width, height),
                    vector = vector,
                    pathData = pathData,
                    style = style,
                    viewBox = viewBox,
                )
            }.getOrNull()
        }
        .sortedBy { it.fileName }
}

/**
 * Converts SVG path data to an ImageVector. Filled packs (Material Symbols) use a black fill;
 * stroke packs (Lucide) keep their round caps and joins. A viewBox like "0 -960 960 960" is
 * honoured with a group translation, because Android vectors have no viewport origin.
 */
private fun parseVector(
    pathData: String,
    style: String? = null,
    viewBox: String? = null,
): ImageVector? {
    return runCatching {
        val box = viewBox.orEmpty()
            .split(' ', ',')
            .mapNotNull { it.trim().toFloatOrNull() }
        val minX = box.getOrNull(0) ?: 0f
        val minY = box.getOrNull(1) ?: 0f
        val width = box.getOrNull(2)?.takeIf { it > 0f } ?: 24f
        val height = box.getOrNull(3)?.takeIf { it > 0f } ?: 24f
        val nodes = PathParser().parsePathString(pathData).toNodes()
        val builder = Builder(
            name = "icon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = width,
            viewportHeight = height,
        )
        if (minX != 0f || minY != 0f) {
            builder.addGroup(translationX = -minX, translationY = -minY)
            if (style == "stroke") {
                builder.addPath(
                    pathData = nodes,
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                )
            } else {
                builder.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            }
            builder.clearGroup()
        } else {
            builder.addPath(
                pathData = nodes,
                fill = if (style == "stroke") null else SolidColor(Color.Black),
                stroke = if (style == "stroke") SolidColor(Color.Black) else null,
                strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        }
        builder.build()
    }.getOrNull()
}

/** Picks an SVG file from the device. */
fun pickSvgLauncher(
    onResult: (Uri) -> Unit,
): ActivityResultContracts.OpenDocument =
    ActivityResultContracts.OpenDocument()

/** Converts SVG text to Android vector drawable XML. Minimal path extraction. */
fun svgToVectorXml(svgText: String, colorHex: String, sizeDp: Int): String? {
    val pathData = Regex("d=\"([^\"]*)\"").find(svgText)?.groupValues?.get(1)
        ?: return null
    return """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${sizeDp}dp" android:height="${sizeDp}dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="$colorHex" android:pathData="$pathData"/>
</vector>""".trimIndent()
}

/** Downloads an icon pack index+file with real progress using HttpURLConnection. */
suspend fun downloadPack(
    url: String,
    out: File,
    onProgress: (Float) -> Unit,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        conn.requestMethod = "GET"
        conn.connect()
        val code = conn.responseCode
        if (code != 200) {
            error("Download failed ($code)")
        }
        val total = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
        val tmp = File(out.path + ".part")
        out.parentFile?.mkdirs()
        conn.inputStream.use { input ->
            tmp.outputStream().use { o ->
                val buf = ByteArray(16 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0L) {
                        onProgress((done.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) error("Could not save the downloaded pack")
    }
}

/** Where to find the pack index. Pages first; the raw file is a working fallback. */
internal val IconPackIndexUrls: List<String> = listOf(
    "https://rapas11.github.io/iconpacks/packs.json",
    "https://raw.githubusercontent.com/Rapas11/iconpacks/main/packs.json",
)

private fun packIndexFile(context: Context): File =
    File(context.filesDir, "iconpacks/packs.json")

/** Parses an index, resolving every relative pack URL against the index that served it. */
internal fun parsePackIndex(text: String, baseUrl: String): List<PackUi> {
    val gson = com.google.gson.Gson()
    val root = gson.fromJson(text, Any::class.java)
    val list: List<*> = when (root) {
        is List<*> -> root
        is Map<*, *> -> (root["packs"] as? List<*>) ?: emptyList<Any>()
        else -> emptyList<Any>()
    }
    return list.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = (map["id"] as? String)
            ?: (map["name"] as? String)?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")
            ?: return@mapNotNull null
        val name = map["name"] as? String ?: id
        val count = (map["count"] as? Number)?.toInt()
            ?: (map["icons"] as? List<*>)?.size
            ?: 0
        val bytes = (map["bytes"] as? Number)?.toLong() ?: 0L
        val sizeText = (map["size"] as? String) ?: formatPackBytes(bytes)
        val rel = map["url"] as? String ?: "$id.json"
        val url = runCatching { URL(URL(baseUrl), rel).toString() }.getOrElse { rel }
        PackUi(
            id = id,
            name = name,
            countText = count.toString(),
            sizeText = sizeText,
            state = PackState.NotLoaded,
            url = url,
            style = map["style"] as? String,
        )
    }
}

internal fun formatPackBytes(bytes: Long): String {
    if (bytes <= 0L) return "unknown"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

/** The last index seen, so Browse still lists packs with no connection. */
fun loadCachedPackIndex(context: Context): List<PackUi> {
    val text = runCatching {
        val file = packIndexFile(context)
        if (file.isFile) file.readText() else readBundledPackText(context, PACK_INDEX_ASSET)
    }.getOrNull().orEmpty()
    if (text.isBlank()) return emptyList()
    return runCatching {
        parsePackIndex(text, IconPackIndexUrls.last())
    }.getOrDefault(emptyList())
}

/** Fetches the hosted index, caching it on success. Returns the cached list on total failure. */
suspend fun fetchPackIndex(context: Context): Result<List<PackUi>> = withContext(Dispatchers.IO) {
    var last: Throwable? = null
    for (base in IconPackIndexUrls) {
        val attempt = runCatching {
            val conn = URL(base).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.connect()
            val code = conn.responseCode
            if (code != 200) error("Pack index failed ($code)")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val packs = parsePackIndex(text, base)
            if (packs.isEmpty()) error("The pack index was empty")
            packIndexFile(context).apply { parentFile?.mkdirs() }.writeText(text)
            packs
        }
        if (attempt.isSuccess) return@withContext attempt
        last = attempt.exceptionOrNull()
    }
    val cached = loadCachedPackIndex(context)
    if (cached.isNotEmpty()) Result.success(cached)
    else Result.failure(last ?: IllegalStateException("Could not load icon packs"))
}

/** The pack index and packs are bundled, so Browse works on a fresh install with no network. */
private const val PACK_INDEX_ASSET = "iconpacks/packs.json"

private fun readBundledPackText(context: Context, path: String): String? =
    runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()

/** Copies one bundled pack to app files, reporting real byte progress. */
private suspend fun installBundledPack(
    context: Context,
    id: String,
    out: File,
    onProgress: (Float) -> Unit,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val assetPath = "iconpacks/$id.json"
        val total = runCatching {
            context.assets.open(assetPath).use { it.available().toLong() }
        }.getOrDefault(0L)
        val tmp = File(out.path + ".part")
        out.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0L) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
                onProgress(1f)
            }
        }
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) error("Could not save the icon pack")
    }
}

/** Reads a downloaded pack JSON file from app files dir. */
fun readPackFile(context: Context, id: String): List<LibIconUi>? {
    val file = File(context.filesDir, "iconpacks/$id.json")
    return runCatching {
        val text = if (file.isFile) file.readText() else readBundledPackText(context, "iconpacks/$id.json")
        val gson = com.google.gson.Gson()
        val root = gson.fromJson(text, Any::class.java)
        val list: List<*> = when (root) {
            is List<*> -> root
            is Map<*, *> -> (root["icons"] as? List<*>) ?: emptyList<Any>()
            else -> emptyList<Any>()
        }
        list.mapNotNull { item ->
            val map = item as? Map<*, *>
                ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val pathData = map["pathData"] as? String
            val style = map["style"] as? String
            val viewBox = map["viewBox"] as? String ?: map["viewbox"] as? String
            LibIconUi(
                name = name,
                providedVector = null,
                pathData = pathData,
                style = style,
                viewBox = viewBox,
            )
        }
    }.getOrNull()
}

private fun CharSequence.replaceFirstChar(transform: (Char) -> Char): String {
    if (isEmpty()) return toString()
    return transform(get(0)) + substring(1)
}

@SuppressLint("NewApi")
fun decodeImageUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
}

/** Rough count of files in the project that reference an icon name. */
fun countIconUses(root: File?, name: String): Int {
    if (root == null) return 0
    val drawableDir = File(root, "app/src/main/res/drawable")
    val files = (drawableDir.listFiles() ?: emptyArray())
        .filter { it.extension == "xml" || it.extension == "kt" }
    return files.count { file ->
        runCatching { file.readText().contains(name) }.getOrDefault(false)
    }
}
