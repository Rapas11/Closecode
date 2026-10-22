package com.closecode.ui.ide

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.closecode.ai.OpenCodeDefaults
import com.closecode.ai.OpenCodeLogLine
import com.closecode.ai.OpenCodePhase
import com.closecode.ai.OpenCodeService
import com.closecode.ai.OpenCodeStatus
import com.closecode.ai.OpenCodeStep
import com.closecode.ai.OpenCodeStepState
import com.closecode.ai.retryCountdownText
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File

/**
 * The AI tab's OpenCode screens.
 *
 * Four screens, as the spec draws them: the card with the Start button, the Starting screen with
 * its four steps and the Build Info box, the Running card, and the Connection lost screen. The
 * design values come from the spec — card #181D29 with 8 dp corners, the primary button 40 dp
 * tall, the small buttons 28 dp, the dot 8 dp — and the colours come from the app's own palette
 * so the screens sit in the app like everything else.
 */
@Composable
fun OpenCodePanel(
    status: OpenCodeStatus,
    projectName: String,
    projectFolder: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onChat: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var hidden by remember { mutableStateOf(false) }

    when {
        status.phase == OpenCodePhase.Running && !hidden -> RunningScreen(
            status = status,
            onChat = { onChat(status.url) },
            onStop = {
                hidden = true
                onStop()
            },
            onHide = { hidden = true },
            onBack = onBack,
            modifier = modifier,
        )

        status.phase == OpenCodePhase.ConnectionLost -> ConnectionLostScreen(
            status = status,
            onCancel = onStop,
            onBack = onBack,
            modifier = modifier,
        )

        status.phase == OpenCodePhase.Starting -> StartingScreen(
            status = status,
            onCancel = onStop,
            onBack = onBack,
            modifier = modifier,
        )

        else -> IdleScreen(
            status = status,
            projectName = projectName,
            projectFolder = projectFolder,
            onStart = onStart,
            modifier = modifier,
        )
    }
}

/**
 * The AI tab: the OpenCode card, and everything that has to happen before the server runs.
 *
 * Two things are asked for before the first start, because both are needed for the server to
 * survive on a phone: the notification permission (Android 13 and up ask for it), and the
 * battery optimisation exemption, so the phone does not stop the server while the user is away.
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
    var batteryAsked by remember { mutableStateOf(false) }
    var batteryDialog by remember { mutableStateOf(false) }

    fun startServer() {
        val root = projectRoot
        if (root == null || !root.isDirectory) {
            // Nothing to run on: the card says so rather than starting a server on no folder.
            OpenCode.reportFailure("This project has no folder on the phone yet.")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        OpenCodeService.start(context, "http://${OpenCodeDefaults.HOST}:${OpenCodeDefaults.PORT}", projectName)
        OpenCode.start(context, root, projectName)
    }

    OpenCodePanel(
        status = status,
        projectName = projectName,
        projectFolder = projectRoot?.name.orEmpty(),
        onStart = {
            startServer()
            if (!batteryAsked) {
                batteryAsked = true
                batteryDialog = true
            }
        },
        onStop = {
            OpenCodeService.stop(context)
            OpenCode.stop()
        },
        onChat = onChat,
        onBack = {},
        modifier = modifier,
    )

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
                    text = "Android stops background apps to save battery. Let Closecode run " +
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

/** The AI tab before anything has been started: the card and the project's folder. */
@Composable
private fun IdleScreen(
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
                StatusDot(
                    color = when (status.phase) {
                        OpenCodePhase.Running -> c.success
                        OpenCodePhase.Starting -> c.amber
                        OpenCodePhase.ConnectionLost -> c.amber
                        else -> c.mu
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryAction(label = "Start OpenCode", onClick = onStart)
            if (status.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = status.error,
                    color = c.red,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "PROJECT",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Folder",
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = projectFolder.ifBlank { projectName },
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
        }
    }
}

/** The Starting screen: four steps, the Build Info box, and a Cancel. */
@Composable
private fun StartingScreen(
    status: OpenCodeStatus,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        TopBar(title = "Starting OpenCode", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = c.amber)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Starting",
                        color = c.tx,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Usually a few seconds. Keep the app open.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
                Spacer(Modifier.height(12.dp))
                status.steps.forEach { step -> StepRow(step) }
                if (status.downloading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Downloading OpenCode · ${status.downloadPercent}%",
                        color = c.amber,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            BuildInfoBox(status.log)
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SecondaryAction(label = "Cancel", onClick = onCancel)
        }
    }
}

