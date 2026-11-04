package com.closecode.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.build.ToolchainDownloadService
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainState
import com.closecode.ai.OpenCodeBinary
import com.closecode.ai.OpenCodeRuntime
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType
import com.closecode.ui.theme.tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One row of screen 06. Every field comes from a real download. */
private sealed interface StepState {
    data object Ready : StepState
    data object Waiting : StepState
    data class Downloading(
        val percent: Float,
        val speedText: String,
        val etaText: String,
        val sizeText: String,
        val paused: Boolean = false,
    ) : StepState

    data class Failed(val message: String) : StepState
    data object Skipped : StepState
}

private data class SetupStepUi(
    val id: String,
    val icon: String,
    val tint: Color,
    val title: String,
    val subtitle: String,
    val required: Boolean,
    val state: StepState,
    val primaryLabel: String? = null,
    val onPrimary: (() -> Unit)? = null,
    val onSkip: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onMobileData: (() -> Unit)? = null,
)

/**
 * Screen 06 — First-time setup.
 *
 * The one-time downloads the app really performs: build tools (required), shell tools and
 * the OpenCode runtime (both optional). Finish is enabled once every required step is
 * Ready. The same screen is reachable later from Settings.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = CcTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val toolchainStore = remember(context) { ToolchainHolder.store(context) }
    val buildState by toolchainStore.state.collectAsState()
    val buildReady = buildState is ToolchainState.Ready || toolchainStore.isReady()

    val shellStore = remember(context) {
        ShellToolsStore(context, ShellToolsManifest.load(context))
    }
    var shellReady by remember { mutableStateOf(shellStore.isReady()) }
    var shellBusy by remember { mutableStateOf(false) }
    var shellProgress by remember { mutableStateOf<ShellToolsStore.Progress?>(null) }
    var shellError by remember { mutableStateOf<String?>(null) }
    var shellSkipped by remember { mutableStateOf(false) }

    val abis = remember { Build.SUPPORTED_ABIS.toList() }
    val arch = remember(abis) { OpenCodeRuntime.alpineArch(abis) }
    var aiReady by remember {
        mutableStateOf(
            arch != null &&
                OpenCodeBinary.isInstalled(context) &&
                OpenCodeRuntime.isInstalled(context, arch),
        )
    }
    var aiBusy by remember { mutableStateOf(false) }
    var aiProgress by remember { mutableStateOf(0f) }
    var aiStage by remember { mutableStateOf("") }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiSkipped by remember { mutableStateOf(false) }

    var buildSkipped by remember { mutableStateOf(false) }

    val buildDownloading = buildState as? ToolchainState.Downloading
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun startBuildTools() {
        if (Build.VERSION.SDK_INT >= 33 &&
            !ToolchainDownloadService.notificationsAllowed(context)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        buildSkipped = false
        ToolchainDownloadService.start(context)
    }

    fun installShellTools() {
        if (shellBusy) return
        shellSkipped = false
        shellError = null
        shellBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                shellStore.install { progress ->
                    scope.launch { shellProgress = progress }
                }
            }
            shellBusy = false
            shellProgress = null
            shellReady = result.ok
            shellError = result.error
        }
    }

    fun installAiRuntime() {
        if (aiBusy) return
        val cpu = arch
        if (cpu == null) {
            aiError = "This phone's CPU is not one OpenCode runs on."
            return
        }
        aiSkipped = false
        aiError = null
        aiBusy = true
        aiProgress = 0f
        aiStage = "Starting the download…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    OpenCodeBinary.ensure(context, abis) { percent ->
                        scope.launch {
                            aiProgress = percent / 200f
                            aiStage = "OpenCode program · $percent%"
                        }
                    }
                    OpenCodeRuntime.ensure(context, cpu) { percent ->
                        scope.launch {
                            aiProgress = 0.5f + percent / 200f
                            aiStage = "Linux runtime · $percent%"
                        }
                    }
                }
            }
            aiBusy = false
            aiReady = result.isSuccess
            aiError = result.exceptionOrNull()?.message
            if (result.isSuccess) aiStage = "Ready"
        }
    }

    val buildSpeed = rememberSpeed(
        doneBytes = buildDownloading?.doneBytes ?: 0L,
        totalBytes = buildDownloading?.totalBytes ?: 0L,
        active = buildDownloading != null,
    )

    val steps = listOf(
        SetupStepUi(
            id = "build",
            icon = "check",
            tint = if (buildReady) c.green else c.amber,
            title = "Build tools",
            subtitle = "Kotlin, Java, dex and the signer. After this, builds work offline.",
            required = true,
            state = when (val state = buildState) {
                is ToolchainState.Downloading -> StepState.Downloading(
                    percent = if (state.totalBytes > 0) {
                        state.doneBytes.toFloat() / state.totalBytes
                    } else {
                        0f
                    },
                    speedText = buildSpeed.speedText,
                    etaText = buildSpeed.etaText,
                    sizeText = "${formatMb(state.doneBytes)} of ${formatMb(state.totalBytes)}",
                )
                ToolchainState.WaitingForWifi -> StepState.Waiting
                is ToolchainState.Failed -> StepState.Failed(state.message)
                is ToolchainState.Verifying, ToolchainState.Checking -> StepState.Downloading(
                    percent = 0f,
                    speedText = "",
                    etaText = "",
                    sizeText = "Checking the files already on the phone…",
                )
                ToolchainState.Ready -> StepState.Ready
                ToolchainState.NotStarted -> if (buildReady) StepState.Ready else StepState.Skipped
            },
            primaryLabel = when {
                buildReady -> null
                buildState is ToolchainState.Downloading -> "Pause"
                buildState is ToolchainState.Verifying || buildState is ToolchainState.Checking -> null
                toolchainStore.needsUpdate() -> "Update build tools"
                else -> "Download build tools"
            },
            onPrimary = {
                if (buildState is ToolchainState.Downloading) {
                    ToolchainDownloadService.send(context, ToolchainDownloadService.ACTION_PAUSE)
                } else {
                    startBuildTools()
                }
            },
            onRetry = {
                if (buildState is ToolchainState.Failed) startBuildTools()
                else Unit
            },
            onMobileData = {
                if (buildState is ToolchainState.WaitingForWifi) {
                    toolchainStore.setMobileAllowed(true)
                }
            },
        ),
        SetupStepUi(
            id = "shell",
            icon = "term",
            tint = c.blue,
            title = "Shell tools",
            subtitle = "Git, Python, Node, curl, zip and jq in the terminal. Optional.",
            required = false,
            state = when {
                shellReady -> StepState.Ready
                shellSkipped -> StepState.Skipped
                shellBusy -> {
                    val p = shellProgress
                    StepState.Downloading(
                        percent = if (p != null && p.totalBytes > 0) {
                            p.doneBytes.toFloat() / p.totalBytes
                        } else {
                            0f
                        },
                        speedText = "",
                        etaText = "",
                        sizeText = p?.let { "${formatMb(it.doneBytes)} of ${formatMb(it.totalBytes)}" }
                            ?: "Reading the package index…",
                    )
                }
                shellError != null -> StepState.Failed(shellError.orEmpty())
                else -> StepState.Skipped
            },
            primaryLabel = when {
                shellReady || shellBusy || shellSkipped -> null
                shellError != null -> "Retry"
                else -> "Download shell tools"
            },
            onPrimary = { installShellTools() },
            onRetry = { installShellTools() },
            onSkip = if (shellReady) null else ({ shellSkipped = true }),
        ),
        SetupStepUi(
            id = "ai",
            icon = "bolt",
            tint = c.accent,
            title = "OpenCode AI",
            subtitle = "The OpenCode program and the Linux libraries it runs through. Optional.",
            required = false,
            state = when {
                aiReady -> StepState.Ready
                aiSkipped -> StepState.Skipped
                aiBusy -> StepState.Downloading(
                    percent = aiProgress,
                    speedText = "",
                    etaText = "",
                    sizeText = aiStage,
                )
                arch == null -> StepState.Failed("This phone's CPU is not one OpenCode runs on.")
                aiError != null -> StepState.Failed(aiError.orEmpty())
                else -> StepState.Skipped
            },
            primaryLabel = when {
                aiReady || aiBusy || aiSkipped || arch == null -> null
                aiError != null -> "Retry"
                else -> "Download AI runtime"
            },
            onPrimary = { installAiRuntime() },
            onRetry = { installAiRuntime() },
            onSkip = if (aiReady || arch == null) null else ({ aiSkipped = true }),
        ),
    )

    val canFinish = steps.filter { it.required }.all { it.state is StepState.Ready }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.rail)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            Column(Modifier.padding(start = 22.dp, top = 30.dp, end = 22.dp, bottom = 20.dp)) {
                Text(
                    "CLOSECODE",
                    style = CcType.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = c.accent,
                )
                Text(
                    "Set up your build environment",
                    modifier = Modifier.padding(top = 10.dp),
                    style = CcType.setupTitle.copy(lineHeight = 31.sp, letterSpacing = (-0.5).sp),
                    color = c.text,
                )
                Text(
                    "One-time downloads. You can skip any and add it later in Settings.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = CcType.body.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 20.sp,
                    ),
                    color = c.muted,
                )
            }

            steps.forEach { step -> SetupCard(step) }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 34.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (canFinish) c.accent else c.field)
                .clickable(enabled = canFinish, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Finish setup",
                style = CcType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = if (canFinish) Color.White else c.faint,
            )
        }
    }
}

@Composable
private fun SetupCard(step: SetupStepUi) {
    val c = CcTheme.colors
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.panel)
            .border(1.dp, c.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(step.tint.tint()),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(step.icon, step.tint, 22.dp, 1.8f)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    step.title,
                    style = CcType.itemTitle.copy(fontSize = 15.5.sp, fontWeight = FontWeight.Bold),
                    color = c.text,
                )
                Text(
                    step.subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = CcType.caption.copy(fontSize = 12.5.sp),
                    color = c.muted,
                )
            }
            Spacer(Modifier.width(8.dp))
            when (val st = step.state) {
                StepState.Ready -> Text(
                    "Ready",
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.diffAddBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = CcType.caption.copy(fontWeight = FontWeight.Bold),
                    color = c.green,
                )
                is StepState.Failed -> Text(
                    "Failed",
                    style = CcType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.red,
                )
                StepState.Skipped -> Text(
                    "Skipped",
                    style = CcType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.muted,
                )
                else -> Text(
                    if (step.required) "Required" else "Optional",
                    style = CcType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.muted,
                )
            }
        }

        (step.state as? StepState.Failed)?.let {
            Text(
                it.message,
                modifier = Modifier.padding(top = 8.dp),
                style = CcType.caption,
                color = c.red,
            )
        }

        (step.state as? StepState.Downloading)?.let { d ->
            val p by animateFloatAsState(
                d.percent.coerceIn(0f, 1f),
                tween(200, easing = LinearEasing),
                label = "dl",
            )
            Row(Modifier.padding(top = 14.dp)) {
                Text(
                    if (d.paused) {
                        "Paused ${(d.percent * 100).toInt()}%"
                    } else {
                        "Downloading ${(d.percent * 100).toInt()}%"
                    },
                    modifier = Modifier.weight(1f),
                    style = CcType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = c.accent,
                )
                if (!d.paused && d.speedText.isNotBlank()) {
                    Text(
                        "${d.speedText} · ${d.etaText}",
                        style = CcType.caption.copy(fontSize = 12.5.sp),
                        color = c.muted,
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = 9.dp)
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.rail),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(p)
                        .background(c.accent),
                )
            }
            Text(
                d.sizeText,
                modifier = Modifier.padding(top = 8.dp),
                style = CcType.caption,
                color = c.muted,
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (step.onPrimary != null) {
                    SetupButton(step.primaryLabel ?: "Pause", Modifier.weight(1f), step.onPrimary)
                }
                if (step.onSkip != null) {
                    SetupButton("Skip for now", Modifier.weight(1f), step.onSkip)
                }
            }
        }

        if (step.state is StepState.Waiting) {
            Text(
                "The download is waiting for Wi-Fi.",
                modifier = Modifier.padding(top = 8.dp),
                style = CcType.caption,
                color = c.muted,
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                step.onMobileData?.let {
                    SetupButton("Use mobile data", Modifier.weight(1f), it)
                }
                step.onSkip?.let {
                    SetupButton("Skip for now", Modifier.weight(1f), it)
                }
            }
        }

        if (step.state is StepState.Failed) {
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                (step.onRetry ?: step.onPrimary)?.let {
                    SetupButton("Retry", Modifier.weight(1f), it)
                }
                step.onMobileData?.let {
                    SetupButton("Use mobile data", Modifier.weight(1f), it)
                }
                step.onSkip?.let {
                    SetupButton("Skip for now", Modifier.weight(1f), it)
                }
            }
        }

        // Idle with nothing running and not ready: the one action that starts it.
        val idle = step.state !is StepState.Downloading &&
            step.state !is StepState.Failed &&
            step.state !is StepState.Ready &&
            step.state !is StepState.Waiting
        if (idle && (step.onPrimary != null || step.onSkip != null)) {
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                step.onPrimary?.let {
                    SetupButton(step.primaryLabel ?: "Download", Modifier.weight(1f), it)
                }
                step.onSkip?.let {
                    SetupButton("Skip for now", Modifier.weight(1f), it)
                }
            }
        }
    }
}

@Composable
private fun SetupButton(text: String, modifier: Modifier, onClick: () -> Unit) = Box(
    modifier
        .height(38.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(CcTheme.colors.field)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Text(
        text,
        style = CcType.small.copy(fontWeight = FontWeight.SemiBold),
        color = CcTheme.colors.text,
    )
}

private data class Speed(val speedText: String, val etaText: String)

/**
 * Bytes per second from successive progress readings, plus the time left. Both are only
 * shown while a real download is moving.
 */
@Composable
private fun rememberSpeed(doneBytes: Long, totalBytes: Long, active: Boolean): Speed {
    var speed by remember { mutableStateOf(0L) }
    var lastBytes by remember { mutableStateOf(doneBytes) }
    var lastAt by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(doneBytes, active) {
        if (!active) {
            speed = 0
            lastBytes = doneBytes
            lastAt = System.currentTimeMillis()
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val dt = now - lastAt
        if (dt >= 700) {
            val delta = doneBytes - lastBytes
            if (delta >= 0) {
                val instant = delta * 1000 / dt
                speed = if (speed == 0L) instant else (speed * 2 + instant) / 3
            }
            lastBytes = doneBytes
            lastAt = now
        }
    }

    if (speed <= 0) return Speed("", "")
    val speedText = "${formatMb(speed)}/s"
    val eta = (totalBytes - doneBytes).coerceAtLeast(0) / speed
    val etaText = if (eta > 0) "${formatSeconds(eta)} left" else ""
    return Speed(speedText, etaText)
}

private fun formatMb(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatSeconds(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds}s"
}
