package com.closecode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll



import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.background.PageBackground
import com.closecode.ui.components.CodeCard
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.Templates
import com.closecode.ui.components.CodeGlyph
import com.closecode.ui.components.PlusGlyph
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

private val Facts = listOf("Open source", "On-device builds", "Private by default")

@Composable
private fun Reveal(
    index: Int,
    content: @Composable () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                delayMillis = (index * 80).coerceAtMost(520),
            ),
        ) { value, _ -> progress = value }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12f * density
        }
    ) { content() }
}

@Composable
private fun Pill(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(52.dp)
            .clip(shape)
            .background(if (primary) c.ab else c.cd)
            .border(1.dp, if (primary) c.ab else c.ln, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (leading != null) leading()
        Text(
            text = label,
            color = if (primary) c.af else c.tx,
            fontSize = 16.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Wordmark() {
    val c = LocalPageColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .background(c.tx, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                color = c.bg,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Closecode",
            color = c.tx,
            fontSize = 18.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Headline(size: TextUnit) {
    val c = LocalPageColors.current
    Text(
        text = buildAnnotatedString {
            append("Build Android apps,\n")
            withStyle(SpanStyle(color = c.ac)) { append("anywhere.") }
        },
        color = c.tx,
        fontSize = size,
        lineHeight = size * 1.02f,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.045f).em,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 620.dp),
    )
}

@Composable
fun HeroScreen(
    onProjectCreated: (name: String, selection: SelectedTemplate, templateName: String, config: ProjectConfig) -> Unit,
    onOpenRecent: () -> Unit,
    recentCount: Int,
) {
    val c = LocalPageColors.current
    var sheetOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2400)
            toast = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxWidth
        val headlineSize = (viewport.value * 0.082f).coerceIn(38f, 76f).sp
        val subSize = (viewport.value * 0.024f).coerceIn(16f, 19f).sp

        PageBackground()

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Reveal(1) { Wordmark() }
            Spacer(Modifier.height(34.dp))

            Reveal(2) {
                Text(
                    text = "THE ANDROID IDE FOR YOUR PHONE",
                    color = c.ac,
                    fontSize = 12.5.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.08.em,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))

            Reveal(3) { Headline(headlineSize) }

            Spacer(Modifier.height(22.dp))

            Reveal(4) {
                Text(
                    text = "Write, build and ship real apps straight from your device. " +
                        "No laptop, no setup, no compromise.",
                    color = c.mu,
                    fontSize = subSize,
                    lineHeight = subSize * 1.6f,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }

            Spacer(Modifier.height(34.dp))

            Reveal(5) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill(
                        label = "Start a project",
                        primary = true,
                        onClick = { sheetOpen = true },
                    ) {
                        PlusGlyph(color = c.af)
                    }
                    Pill(
                        label = "GitHub",
                        primary = false,
                        onClick = {},
                        leading = {
                            CodeGlyph(color = c.tx)
                        },
                    )
                }
            }

            Spacer(Modifier.height(56.dp))

            Reveal(6) { CodeCard() }

            Spacer(Modifier.height(38.dp))

            Reveal(7) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Facts.forEach { fact ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(c.ac, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = fact,
                                color = c.mu,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = toast.orEmpty(),
                color = c.af,
                fontSize = 14.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.ab)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }

    if (sheetOpen) {
        StartProjectSheet(
            onOpenRecent = onOpenRecent,
            recentCount = recentCount,
            onDismiss = { sheetOpen = false },
            onCreate = { projectName, selection, config ->
                sheetOpen = false
                val templateName = Templates[selection.tab]
                    .orEmpty()
                    .getOrNull(selection.index)
                    ?.name
                    .orEmpty()
                onProjectCreated(projectName, selection, templateName, config)
            },
        )
    }
}