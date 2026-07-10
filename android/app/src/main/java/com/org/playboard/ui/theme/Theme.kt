package com.org.playboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PlayboardDarkColorScheme = darkColorScheme(
    primary = BrandLime,
    onPrimary = OnBrandLime,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
)

/** Dark-only, branded theme — the prototype design isn't adaptive to system light/dark or Material You. */
@Composable
fun PlayboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlayboardDarkColorScheme,
        typography = Typography,
        content = content
    )
}
