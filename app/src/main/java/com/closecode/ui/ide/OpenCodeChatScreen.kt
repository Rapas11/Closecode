package com.closecode.ui.ide

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.closecode.ai.OpenCodePageLog
import com.closecode.ai.OpenCodeSessions
import com.closecode.ai.isRestorableOpenCodePage
import com.closecode.ai.openCodePagePath
import com.closecode.ai.openCodePageUrl
import com.closecode.ai.openCodeServerKey
import com.closecode.ai.rebaseOpenCodeServerPath
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import org.json.JSONObject

/**
 * OpenCode's own web page, inside the app.
 *
 * The page is not redesigned and not reimplemented: it is OpenCode's own page, loaded from the
 * phone's own loopback. Two things are enforced around it:
 *
 *  - the page itself stays on the local address. The server is bound to 127.0.0.1, and a link
 *    that points somewhere else is handed to the phone's browser instead of navigating the
 *    WebView away from the project;
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
    baseUrl: String,
    projectUrl: String,
    projectDirectory: String,
    sessionId: String = "",
    generation: Int = 0,
    onBack: () -> Unit,
    onAuthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val holder = remember { WebViewHolder() }
    fun pageTarget(): PageTarget {
        if (baseUrl.isNotBlank() && sessionId.isNotBlank()) {
            return PageTarget(OpenCodeSessions.sessionPageUrl(baseUrl, sessionId), false)
        }
        val saved = savedPagePath(context, baseUrl, projectDirectory)
        if (saved != null) return PageTarget(openCodePageUrl(baseUrl, saved), false)
        if (baseUrl.isBlank() || projectUrl.isBlank()) {
            return PageTarget(projectUrl.ifBlank { baseUrl }, false)
        }
        return PageTarget(openCodePageUrl(baseUrl, "/") + RESUME_QUERY, true)
    }
    val initialTarget = remember(baseUrl, projectUrl, projectDirectory, sessionId) { pageTarget() }
    var currentUrl by remember { mutableStateOf(initialTarget.url) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileCallback
        fileCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        val clip = data?.clipData
        val uris: Array<Uri> = when {
            clip != null -> (0 until clip.itemCount)
                .mapNotNull { index -> clip.getItemAt(index).uri }
                .toTypedArray()

            data?.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        callback.onReceiveValue(uris)
    }

    // Leaving the page must not walk OpenCode's router history: Back can land on a
    // new-session route, which creates a session the reader never asked for.
    BackHandler { onBack() }

    LaunchedEffect(baseUrl, projectUrl, projectDirectory, sessionId, generation) {
        val view = holder.view ?: return@LaunchedEffect
        if (holder.baseUrl == baseUrl &&
            holder.projectUrl == projectUrl &&
            holder.projectDirectory == projectDirectory &&
            holder.sessionId == sessionId &&
            holder.generation == generation
        ) {
            return@LaunchedEffect
        }
        val target = pageTarget()
        holder.baseUrl = baseUrl
        holder.projectUrl = projectUrl
        holder.projectDirectory = projectDirectory
        holder.sessionId = sessionId
        holder.generation = generation
        holder.bootstrap = target.bootstrap
        currentUrl = target.url
        OpenCodePageLog.add("open ${target.url}")
        view.loadUrl(target.url)
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
            url = currentUrl,
            onBack = onBack,
            onAuthenticate = onAuthenticate,
            onRefresh = { holder.view?.reload() },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(
                factory = { webContext ->
                    if (com.closecode.BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }
                    val target = pageTarget()
                    WebView(webContext).apply {
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

                            // The OpenCode page uses a normal file input for attachments.
                            // Android has no built-in answer to that input, so the WebView
                            // asks this client for an Intent and waits for the chosen URIs.
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: WebChromeClient.FileChooserParams?,
                            ): Boolean {
                                fileCallback?.onReceiveValue(null)
                                fileCallback = filePathCallback
                                if (filePathCallback == null) return false
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                return runCatching {
                                    fileLauncher.launch(
                                        Intent.createChooser(intent, "Choose a file"),
                                    )
                                    true
                                }.getOrElse {
                                    fileCallback = null
                                    filePathCallback.onReceiveValue(null)
                                    false
                                }
                            }
                        }
                        holder.view = this
                        holder.baseUrl = baseUrl
                        holder.projectUrl = projectUrl
                        holder.projectDirectory = projectDirectory
                        holder.sessionId = sessionId
                        holder.generation = generation
                        holder.bootstrap = target.bootstrap
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
                                val target = request?.url ?: return true
                                val host = target.host
                                if (host == "127.0.0.1" || host == "localhost" || host == "::1") {
                                    return false
                                }
                                // A provider login or documentation link belongs in the
                                // phone's browser, not inside the project page's WebView.
                                // Keep the page on the project it was opened for.
                                val context = view?.context ?: return true
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, target)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "No app can open that link",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                return true
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                pageUrl: String?,
                                isReload: Boolean,
                            ) {
                                if (pageUrl == null) return
                                currentUrl = pageUrl
                                rememberPagePath(
                                    view?.context,
                                    holder.baseUrl,
                                    holder.projectDirectory,
                                    pageUrl,
                                )
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                startedUrl: String?,
                                favicon: Bitmap?,
                            ) {
                                if (startedUrl != null) currentUrl = startedUrl
                                OpenCodePageLog.add("page started: $startedUrl")
                                installPageLog(view)
                                installViewportFix(view)
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                installViewportFix(view)
                            }

                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                OpenCodePageLog.add("page finished: $finishedUrl")
                                if (finishedUrl != null) {
                                    currentUrl = finishedUrl
                                    rememberPagePath(
                                        view?.context,
                                        holder.baseUrl,
                                        holder.projectDirectory,
                                        finishedUrl,
                                    )
                                }
                                installPageLog(view)
                                installViewportFix(view)
                                if (holder.bootstrap) {
                                    holder.bootstrap = false
                                    installResumeScript(
                                        view,
                                        holder.baseUrl,
                                        holder.projectUrl,
                                        holder.projectDirectory,
                                    )
                                }
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
                        loadUrl(target.url)
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
    onAuthenticate: () -> Unit,
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
                DropdownMenuItem(
                    text = { PageBarMenuText("Authenticate") },
                    onClick = {
                        menuOpen = false
                        onAuthenticate()
                    },
                )
                DropdownMenuItem(
                    text = { PageBarMenuText("Close page") },
                    onClick = {
                        menuOpen = false
                        onBack()
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

private data class PageTarget(val url: String, val bootstrap: Boolean)

/** The one WebView this screen owns, so the back handler can reach it. */
private class WebViewHolder {
    var view: WebView? = null
    var baseUrl: String = ""
    var projectUrl: String = ""
    var projectDirectory: String = ""
    var sessionId: String = ""
    var generation: Int = -1
    var bootstrap: Boolean = false
}

