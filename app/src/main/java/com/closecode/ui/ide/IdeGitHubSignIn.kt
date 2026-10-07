package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.github.DeviceCode
import com.closecode.ui.github.DevicePoll
import com.closecode.ui.github.GitHubClient
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
    // JGit wraps transport failures several deep — a push failure is "Exception caught during
    // execution of push command" around a TransportException around the HTTP status that
    // actually says why. One level of cause keeps the sentence useless, so walk a few.
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
 * The GitHub Device Flow sign-in.
 *
 * GitHub hands out a short code, the user types it at github.com/login/device on whatever
 * device is near them, and this screen polls until they approve. No browser callback, no
 * client secret, and no token ever leaves this process except to GitHub itself.
 */
@Composable
fun GitHubSignIn(
    onSignedIn: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    val client = remember { GitHubClient() }

    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var note by remember { mutableStateOf("Asking GitHub for a code…") }
    var failure by remember { mutableStateOf<String?>(null) }
    // Bumped by "Try again": re-keying the effect is what restarts the whole request.
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        // LaunchedEffect runs on Dispatchers.Main. HttpURLConnection blocks, and Android answers
        // that with NetworkOnMainThreadException — whose message is null, which is why the panel
        // used to show a bare generic sentence instead of the real reason.
        val requested = withContext(Dispatchers.IO) { client.requestDeviceCode() }
        val start = requested.getOrElse { error ->
            failure = describeFailure(error, "GitHub could not start the sign-in.")
            return@LaunchedEffect
        }
        code = start
        var interval = start.intervalSec.coerceAtLeast(1)
        while (failure == null) {
            delay(interval * 1_000L)
            val polled = withContext(Dispatchers.IO) { client.pollForToken(start.deviceCode) }
            val step = polled.getOrElse { error ->
                failure = describeFailure(error, "GitHub could not finish the sign-in.")
                return@LaunchedEffect
            }
            when (step) {
                is DevicePoll.Authorized -> {
                    onSignedIn(step.accessToken)
                    return@LaunchedEffect
                }
                DevicePoll.Pending -> note = "Waiting for you to approve on GitHub…"
                DevicePoll.SlowDown -> {
                    interval += 5
                    note = "GitHub asked us to slow down. Still waiting…"
                }
                DevicePoll.Expired -> {
                    failure = "The code expired before you approved it."
                    return@LaunchedEffect
                }
                is DevicePoll.Refused -> {
                    failure = step.reason
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(16.dp))
                // Enabled no-op so the tap lands here instead of falling through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sign in to GitHub",
                color = c.tx,
                fontSize = 16.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Open github.com/login/device on any device and enter this code.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.bg)
                    .border(1.dp, c.ln, RoundedCornerShape(12.dp))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code?.userCode ?: "····-····",
                    color = if (failure == null) c.ac else c.mu,
                    fontSize = 26.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
            }

            Text(
                text = failure ?: note,
                color = if (failure == null) c.mu else c.red,
                fontSize = 12.sp,
                fontFamily = Sans,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SignInAction(
                    label = "Cancel",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                if (failure != null) {
                    SignInAction(
                        label = "Try again",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            failure = null
                            note = "Asking GitHub for a code…"
                            code = null
                            attempt++
                        },
                    )
                }
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun SignInAction(
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
            .background(if (primary) c.ac.copy(alpha = 0.16f) else c.panel2)
            .border(1.dp, if (primary) c.ac else c.ln, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) c.ac else c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
