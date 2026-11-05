package com.closecode.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ClosecodeTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    forceDark: Boolean? = null,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val useDark = forceDark ?: darkTheme
    val base = if (useDark) DarkColors else LightColors
    // One swatch changes every accent-bearing token at once. The tokens are all aliases of the
    // same idea, so changing only `ac` would leave Material buttons and selections on the old
    // colour while the rest of the screen moved.
    val colors = accent?.let { picked ->
        // A tint and its label cannot be the same colour: the outline buttons and the
        // tinted terminal keys draw one on the other, and a solid accent in both places
        // made their labels disappear. Keep the fill translucent, darken the label on
        // light pages, and pick black or white for text that sits on a solid accent.
        val link = if (useDark) picked else lerp(picked, Color.Black, 0.35f)
        val onAccent = if (picked.luminance() > 0.32f) Color(0xFF0B0F16) else Color.White
        base.copy(
            ac = picked,
            accent = picked,
            primary = picked,
            ab = picked,
            toolbarAccent = picked,
            link = link,
            primaryTint = picked.copy(alpha = if (useDark) 0.18f else 0.12f),
            af = onAccent,
            selection = picked.copy(alpha = if (useDark) 0.20f else 0.14f),
            glow = picked.copy(alpha = if (useDark) 0.10f else 0.06f),
            mock = base.mock.copy(
                accent = picked,
                onAccent = onAccent,
                selected = base.mock.selected,
            ),
        )
    } ?: base

    val view = LocalView.current
    val window = view.context.activityOrNull()?.window
    SideEffect {
        if (window != null) {
            window.statusBarColor = colors.mock.rail.toArgb()
            window.navigationBarColor = colors.bg.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    // Spec palette from CcTheme.kt (screens 01-57). Restyled screens read LocalCcColors;
    // LocalPageColors stays for the screens still on the old palette during the migration.
    CcTheme(darkTheme = useDark) {
    CompositionLocalProvider(
        LocalPageColors provides colors,
        // Compose tints the text cursor's drag handle and the selection with these. Left at their
        // defaults the handle came out in Material's own purple, a colour that appears nowhere in
        // this palette, which is what put a stray lavender shape in the middle of the code.
        LocalTextSelectionColors provides TextSelectionColors(
            backgroundColor = colors.textSelection,
            handleColor = colors.accent,
        ),
    ) {
        MaterialTheme(
            // primary and the surfaces are mapped from the spec's own tokens. primary was
            // previously never set, so every Material-drawn affordance fell back to its default.
            colorScheme = if (useDark) {
                androidx.compose.material3.darkColorScheme(
                    primary = colors.ac,
                    onPrimary = colors.af,
                    primaryContainer = colors.selection,
                    onPrimaryContainer = colors.tx,
                    background = colors.bg,
                    surface = colors.cd,
                    onBackground = colors.tx,
                    onSurface = colors.tx,
                    surfaceVariant = colors.panel2,
                    outline = colors.ln,
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    primary = colors.ac,
                    onPrimary = colors.af,
                    primaryContainer = colors.selection,
                    onPrimaryContainer = colors.tx,
                    background = colors.bg,
                    surface = colors.cd,
                    onBackground = colors.tx,
                    onSurface = colors.tx,
                    surfaceVariant = colors.panel2,
                    outline = colors.ln,
                )
            },
            typography = AppTypography,
            content = content,
        )
    }
    }
}

private fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
