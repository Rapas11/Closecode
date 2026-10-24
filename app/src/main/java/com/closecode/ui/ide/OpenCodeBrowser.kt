package com.closecode.ui.ide

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Hands OpenCode's project page to the phone's browser.
 *
 * This is the fallback, not the default: the page normally runs in the app's WebView. A Custom
 * Tab keeps the loopback page in the browser's own engine, which matters on phones whose system
 * WebView is older than their Chrome.
 */
internal fun openOpenCodeInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}
