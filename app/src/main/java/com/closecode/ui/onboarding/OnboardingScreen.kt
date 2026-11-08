package com.closecode.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.closecode.ai.OpenCodeBinary
import com.closecode.ai.OpenCodeRuntime
import com.closecode.build.ToolchainDownloadService
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainState
import com.closecode.tools.ShellToolsManifest
import com.closecode.tools.ShellToolsStore
import com.closecode.ui.background.PageBackground
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.ScreenCard
import com.closecode.ui.components.TextLink
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First launch: three optional, one-time downloads. Every step can be skipped, and the setup
 * can be left at any point; the same downloads remain available in Settings > Setup guide.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var focusStep by rememberSaveable { mutableStateOf<Int?>(null) }

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
    val buildBusy = buildState is ToolchainState.Downloading ||
        buildState is ToolchainState.Verifying ||
        buildState is ToolchainState.Checking
    val setupBusy = buildBusy || shellBusy || aiBusy

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

    Box(Modifier.fillMaxSize()) {
        PageBackground()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (showWelcome) {
                WelcomePanel(
                    onStart = { showWelcome = false },
                    onSkip = onDone,
                )
            } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.brightText),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "C",
                            color = c.mock.page,
                            fontSize = 15.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.size(9.dp))
                    Text(
                        text = "Closecode",
                        color = c.brightText,
                        fontSize = 18.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextLink(label = "Skip all", onClick = onDone, color = c.textMuted)
            }

            Spacer(Modifier.height(30.dp))
            Text(
                text = "Set up your build environment",
                color = c.brightText,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "One-time downloads. You can skip any and add it later in Settings.",
                color = c.textMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontFamily = Sans,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            Spacer(Modifier.height(26.dp))

            SetupStep(
                number = 1,
                title = "Build tools",
                description = "Kotlin, Java, dex and the signer. After this, builds work offline.",
                ready = buildReady,
                skipped = buildSkipped,
                busy = buildBusy,
                onFocus = { focusStep = 1 },
                status = when (val state = buildState) {
                    is ToolchainState.Downloading -> {
                        val percent = if (state.totalBytes > 0) {
                            (state.doneBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        "Downloading · $percent%"
                    }
                    ToolchainState.WaitingForWifi -> "Waiting for Wi-Fi"
                    is ToolchainState.Failed -> "Needs attention"
                    is ToolchainState.Verifying, ToolchainState.Checking -> "Checking files"
                    ToolchainState.Ready -> "Ready"
                    ToolchainState.NotStarted -> if (buildReady) "Ready" else "Not installed"
                },
                onSkip = { buildSkipped = true },
            ) {
                when (val state = buildState) {
                    is ToolchainState.Downloading -> {
                        Text(
                            text = "Downloading ${state.index} of ${state.count} · " +
                                "${state.doneBytes / 1_048_576} MB of " +
                                "${state.totalBytes / 1_048_576} MB",
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(
                            value = if (state.totalBytes > 0) {
                                state.doneBytes.toFloat() / state.totalBytes
                            } else {
                                0f
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlineButton(
                                label = "Pause",
                                onClick = {
                                    ToolchainDownloadService.send(
                                        context,
                                        ToolchainDownloadService.ACTION_PAUSE,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                            OutlineButton(
                                label = "Cancel",
                                onClick = {
                                    ToolchainDownloadService.send(
                                        context,
                                        ToolchainDownloadService.ACTION_CANCEL,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    ToolchainState.WaitingForWifi -> {
                        Text(
                            text = "The download is waiting for Wi-Fi.",
                            color = c.text,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(
                            label = "Use mobile data",
                            onClick = { toolchainStore.setMobileAllowed(true) },
                        )
                    }
                    is ToolchainState.Failed -> {
                        Text(
                            text = state.message,
                            color = c.error,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(label = "Try again", onClick = { startBuildTools() })
                    }
                    is ToolchainState.Verifying, ToolchainState.Checking -> {
                        Text(
                            text = "Checking the files already on the phone…",
                            color = c.textMuted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                    else -> {
                        if (buildReady) {
                            Text(
                                text = "Build tools are ready.",
                                color = c.mock.green,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                            )
                        } else {
                            PrimaryButton(
                                label = if (toolchainStore.needsUpdate()) {
                                    "Update build tools"
                                } else {
                                    "Download build tools"
                                },
                                onClick = { startBuildTools() },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SetupStep(
                number = 2,
                title = "Shell tools",
                description = "Git, Python, Node, curl, zip and jq",
                ready = shellReady,
                skipped = shellSkipped,
                busy = shellBusy,
                onFocus = { focusStep = 2 },
                status = when {
                    shellReady -> "Ready"
                    shellBusy -> "Installing"
                    shellError != null -> "Needs attention"
                    else -> "Optional"
                },
                onSkip = { shellSkipped = true },
            ) {
                val progress = shellProgress
                if (shellBusy) {
                    Text(
                        text = progress?.message ?: "Reading the package index…",
                        color = c.text,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                    if (progress != null && progress.totalBytes > 0) {
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(
                            value = progress.doneBytes.toFloat() / progress.totalBytes,
                        )
                    }
                } else if (shellReady) {
                    Text(
                        text = "Shell tools are ready.",
                        color = c.mock.green,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                } else {
                    shellError?.let {
                        Text(
                            text = it,
                            color = c.error,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryButton(label = "Download shell tools", onClick = { installShellTools() })
                }
            }

            Spacer(Modifier.height(14.dp))

            SetupStep(
                number = 3,
                title = "OpenCode AI",
                description = "Runtime and Linux libraries",
                ready = aiReady,
                skipped = aiSkipped,
                busy = aiBusy,
                onFocus = { focusStep = 3 },
                status = when {
                    aiReady -> "Ready"
                    aiBusy -> "Downloading"
                    aiError != null -> "Needs attention"
                    arch == null -> "Not supported"
                    else -> "Not installed"
                },
                onSkip = { aiSkipped = true },
            ) {
                if (aiBusy) {
                    Text(
                        text = aiStage,
                        color = c.text,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(value = aiProgress)
                } else if (aiReady) {
                    Text(
                        text = "The AI runtime is ready.",
                        color = c.mock.green,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                } else if (arch == null) {
                    Text(
                        text = "This phone's CPU is not one OpenCode runs on.",
                        color = c.error,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                    )
                } else {
                    aiError?.let {
                        Text(
                            text = it,
                            color = c.error,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryButton(
                        label = if (aiError == null) "Download AI runtime" else "Try again",
                        onClick = { installAiRuntime() },
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            PrimaryButton(
                label = "Finish setup",
                onClick = onDone,
                enabled = !setupBusy,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (setupBusy) {
                    "Finish setup is available when the active download stops."
                } else {
                    "You can return to these downloads from Settings > Setup guide."
                },
                color = c.textFaint,
                fontSize = 12.sp,
                fontFamily = Sans,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            }
        }
    }

    focusStep?.let { step ->
        FocusPanel(
            step = step,
            buildState = buildState,
            shellProgress = shellProgress,
            aiProgress = aiProgress,
            aiStage = aiStage,
            onDismiss = { focusStep = null },
        )
    }
}

@Composable
private fun WelcomePanel(onStart: () -> Unit, onSkip: () -> Unit) {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.brightText),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "C",
                    color = c.mock.page,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(9.dp))
            Text(
                text = "Closecode",
                color = c.brightText,
                fontSize = 18.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        TextLink(label = "Skip all", onClick = onSkip, color = c.textMuted)
    }

    Spacer(Modifier.height(54.dp))
    Text(
        text = "Build apps\non your phone",
        color = c.brightText,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = "An editor, terminal, build tools and an AI assistant, together in one place. " +
            "Three optional downloads get the phone ready.",
        color = c.textMuted,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontFamily = Sans,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(34.dp))
    ScreenCard {
        WelcomeLine("1", "Build tools", "Compile Kotlin and Java apps offline.")
        Spacer(Modifier.height(14.dp))
        WelcomeLine("2", "Shell tools", "Git, Python, Node and the usual terminal commands.")
        Spacer(Modifier.height(14.dp))
        WelcomeLine("3", "OpenCode AI", "Chat with an AI that works inside the project.")
    }
    Spacer(Modifier.height(28.dp))
    PrimaryButton(label = "Get started", onClick = onStart)
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Every download is optional and can be added later from Settings > Setup guide.",
        color = c.textFaint,
        fontSize = 12.sp,
        fontFamily = Sans,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun WelcomeLine(number: String, title: String, detail: String) {
    val c = LocalPageColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(c.chipBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = c.accent,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.brightText,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                color = c.textMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = Sans,
            )
        }
    }
}

@Composable
private fun FocusPanel(
    step: Int,
    buildState: ToolchainState,
    shellProgress: ShellToolsStore.Progress?,
    aiProgress: Float,
    aiStage: String,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    val title = when (step) {
        1 -> "Build tools"
        2 -> "Shell tools"
        else -> "OpenCode AI"
    }
    val status = when (step) {
        1 -> when (buildState) {
            is ToolchainState.Downloading -> "Downloading build tools"
            ToolchainState.WaitingForWifi -> "Waiting for Wi-Fi"
            is ToolchainState.Verifying, ToolchainState.Checking -> "Checking files"
            else -> "Preparing build tools"
        }
        2 -> shellProgress?.message ?: "Installing shell tools"
        else -> aiStage.ifBlank { "Downloading the AI runtime" }
    }
    val progress = when (step) {
        1 -> {
            val state = buildState
            if (state is ToolchainState.Downloading && state.totalBytes > 0) {
                state.doneBytes.toFloat() / state.totalBytes
            } else {
                0f
            }
        }
        2 -> {
            val p = shellProgress
            if (p != null && p.totalBytes > 0) p.doneBytes.toFloat() / p.totalBytes else 0f
        }
        else -> aiProgress
    }
    Dialog(onDismissRequest = onDismiss) {
        ScreenCard(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                color = c.brightText,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = status,
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
            Spacer(Modifier.height(12.dp))
            ProgressBar(progress)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "You can leave this panel open or go back. The download continues " +
                    "while the phone stays awake.",
                color = c.textFaint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(label = "Back to setup", onClick = onDismiss)
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    description: String,
    ready: Boolean,
    skipped: Boolean,
    status: String,
    onSkip: () -> Unit,
    busy: Boolean = false,
    onFocus: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    ScreenCard(
        modifier = Modifier.clickable(enabled = busy, onClick = onFocus),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (ready) c.mock.green else c.chipBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (ready) "✓" else number.toString(),
                    color = if (ready) c.mock.onAccent else c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = c.brightText,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status,
                    color = when {
                        ready -> c.mock.green
                        skipped -> c.textFaint
                        else -> c.textMuted
                    },
                    fontSize = 11.sp,
                    fontFamily = Sans,
                )
            }
            if (skipped && !ready) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.chipBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Skipped",
                        color = c.textFaint,
                        fontSize = 11.sp,
                        fontFamily = Sans,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = description,
            color = c.textMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = Sans,
        )
        Spacer(Modifier.height(12.dp))
        content()
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                TextLink(label = "View download", onClick = onFocus, color = c.link)
            }
        }
        if (!ready && !skipped) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                TextLink(label = "Skip for now", onClick = onSkip, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun ProgressBar(value: Float) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.panel2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.accent),
        )
    }
}
