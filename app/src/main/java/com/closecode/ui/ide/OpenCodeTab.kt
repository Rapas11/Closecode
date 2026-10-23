package com.closecode.ui.ide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.closecode.ai.OpenCode
import com.closecode.ai.OpenCodeLogLine
import com.closecode.ai.OpenCodePageLog
import com.closecode.ai.OpenCodePhase
import com.closecode.ai.OpenCodeService
import com.closecode.ai.OpenCodeStatus
import com.closecode.ai.retryCountdownText
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File

/**
 * The AI tab: OpenCode, running on this phone.
 *
 * The tab is deliberately small. OpenCode ships its own complete web interface — sessions,
 * models, files, permissions — and the app's job is to start the documented server on loopback
 * and hand the screen to that interface. The server is unsecured on that loopback, as OpenCode
 * documents for local use. The tab itself only shows the few states around it: not started,
 * downloading/starting, running with the page closed, reconnecting, or failed with the exact
 * reason.
 *
 * As soon as a server answers its health check, the page opens by itself. The user asked for
 * OpenCode, not for a launcher to click through every time.
 */
@Composable
fun OpenCodeTab(
    projectName: String,
    projectRoot: File?,
    onChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status by OpenCode.state
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val prefs = remember {
        context.getSharedPreferences(OPEN_CODE_PREFS, Context.MODE_PRIVATE)
    }
    var batteryDialog by remember { mutableStateOf(false) }

    // Open the page once for every server that comes up. The generation is kept on the
    // OpenCode object, not in this composable: the AI tab leaves composition while the page
    // covers the screen, and a fresh remember would pop the page open again on the way back.
    // A reconnect is a new generation, so that one does open the fresh page.
    LaunchedEffect(status.phase, status.generation) {
        if (status.phase == OpenCodePhase.Running &&
            status.generation != OpenCode.lastAutoOpenedGeneration
        ) {
            OpenCode.lastAutoOpenedGeneration = status.generation
            onChat(status.url)
        }
    }

    fun startServer() {
        val root = projectRoot
        if (root == null || !root.isDirectory) {
            OpenCode.reportFailure("This project has no folder on the phone yet.")
            return
        }
        // targetSdk is 28 so OpenCode can execute from the app's own storage; with that target
        // Android 13's notification permission is not required, and asking for it would only
        // put another dialog in front of the user for no reason.
        if (Build.VERSION.SDK_INT >= 33 &&
            context.applicationInfo.targetSdkVersion >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        OpenCodeService.start(context, "", projectName)
        OpenCode.start(context, root, projectName)
        // The battery question is a one-time nudge, not a dialog on every start. It is kept
        // in the app's preferences so leaving the tab and coming back cannot bring it back.
        if (!prefs.getBoolean(KEY_BATTERY_ASKED, false)) {
            prefs.edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
            batteryDialog = true
        }
    }

    fun stopServer() {
        OpenCodeService.stop(context)
        OpenCode.stop()
    }

    when (status.phase) {
        OpenCodePhase.Running -> RunningTab(
            status = status,
            onOpen = { onChat(status.url) },
            onStop = ::stopServer,
            modifier = modifier,
        )

        OpenCodePhase.Starting -> StartingTab(
            status = status,
            onCancel = ::stopServer,
            modifier = modifier,
        )

        OpenCodePhase.ConnectionLost -> ReconnectingTab(
            status = status,
            onCancel = ::stopServer,
            modifier = modifier,
        )

        OpenCodePhase.Failed -> FailedTab(
            status = status,
            onRetry = ::startServer,
            modifier = modifier,
        )

        OpenCodePhase.Idle -> IdleTab(
            status = status,
            projectName = projectName,
            projectFolder = projectRoot?.name.orEmpty(),
            onStart = ::startServer,
            modifier = modifier,
        )
    }

    if (batteryDialog) {
        BatteryDialog(
            onDismiss = { batteryDialog = false },
            onOpen = {
                batteryDialog = false
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}

/** The tab before anything has been started. */
@Composable
private fun IdleTab(
    status: OpenCodeStatus,
    projectName: String,
    projectFolder: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.panel2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "</>", color = c.mu, fontSize = 12.sp, fontFamily = Mono)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = "OpenCode",
                        color = c.tx,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = projectName,
                        color = c.mu,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
                StatusDot(c.mu)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "OpenCode runs on this phone and opens its own chat and coding page. " +
                    "This project is its working folder.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryAction(label = "Open OpenCode", onClick = onStart)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The first start downloads OpenCode once. Later starts use the copy " +
                    "already on the phone.",
                color = c.mu,
                fontSize = 11.sp,
                fontFamily = Sans,
                lineHeight = 16.sp,
            )
            status.error?.let { message ->
                Spacer(Modifier.height(10.dp))
                ErrorText(message)
            }
        }

        Spacer(Modifier.height(18.dp))
        OpenCodeSection("PROJECT")
        Spacer(Modifier.height(6.dp))
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Folder", color = c.tx, fontSize = 13.sp, fontFamily = Sans)
                Spacer(Modifier.weight(1f))
                Text(
                    text = projectFolder.ifBlank { projectName },
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
        }

        if (status.log.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            LogSection(status.log)
        }
    }
}

