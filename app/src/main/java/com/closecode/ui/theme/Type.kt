package com.closecode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.closecode.R

/**
 * Code font. JetBrains Mono 11.5 sp with a line height of exactly 21 dp: the gutter, underlines,
 * pills and the lightbulb are all positioned against that number, so it must not drift.
 */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * UI font. Inter 14 sp base, line height 1.4, weights 400 / 500 / 600 / 700 / 800.
 */
val Sans = FontFamily(
    inter(400),
    inter(500),
    inter(600),
    inter(700),
    inter(800),
)

/**
 * Fallback for the geometric symbols Fira Code does not cover (chevrons, triangles, the
 * circled cross). Kept separate so code text still renders in Fira Code.
 */
val Symbol = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Spec section 3: code line height is fixed at 21 dp and drives all editor geometry. */
val CodeLineHeight = 21.sp

/** Spec section 3: UI base size, with line height 1.45. */
val UiBaseLineHeight = (14 * 1.4).sp

val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = UiBaseLineHeight,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.02 * 16).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = UiBaseLineHeight,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = UiBaseLineHeight,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    ),
)