/** The Running screen: what is running, where, and the three buttons. */
@Composable
private fun RunningScreen(
    status: OpenCodeStatus,
    onChat: () -> Unit,
    onStop: () -> Unit,
    onHide: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        TopBar(title = "OpenCode", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = c.success)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Running",
                        color = c.tx,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                StatusRow("Version", status.version.ifBlank { "unknown" })
                StatusRow("URL", status.url)
                StatusRow("Project", status.projectName)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PrimaryAction(label = "Chat", onClick = onChat)
                }
                Box(Modifier.weight(1f)) {
                    SecondaryAction(label = "Stop", onClick = onStop, danger = true)
                }
                Box(Modifier.weight(1f)) {
                    SecondaryAction(label = "Hide", onClick = onHide)
                }
            }
            Spacer(Modifier.height(14.dp))
            BuildInfoBox(status.log)
        }
    }
}

/** The Connection lost screen: it is starting again by itself, and says when. */
@Composable
private fun ConnectionLostScreen(
    status: OpenCodeStatus,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        TopBar(title = "Connection lost", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = c.amber)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Connection lost",
                        color = c.tx,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Starting again automatically. ${retryCountdownText(status.retryInSeconds)}.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
            Spacer(Modifier.height(14.dp))
            BuildInfoBox(status.log)
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SecondaryAction(label = "Cancel", onClick = onCancel)
        }
    }
}

/** The Build Info box: the log, with Copy log and Fullscreen. */
@Composable
private fun BuildInfoBox(lines: List<OpenCodeLogLine>) {
    val c = LocalPageColors.current
    val clipboard = LocalClipboardManager.current
    var fullscreen by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "BUILD INFO",
                color = c.mu,
                fontSize = 11.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            SmallButton(label = "Copy log") {
                clipboard.setText(AnnotatedString(lines.joinToString("\n") { it.text }))
            }
            Spacer(Modifier.width(8.dp))
            SmallButton(label = "Fullscreen") { fullscreen = true }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(c.bg)
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Column {
                lines.forEach { line ->
                    Text(
                        text = line.text,
                        color = if (line.warning) c.amber else c.mu,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        lineHeight = 16.5.sp,
                    )
                }
            }
        }
    }

    if (fullscreen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .clickable { fullscreen = false }
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column {
                lines.forEach { line ->
                    Text(
                        text = line.text,
                        color = if (line.warning) c.amber else c.mu,
                        fontSize = 11.sp,
                        fontFamily = Mono,
                        lineHeight = 16.5.sp,
                    )
                }
                Spacer(Modifier.height(24.dp))
                SecondaryAction(label = "Close", onClick = { fullscreen = false })
            }
        }
    }
}

/** One step of the Starting screen: a dot, the name, and how it went. */
@Composable
private fun StepRow(step: OpenCodeStep) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = when (step.state) {
                OpenCodeStepState.Done -> c.success
                OpenCodeStepState.Working -> c.amber
                OpenCodeStepState.Failed -> c.red
                OpenCodeStepState.Pending -> c.mu
            },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = step.label,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = step.note,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
        )
    }
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
        Text(
            text = label,
            color = c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Mono,
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

/** A card: 8 dp corners, a 1 dp border, 12 dp of padding, and its contents stacked top down. */
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

/** The small buttons on the Build Info box: 28 dp tall, 11 sp. */
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

/** The compact top bar with a back arrow. */
@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(c.panel)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.panel2)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                color = c.mu,
                fontSize = 19.sp,
                fontFamily = Sans,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = c.tx,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
    }
}
