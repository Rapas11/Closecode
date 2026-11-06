package com.closecode.ui.ide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.debug.SelfTest
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Sans

/** Screen 51: Debug tools > Self-test, with the phone's real answers. */
@Composable
fun IdeSelfTestScreen(
    checks: List<SelfTest.Check>,
    running: Boolean,
    elapsedMs: Long,
    onRun: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalCcColors.current
    val passed = checks.count { it.status == SelfTest.Status.Pass }
    val warned = checks.count { it.status == SelfTest.Status.Skipped }
    val failed = checks.count { it.status == SelfTest.Status.Fail }
    BackHandler(onBack = onBack)
    IconScreenFrame("Developer", "Self-test", onBack, footer = {
        BigButton(
            text = "Share report",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            icon = "share",
            bg = c.field,
            fg = c.text2,
            enabled = checks.isNotEmpty() && !running,
        )
    }) {
        Text(
            "What this phone says about itself and the tools it runs. Nothing leaves the device.",
            color = c.muted,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        )
        Card20 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(c.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(if (running) "refresh" else "check", c.accent, 21.dp, 2f)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            running -> "Running..."
                            checks.isEmpty() -> "Ready to check"
                            else -> "$passed passed" +
                                if (warned + failed > 0) " · ${warned + failed} to check" else ""
                        },
                        color = c.text,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = when {
                            running -> "Checking ${checks.size} of ${SelfTest.totalChecks}"
                            elapsedMs > 0 -> "Finished in ${"%.1f".format(elapsedMs / 1000.0)} s"
                            else -> "${SelfTest.totalChecks} checks, one at a time"
                        },
                        color = c.muted,
                        fontSize = 12.5.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!running) {
                    Box(
                        Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accent)
                            .clickable(onClick = onRun)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Run again",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (running) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(c.rail),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((checks.size.toFloat() / SelfTest.totalChecks).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(c.accent),
                    )
                }
            }
        }
        IconSectionLabel("Checks")
        val completed = checks.associateBy { it.number }
        for (number in 1..SelfTest.totalChecks) {
            val check = completed[number]
            SelfTestRow(number, check, running && check == null)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SelfTestRow(number: Int, check: SelfTest.Check?, active: Boolean) {
    val c = LocalCcColors.current
    val status = check?.status
    val color = when (status) {
        SelfTest.Status.Pass -> c.green
        SelfTest.Status.Fail -> c.red
        SelfTest.Status.Skipped -> c.amber
        null -> if (active) c.accent else c.faint
    }
    val icon = when (status) {
        SelfTest.Status.Pass -> "check"
        SelfTest.Status.Fail -> "close"
        SelfTest.Status.Skipped -> "alert"
        null -> if (active) "refresh" else "more"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, color, 16.dp, 2f)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(top = 1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$number. ${SelfTest.displayName(number)}",
                    color = c.text,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when (status) {
                        SelfTest.Status.Pass -> "Pass"
                        SelfTest.Status.Fail -> "Fail"
                        SelfTest.Status.Skipped -> "Warn"
                        null -> if (active) "Running" else "Waiting"
                    },
                    color = color,
                    fontSize = 11.5.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (check != null) {
                Text(
                    text = check.detail,
                    color = c.muted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
