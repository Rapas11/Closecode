package com.closecode.ui.ide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.closecode.ai.OpenCodeDefaults
import com.closecode.ai.OpenCodeLogLine
import com.closecode.ai.OpenCodePhase
import com.closecode.ai.OpenCodeService
import com.closecode.ai.OpenCodeStatus
import com.closecode.ai.sameProjectPath
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import java.io.File

/**
 * The OpenCode tool: OpenCode, running on this phone.
 *
 * The panel is a launcher, not a second chat implementation. OpenCode ships its own complete web
 * interface and this app starts the server and hands the screen to it. The panel exists only for
 * the states the web page cannot explain: before the first start, while the server is coming up,
 * when the server belongs to another project, when it stopped, and when it is running in the
 * background.
 *
 * The layout follows screens 46 to 49 of the light handoff. The real runtime is unchanged: the
 * Start button starts the same server, Cancel stops it, Hide leaves the panel with the server
 * still running, and Chat opens OpenCode's own page.
 */
@Composable
fun OpenCodeTab(
    projectName: String,
    projectId: String,
    projectRoot: File?,
    onChat: (String) -> Unit,
    onAuthenticate: () -> Unit,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val running = OpenCode.state.value
    val ownStatus = when {
        running.projectDirectory.isBlank() -> running
        projectRoot != null &&
            sameProjectPath(running.projectDirectory, projectRoot.absolutePath) -> running
        else -> null
    }
    val otherStatus = projectRoot?.let { OpenCode.runningForOtherProject(it) }
    val status = ownStatus ?: OpenCodeStatus()
    val prefs = remember {
        context.getSharedPreferences(OPEN_CODE_PREFS, Context.MODE_PRIVATE)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var batteryDialog by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }
    var switchTo by remember { mutableStateOf<OpenCodeStatus?>(null) }

    if (logOpen) {
        OpenCodeLogPage(
            onBack = { logOpen = false },
            liveLines = status.log.map { it.text },
        )
        return
    }

    fun reallyStart() {
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
        OpenCode.start(context, root, projectName, projectId)
        // The battery question is a one-time nudge, not a dialog on every start. It is kept
        // in the app's preferences so leaving the tab and coming back cannot bring it back.
        if (!prefs.getBoolean(KEY_BATTERY_ASKED, false)) {
            prefs.edit().putBoolean(KEY_BATTERY_ASKED, true).apply()
            batteryDialog = true
        }
    }

    fun startServer() {
        val root = projectRoot ?: run {
            OpenCode.reportFailure("This project has no folder on the phone yet.")
            return
        }
        val other = OpenCode.runningForOtherProject(root)
        if (other != null) {
            switchTo = other
            return
        }
        reallyStart()
    }

    fun stopServer() {
        OpenCodeService.stop(context)
        OpenCode.stop()
        com.closecode.device.ScreenshotStore.clear(context)
    }

    fun stopOtherAndStart() {
        stopServer()
        reallyStart()
    }

    fun hidePanel() {
        if (onHide != null) onHide() else backDispatcher?.onBackPressed()
    }

    // Open the page once for every server that comes up for this project. The generation is
    // kept on the OpenCode object, not in this composable: the AI tab leaves composition while
    // the page covers the screen, and a fresh remember would pop the page open again on the
    // way back. A server for another project must never pull this tab into its page.
    LaunchedEffect(status.phase, status.generation, ownStatus != null) {
        if (ownStatus != null &&
            status.phase == OpenCodePhase.Running &&
            status.generation != OpenCode.lastAutoOpenedGeneration
        ) {
            OpenCode.lastAutoOpenedGeneration = status.generation
            onChat(status.pageUrl.ifBlank { status.url })
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.mock.panel),
    ) {
        PanelHeader(projectName)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when {
                otherStatus != null -> OtherProjectCard(
                    other = otherStatus,
                    projectName = projectName,
                    onStop = ::stopServer,
                    onStopAndStart = ::stopOtherAndStart,
                )

                else -> when (status.phase) {
                    OpenCodePhase.Idle -> LauncherCard(
                        projectName = projectName,
                        onStart = ::startServer,
                    )

                    OpenCodePhase.Starting -> StartingScreen(
                        status = status,
                        onCancel = ::stopServer,
                        onFullscreen = { logOpen = true },
                    )

                    OpenCodePhase.Running -> RunningCard(
                        status = status,
                        projectName = projectName,
                        onChat = { onChat(status.pageUrl.ifBlank { status.url }) },
                        onStop = ::stopServer,
                        onHide = ::hidePanel,
                    )

                    OpenCodePhase.ConnectionLost -> ReconnectingScreen(
                        status = status,
                        onFullscreen = { logOpen = true },
                    )

                    OpenCodePhase.Failed -> FailedCard(
                        status = status,
                        onRetry = ::startServer,
                        onFullscreen = { logOpen = true },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    switchTo?.let { other ->
        SwitchProjectDialog(
            other = other,
            onSwitch = {
                switchTo = null
                stopOtherAndStart()
            },
            onDismiss = { switchTo = null },
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

/** Screen 46's panel title: OpenCode, and the project it will run for. */
@Composable
private fun PanelHeader(projectName: String) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Text(
            text = "OpenCode",
            color = c.mock.text,
            fontSize = 21.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = projectName,
            color = c.mock.muted,
            fontSize = 12.sp,
            fontFamily = Sans,
            maxLines = 1,
        )
    }
}

/** Screen 46: the card before anything has been started. */
@Composable
private fun LauncherCard(
    projectName: String,
    onStart: () -> Unit,
) {
    val c = LocalPageColors.current
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tile(
                background = c.mock.purpleBg,
                tint = c.mock.purple,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = "OpenCode",
                    color = c.mock.text,
                    fontSize = 14.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = projectName,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Starts OpenCode on this phone for the open project.",
            color = c.mock.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryAction(
            label = "Start",
            icon = CcIcons.Play,
            onClick = onStart,
        )
    }
}

/** Screen 47: the four steps while the server comes up, then the build log. */
@Composable
private fun StartingScreen(
    status: OpenCodeStatus,
    onCancel: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = LocalPageColors.current
    val steps = startupSteps(status)
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Starting OpenCode",
                color = c.mock.text,
                fontSize = 14.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        steps.forEach { step ->
            StartupStepRow(step)
        }
        if (status.downloading) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Downloading ${status.downloadPercent}%",
                color = c.mock.muted,
                fontSize = 11.5.sp,
                fontFamily = Sans,
            )
            Spacer(Modifier.height(6.dp))
            DownloadBar(status.downloadPercent)
        }
    }
    Spacer(Modifier.height(12.dp))
    BuildInfoBox(
        lines = status.log,
        onFullscreen = onFullscreen,
    )
    Spacer(Modifier.height(12.dp))
    SecondaryAction(label = "Cancel", onClick = onCancel)
}

/** Screen 49: connection lost, with the automatic restart countdown and no button to press. */
@Composable
private fun ReconnectingScreen(
    status: OpenCodeStatus,
    onFullscreen: () -> Unit,
) {
    val c = LocalPageColors.current
    val steps = startupSteps(status)
    OpenCard {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.mock.dangerBg)
                .padding(12.dp),
        ) {
            Text(
                text = if (status.retryInSeconds > 0) {
                    "Connection lost. Starting again in ${status.retryInSeconds} s"
                } else {
                    "Connection lost. Starting again…"
                },
                color = c.mock.danger,
                fontSize = 12.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        steps.forEach { step ->
            StartupStepRow(step)
        }
    }
    Spacer(Modifier.height(12.dp))
    BuildInfoBox(
        lines = status.log,
        onFullscreen = onFullscreen,
    )
}

/** Screen 48: the server is up, with the address and the three things to do with it. */
@Composable
private fun RunningCard(
    status: OpenCodeStatus,
    projectName: String,
    onChat: () -> Unit,
    onStop: () -> Unit,
    onHide: () -> Unit,
) {
    val c = LocalPageColors.current
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c.mock.green)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Running",
                color = c.mock.text,
                fontSize = 14.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        StatusRow(
            label = "Version",
            value = status.version.ifBlank { OpenCodeDefaults.VERSION },
        )
        StatusRow(
            label = "URL",
            value = status.url.removePrefix("http://").removePrefix("https://"),
            mono = true,
        )
        StatusRow(
            label = "Project",
            value = status.projectName.ifBlank { projectName },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1.35f)) {
                PrimaryAction(
                    label = "Chat",
                    icon = CcIcons.Sparkle,
                    onClick = onChat,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                SecondaryAction(
                    label = "Stop",
                    onClick = onStop,
                    danger = true,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                SecondaryAction(
                    label = "Hide",
                    onClick = onHide,
                )
            }
        }
    }
}

