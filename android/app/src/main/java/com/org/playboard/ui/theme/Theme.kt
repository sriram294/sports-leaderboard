package com.org.playboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
            primaryContainer = colors.brand.copy(alpha = 0.16f),
            onPrimaryContainer = colors.brand,
            secondary = colors.winRateMid,
            onSecondary = colors.background,
            secondaryContainer = colors.surface,
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.statLoss,
            onTertiary = colors.background,
            error = colors.statLoss,
            onError = colors.background,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.background,
            onSurfaceVariant = colors.textMuted,
            outline = colors.textMuted.copy(alpha = 0.45f),
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brand.copy(alpha = 0.14f),
            onPrimaryContainer = colors.brand,
            secondary = colors.winRateMid,
            onSecondary = colors.onBrand,
            secondaryContainer = colors.winRateMid.copy(alpha = 0.16f),
            onSecondaryContainer = colors.textPrimary,
            tertiary = colors.statLoss,
            onTertiary = colors.onBrand,
            error = colors.statLoss,
            onError = colors.onBrand,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.background,
            onSurfaceVariant = colors.textMuted,
            outline = colors.textMuted.copy(alpha = 0.45f),
        )
    }

/** Soft, card-forward geometry shared by Material components that do not set a shape. */
private val PlayboardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
            shapes = PlayboardShapes,
            content = content,
        )
    }
}
