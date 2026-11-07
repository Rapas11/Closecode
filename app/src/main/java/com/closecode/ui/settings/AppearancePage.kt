package com.closecode.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.CcIcon
import com.closecode.ui.theme.DarkCcColors
import com.closecode.ui.theme.LightCcColors
import com.closecode.ui.theme.LocalCcColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import com.closecode.ui.theme.codeFontFamily

// The theme tiles are pictures of the two themes, so their colours are the pictures' own,
// not the active palette's.
private val TileLight = Color(0xFFE3E5EA)
private val TileLightBar = Color(0xFF2E3036)
private val TileDark = Color(0xFF1F2024)
private val TileDarkBar = Color(0xFFDBDEE1)
private val TileSystemBar = Color(0xFF8A8F98)

/** Screen 40: theme tiles, accent swatches, the code rows and a live preview. */
@Composable
internal fun AppearanceSettingsPage(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalCcColors.current
    val accent = AccentOptions.getOrElse(settings.accentIndex) { AccentOptions.first() }
    var picker by remember { mutableStateOf<String?>(null) }

    SettingsPage(title = "Appearance", onBack = onBack) {
        AppearanceLabel("Theme", top = 16.dp, bottom = 6.dp)
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeChoices.forEach { mode ->
                ThemeTile(
                    mode = mode,
                    selected = mode == settings.theme,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSettingsChange(settings.withOption(OptionSetting.THEME, mode))
                    },
                )
            }
        }

        AppearanceLabel("Accent color", top = 18.dp, bottom = 8.dp)
        Row(
            Modifier.padding(start = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentOptions.forEach { colour ->
                val on = colour == accent
                Box(
                    Modifier
                        .size(34.dp)
                        .drawBehind {
                            if (on) {
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2f + 3.25.dp.toPx(),
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }
                        }
                        .clip(CircleShape)
                        .background(colour)
                        .clickable {
                            val index = AccentOptions.indexOf(colour)
                            if (index >= 0) onSettingsChange(settings.copy(accentIndex = index))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) CcIcon("check", Color.White, 16.dp, 3f)
                }
            }
        }

        SetGroup("Code") {
            Item(
                icon = "code",
                tint = c.purple,
                title = "Code font",
                sub = settings.codeFont,
                chevron = true,
                onClick = { picker = "font" },
            )
            Item(
                icon = "search",
                tint = c.blue,
                title = "Code size",
                value = "${settings.fontSize}",
                onClick = { picker = "size" },
            )
            Item(
                icon = "layers",
                tint = c.teal,
                title = "Syntax colors",
                sub = settings.syntaxColors,
                chevron = true,
                onClick = { picker = "syntax" },
            )
        }

        Box(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.rail)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = previewCode(settings),
                color = c.text2,
                style = TextStyle(
                    fontFamily = codeFontFamily(settings.codeFont),
                    fontSize = 11.sp,
                    lineHeight = 18.7.sp,
                ),
            )
        }
    }

    when (picker) {
        "font" -> SettingsChoiceDialog(
            title = "Code font",
            choices = CodeFontChoices,
            selected = settings.codeFont,
            onPick = { value ->
                picker = null
                onSettingsChange(settings.withOption(OptionSetting.CODE_FONT, value))
            },
            onDismiss = { picker = null },
        )

        "size" -> SettingsChoiceDialog(
            title = "Code size",
            choices = FontSizeChoices,
            selected = settings.fontSize.toString(),
            onPick = { value ->
                picker = null
                onSettingsChange(settings.withOption(OptionSetting.FONT_SIZE, value))
            },
            onDismiss = { picker = null },
        )

        "syntax" -> SettingsChoiceDialog(
            title = "Syntax colors",
            choices = SyntaxColorChoices,
            selected = settings.syntaxColors,
            onPick = { value ->
                picker = null
                onSettingsChange(settings.withOption(OptionSetting.SYNTAX_COLORS, value))
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun AppearanceLabel(text: String, top: Dp, bottom: Dp) {
    Text(
        text = text.uppercase(),
        color = LocalCcColors.current.muted,
        fontSize = 11.sp,
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 18.dp, top = top, bottom = bottom),
    )
}

@Composable
private fun ThemeTile(
    mode: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalCcColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .drawBehind {
                    if (selected) {
                        drawRoundRect(
                            color = c.accent,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(18.dp.toPx()),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                    }
                }
                .clickable(onClick = onClick),
        ) {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.5.dp)
                    .clip(shape)
                    .then(
                        if (selected) Modifier else Modifier.border(1.dp, c.border, shape),
                    ),
            ) {
                when (mode) {
                    "Dark" -> Box(Modifier.matchParentSize().background(TileDark))
                    "System" -> Row(Modifier.matchParentSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(TileLight))
                        Box(Modifier.weight(1f).fillMaxHeight().background(TileDark))
                    }
                    else -> Box(Modifier.matchParentSize().background(TileLight))
                }
                Canvas(Modifier.matchParentSize()) {
                    val barColor = when (mode) {
                        "Dark" -> TileDarkBar
                        "System" -> TileSystemBar
                        else -> TileLightBar
                    }
                    val left = 10.dp.toPx()
                    val top = 10.dp.toPx()
                    val gap = 5.dp.toPx()
                    val bar = 7.dp.toPx()
                    val radius = 3.dp.toPx()
                    val inner = size.width - left * 2f
                    floatArrayOf(0.60f, 0.85f, 0.45f, 0.70f).forEachIndexed { index, part ->
                        drawRoundRect(
                            color = barColor.copy(alpha = if (index == 0) 0.9f else 0.4f),
                            topLeft = Offset(left, top + index * (bar + gap)),
                            size = Size(inner * part, bar),
                            cornerRadius = CornerRadius(radius),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = mode,
            color = if (selected) c.text else c.muted,
            fontSize = 12.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun previewCode(settings: Settings): AnnotatedString {
    val scheme = if (settings.syntaxColors == "Default dark") DarkCcColors() else LightCcColors()
    return buildAnnotatedString {
        withStyle(SpanStyle(color = scheme.syntaxKeyword)) { append("fun ") }
        withStyle(SpanStyle(color = scheme.syntaxFunction)) { append("greet") }
        append("(")
        withStyle(SpanStyle(color = scheme.syntaxVariable)) { append("name") }
        append(": ")
        withStyle(SpanStyle(color = scheme.syntaxType)) { append("String") }
        append(") {\n    ")
        withStyle(SpanStyle(color = scheme.syntaxKeyword)) { append("val ") }
        withStyle(SpanStyle(color = scheme.syntaxVariable)) { append("message") }
        append(" = ")
        withStyle(SpanStyle(color = scheme.syntaxString)) { append("\"Hello, \$name\"") }
        append("\n    ")
        withStyle(SpanStyle(color = scheme.syntaxKeyword)) { append("return ") }
        withStyle(SpanStyle(color = scheme.syntaxVariable)) { append("message") }
        append(" + ")
        withStyle(SpanStyle(color = scheme.syntaxNumber)) { append("42") }
        append("\n}")
    }
}
