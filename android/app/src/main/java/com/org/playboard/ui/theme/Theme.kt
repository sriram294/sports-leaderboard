package com.org.playboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provides the active [PlayboardColors] to the composition. Defaults to the
 * dark palette so previews and any composable rendered outside [PlayboardTheme]
 * fall back to the app's original look.
 */
val LocalPlayboardColors = staticCompositionLocalOf { DarkPlayboardColors }

/** Accessor for the app's semantic palette, e.g. `PlayboardTheme.colors.textMuted`. */
object PlayboardTheme {
    val colors: PlayboardColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPlayboardColors.current
}

private fun colorSchemeFor(colors: PlayboardColors, darkTheme: Boolean) =
    if (darkTheme) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
        )
    }

/**
 * Branded Playboard theme. Supports a dark (default) and a light palette,
 * selected by [darkTheme]; the design isn't Material You / dynamic-color.
 */
@Composable
fun PlayboardTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPlayboardColors else LightPlayboardColors
    CompositionLocalProvider(LocalPlayboardColors provides colors) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colors, darkTheme),
            typography = Typography,
            content = content,
        )
    }
}