/** Starting: download progress if there is one, and the server's own log. */
@Composable
private fun StartingTab(
    status: OpenCodeStatus,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.amber)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Starting OpenCode",
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (status.downloading) {
                    "Downloading · ${status.downloadPercent}%"
                } else {
                    "Getting the server ready. Keep the app open."
                },
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.panel2),
            ) {
                val fraction = if (status.downloading) {
                    status.downloadPercent.coerceIn(0, 100) / 100f
                } else {
                    0f
                }
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(c.primary),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        LogSection(status.log)

        Spacer(Modifier.height(14.dp))
        SecondaryAction(label = "Cancel", onClick = onCancel, danger = true)
    }
}

/** Failed: the exact reason, the log, and one button that tries again. */
@Composable
private fun FailedTab(
    status: OpenCodeStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.red)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "OpenCode could not start",
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            ErrorText(status.error ?: "The server did not start.")
            Spacer(Modifier.height(12.dp))
            PrimaryAction(label = "Try again", onClick = onRetry)
        }

        Spacer(Modifier.height(14.dp))
        LogSection(status.log)
    }
}

/** Reconnecting: the server went away and the app is bringing it back by itself. */
@Composable
private fun ReconnectingTab(
    status: OpenCodeStatus,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.amber)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Reconnecting",
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "OpenCode stopped answering. ${retryCountdownText(status.retryInSeconds)}.",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryAction(label = "Stop", onClick = onCancel, danger = true)
        }

        Spacer(Modifier.height(14.dp))
        LogSection(status.log)
    }
}

/** Running, with the page closed: the address, the version, and the way back in. */
@Composable
private fun RunningTab(
    status: OpenCodeStatus,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.success)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "OpenCode is running",
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            StatusRow("Version", status.version.ifBlank { "unknown" })
            StatusRow("WebView", webViewVersion())
            StatusRow("Address", status.url)
            StatusRow("Project", status.projectName)
            Spacer(Modifier.height(12.dp))
            PrimaryAction(label = "Open OpenCode", onClick = onOpen)
            Spacer(Modifier.height(8.dp))
            SecondaryAction(label = "Stop server", onClick = onStop, danger = true)
        }

        Spacer(Modifier.height(14.dp))
        LogSection(status.log)

        val pageLines = OpenCodePageLog.lines
        if (pageLines.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            PageLogSection(pageLines)
        }
    }
}

/**
 * The battery question.
 *
 * A phone that is not told otherwise puts the OpenCode server to sleep when the screen is off,
 * and the connection is lost for no reason the user can see. The explanation is one sentence
 * and one button; the settings screen does the rest.
 */
