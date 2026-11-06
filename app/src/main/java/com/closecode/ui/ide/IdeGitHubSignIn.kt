package com.closecode.ui.ide

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment as UiAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.debug.DebugLog
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.github.DeviceCode
import com.closecode.ui.github.DevicePoll
import com.closecode.ui.github.GitHubClient
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A failure a person can act on. The exception class comes first because the messages Android
 * throws on a dead socket are often null or uselessly short, and the cause chain is what names
 * the real reason (no route, TLS failure, HTTP status). Never contains the token: the client
 * only ever throws messages it built itself.
 */
internal fun describeFailure(error: Throwable, fallback: String): String {
    val head = error.message?.takeIf { it.isNotBlank() }
        ?: "${error::class.simpleName ?: "IOException"}: no detail"
    val seen = mutableSetOf(head)
    val chain = mutableListOf<String>()
    var current: Throwable? = error.cause
    var depth = 0
    while (current != null && depth < 4 && chain.size < 3) {
        val step: Throwable = current
        val cls = step::class.simpleName ?: "Throwable"
        val text = step.message?.takeIf { it.isNotBlank() && it != head }
        val label = if (text == null) cls else "$cls: $text"
        if (seen.add(label)) chain += label
        current = step.cause
        depth++
    }
    return (listOf(head) + chain).joinToString(" — ")
}

/**
 * True when the failure is the phone being offline rather than GitHub saying no: dead route,
 * refused connection or a timeout, at any depth of the cause chain. The sign-in shows
 * "No internet connection" for these instead of a stack-trace sentence.
 */
