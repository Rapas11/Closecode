package com.closecode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.icons.CcIcon
import com.closecode.ui.theme.CcTheme
import com.closecode.ui.theme.CcType

/**
 * Empty state: centred 64 dp tile (radius 20, field colour, muted icon 28), a title,
 * one muted line saying what appears here, and the action button when one exists.
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = CcTheme.colors
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.field),
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(icon, c.muted, 28.dp, 1.6f)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = CcType.itemTitle.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = c.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = CcType.small,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(actionText, onAction)
        }
    }
}

/** Loading skeleton: rows of field-coloured bars. */
@Composable
fun LoadingList(rows: Int = 4) {
    val c = CcTheme.colors
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.field),
            )
        }
    }
}

/** Error card: red-tinted block with the real message and an optional Retry. */
@Composable
fun ErrorCard(message: String, onRetry: (() -> Unit)?) {
    val c = CcTheme.colors
    Column(
        Modifier
            .padding(14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.diffDelBg)
            .padding(12.dp),
    ) {
        Text(
            message,
            style = CcType.small,
            color = c.red, // the real error text from the system
        )
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "Retry",
                onRetry,
                icon = "refresh",
                height = 40.dp,
            )
        }
    }
}

/** Label + animated progress line (6 dp bar, accent fill, 200 ms between values). */
@Composable
fun ProgressLine(
    label: String,
    percent: Float,
    right: String? = null,
) {
    val c = CcTheme.colors
    val p by animateFloatAsState(
        percent.coerceIn(0f, 1f),
        tween(200, easing = LinearEasing),
        label = "progress-line",
    )
    Column {
        Row {
            Text(
                label,
                style = CcType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = c.accent,
                modifier = Modifier.weight(1f),
            )
            if (right != null) {
                Text(
                    right,
                    style = CcType.caption,
                    color = c.muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
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
    }
}
