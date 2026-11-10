package com.closecode.device

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.google.gson.JsonObject
import java.io.File

/**
 * The in-app website preview. It also runs a JavaScript assertion script, writes the served
 * DOM, and draws the rendered page to a PNG so `closecode preview --test` is a real browser
 * test rather than an HTTP fetch.
 */
class WebPreviewActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var output: File
    private var script: String? = null
    private var screenshot = false
    private var finishAfter = false
    private var pageFinished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        val directory = intent.getStringExtra(EXTRA_OUTPUT)
        if (url.isNullOrBlank() || directory.isNullOrBlank()) {
            finish()
            return
        }
        output = File(directory).apply { mkdirs() }
        script = intent.getStringExtra(EXTRA_SCRIPT)?.takeIf { it.isNotBlank() }
        screenshot = intent.getBooleanExtra(EXTRA_SCREENSHOT, false)
        finishAfter = intent.getBooleanExtra(EXTRA_FINISH, false)
        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    pageFinished = true
                    Handler(Looper.getMainLooper()).postDelayed({ afterPage() }, 350)
                }
            }
        }
        val container = FrameLayout(this).apply { addView(webView) }
        setContentView(container)
        webView.loadUrl(url)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!pageFinished) {
                writeResult(false, "The page did not finish loading in time.", null)
                if (finishAfter) finish()
            }
        }, 25_000)
    }

    private fun afterPage() {
        if (script != null) {
            webView.evaluateJavascript("(function(){${script}\n})()") { value ->
                val parsed = runCatching {
                    com.google.gson.JsonParser.parseString(value)
                }.getOrNull()
                val textual = runCatching { parsed?.asString }.getOrNull()
                val hasError = textual?.let { runCatching { com.google.gson.JsonParser.parseString(it).asJsonObject.get("ok")?.asBoolean }.getOrNull() } == false
                writeResult(!hasError, null, parsed)
            }
        }
        if (screenshot) {
            Handler(Looper.getMainLooper()).postDelayed({
                capture()
                if (finishAfter) finish()
            }, 500)
        } else if (finishAfter) {
            finish()
        }
    }

    private fun capture() {
        runCatching {
            val bitmap = Bitmap.createBitmap(
                webView.width.coerceAtLeast(1),
                webView.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            webView.draw(android.graphics.Canvas(bitmap))
            File(output, "screenshot.png").outputStream().buffered().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }.onFailure { writeResult(false, "Screenshot failed: ${it.message}", null) }
    }

    private fun writeResult(ok: Boolean, error: String?, value: com.google.gson.JsonElement?) {
        if (!::output.isInitialized) return
        runCatching {
            val json = JsonObject().apply {
                addProperty("ok", ok)
                error?.let { addProperty("error", it) }
                value?.let { add("value", it) }
            }
            File(output, "result.json").writeText(json.toString())
        }
    }

    override fun onDestroy() {
        runCatching { webView.destroy() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "com.closecode.preview.URL"
        const val EXTRA_OUTPUT = "com.closecode.preview.OUTPUT"
        const val EXTRA_SCRIPT = "com.closecode.preview.SCRIPT"
        const val EXTRA_SCREENSHOT = "com.closecode.preview.SCREENSHOT"
        const val EXTRA_FINISH = "com.closecode.preview.FINISH"
    }
}
