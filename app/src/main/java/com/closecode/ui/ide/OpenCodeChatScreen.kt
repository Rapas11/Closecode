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
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodePageLog
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

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

    Column(
        modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        OpenCodePageBar(
            url = url,
            onBack = onBack,
            onRefresh = { holder.view?.reload() },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(
                factory = { context ->
                    if (com.closecode.BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
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
                        // The page opens on its new-session prompt. Do not give the WebView
                        // focus on load: that is what opens the keyboard before the user has
                        // touched anything. A tap on the prompt still focuses it normally.
                        settings.setNeedInitialFocus(false)
                        setBackgroundColor(AndroidColor.parseColor("#0F1117"))
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val host = request?.url?.host ?: return true
                                // Only the loopback is allowed. Everything else is refused.
                                return !(
                                    host == "127.0.0.1" ||
                                        host == "localhost" ||
                                        host == "::1"
                                    )
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                startedUrl: String?,
                                favicon: Bitmap?,
                            ) {
                                OpenCodePageLog.add("page started: $startedUrl")
                                installPageLog(view)
                                installViewportFix(view)
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                installViewportFix(view)
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                OpenCodePageLog.add("page finished: $finishedUrl")
                                installPageLog(view)
                                installViewportFix(view)
                                // React can mount a moment after the document finishes; apply
                                // once more after that so the page fills the WebView at once.
                                view?.postDelayed({ installViewportFix(view) }, 300)
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
                        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            post { installViewportFix(this) }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The small app bar above OpenCode's own page, matching the app's top bar.
 *
 * OpenCode's page has its own header; this bar is only the way out, a refresh, the browser
 * fallback and the project link. The page itself is untouched below it.
 */
@Composable
private fun OpenCodePageBar(
    url: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }

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
        PageBarButton(
            icon = CcIcons.ChevronLeft,
            label = "Back",
            tint = c.iconTint,
            onClick = onBack,
        )
        Text(
            text = "OpenCode",
            color = c.brightText,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            PageBarButton(
                icon = CcIcons.More,
                label = "More",
                tint = if (menuOpen) c.tx else c.iconTint,
                background = if (menuOpen) c.panel2 else Color.Transparent,
                onClick = { menuOpen = true },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { PageBarMenuText("Refresh") },
                    onClick = {
                        menuOpen = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { PageBarMenuText("Open in browser") },
                    onClick = {
                        menuOpen = false
                        openOpenCodeInBrowser(context, url)
                    },
                )
                DropdownMenuItem(
                    text = { PageBarMenuText("Copy link") },
                    onClick = {
                        menuOpen = false
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun PageBarMenuText(text: String) {
    val c = LocalPageColors.current
    Text(
        text = text,
        color = c.tx,
        fontSize = 13.sp,
        fontFamily = Sans,
    )
}

/** One 34 x 34 page-bar button holding a 19 dp icon. */
@Composable
private fun PageBarButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(icon, tint, 19.dp)
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

/**
 * Gives OpenCode's page a real height in WebViews that resolve `vh`/`dvh` to zero.
 *
 * OpenCode's root element is `height: 100dvh`. The system WebView on some phones reports the
 * viewport height correctly to JavaScript but computes every viewport unit to `0px`; the root
 * then collapses to its 1 px padding and the page looks black, while the same page in a browser
 * is fine. The page is not redesigned: its own root element gets the measured CSS-pixel height
 * as an inline style, and the same value is reapplied whenever the viewport or the WebView is
 * resized (keyboard, rotation, split screen).
 */
private fun installViewportFix(view: WebView?) {
    if (view == null) return
    runCatching { view.evaluateJavascript(PAGE_VIEWPORT_SCRIPT, null) }
}

private const val PAGE_VIEWPORT_SCRIPT = """
    (function () {
        var apply = function () {
            var height = window.innerHeight;
            if (!height) return;
            var root = document.getElementById('root');
            if (root) root.style.setProperty('height', height + 'px', 'important');
        };
        window.__closecodeApplyViewport = apply;
        if (!window.__closecodeViewportBound) {
            window.__closecodeViewportBound = true;
            window.addEventListener('resize', apply);
            if (window.visualViewport) {
                window.visualViewport.addEventListener('resize', apply);
            }
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', apply);
            }
            // OpenCode focuses its new-session prompt on load. Blur that one focus so the
            // keyboard does not open before the user has touched the page. The guard is
            // dropped as soon as the user touches the page, so their own tap always focuses
            // the prompt and typing stays visible.
            var touched = false;
            var markTouched = function () {
                touched = true;
                document.removeEventListener('pointerdown', markTouched, true);
                document.removeEventListener('keydown', markTouched, true);
            };
            document.addEventListener('pointerdown', markTouched, true);
            document.addEventListener('keydown', markTouched, true);
            var guard = function (event) {
                if (touched) return;
                var element = event.target;
                if (element && (
                    element.tagName === 'INPUT' ||
                    element.tagName === 'TEXTAREA' ||
                    element.isContentEditable
                )) {
                    element.blur();
                    document.removeEventListener('focusin', guard, true);
                }
            };
            document.addEventListener('focusin', guard, true);
            setTimeout(function () {
                document.removeEventListener('focusin', guard, true);
            }, 1200);
        }
        apply();
    })();
"""

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
