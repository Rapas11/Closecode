package com.closecode.ui.ide

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodePageLog
import com.closecode.ui.theme.LocalPageColors

/**
 * OpenCode's own web page, inside the app.
 *
 * The page is not redesigned and not reimplemented: it is OpenCode's own page, loaded from the
 * phone's own loopback. Two things are enforced around it:
 *
 *  - only the local address may be loaded. The server is bound to 127.0.0.1, but a page could
 *    still try to send the user somewhere else, and the WebView refuses to follow it;
 *  - the back button goes back inside the page first, and only leaves the page at its start —
 *    leaving never stops the server.
 *
 * The server is unsecured on loopback, as OpenCode documents for local use, so there is no
 * password box to fill and no HTTP-auth challenge to answer. The page's console and its own
 * network failures are copied into [OpenCodePageLog] so the AI tab can show them when the page
 * loads but does not work.
 */
@SuppressLint("SetJavaScriptEnabled", "AddVsClear")
@Composable
fun OpenCodeChatScreen(
    url: String,
    generation: Int = 0,
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

    // A reconnect is a new server: the page that was open belongs to the old one. Load the
    // fresh address into a clean browser instead of leaving OpenCode's error screen up.
    LaunchedEffect(url, generation) {
        val view = holder.view ?: return@LaunchedEffect
        if (holder.url == url && holder.generation == generation) return@LaunchedEffect
        if (holder.generation != generation && OpenCode.consumeWebWipe()) {
            wipePage(view.context)
            runCatching { view.clearCache(true) }
        }
        holder.url = url
        holder.generation = generation
        OpenCodePageLog.add("open $url")
        view.loadUrl(url)
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
                val wipe = OpenCode.consumeWebWipe()
                if (wipe) {
                    // The page this WebView would inherit was written while an older server
                    // ran. OpenCode's frontend trips over state from a dead server — its own
                    // error screen is the known result — so this server's first page loads
                    // into a clean browser instead. The phone's own files keep everything.
                    wipePage(context)
                }
                WebView(context).apply {
                    if (wipe) runCatching { clearCache(true) }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val line = "console ${message.messageLevel()}: " +
                                "${message.message()} " +
                                "(${message.sourceId()}:${message.lineNumber()})"
                            OpenCodePageLog.add(line)
                            if (com.closecode.BuildConfig.DEBUG) {
                                Log.i("ClosecodeWeb", line)
                            }
                            return false
                        }
                    }
                    holder.view = this
                    holder.url = url
                    holder.generation = generation
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

                        override fun onPageStarted(
                            view: WebView?,
                            startedUrl: String?,
                            favicon: Bitmap?,
                        ) {
                            OpenCodePageLog.add("page started: $startedUrl")
                            installPageLog(view)
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            OpenCodePageLog.add("page finished: $finishedUrl")
                            installPageLog(view)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            OpenCodePageLog.add(
                                "page error: ${request?.url} " +
                                    "${error?.errorCode} ${error?.description}",
                            )
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?,
                        ) {
                            OpenCodePageLog.add(
                                "http ${errorResponse?.statusCode}: ${request?.url}",
                            )
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
    var url: String = ""
    var generation: Int = -1
}

/**
 * Clears the state a previous server left in the WebView: cookies and local storage. The
 * server is new; its page must not start from the old one's state.
 */
private fun wipePage(context: android.content.Context) {
    runCatching {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().removeSessionCookies(null)
        WebStorage.getInstance().deleteAllData()
    }
}

/**
 * Adds the page-log hooks to the page that is loading.
 *
 * The hooks print one line for every fetch, XHR and event-source request and for every page
 * error. The WebView's console client copies those lines into [OpenCodePageLog]. Nothing here
 * sends data anywhere; it only makes the page's own failures visible to the AI tab.
 */
private fun installPageLog(view: WebView?) {
    if (view == null) return
    runCatching { view.evaluateJavascript(PAGE_LOG_SCRIPT, null) }
}

private const val PAGE_LOG_SCRIPT = """
    (function () {
        if (window.__closecodePageLog) return;
        window.__closecodePageLog = true;
        var report = function (text) {
            try { console.log(text); } catch (e) {}
        };
        var originalFetch = window.fetch;
        if (originalFetch) {
            window.fetch = function () {
                var input = arguments[0];
                var url = (input && input.url) ? input.url : String(input);
                return originalFetch.apply(window, arguments).then(function (response) {
                    report('[fetch] ' + response.status + ' ' + url);
                    return response;
                }, function (error) {
                    report('[fetch] FAILED ' + url + ' ' + error);
                    throw error;
                });
            };
        }
        var originalOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (method, url) {
            this.addEventListener('loadend', function () {
                report('[xhr] ' + method + ' ' + this.status + ' ' + url);
            });
            return originalOpen.apply(this, arguments);
        };
        window.addEventListener('error', function (event) {
            report('[pageerror] ' + event.message + ' ' + event.filename + ':' + event.lineno);
        });
        window.addEventListener('unhandledrejection', function (event) {
            report('[promise] ' + event.reason);
        });
        var OriginalEventSource = window.EventSource;
        if (OriginalEventSource) {
            window.EventSource = function (url, config) {
                report('[sse] open ' + url);
                var source = new OriginalEventSource(url, config);
                source.addEventListener('error', function () {
                    report('[sse] error ' + url);
                });
                return source;
            };
            window.EventSource.prototype = OriginalEventSource.prototype;
        }
    })();
"""
