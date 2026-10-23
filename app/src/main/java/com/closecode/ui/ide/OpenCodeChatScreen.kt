package com.closecode.ui.ide

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.closecode.ui.theme.LocalPageColors

/**
 * OpenCode's own web page, inside the app.
 *
 * The page is not redesigned and not reimplemented: it is OpenCode's own page, loaded from the
 * phone's own loopback. Three things are enforced around it:
 *
 *  - only the local address may be loaded. The server is bound to 127.0.0.1, but a page could
 *    still try to send the user somewhere else, and the WebView refuses to follow it;
 *  - the password is filled in for the user, so the page opens straight into the chat;
 *  - the back button goes back inside the page first, and only leaves the page at its start —
 *    leaving never stops the server.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenCodeChatScreen(
    url: String,
    password: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val holder = remember { WebViewHolder() }

    // The page's own back button: back inside the page first, then leave the page.
    BackHandler {
        val view = holder.view
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.view?.destroy()
            holder.view = null
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    holder.view = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    setBackgroundColor(AndroidColor.parseColor("#0F1117"))
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val host = request?.url?.host ?: return true
                            // Only the loopback is allowed. Everything else is refused.
                            return !(host == "127.0.0.1" || host == "localhost" || host == "::1")
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            fillPassword(view, password)
                        }

                        /**
                         * The server answers `401 Unauthorized` with an HTTP Basic challenge
                         * before it hands over any page, so the password box is never one the
                         * page could fill: it is answered here, at the network level, straight
                         * out of the same secret the server was started with.
                         */
                        override fun onReceivedHttpAuthRequest(
                            view: WebView?,
                            handler: HttpAuthHandler?,
                            host: String?,
                            realm: String?,
                        ) {
                            if (handler != null && password.isNotBlank()) {
                                handler.proceed("opencode", password)
                            } else {
                                handler?.cancel()
                            }
                        }
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The one WebView this screen owns, so the back handler can reach it. */
private class WebViewHolder {
    var view: WebView? = null
}

/**
 * Fills the password box on OpenCode's page and presses the button.
 *
 * OpenCode's web page asks for the server password when one is set. The password never goes
 * into a log, and it is put in by the page itself rather than shown on the screen.
 */
private fun fillPassword(view: WebView?, password: String) {
    if (view == null || password.isBlank()) return
    val script = """
        (function () {
            var input = document.querySelector('input[type="password"]');
            if (!input) return;
            var setter = Object.getOwnPropertyDescriptor(
                window.HTMLInputElement.prototype, 'value'
            ).set;
            setter.call(input, ${jsString(password)});
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            var form = input.form;
            if (form) { form.submit(); return; }
            var button = document.querySelector('button[type="submit"]');
            if (button) button.click();
        })();
    """.trimIndent()
    runCatching { view.evaluateJavascript(script, null) }
}

/** A JavaScript string literal, so a password with a quote in it cannot break the script. */
internal fun jsString(value: String): String {
    val out = StringBuilder("\"")
    for (ch in value) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            else -> out.append(ch)
        }
    }
    out.append("\"")
    return out.toString()
}