/** Failed: the exact reason and one button that tries again. */
@Composable
private fun FailedCard(
    status: OpenCodeStatus,
    onRetry: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = LocalPageColors.current
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c.mock.danger)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "OpenCode could not start",
                color = c.mock.text,
                fontSize = 14.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = status.error ?: "The server did not start.",
            color = c.mock.danger,
            fontSize = 12.sp,
            fontFamily = Mono,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryAction(label = "Try again", onClick = onRetry)
    }
    if (status.log.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        BuildInfoBox(
            lines = status.log,
            onFullscreen = onFullscreen,
        )
    }
}

@Composable
private fun OtherProjectCard(
    other: OpenCodeStatus,
    projectName: String,
    onStop: () -> Unit,
    onStopAndStart: () -> Unit,
) {
    val c = LocalPageColors.current
    val runningName = other.projectName.ifBlank { other.projectDirectory }
    val verb = if (other.phase == OpenCodePhase.Starting) "starting" else "running"
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (other.phase == OpenCodePhase.Starting) c.mock.warning else c.mock.green)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "OpenCode is $verb on $runningName",
                    color = c.mock.text,
                    fontSize = 14.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = projectName,
                    color = c.mock.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Only one OpenCode server runs at a time. Stop $runningName before " +
                "starting $projectName.",
            color = c.mock.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        SecondaryAction(
            label = "Stop $runningName",
            onClick = onStop,
            danger = true,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryAction(
            label = "Stop and start $projectName",
            onClick = onStopAndStart,
        )
    }
}

