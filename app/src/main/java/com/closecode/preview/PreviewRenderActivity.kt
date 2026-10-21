package com.closecode.preview

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.closecode.ui.ide.PreviewBuild
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Draws the project's real @Preview composables, in CloseCode's own `:preview` process.
 *
 * The process boundary is the safety net: a preview that crashes a class loader or exhausts
 * memory takes down this activity, not the editor. The APK built by [PreviewBuild] is loaded
 * with a class loader whose parent is CloseCode, so Compose runtime classes are shared (the
 * template pins the same BOM) while the project's own classes come from its dex. Its resources
 * are attached to a separate AssetManager, which is what makes project fonts, drawables and
 * colors resolve inside the harness.
 *
 * One view is composed, laid out at the requested device size, given a few frames to settle,
 * then drawn into a bitmap. Pictures are scaled before they are written as PNGs, so only small
 * files cross the process boundary — in fact they cross through the app's cache directory,
 * not through Binder at all.
 */
class PreviewRenderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_WIDTH = "widthPx"
        const val EXTRA_HEIGHT = "heightPx"
        const val EXTRA_DARK = "dark"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        // Invisible, not merely transparent: a full-size decor gives Compose a real size to
        // lay out, while alpha 0 keeps every frame off the screen.
        window.setDimAmount(0f)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        val outputDir = intent.getStringExtra(EXTRA_OUTPUT)?.let(::File)
        if (outputDir == null) {
            finish()
            return
        }
        val widthPx = intent.getIntExtra(EXTRA_WIDTH, 1080).coerceIn(120, 4096)
        val heightPx = intent.getIntExtra(EXTRA_HEIGHT, 2200).coerceIn(120, 4096)
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)
        val container = FrameLayout(this)
        setContentView(container, ViewGroup.LayoutParams(widthPx, heightPx))
        window.decorView.alpha = 0f
        lifecycleScope.launch {
            render(outputDir, widthPx, heightPx, dark, container)
        }
    }

    private suspend fun render(
        outputDir: File,
        widthPx: Int,
        heightPx: Int,
        dark: Boolean,
        container: FrameLayout,
    ) {
        val planFile = File(outputDir, PreviewBuild.PLAN_NAME)
        val index = JSONArray()
        try {
            val plan = JSONObject(planFile.readText())
            val apk = File(plan.getString("apk"))
            val loader = DexClassLoader(
                apk.absolutePath,
                File(cacheDir, "preview-dex").absolutePath,
                null,
                javaClass.classLoader,
            )
            val projectContext = projectContext(apk, dark)
            val entries = plan.getJSONArray("entries")
            for (position in 0 until entries.length()) {
                val entry = entries.getJSONObject(position)
                val row = JSONObject().apply {
                    put("id", entry.optString("id"))
                    put("function", entry.optString("function"))
                    put("png", "")
                    put("widthPx", 0)
                    put("heightPx", 0)
                }
                try {
                    val harness = Class.forName(
                        entry.getString("harness"),
                        true,
                        loader,
                    ).getDeclaredConstructor().newInstance()
                    val host = harness as PreviewHost
                    val view = ComposeView(projectContext).apply {
                        layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
                    }
                    container.removeAllViews()
                    container.addView(view)
                    // ComposeView needs a window before it can find its recomposer; the
                    // activity's decor is attached after onCreate returns, so wait for it.
                    awaitAttached(view)
                    view.setContent {
                        PreviewSurface(dark) { host.Content() }
                    }
                    val exactWidth = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
                    val exactHeight = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                    view.measure(exactWidth, exactHeight)
                    view.layout(0, 0, widthPx, heightPx)
                    // One frame composes, the second paints effects that run after it. The short
                    // wait covers a project drawable or font that resolves through the attached
                    // resources; network images are not loaded at all, as the panel says.
                    awaitFrame()
                    awaitFrame()
                    delay(250)
                    awaitFrame()
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val picture = scaleToCard(bitmap)
                    val png = File(outputDir, "${entry.optString("id")}.png")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(png).use { stream ->
                            picture.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        }
                    }
                    row.put("png", png.name)
                    row.put("widthPx", picture.width)
                    row.put("heightPx", picture.height)
                    if (picture !== bitmap) picture.recycle()
                    bitmap.recycle()
                } catch (error: Throwable) {
                    row.put("error", plain(error))
                }
                index.put(row)
            }
            File(outputDir, PreviewBuild.INDEX_NAME).writeText(index.toString())
            setResult(RESULT_OK)
        } catch (error: Throwable) {
            // No index means the caller keeps its old cards and explains this one line.
            File(outputDir, PreviewBuild.ERROR_NAME).writeText(plain(error))
            setResult(RESULT_OK)
        }
        finish()
    }

    /**
     * The activity's context with the project's resources swapped in. Compose reads
     * [Context.getResources] through LocalContext, so painterResource and Font see the APK.
     * The configuration carries the chosen light/dark mode, which is what
     * isSystemInDarkTheme() reads.
     */
    private fun projectContext(apk: File, dark: Boolean): Context {
        // A new AssetManager that contains only the preview APK. The constructor and
        // addAssetPath are not in the public SDK, but they are the supported-at-runtime way
        // resource overlays have always been loaded; keeping the manager separate is what
        // stops the project's 0x7f ids from colliding with CloseCode's own.
        val managerClass = AssetManager::class.java
        val assets = managerClass.getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as AssetManager
        val added = managerClass.getMethod("addAssetPath", String::class.java)
            .invoke(assets, apk.absolutePath) as? Int ?: 0
        if (added == 0) {
            throw IllegalStateException("The preview APK's resources could not be loaded.")
        }
        val configuration = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val projectResources = Resources(assets, resources.displayMetrics, configuration)
        return ProjectContext(this, projectResources)
    }

    private suspend fun awaitFrame() {
        suspendCancellableCoroutine { continuation ->
            Choreographer.getInstance().postFrameCallback {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /**
     * A ComposeView asks its window for the recomposer when setContent runs. The activity's
     * decor is attached a traversal after onCreate, so composing immediately fails with
     * "Cannot locate windowRecomposer". Wait for attachment, with a hard stop so a window
     * that never attaches produces a sentence instead of a frozen render.
     */
    private suspend fun awaitAttached(view: View) {
        var frames = 0
        while (!view.isAttachedToWindow) {
            if (++frames > 120) {
                throw IllegalStateException("The preview window did not attach.")
            }
            awaitFrame()
        }
    }

    private fun scaleToCard(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= 540) return bitmap
        val height = (bitmap.height.toLong() * 540 / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, 540, height, true)
    }

    private fun plain(error: Throwable): String {
        val message = error.message?.replace('\n', ' ')?.trim()
        val text = if (message.isNullOrBlank()) error.javaClass.simpleName else message
        return text.take(240)
    }
}

@Composable
private fun PreviewSurface(dark: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF0B0D12) else Color.White),
    ) {
        content()
    }
}

/** A context whose resources are the preview APK's. */
private class ProjectContext(base: Context, private val projectResources: Resources) :
    ContextWrapper(base) {
    override fun getResources(): Resources = projectResources
    override fun getAssets(): AssetManager = projectResources.assets
}
