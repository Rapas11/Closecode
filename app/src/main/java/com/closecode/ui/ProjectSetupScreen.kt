package com.closecode.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.Project
import com.closecode.build.BuildSetup
import com.closecode.build.BuildSetupFactory
import com.closecode.build.ToolchainDownloadService
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainState
import com.closecode.rememberProjectRepository
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.TextLink
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * After a project has been scaffolded, this is the honest setup step: check the real toolchain,
 * download what is missing, and only then let the IDE open. Closing this screen leaves the
 * project in Recents; it is never deleted because setup was interrupted.
 */
@Composable
fun ProjectSetupScreen(
    project: Project,
    onReady: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val repository = rememberProjectRepository()
    val scope = rememberCoroutineScope()
    val toolchainStore = remember(context) { ToolchainHolder.store(context) }
    val buildState by toolchainStore.state.collectAsState()

    var setup by remember(project.slug) { mutableStateOf<BuildSetup?>(null) }
    var checking by remember(project.slug) { mutableStateOf(true) }
    var checkError by remember(project.slug) { mutableStateOf<String?>(null) }
    var opened by remember(project.slug) { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun check() {
        checking = true
        checkError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    BuildSetupFactory.create(
                        context,
                        BuildSetup.Project(
                            slug = project.slug,
                            name = project.name,
                            directory = repository.projectDir(project.slug),
                            id = project.id,
                            savedPackage = project.config.packageName,
                            savedMinSdk = project.config.minSdk.toIntOrNull(),
                            savedTargetSdk = project.config.targetSdk.toIntOrNull(),
                            savedCompileSdk = project.config.compileSdk.toIntOrNull(),
                        ),
                    )
                }
            }
            checking = false
            result.onSuccess { setup = it }
            result.onFailure { checkError = it.message ?: it.javaClass.simpleName }
        }
    }

    LaunchedEffect(project.slug) { check() }

    LaunchedEffect(setup) {
        if (setup?.tools != null && !opened) {
            opened = true
            onReady()
        }
    }

    // A toolchain download runs in its own service. When it finishes, re-read the real setup
    // instead of trusting a progress number, then open only if every tool is actually there.
    LaunchedEffect(buildState) {
        if (buildState is ToolchainState.Ready) check()
    }

    fun startDownload() {
        if (Build.VERSION.SDK_INT >= 33 &&
            !ToolchainDownloadService.notificationsAllowed(context)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ToolchainDownloadService.start(context)
    }

    val current = setup
    val missing = current?.missing.orEmpty()
    val toolsReady = current?.tools != null
    val download = buildState as? ToolchainState.Downloading

    Column(
        Modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        com.closecode.ui.components.DesignPageHeader(
            title = "Setting up ${project.name}",
            subtitle = "The project is created. This step prepares its tools.",
            onBack = onCancel,
        )

        Spacer(Modifier.height(4.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
        com.closecode.ui.components.DesignCard {
            Column {
                Text(
                    text = when {
                        checking -> "Checking the build tools…"
                        toolsReady -> "Build tools are ready."
                        checkError != null -> "Could not check the build tools."
                        missing.isNotEmpty() -> "Some build tools are missing."
                        else -> "Preparing…"
                    },
                    color = if (toolsReady) c.mock.green else c.mock.text,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    checking -> {
                        Text(
                            text = "Reading the project and the files on this phone.",
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                    checkError != null -> {
                        Text(
                            text = checkError.orEmpty(),
                            color = c.mock.danger,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlineButton(label = "Check again", onClick = { check() })
                    }
                    toolsReady -> {
                        Text(
                            text = "Opening the editor…",
                            color = c.mock.muted,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                        )
                    }
                    else -> {
                        if (missing.isNotEmpty()) {
                            Text(
                                text = missing.joinToString(", "),
                                color = c.mock.muted,
                                fontSize = 12.sp,
                                fontFamily = Sans,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        when (val state = buildState) {
                            is ToolchainState.Downloading -> {
                                Text(
                                    text = "Downloading ${state.index} of ${state.count} · " +
                                        "${state.doneBytes / 1_048_576} MB of " +
                                        "${state.totalBytes / 1_048_576} MB",
                                    color = c.mock.text,
                                    fontSize = 12.sp,
                                    fontFamily = Sans,
                                )
                                Spacer(Modifier.height(8.dp))
                                ProgressBar(
                                    if (state.totalBytes > 0) {
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
                                    color = c.mock.text,
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
                                    color = c.mock.danger,
                                    fontSize = 12.sp,
                                    fontFamily = Sans,
                                )
                                Spacer(Modifier.height(10.dp))
                                PrimaryButton(
                                    label = "Try again",
                                    onClick = { startDownload() },
                                )
                            }
                            else -> {
                                PrimaryButton(
                                    label = if (toolchainStore.needsUpdate()) {
                                        "Update build tools"
                                    } else {
                                        "Download build tools"
                                    },
                                    onClick = { startDownload() },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (download != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "You can close this screen. The download continues in the notification, " +
                    "and ${project.name} stays in Recents.",
                color = c.mock.faint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = Sans,
            )
        }

        Spacer(Modifier.height(24.dp))
        OutlineButton(label = "Back to projects", onClick = onCancel)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "CloseCode never opens a project before its build tools are ready.",
            color = c.mock.faint,
            fontSize = 11.sp,
            fontFamily = Sans,
        )
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
            .background(c.mock.cardMuted),
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.mock.accent),
        )
    }
}