/** One of the four start steps: green tick, blue spinner, or three waiting dots. */
@Composable
private fun StartupStepRow(step: StartupStep) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (step.state) {
                StartupState.Done -> CcIcon(CcIcons.Check, c.mock.green, 14.dp)
                StartupState.Now -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = c.mock.blue,
                    strokeWidth = 2.dp,
                )
                StartupState.Waiting -> WaitingDots()
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = step.label,
            color = when (step.state) {
                StartupState.Done -> c.mock.text2
                StartupState.Now -> c.mock.blue
                StartupState.Waiting -> c.mock.muted
            },
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = if (step.state == StartupState.Now) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
        )
    }
}

@Composable
private fun WaitingDots() {
    val c = LocalPageColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(2.5.dp)
                    .clip(CircleShape)
                    .background(c.mock.faint),
            )
        }
    }
}

/** The BUILD INFO box from screens 47 and 49. */
@Composable
private fun BuildInfoBox(
    lines: List<OpenCodeLogLine>,
    onFullscreen: () -> Unit,
) {
    val c = LocalPageColors.current
    val clipboard = LocalClipboardManager.current
    val shown = lines.takeLast(6)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.mock.card)
            .border(1.dp, c.mock.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "BUILD INFO",
                color = c.mock.muted,
                fontSize = 10.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.weight(1f))
            BoxLink(
                label = "Copy log",
                onClick = {
                    clipboard.setText(AnnotatedString(lines.joinToString("\n") { it.text }))
                },
            )
            Spacer(Modifier.width(10.dp))
            BoxLink(label = "Fullscreen", onClick = onFullscreen)
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Text(
                text = "Waiting for the first line…",
                color = c.mock.muted,
                fontSize = 10.5.sp,
                fontFamily = Mono,
                lineHeight = 15.sp,
            )
        } else {
            shown.forEach { line ->
                Text(
                    text = line.text,
                    color = if (line.warning) c.mock.danger else c.mock.text2,
                    fontSize = 10.5.sp,
                    fontFamily = Mono,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun BoxLink(label: String, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Text(
        text = label,
        color = c.mock.accent,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    )
}

@Composable
private fun DownloadBar(percent: Int) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(c.mock.field),
    ) {
        val fraction = percent.coerceIn(0, 100) / 100f
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(c.mock.accent),
            )
        }
    }
}

