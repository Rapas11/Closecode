package com.closecode.ui.ide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Sans
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screen 46: the project's launcher icon. */
@Composable
fun IdeAppIconScreen(
    projectRoot: File?,
    projectColors: List<Color>,
    onBack: () -> Unit,
    onApplied: () -> Unit = {},
) {
    val c = LocalCcColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot: Map<String, ByteArray?> =
        remember(projectRoot) { projectRoot?.let(ProjectIconFiles::snapshot) ?: emptyMap() }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var selectedBg by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(false) }
    val original = remember(projectRoot) {
        snapshot[ProjectIconFiles.LAUNCHER]?.let { bytes -> decode(bytes) }
    }
    val pickedBitmap by produceState<ImageBitmap?>(initialValue = null, picked) {
        value = picked?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()?.asImageBitmap()
            }
        }
    }
    val preview = pickedBitmap ?: original?.asImageBitmap()
    val hasIcon = preview != null

    BackHandler(onBack = onBack)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            picked = uri
            error = null
        }
    }

    LaunchedEffect(picked) {
        val uri = picked ?: return@LaunchedEffect
        val problem = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it)
                } ?: error("This file is not a readable picture")
                require(minOf(bmp.width, bmp.height) >= 512) {
                    "The picture is too small. Use at least 512 by 512."
                }
            }.exceptionOrNull()?.message
        }
        if (problem != null) {
            error = problem
            picked = null
        }
    }

    IconScreenFrame("Project settings", "App icon", onBack, footer = {
        BigButton(
            text = "Reset",
            onClick = {
                picked = null
                selectedBg = 0
                error = null
                applied = false
                scope.launch {
                    withContext(Dispatchers.IO) { ProjectIconFiles.restore(projectRoot, snapshot) }
                    onApplied()
                }
            },
            modifier = Modifier.weight(1f),
            bg = c.field,
            fg = c.text2,
        )
        BigButton(
            text = if (busy) "Applying…" else "Apply to project",
            onClick = {
                val uri = picked
                busy = true
                error = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        if (uri == null) {
                            ProjectIconFiles.writeBackground(projectRoot, projectColors[selectedBg])
                        } else {
                            ProjectIconFiles.apply(projectRoot, context, uri, projectColors[selectedBg])
                        }
                    }
                    busy = false
                    outcome
                        .onSuccess {
                            applied = true
                            onApplied()
                        }
                        .onFailure { error = it.message ?: "The icon could not be written." }
                }
            },
            modifier = Modifier.weight(1.6f),
            icon = "check",
            enabled = !busy && (picked != null || selectedBg != 0),
        )
    }) {
        Card20 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LetterOrImage(preview, 88.dp, 24.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (hasIcon) "Current icon" else "No icon yet",
                        color = c.text,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (hasIcon) {
                            "Read from this project's res folder"
                        } else {
                            "Your app uses the default Android icon."
                        },
                        color = c.muted,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                listOf("Circle" to 26, "Rounded" to 16, "Square" to 4).forEach { (name, radius) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LetterOrImage(preview, 52.dp, radius.dp)
                        Text(
                            text = name,
                            color = c.muted,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        IconSectionLabel("Icon image")
        Column(Modifier.padding(horizontal = 16.dp)) {
            BigButton(
                text = "Choose an image",
                onClick = { picker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
                icon = "image",
            )
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    color = c.red,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            IconNote("Pick a square PNG from Files or Gallery. 1024 by 1024 is best, 512 by 512 is the smallest.")
        }
        IconSectionLabel("Background colour")
        Row(
            Modifier.padding(start = 16.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            projectColors.forEachIndexed { index, color ->
                Box(
                    Modifier
                        .size(34.dp)
                        .then(
                            if (index == selectedBg) {
                                Modifier
                                    .border(2.dp, c.accent, RoundedCornerShape(14.dp))
                                    .padding(2.dp)
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .clickable { selectedBg = index; applied = false },
                )
            }
        }
        IconNote(
            "Pictures with transparent edges (an adaptive icon foreground) sit on this colour.",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        if (applied) {
            Text(
                text = "Applied to the project",
                color = c.green,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LetterOrImage(image: ImageBitmap?, size: Dp, radius: Dp) {
    val c = LocalCcColors.current
    val shape = RoundedCornerShape(radius)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(c.faint.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon("image", c.muted, size * 0.4f, 1.6f)
        }
    }
}

private fun decode(bytes: ByteArray): Bitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

/** Reads and writes the launcher icon files the guide names. */
internal object ProjectIconFiles {
    const val LAUNCHER = "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
    private val sizes = linkedMapOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192,
    )
    private val paths = buildList {
        sizes.keys.forEach { density ->
            add("app/src/main/res/mipmap-$density/ic_launcher.png")
            add("app/src/main/res/mipmap-$density/ic_launcher_round.png")
        }
        add("app/src/main/res/values/ic_launcher_background.xml")
    }

    fun snapshot(root: File?): Map<String, ByteArray?> {
        if (root == null) return emptyMap()
        return paths.associateWith { relative ->
            File(root, relative).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
        }
    }

    fun restore(root: File?, snapshot: Map<String, ByteArray?>) {
        if (root == null) return
        snapshot.forEach { (relative, bytes) ->
            val file = File(root, relative)
            if (bytes == null) file.delete() else {
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
        }
    }

    fun writeBackground(root: File?, color: Color): Result<Unit> = runCatching {
        val dir = root ?: error("The project folder could not be opened.")
        val file = File(dir, "app/src/main/res/values/ic_launcher_background.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<resources><color name="ic_launcher_background">#%06X</color></resources>"""
                .format(color.toArgb() and 0xFFFFFF),
        )
    }

    fun apply(root: File?, context: Context, source: Uri, background: Color): Result<Unit> = runCatching {
        val dir = root ?: error("The project folder could not be opened.")
        val bmp = context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it) }
            ?: error("This file is not a readable picture")
        require(minOf(bmp.width, bmp.height) >= 512) {
            "The picture is too small. Use at least 512 by 512."
        }
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(
            bmp,
            (bmp.width - side) / 2,
            (bmp.height - side) / 2,
            side,
            side,
        )
        sizes.forEach { (density, px) ->
            val folder = File(dir, "app/src/main/res/mipmap-$density").apply { mkdirs() }
            val scaled = Bitmap.createScaledBitmap(square, px, px, true)
            File(folder, "ic_launcher.png").outputStream().use {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(folder, "ic_launcher_round.png").outputStream().use {
                roundOf(scaled).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        writeBackground(dir, background).getOrThrow()
    }

    private fun roundOf(source: Bitmap): Bitmap {
        val size = source.width
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return output
    }
}
