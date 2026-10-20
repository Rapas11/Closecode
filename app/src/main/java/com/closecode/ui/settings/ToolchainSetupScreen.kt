package com.closecode.ui.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.closecode.build.ToolchainDownloadService
import com.closecode.build.ToolchainHolder
import com.closecode.build.ToolchainManifest
import com.closecode.build.ToolchainState
import com.closecode.build.ToolchainStore
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.components.TextLink
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.launch

/**
 * First-setup download: the one-time build-tools pack. Exactly the spec's five states —
 * Not installed, Downloading, Checking, Ready, Error — with real numbers, a resumable
 * download that runs in [ToolchainDownloadService] so it survives leaving this screen,
 * Wi-Fi only unless the user allows mobile data, and checksums verified before any file
 * is used.
 */
@Composable
fun ToolchainSetupScreen(
    store: ToolchainStore,
    onClose: () -> Unit,
    onDone: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState()
    val toolchain = remember(context) { ToolchainManifest.load(context) }
    val is64Bit = remember { Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } }
    var allowMobile by remember { mutableStateOf(store.mobileAllowed()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Measured per state so "Free space now" refreshes after a download changes it.
    val freeBytes = remember(state) { context.filesDir.usableSpace }
    val tooLow = freeBytes < ToolchainStore.FREE_SPACE_NEEDED_BYTES

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun startDownload() {
        if (Build.VERSION.SDK_INT >= 33 && !ToolchainDownloadService.notificationsAllowed(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ToolchainDownloadService.start(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Build tools",
            color = c.brightText,
            fontSize = 20.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))

        if (!is64Bit) {
            Text(
                text = "Building needs a 64-bit phone.",
                color = c.brightText,
                fontSize = 14.sp,
                fontFamily = Sans,
                lineHeight = 20.sp,
            )
            CloseLink(c.textMuted, onClose)
            return@Column
        }

        when (val current = state) {
            ToolchainState.NotStarted -> {
                Text(
                    text = "To build Android apps on this phone, CloseCode needs to download " +
                        "its build tools once. After that, building works without internet.",
                    color = c.textMuted,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                FactRow("Download size", humanSize(toolchain.totalBytes))
                FactRow("Free space needed", "1.5 GB")
                FactRow("Free space now", humanSize(freeBytes), danger = tooLow)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Use mobile data",
                            color = c.text,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                        )
                        Text(
                            text = "Off: downloads wait for Wi-Fi.",
                            color = c.textFaint,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                        )
                    }
                    SetupSwitch(checked = allowMobile) { checked ->
                        allowMobile = checked
                        store.setMobileAllowed(checked)
                    }
                }
                if (store.needsUpdate()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Build tools need an update.",
                        color = c.text,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    label = if (store.needsUpdate()) "Update build tools" else "Download build tools",
                    onClick = { startDownload() },
                    enabled = !tooLow,
                )
                if (tooLow) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Not enough storage. You need 1.5 GB free. Free up space and try again.",
                        color = c.error,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        lineHeight = 18.sp,
                    )
                }
            }

            is ToolchainState.Downloading -> {
                DownloadLine(current)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineButton(
                        label = "Pause",
                        onClick = { ToolchainDownloadService.send(context, ToolchainDownloadService.ACTION_PAUSE) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton(
                        label = "Cancel",
                        onClick = { ToolchainDownloadService.send(context, ToolchainDownloadService.ACTION_CANCEL) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ToolchainState.WaitingForWifi -> {
                Text(
                    text = "Waiting for Wi-Fi",
                    color = c.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    label = "Use mobile data",
                    onClick = {
                        allowMobile = true
                        store.setMobileAllowed(true)
                    },
                )
            }

            is ToolchainState.Verifying, ToolchainState.Checking -> {
                Text(
                    text = "Checking files…",
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }

            ToolchainState.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "✓", color = c.success, fontSize = 16.sp, fontFamily = Sans)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Build tools are ready.",
                        color = c.success,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(14.dp))
                FactRow("Version", "Kotlin ${toolchain.kotlin}")
                FactRow("Size on phone", humanSize(store.sizeOnPhone()))
                Spacer(Modifier.height(14.dp))
                OutlineButton(
                    label = "Check files",
                    onClick = {
                        scope.launch {
                            val bad = store.verifyAll()
                            message = if (bad.isEmpty()) {
                                "All files check out."
                            } else {
                                "Some files were damaged. Downloading them again…"
                            }
                            if (bad.isNotEmpty()) startDownload()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlineButton(
                    label = "Check for updates",
                    onClick = {
                        scope.launch {
                            val outdated = store.needsUpdate()
                            message = if (outdated) {
                                "A newer set of build tools is ready. Downloading it…"
                            } else {
                                "You have the latest build tools."
                            }
                            if (outdated) startDownload()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                TextLink(
                    label = "Delete build tools",
                    onClick = { confirmDelete = true },
                    color = c.error,
                )
            }

            is ToolchainState.Failed -> {
                Text(
                    text = current.message,
                    color = c.error,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(label = "Try again", onClick = { startDownload() })
            }
        }

        message?.let { line ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = line,
                color = c.textMuted,
                fontSize = 12.sp,
                fontFamily = Sans,
                lineHeight = 18.sp,
            )
        }

        if (confirmDelete) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Delete the build tools? You can download them again.",
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineButton(
                        label = "Delete",
                        onClick = {
                            confirmDelete = false
                            message = if (ToolchainHolder.deleteEverything(context)) {
                                "Build tools deleted."
                            } else {
                                "Could not delete the build tools."
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton(
                        label = "Cancel",
                        onClick = { confirmDelete = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Licenses",
            color = c.brightText,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = toolchain.files
                .filter { it.id == "kotlin-compiler" || it.id == "ecj" || it.id == "r8" ||
                    it.id == "kotlin-stdlib" || it.id == "android-jar" }
                .joinToString(" · ") { "${it.id} ${it.version} (${it.license})" } +
                ". aapt2 ${toolchain.aapt2.version}: ${toolchain.aapt2.license}. " +
                "The debug signing key never leaves this phone; apksig is part of the app.",
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Sans,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onClose)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Close", color = c.textMuted, fontSize = 14.sp, fontFamily = Sans)
        }
    }
}

/** The progress bar and the exact line: "Downloading 3 of 14 · 120 MB of 260 MB". */
@Composable
private fun DownloadLine(current: ToolchainState.Downloading) {
    val c = LocalPageColors.current
    val doneMb = current.doneBytes / 1_048_576
    val totalMb = current.totalBytes / 1_048_576
    Text(
        text = "Downloading ${current.index} of ${current.count} · $doneMb MB of $totalMb MB",
        color = c.text,
        fontSize = 13.sp,
        fontFamily = Sans,
    )
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.panel2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(
                    (if (current.totalBytes > 0) {
                        current.doneBytes.toFloat() / current.totalBytes
                    } else {
                        0f
                    }).coerceIn(0f, 1f),
                )
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.accent),
        )
    }
    current.note?.let { note ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = note,
            color = c.textMuted,
            fontSize = 12.sp,
            fontFamily = Sans,
        )
    }
}

/** One `label … value` line, red on the value when it is a problem. */
@Composable
private fun FactRow(label: String, value: String, danger: Boolean = false) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = c.textMuted, fontSize = 13.sp, fontFamily = Sans)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = if (danger) c.error else c.text,
            fontSize = 13.sp,
            fontFamily = Sans,
        )
    }
}

/** The small pill switch the developer rows use. */
@Composable
private fun SetupSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .padding(start = 12.dp)
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) c.accent else c.panel2)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.White),
        )
    }
}

@Composable
private fun CloseLink(color: androidx.compose.ui.graphics.Color, onClose: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClose)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Close", color = color, fontSize = 14.sp, fontFamily = Sans)
    }
}