internal fun isOfflineFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 5) {
        if (current is java.net.UnknownHostException ||
            current is java.net.ConnectException ||
            current is java.net.SocketTimeoutException ||
            current is java.net.NoRouteToHostException
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

/** What a refused authorization reads as. A decline is the user's own tap, not an error. */
internal fun refusedText(reason: String): String =
    if (reason.contains("declin", ignoreCase = true)) "You did not allow access" else reason

/** Seconds as M:SS for the code-expiry countdown. */
internal fun countdownText(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

/**
 * Screen 16: the GitHub Device Flow sign-in as a full page. GitHub hands out a short code,
 * the user types it at github.com/login/device, and this page polls until approval.
 */
@Composable
fun GitHubSignIn(
    onSignedIn: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cc = com.closecode.ui.theme.LocalCcColors.current
    BackHandler(onBack = {
        BackLog.press("github-signin", "branch=dismiss")
        onDismiss()
    })
    Column(
        Modifier
            .fillMaxSize()
            .background(cc.content),
    ) {
        ScreenHeader(
            origin = "GitHub",
            title = "Sign in",
            onBack = onDismiss,
        )
        GitHubSignInBody(
            onSignedIn = onSignedIn,
            onCancel = onDismiss,
        )
    }
}

/**
 * The device-flow steps on their own: the code, what it is waiting for, and the way out.
 *
 * [onCancel] is nullable because the steps have two homes. Full-screen they need a Cancel;
 * embedded in the clone screen they do not — the screen behind them is the way out.
 */
@Composable
internal fun GitHubSignInBody(
    onSignedIn: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val cc = LocalCcColors.current
    val client = remember { GitHubClient() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf<String?>(null) }
    var expired by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var reconnecting by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var remainingSec by remember { mutableStateOf(0) }
    var attempt by remember { mutableStateOf(0) }

    fun reset() {
        code = null
        waiting = false
        username = null
        expired = false
        refused = null
        offline = false
        failure = null
        reconnecting = false
        attempt++
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    LaunchedEffect(code) {
        val start = code ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + start.expiresInSec * 1_000L
        while (true) {
            val left = ((deadline - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
            remainingSec = left
            if (left <= 0) return@LaunchedEffect
            delay(1_000)
        }
    }

    LaunchedEffect(attempt) {
        val requested = withContext(Dispatchers.IO) { client.requestDeviceCode() }
        val start = requested.getOrElse { error ->
            val reason = describeFailure(error, "GitHub could not start the sign-in.")
            if (isOfflineFailure(error)) {
                offline = true
                DebugLog.crumb("GITHUB", "sign-in offline")
            } else {
                failure = reason
                DebugLog.crumb("GITHUB", "sign-in could not start: $reason")
            }
            return@LaunchedEffect
        }
        code = start
        waiting = true
        DebugLog.crumb("GITHUB", "sign-in code shown")
        val deadlines = System.currentTimeMillis() + start.expiresInSec * 1_000L
        var interval = maxOf(start.intervalSec, 5)
        while (true) {
            if (System.currentTimeMillis() >= deadlines) {
                waiting = false
                offline = false
                failure = null
                reconnecting = false
                expired = true
                DebugLog.crumb("GITHUB", "sign-in code expired while offline")
                return@LaunchedEffect
            }
            delay(interval * 1_000L)
            val polled = withContext(Dispatchers.IO) { client.pollForToken(start.deviceCode) }
            val step = polled.getOrNull()
            if (step == null) {
                // A single failed poll is not the end: the phone hopped networks while the
                // browser had the focus. Keep asking until the code expires; the status line
                // says the connection dropped and is trying again.
                val error = polled.exceptionOrNull() ?: return@LaunchedEffect
                if (isOfflineFailure(error)) {
                    reconnecting = true
                    interval = minOf(interval + 2, 15)
                    DebugLog.crumb("GITHUB", "poll lost, retrying")
                    continue
                }
                waiting = false
                reconnecting = false
                failure = describeFailure(error, "GitHub could not finish the sign-in.")
                DebugLog.crumb("GITHUB", "sign-in failed")
                return@LaunchedEffect
            }
            reconnecting = false
            when (step) {
                is DevicePoll.Authorized -> {
                    val login = withContext(Dispatchers.IO) {
                        client.currentUser(step.accessToken).getOrNull()?.login
                    }
                    waiting = false
                    username = login ?: "your account"
                    DebugLog.crumb("GITHUB", "sign-in approved")
                    delay(1_500)
                    onSignedIn(step.accessToken)
                    return@LaunchedEffect
                }
                DevicePoll.Pending -> Unit
                DevicePoll.SlowDown -> interval += 5
                DevicePoll.Expired -> {
                    waiting = false
                    expired = true
                    DebugLog.crumb("GITHUB", "sign-in code expired")
                    return@LaunchedEffect
                }
                is DevicePoll.Refused -> {
                    waiting = false
                    refused = step.reason
                    DebugLog.crumb("GITHUB", "sign-in refused")
                    return@LaunchedEffect
                }
            }
        }
    }

    fun openGitHub() {
        // The device page belongs to the phone's own browser: Custom Tabs die with the app's
        // task and fight the polling on the way back, so the default browser it is. A chooser
        // catches phones without a default set.
        val uri = android.net.Uri.parse("https://github.com/login/device")
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Open with"),
            )
        }
    }

    val statusText = when {
        username != null -> "Connected as @$username"
        expired -> "Code expired"
        refused != null -> refusedText(refused.orEmpty())
        offline -> "No internet connection"
        failure != null -> failure.orEmpty()
        reconnecting -> "Connection lost, trying again…"
        waiting -> "Waiting for you to approve..."
        else -> "Asking GitHub for a code…"
    }
    val statusColor = when {
        username != null -> cc.green
        expired || refused != null || offline || failure != null -> cc.red
        else -> cc.muted
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = UiAlignment.CenterHorizontally,
    ) {
        Text(
            text = "Open the link below and enter this code.",
            color = cc.textSoft,
            fontSize = 13.5.sp,
            fontFamily = Sans,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )

        Box(
            Modifier
                .padding(top = 20.dp, bottom = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cc.field)
                .alpha(if (expired) 0.4f else 1f)
                .padding(vertical = 18.dp),
            contentAlignment = UiAlignment.Center,
        ) {
            Text(
                text = code?.userCode ?: "····-····",
                color = cc.text,
                fontSize = 34.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                lineHeight = 40.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            )
        }

        if (!expired) {
            Row(
                Modifier.padding(top = 12.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = UiAlignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cc.field)
                        .clickable(enabled = code != null, onClick = {
                            code?.userCode?.let { clipboard.setText(AnnotatedString(it)) }
                            copied = true
                        })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = UiAlignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    com.closecode.ui.CcIcon("copy", cc.text2, 16.dp)
                    Text(
                        text = if (copied) "Copied" else "Copy code",
                        color = cc.text2,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cc.field)
                        .clickable(enabled = code != null, onClick = ::openGitHub)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = UiAlignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    com.closecode.ui.CcIcon("globe", cc.text2, 16.dp)
                    Text(
                        text = "Open ${runCatching { java.net.URI(code?.verificationUri).host }.getOrNull() ?: "github.com"}",
                        color = cc.text2,
                        fontSize = 13.5.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp + 18.dp + 40.dp))
        }

        when {
            username != null -> {
                com.closecode.ui.CcIcon("check", cc.green, 15.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Connected as @$username",
                    color = cc.green,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                )
            }
            waiting || reconnecting -> {
                val turn = rememberInfiniteTransition(label = "wait")
                val deg by turn.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                    label = "deg",
                )
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = UiAlignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    com.closecode.ui.CcIcon("refresh", cc.accent, 15.dp, 2.2f, Modifier.rotate(deg))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "Waiting for you to approve…",
                        color = cc.muted,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            expired -> Text(
                text = "This code has expired",
                color = cc.red,
                fontSize = 13.sp,
                fontFamily = Sans,
                textAlign = TextAlign.Center,
            )
            refused != null -> Text(
                text = refusedText(refused.orEmpty()),
                color = cc.red,
                fontSize = 13.sp,
                fontFamily = Sans,
                textAlign = TextAlign.Center,
            )
            offline -> Text(
                text = "No internet connection",
                color = cc.red,
                fontSize = 13.sp,
                fontFamily = Sans,
                textAlign = TextAlign.Center,
            )
            failure != null -> Text(
                text = failure.orEmpty(),
                color = cc.red,
                fontSize = 13.sp,
                fontFamily = Sans,
                textAlign = TextAlign.Center,
            )
            else -> Text(
                text = "Asking GitHub for a code…",
                color = cc.muted,
                fontSize = 13.sp,
                fontFamily = Sans,
                textAlign = TextAlign.Center,
            )
        }

        if (expired || refused != null) {
            Spacer(Modifier.height(22.dp))
            com.closecode.ui.components.PrimaryButton(
                text = "Get a new code",
                onClick = ::reset,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                container = cc.accent,
                radius = 15.dp,
            )
        } else if (offline || failure != null) {
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                com.closecode.ui.components.PrimaryButton(
                    text = "Try again",
                    onClick = ::reset,
                    modifier = Modifier.height(48.dp),
                    container = cc.field,
                    radius = 15.dp,
                )
            }
        } else if (onCancel != null) {
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(cc.field)
                        .clickable(onClick = onCancel),
                    verticalAlignment = UiAlignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = cc.text,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (code != null && !expired) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Code expires in ${countdownText(remainingSec)}",
                    color = cc.muted,
                    fontSize = 11.5.sp,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun SignInAction(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) c.mock.accent.copy(alpha = 0.16f) else c.panel2)
            .border(1.dp, if (primary) c.mock.accent else c.mock.divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = UiAlignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) c.mock.accent else c.mock.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
