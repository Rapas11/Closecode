package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.debug.SelfTest
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/**
 * The rows the Developer sheet draws, in order. Kept as data so a unit test can pin them
 * without rendering anything (the crash test is debug-only, so it is not part of the pin).
 */
internal val DeveloperLabels = listOf(
    "Debug log",
    "Self-test",
    "View last crash",
    "Share debug report",
    "Clear build cache",
    "Delete build tools",
    "Use pipe terminal",
)

/** Debug builds only: proves the crash report works by stopping the app on purpose. */
internal const val DeveloperCrashTestLabel = "Crash test"

/**
 * More > Developer (spec, Debug tools): every tool that lets the phone report facts, in the
 * one place the spec puts them. Rows that open something close the sheet first; the two
 * switches stay open so their buttons and notes remain visible while they are used.
 */
@Composable
fun IdeDeveloperSheet(
    debugLogOn: Boolean,
    usePipe: Boolean,
    crashAvailable: Boolean,
    debugBuild: Boolean,
    onToggleDebugLog: (Boolean) -> Unit,
    onCopyLog: () -> Unit,
    onShareLog: () -> Unit,
    onClearLog: () -> Unit,
    onSelfTest: () -> Unit,
    onViewCrash: () -> Unit,
    onShareReport: () -> Unit,
    onClearBuildCache: () -> Unit,
    onDeleteTools: () -> Unit,
    onToggleUsePipe: (Boolean) -> Unit,
    onCrashTest: () -> Unit,
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Text(
            text = "DEVELOPER",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DeveloperSwitchRow(
                label = "Debug log",
                note = "Keeps the last 500 lines and writes debug/debug.log. Off again at every start.",
                checked = debugLogOn,
                onCheckedChange = onToggleDebugLog,
            )
            if (debugLogOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeveloperSmallButton("Copy log", onCopyLog)
                    DeveloperSmallButton("Share log", onShareLog)
                    DeveloperSmallButton("Clear", onClearLog)
                }
            }
            DeveloperActionRow(
                label = "Self-test",
                note = "Sixteen checks with one plain sentence each.",
            ) {
                onSelfTest()
                onDismiss()
            }
            DeveloperActionRow(
                label = "View last crash",
                note = if (crashAvailable) "The report from the last stop." else "No crash is recorded.",
            ) {
                onViewCrash()
                onDismiss()
            }
            DeveloperActionRow(
                label = "Share debug report",
                note = "The version line, the last self-test, the last 200 log lines and the last crash.",
            ) {
                onShareReport()
                onDismiss()
            }
            DeveloperActionRow(
                label = "Clear build cache",
                note = "Deletes this project's build output; the next check builds it again.",
            ) {
                onClearBuildCache()
                onDismiss()
            }
            DeveloperActionRow(
                label = "Delete build tools",
                note = "The compiler pack; it can be downloaded again.",
            ) {
                onDeleteTools()
                onDismiss()
            }
            DeveloperSwitchRow(
                label = "Use pipe terminal",
                note = "Fallback: the plain pipe shell, without colors or Ctrl+C signals. " +
                    "Applies to new shells. Off means the real PTY.",
                checked = usePipe,
                onCheckedChange = onToggleUsePipe,
            )
            if (debugBuild) {
                DeveloperActionRow(
                    label = DeveloperCrashTestLabel,
                    note = "Stops the app on purpose, to test the crash report.",
                ) {
                    onCrashTest()
                    onDismiss()
                }
            }
        }
    }
}

/**
 * The self-test sheet: the facts block at the top (which APK, what phone, what is free),
 * then one row per check as it lands. Copy report copies exactly what is on screen.
 */
@Composable
fun IdeSelfTestSheet(
    facts: String,
    checks: List<SelfTest.Check>,
    running: Boolean,
    onRun: () -> Unit,
    onCopyReport: () -> Unit,
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Text(
            text = "SELF-TEST",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.panel2)
                    .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = facts.ifEmpty { "…" },
                    color = c.mu,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    lineHeight = 16.sp,
                )
            }
            PrimaryButton(
                label = if (running) "Running…" else "Run self-test",
                onClick = onRun,
                enabled = !running,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            checks.forEach { check -> SelfTestCheckRow(check) }
            if (checks.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCopyReport),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Copy report",
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** The last crash as a sheet: the report's text plus Copy, Share and Close. */
@Composable
fun IdeCrashSheet(
    text: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Text(
            text = "LAST CRASH",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.panel2)
                    .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = text.take(6000),
                    color = c.tx,
                    fontSize = 10.5.sp,
                    fontFamily = Mono,
                    lineHeight = 15.sp,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeveloperSmallButton("Copy report", onCopy)
                DeveloperSmallButton("Share", onShare)
                DeveloperSmallButton("Close", onDismiss)
            }
        }
    }
}

// --- pieces ---------------------------------------------------------------------

@Composable
private fun DeveloperActionRow(
    label: String,
    note: String,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = c.tx,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = note,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun DeveloperSwitchRow(
    label: String,
    note: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = c.tx,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = note,
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        DeveloperSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The small pill switch these rows use; the app's settings switches are 48 dp tall, too big here. */
@Composable
private fun DeveloperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .padding(start = 12.dp)
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) c.ac else c.ln)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) Color.White else c.mu),
        )
    }
}

@Composable
private fun DeveloperSmallButton(label: String, onClick: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, c.ln, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = c.tx,
            fontSize = 12.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SelfTestCheckRow(check: SelfTest.Check) {
    val c = LocalPageColors.current
    val statusColor = when (check.status) {
        SelfTest.Status.Pass -> c.success
        SelfTest.Status.Fail -> c.red
        SelfTest.Status.Skipped -> c.mu
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${check.number}. ${check.name}",
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = check.status.name,
                color = statusColor,
                fontSize = 11.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = check.detail,
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