private const val PAGE_PREFS = "closecode_open_code_page"
private const val PAGE_PATH_PREFIX = "last:"
private const val PAGE_PATHS_VERSION = "pathsVersion"
private const val PAGE_PATHS_VERSION_CURRENT = 2
private const val RESUME_QUERY = "?closecode_resume=1"

/**
 * Build 36 could save another project's session under this project after the resume script
 * force-adopted a pending tab. Drop those saved routes once; the session itself stays in the
 * page's own storage and the fixed resume script can find it by directory.
 */
private fun pagePrefs(context: Context) =
    context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE).also { prefs ->
        if (prefs.getInt(PAGE_PATHS_VERSION, 1) < PAGE_PATHS_VERSION_CURRENT) {
            prefs.edit()
                .clear()
                .putInt(PAGE_PATHS_VERSION, PAGE_PATHS_VERSION_CURRENT)
                .apply()
        }
    }

private fun savedPagePath(context: Context, baseUrl: String, directory: String): String? {
    if (baseUrl.isBlank() || directory.isBlank()) return null
    val saved = pagePrefs(context)
        .getString(PAGE_PATH_PREFIX + directory, null) ?: return null
    return rebaseOpenCodeServerPath(saved, baseUrl)
        .takeIf { isRestorableOpenCodePage(it) }
}

