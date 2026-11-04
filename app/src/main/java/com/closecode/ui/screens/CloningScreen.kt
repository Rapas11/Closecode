package com.closecode.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.ErrorCard
import com.closecode.ui.components.OneLine
import com.closecode.ui.components.ScreenHeader
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/**
 * Screen 05 — Cloning progress.
 *
 * One card holds the git phase, the percentage, the bytes downloaded and the last three
 * log lines the clone printed. Cancel asks first; a failure keeps the card's place with
 * the real message and a retry, and the button under it becomes Back.
 */
@Composable
fun CloningScreen(
    origin: String?,
    phase: String,
    percent: Float?,
    sizeText: String,
    log: List<String>,
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val c = CcTheme.colors
    val p by animateFloatAsState(
        (percent ?: 0f).coerceIn(0f, 1f),
        tween(200, easing = LinearEasing),
        label = "clone",
    )

    Column(Modifier.fillMaxSize().background(c.content)) {
        ScreenHeader(origin, "Cloning", onCancel)
        Column(Modifier.padding(start = 18.dp, top = 22.dp, end = 18.dp)) {
            if (error != null) {
                ErrorCard(error, onRetry)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.field)
                        .padding(18.dp),
                ) {
                    Row {
                        OneLine(
                            if (percent != null) "$phase ${(percent * 100).toInt()}%" else phase,
                            CcType.body.copy(fontWeight = FontWeight.Bold),
                            c.accent,
                            Modifier.weight(1f),
                        )
                        OneLine(
                            sizeText,
                            CcType.body.copy(fontWeight = FontWeight.Bold),
                            c.muted,
                        )
                    }
                    Box(
                        Modifier
                            .padding(top = 11.dp, bottom = 14.dp)
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
                    log.takeLast(3).forEach {
                        Text(
                            it,
                            style = CcType.code.copy(fontSize = 11.sp, lineHeight = 17.6.sp),
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.field)
                    .clickable(onClick = if (error != null) onBack else onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (error != null) "Back" else "Cancel",
                    style = CcType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = c.text,
                )
            }
        }
    }
}