/** A 38 dp tinted square holding the sparkle. */
@Composable
private fun Tile(background: Color, tint: Color) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        CcIcon(CcIcons.Sparkle, tint, 20.dp)
    }
}

/** The project-switch question, in the app's own dialog shape. */
@Composable
private fun SwitchProjectDialog(
    other: OpenCodeStatus,
    onSwitch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.mock.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.mock.panel)
                .border(1.dp, c.mock.border, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .clickable(enabled = false) {},
        ) {
            Column {
                Text(
                    text = "Switch OpenCode project?",
                    color = c.mock.text,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Currently bound to another project: " +
                        (other.projectName.ifBlank { other.projectDirectory }) +
                        ". Switching stops that server and starts this project.",
                    color = c.mock.muted,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryAction(label = "Switch project", onClick = onSwitch)
                Spacer(Modifier.height(8.dp))
                SecondaryAction(label = "Cancel", onClick = onDismiss)
            }
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
            .background(c.mock.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.mock.panel)
                .border(1.dp, c.mock.border, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .clickable(enabled = false) {},
        ) {
            Column {
                Text(
                    text = "Keep OpenCode running",
                    color = c.mock.text,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Android stops background apps to save battery. Let CloseCode run " +
                        "without that, or the OpenCode server is stopped while you use another app.",
                    color = c.mock.muted,
                    fontSize = 12.5.sp,
                    fontFamily = Sans,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryAction(label = "Open settings", onClick = onOpen)
                Spacer(Modifier.height(8.dp))
                SecondaryAction(label = "Not now", onClick = onDismiss)
            }
        }
    }
}

/** A card: content colour, 18 dp corners, 14 dp of padding. */
@Composable
private fun OpenCard(content: @Composable () -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.mock.content)
            .padding(14.dp),
    ) {
        content()
    }
}

/** The primary button: blurple, 42 dp tall, optional icon. */
@Composable
private fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.mock.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                CcIcon(icon, c.mock.onAccent, 14.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = c.mock.onAccent,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** The other buttons: field colour, dark text, 40 dp tall. */
@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.mock.field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (danger) c.mock.danger else c.mock.text,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
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
private fun StatusRow(label: String, value: String, mono: Boolean = false) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = c.mock.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = c.mock.text2,
            fontSize = if (mono) 11.5.sp else 12.5.sp,
            fontFamily = if (mono) Mono else Sans,
            maxLines = 1,
        )
    }
}

private enum class StartupState { Done, Now, Waiting }

private data class StartupStep(val label: String, val state: StartupState)

/**
 * Maps the real start log and phase onto the four drawn steps.
 *
 * The server writes the exact facts into its log ("Project:", "Launch:", "Waiting for"), so the
 * steps follow the same truth the log shows instead of a separate timer that could disagree with
 * it. A lost connection starts from step three again, as screen 49 shows.
 */
private fun startupSteps(status: OpenCodeStatus): List<StartupStep> {
    val log = status.log.joinToString("\n") { it.text }.lowercase()
    val lost = status.phase == OpenCodePhase.ConnectionLost
    val running = status.phase == OpenCodePhase.Running
    val checked = lost || running || log.contains("is ready") ||
        log.contains("installed") || log.contains("project:")
    val connected = lost || running || log.contains("project:")
    val server = lost || running || log.contains("launch:") || log.contains("server:")
    val answered = running

    val done = mutableListOf<Boolean>()
    done += checked
    done += connected
    done += if (lost) false else server
    done += answered

    val firstOpen = done.indexOfFirst { !it }
    return listOf(
        "Checking files",
        "Connecting your project",
        "Starting the server",
        "Waiting for it to answer",
    ).mapIndexed { index, label ->
        val state = when {
            done[index] -> StartupState.Done
            index == firstOpen -> StartupState.Now
            else -> StartupState.Waiting
        }
        StartupStep(label, state)
    }
}

/** Where the AI tab keeps the one-time battery question. */
private const val OPEN_CODE_PREFS = "closecode_ai"
private const val KEY_BATTERY_ASKED = "battery_asked"
