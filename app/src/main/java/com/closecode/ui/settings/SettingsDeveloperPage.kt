package com.closecode.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.BuildConfig
import com.closecode.CrashReport
import com.closecode.build.ToolchainHolder
import com.closecode.debug.DebugLog
import com.closecode.debug.DebugReport
import com.closecode.debug.SelfTest
import com.closecode.debug.sharePlainText
import com.closecode.ui.components.DesignBackButton
import com.closecode.ui.ide.CcIcon
import com.closecode.ui.ide.CcIcons
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DeveloperPage(onBack: () -> Unit) {
    val c = LocalPageColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugLogOn by remember { mutableStateOf(DebugLog.enabled) }
    val checks = remember { mutableStateListOf<SelfTest.Check>() }
    var running by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf<String?>(null) }
    var showCrash by remember { mutableStateOf(false) }
    var confirmDeleteTools by remember { mutableStateOf(false) }
    var confirmCrash by remember { mutableStateOf(false) }
    LaunchedEffectOnce {
        crashText = runCatching { CrashReport.readLast(context.filesDir) }.getOrNull()
    }

    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.mock.page)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesignBackButton(onClick = onBack)
            Text(
                text = "Developer",
                color = c.mock.text,
                fontSize = 18.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DevLabel("Diagnostics")
            DevCard {
                DevSwitchRow(
                    label = "Debug log",
                    note = "Keeps the last 500 lines. Off at every start.",
                    checked = debugLogOn,
                    onChange = {
                        debugLogOn = it
                        DebugLog.setEnabled(it)
                    },
                )
                DevDivider()
                DevActionRow(
                    label = "Self-test",
                    note = if (checks.isEmpty()) {
                        "Quick checks with one sentence each."
                    } else {
                        "${checks.count { it.status == SelfTest.Status.Pass }} passed · " +
                            "${checks.count { it.status == SelfTest.Status.Fail }} failed"
                    },
                    action = if (running) "Running…" else "Run",
                    enabled = !running,
                ) {
                    running = true
                    checks.clear()
                    scope.launch {
                        SelfTest.run(context) { checks.add(it) }
                        running = false
                    }
                }
                if (checks.isNotEmpty()) {
                    checks.forEach { check ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            CcIcon(
                                if (check.status == SelfTest.Status.Pass) {
                                    CcIcons.Check
                                } else {
                                    CcIcons.Warning
                                },
                                if (check.status == SelfTest.Status.Pass) {
                                    c.mock.green
                                } else {
                                    c.mock.warning
                                },
                                14.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "${check.number}. ${check.name} — ${check.detail}",
                                color = c.mock.muted,
                                fontSize = 11.sp,
                                fontFamily = Sans,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
                DevDivider()
                DevActionRow(
                    label = "Last crash",
                    note = crashText?.lineSequence()?.drop(1)?.firstOrNull()
                        ?: "No crash recorded",
                    action = if (crashText == null) "" else "View",
                    enabled = crashText != null,
                    onClick = { showCrash = true },
                )
                DevDivider()
                DevActionRow(
                    label = "Share debug report",
                    note = "Version, self-test, last 200 log lines and last crash.",
                    action = "Share",
                ) {
                    val report = DebugReport.compose(context)
                    if (!sharePlainText(context, "CloseCode debug report", report)) {
                        android.widget.Toast.makeText(
                            context,
                            "No app can share text",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }

            DevLabel("Storage")
            DevCard {
                DevActionRow(
                    label = "Clear build cache",
                    note = "Needs an open project. Open a project, then use Developer there.",
                    action = "Unavailable",
                    enabled = false,
                ) {}
                DevDivider()
                DevActionRow(
                    label = "Delete build tools",
                    note = "The compiler pack; it can be downloaded again.",
                    action = "Delete",
                    danger = true,
                    onClick = { confirmDeleteTools = true },
                )
            }

            if (BuildConfig.DEBUG) {
                DevLabel("Danger zone")
                DevCard {
                    DevActionRow(
                        label = "Crash test",
                        note = "Stops the app on purpose to test the crash report.",
                        action = "Crash",
                        danger = true,
                        onClick = { confirmCrash = true },
                    )
                }
            }
        }
    }

    if (showCrash) {
        val text = crashText ?: ""
        CcPopup(
            title = "Last crash",
            onDismiss = { showCrash = false },
            actions = {
                CcPopupButton("Close", onClick = { showCrash = false })
            },
        ) {
            Column(
                Modifier
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text,
                    color = c.mock.text,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    lineHeight = 16.sp,
                )
            }
        }
    }
    if (confirmDeleteTools) {
        CcConfirmPopup(
            title = "Delete build tools?",
            message = "You can download them again.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDeleteTools = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ToolchainHolder.deleteEverything(context)
                    }
                    android.widget.Toast.makeText(
                        context,
                        "Build tools deleted",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { confirmDeleteTools = false },
        )
    }
    if (confirmCrash) {
        CcConfirmPopup(
            title = "Crash test?",
            message = "The app will stop now. The next launch shows the crash report.",
            confirmText = "Crash",
            destructive = true,
            onConfirm = {
                confirmCrash = false
                throw RuntimeException("CloseCode crash test")
            },
            onDismiss = { confirmCrash = false },
        )
    }
}

@Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

@Composable
private fun DevLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LocalPageColors.current.mock.faint,
        fontSize = 10.5.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun DevCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.mock.card)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun DevDivider() {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp)
            .height(1.dp)
            .background(c.mock.border.copy(alpha = 0.55f)),
    )
}

@Composable
private fun DevActionRow(
    label: String,
    note: String,
    action: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && action.isNotEmpty(), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = c.mock.text,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = note,
                color = c.mock.muted,
                fontSize = 11.5.sp,
                fontFamily = Sans,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action.isNotEmpty()) {
            Text(
                text = action,
                color = when {
                    !enabled -> c.mock.faint
                    danger -> c.mock.danger
                    else -> c.mock.accent
                },
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DevSwitchRow(
    label: String,
    note: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = c.mock.text,
                fontSize = 13.5.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = note,
                color = c.mock.muted,
                fontSize = 11.5.sp,
                fontFamily = Sans,
                lineHeight = 15.sp,
            )
        }
        Box(
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .clip(CircleShape)
                .background(if (checked) c.mock.accent else c.mock.grayBg)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c.mock.onAccent),
            )
        }
    }
}