private fun rememberPagePath(
    context: Context?,
    baseUrl: String,
    directory: String,
    pageUrl: String,
) {
    if (context == null || baseUrl.isBlank() || directory.isBlank()) return
    val path = openCodePagePath(baseUrl, pageUrl) ?: return
    if (!isRestorableOpenCodePage(path)) return
    pagePrefs(context)
        .edit()
        .putString(PAGE_PATH_PREFIX + directory, path)
        .apply()
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

private fun installResumeScript(
    view: WebView?,
    baseUrl: String,
    projectUrl: String,
    directory: String,
) {
    if (view == null || baseUrl.isBlank() || directory.isBlank()) return
    val directoryLiteral = JSONObject.quote(directory)
    val projectUrlLiteral = JSONObject.quote(projectUrl)
    val serverLiteral = JSONObject.quote(openCodeServerKey(baseUrl))
    val tabsKeyLiteral = JSONObject.quote("opencode.window.browser.dat:tabs")
    val recentKeyLiteral = JSONObject.quote("opencode.window.browser.dat:tabs.recent")
    val infoKeyLiteral = JSONObject.quote("opencode.window.browser.dat:tabs.info")
    val legacyKeyLiteral = JSONObject.quote("opencode.window.browser.dat:lastProjectSession")
    val script = """
        (function () {
            if (window.__closecodeResume) return;
            window.__closecodeResume = true;
            if (window.location.search.indexOf('closecode_resume=1') < 0) return;
            try { history.replaceState(null, '', window.location.pathname); } catch (error) {}
            var directory = $directoryLiteral;
            var projectUrl = $projectUrlLiteral;
            var server = $serverLiteral;
            var tabsKey = $tabsKeyLiteral;
            var recentKey = $recentKeyLiteral;
            var infoKey = $infoKeyLiteral;
            var legacyKey = $legacyKeyLiteral;
            var read = function (key) {
                try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch (error) { return null; }
            };
            var b64 = function (text) {
                return btoa(text).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
            };
            var samePath = function (left, right) {
                if (typeof left !== 'string' || typeof right !== 'string') return false;
                return left.replace(/\/+$/, '') === right.replace(/\/+$/, '');
            };
            var href = function (tab, targetServer) {
                if (tab.type === 'draft') {
                    return '/new-session?draftId=' + encodeURIComponent(tab.draftID);
                }
                return '/server/' + b64(targetServer || tab.server) + '/session/' + tab.sessionId;
            };
            var key = function (tab) {
                return tab.type === 'draft' ? 'draft:' + tab.draftID : tab.server + '\n' + href(tab, tab.server);
            };
            var tabs = read(tabsKey);
            var info = read(infoKey) || {};
            var recent = read(recentKey) || {};
            var matches = [];
            var pending = false;
            var collect = function () {
                matches = [];
                pending = false;
                if (!Array.isArray(tabs)) return;
                tabs.forEach(function (tab) {
                    if (!tab || typeof tab !== 'object') return;
                    if (tab.type === 'draft') {
                        if (samePath(tab.directory || tab.worktree, directory)) matches.push(tab);
                        return;
                    }
                    if (tab.type !== 'session') return;
                    var record = info[key(tab)];
                    if (record && typeof record.directory === 'string') {
                        if (samePath(record.directory, directory)) matches.push(tab);
                        return;
                    }
                    if (tab.server === server) pending = true;
                });
            };
            var choose = function () {
                var chosen = null;
                if (recent && recent.key) {
                    for (var i = 0; i < matches.length; i++) {
                        if (key(matches[i]) === recent.key) { chosen = matches[i]; break; }
                    }
                }
                return chosen || matches[matches.length - 1] || null;
            };
            var go = function (force) {
                var chosen = choose();
                if (chosen) {
                    if (chosen.type === 'draft' && chosen.server !== server) {
                        chosen.server = server;
                        try { localStorage.setItem(tabsKey, JSON.stringify(tabs)); } catch (error) {}
                    }
                    window.location.replace(href(chosen, server));
                    return true;
                }
                if (pending && !force) return false;
                var legacy = read(legacyKey);
                var legacyID = legacy && (legacy.id || legacy.sessionId);
                if (legacyID && samePath(legacy.directory, directory)) {
                    window.location.replace('/server/' + b64(server) + '/session/' + legacyID);
                    return true;
                }
                if (projectUrl) window.location.replace(projectUrl);
                return true;
            };
            collect();
            if (go(false)) return;
            var attempts = 0;
            var timer = setInterval(function () {
                info = read(infoKey) || {};
                collect();
                attempts++;
                if (go(false)) {
                    clearInterval(timer);
                    return;
                }
                if (attempts >= 10) {
                    clearInterval(timer);
                    go(true);
                }
            }, 150);
        })();
    """.trimIndent()
    runCatching { view.evaluateJavascript(script, null) }
}

/**
 * Gives OpenCode's page a real height in WebViews that resolve vertical viewport units to zero.
 *
 * The system WebView on some phones reports the viewport height correctly to JavaScript but
 * computes `vh`, `dvh`, `svh` and `lvh` as `0px`. The page's root and every stylesheet rule are
 * rewritten to use a measured CSS variable, and the value is reapplied whenever the viewport or
 * the WebView is resized (keyboard, rotation, split screen).
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
            var html = document.documentElement;
            if (!html) return;
            html.style.setProperty('--closecode-vh', height + 'px');
            var root = document.getElementById('root');
            if (root) root.style.setProperty('height', height + 'px', 'important');
        };
        var rewriteValue = function (value) {
            return value.replace(/(^|[^\w.-])(\d*\.?\d+)(d|s|l)?vh\b/gi, function (match, prefix, number) {
                return prefix + 'calc(var(--closecode-vh) * ' + (parseFloat(number) / 100) + ')';
            });
        };
        var rewriteRule = function (rule) {
            try {
                if (rule.style && rule.style.cssText) {
                    var css = rule.style.cssText;
                    var next = rewriteValue(css);
                    if (next !== css) rule.style.cssText = next;
                }
            } catch (error) {}
            try {
                var children = rule.cssRules;
                if (children) {
                    for (var i = 0; i < children.length; i++) rewriteRule(children[i]);
                }
            } catch (error) {}
        };
        var rewriteSheet = function (sheet) {
            try {
                var rules = sheet.cssRules;
                if (!rules) return;
                for (var i = 0; i < rules.length; i++) rewriteRule(rules[i]);
            } catch (error) {}
        };
        var rewriteElement = function (element) {
            if (!element || !element.style) return;
            var css = element.style.cssText;
            if (!/(\d*\.?\d+)(d|s|l)?vh\b/i.test(css)) return;
            var next = rewriteValue(css);
            if (next !== css) element.style.cssText = next;
        };
        var rewriteStyles = function () {
            var sheets = document.styleSheets;
            for (var i = 0; i < sheets.length; i++) rewriteSheet(sheets[i]);
            var adopted = document.adoptedStyleSheets;
            if (adopted) {
                for (var j = 0; j < adopted.length; j++) rewriteSheet(adopted[j]);
            }
            var elements = document.querySelectorAll('[style]');
            for (var k = 0; k < elements.length; k++) rewriteElement(elements[k]);
        };
        window.__closecodeApplyViewport = apply;
        window.__closecodeRewriteViewport = rewriteStyles;
        if (!window.__closecodeViewportBound) {
            window.__closecodeViewportBound = true;
            window.addEventListener('resize', apply);
            if (window.visualViewport) {
                window.visualViewport.addEventListener('resize', apply);
            }
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', apply);
            }
            if (window.MutationObserver) {
                var observer = new MutationObserver(function (records) {
                    var styles = false;
                    for (var i = 0; i < records.length; i++) {
                        var record = records[i];
                        if (record.type === 'characterData') {
                            var parent = record.target.parentNode;
                            if (parent && parent.tagName === 'STYLE') styles = true;
                            continue;
                        }
                        if (record.type === 'attributes') {
                            rewriteElement(record.target);
                            continue;
                        }
                        var added = record.addedNodes;
                        for (var j = 0; j < added.length; j++) {
                            var node = added[j];
                            if (!node || node.nodeType !== 1) continue;
                            if (node.tagName === 'STYLE' || node.tagName === 'LINK') {
                                styles = true;
                                if (node.tagName === 'LINK') node.addEventListener('load', rewriteStyles);
                            }
                            if (node.querySelector && node.querySelector('style,link[rel="stylesheet"]')) {
                                styles = true;
                            }
                        }
                    }
                    if (styles) setTimeout(rewriteStyles, 0);
                });
                observer.observe(document.documentElement || document, {
                    childList: true,
                    subtree: true,
                    characterData: true,
                    attributes: true,
                    attributeFilter: ['style']
                });
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
        rewriteStyles();
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