@Composable
private fun BatteryDialog(onDismiss: () -> Unit, onOpen: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .clickable(enabled = false) {},
        ) {
            Column {
                Text(
                    text = "Keep OpenCode running",
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Android stops background apps to save battery. Let CloseCode run " +
                        "without that, or the OpenCode server is stopped while you use another app.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryAction(label = "Open settings", onClick = onOpen)
                Spacer(Modifier.height(8.dp))
                SecondaryAction(label = "Not now", onClick = onDismiss)
            }
        }
    }
}

/** The log box: the newest lines, and a button that copies the whole thing. */
@Composable
private fun LogSection(lines: List<OpenCodeLogLine>) {
    if (lines.isEmpty()) return
    val c = LocalPageColors.current
    val clipboard = LocalClipboardManager.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OpenCodeSection("LOG")
            Spacer(Modifier.weight(1f))
            SmallButton(label = "Copy log") {
                clipboard.setText(AnnotatedString(lines.joinToString("\n") { it.text }))
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(c.bg)
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Column {
                lines.takeLast(14).forEach { line ->
                    Text(
                        text = line.text,
                        color = if (line.warning) c.amber else c.mu,
                        fontSize = 10.5.sp,
                        fontFamily = Mono,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

/**
 * The page's own log: what OpenCode's JavaScript printed and which requests it made. The
 * "Copy page log" button is the way to get a real failure report off the phone.
 */
@Composable
private fun PageLogSection(lines: List<String>) {
    if (lines.isEmpty()) return
    val c = LocalPageColors.current
    val clipboard = LocalClipboardManager.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OpenCodeSection("PAGE LOG")
            Spacer(Modifier.weight(1f))
            SmallButton(label = "Copy page log") {
                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(c.bg)
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Column {
                lines.takeLast(14).forEach { line ->
                    Text(
                        text = line,
                        color = if (pageLogWarning(line)) c.amber else c.mu,
                        fontSize = 10.5.sp,
                        fontFamily = Mono,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

/** A page line that names a failure, so the log draws it in the warning colour. */
private fun pageLogWarning(line: String): Boolean =
    line.contains("FAILED") ||
        line.contains("error", ignoreCase = true) ||
        Regex("\\b[45]\\d{2}\\b").containsMatchIn(line)

/** The WebView version, which is the browser OpenCode's page actually runs in. */
private fun webViewVersion(): String =
    runCatching { WebView.getCurrentWebViewPackage()?.versionName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"

/** The exact failure text, in the colour that says it is one. */
@Composable
private fun ErrorText(text: String) {
    val c = LocalPageColors.current
    Text(
        text = text,
        color = c.red,
        fontSize = 12.sp,
        fontFamily = Mono,
        lineHeight = 17.sp,
    )
}

/** A card: 8 dp corners, a 1 dp border, 12 dp of padding. */
@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.panel)
            .border(1.dp, c.ln, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        content()
    }
}

/** The primary button: 40 dp tall, 8 dp corners, white text. */
@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The other buttons: the panel colour, a border, and the text colour. */
@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (danger) c.red else c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The small button beside a section label. */
@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.panel2)
            .border(1.dp, c.ln, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.tx,
            fontSize = 11.sp,
            fontFamily = Sans,
        )
    }
}

/** The 8 dp dot that says what is happening. */
@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** One line of the status card: a label on the left, the value on the right. */
@Composable
private fun StatusRow(label: String, value: String) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = c.mu, fontSize = 13.sp, fontFamily = Sans)
        Spacer(Modifier.weight(1f))
        Text(text = value, color = c.tx, fontSize = 13.sp, fontFamily = Mono)
    }
}

/** A section heading: "PROJECT", "LOG". */
@Composable
private fun OpenCodeSection(text: String) {
    val c = LocalPageColors.current
    Text(
        text = text,
        color = c.mu,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Where the AI tab keeps the one-time battery question. */
private const val OPEN_CODE_PREFS = "closecode_ai"
private const val KEY_BATTERY_ASKED = "battery_asked"
