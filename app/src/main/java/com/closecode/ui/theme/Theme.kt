package com.closecode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ClosecodeTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalPageColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                androidx.compose.material3.darkColorScheme(
                    background = colors.bg,
                    surface = colors.cd,
                    onBackground = colors.tx,
                    onSurface = colors.tx,
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    background = colors.bg,
                    surface = colors.cd,
                    onBackground = colors.tx,
                    onSurface = colors.tx,
                )
            },
            typography = AppTypography,
            content = content,
        )
    }
}