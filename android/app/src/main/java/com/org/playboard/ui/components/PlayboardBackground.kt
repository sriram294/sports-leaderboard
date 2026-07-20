package com.org.playboard.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.org.playboard.ui.theme.PlayboardColors
import com.org.playboard.ui.theme.PlayboardTheme

/**
 * One radial light bleed: where it sits, how far it reaches, and its (alpha-baked) color.
 * Extracted from the drawing code so the placement rules can be unit tested.
 */
internal data class GlowSpec(val center: Offset, val radius: Float, val color: Color)

/**
 * Places the app's two ambient glows for a surface of [size].
 *
 * Centers are expressed as per-axis fractions and radii as fractions of *width*, so the
 * same rules scale from a phone screen to the much taller, wrap-content share card
 * without a second code path.
 */
internal fun glowSpecs(size: Size, colors: PlayboardColors): List<GlowSpec> = listOf(
    // Warm: centered just above the top edge and left of center, so the brightest part
    // falls behind the wordmark and group switcher and has decayed by the rankings card.
    GlowSpec(
        center = Offset(size.width * 0.18f, -size.height * 0.04f),
        radius = size.width * 1.15f,
        color = colors.glowWarm,
    ),
    // Cool: centered off the right edge and low, so only its inner shoulder bleeds into
    // the bottom of the screen behind the nav bar.
    GlowSpec(
        center = Offset(size.width * 1.06f, size.height * 0.84f),
        radius = size.width * 0.95f,
        color = colors.glowCool,
    ),
)

/**
 * Three-stop falloff. A plain two-stop `[color, Transparent]` radial fades linearly and
 * reads as a visible disc; pulling the midpoint down to 30% alpha gives the soft knee of
 * a real light bleed.
 */
private fun GlowSpec.brush(): Brush = Brush.radialGradient(
    0f to color,
    0.55f to color.copy(alpha = color.alpha * 0.3f),
    1f to Color.Transparent,
    center = center,
    radius = radius,
)

/**
 * Paints the app's ambient background: the opaque base color plus two low-opacity radial
 * light bleeds.
 *
 * Uses `drawWithCache` rather than `drawBehind` or `background(brush)` — the brushes need
 * the layout size to place their centers, and this rebuilds them only when the size or
 * palette changes rather than on every frame. Drawing happens in the draw phase only, so
 * a scrolling list on top of this never invalidates it.
 */
fun Modifier.playboardGlow(colors: PlayboardColors): Modifier = this.drawWithCache {
    val specs = glowSpecs(size, colors)
    val brushes = specs.map { it to it.brush() }
    onDrawBehind {
        drawRect(colors.background)
        brushes.forEach { (spec, brush) ->
            // Bound each glow to its own box instead of the whole viewport; the shader
            // only has to cover where the gradient is actually visible.
            val left = (spec.center.x - spec.radius).coerceAtLeast(0f)
            val top = (spec.center.y - spec.radius).coerceAtLeast(0f)
            val right = (spec.center.x + spec.radius).coerceAtMost(size.width)
            val bottom = (spec.center.y + spec.radius).coerceAtMost(size.height)
            if (right > left && bottom > top) {
                drawRect(
                    brush = brush,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
            }
        }
    }
}

/**
 * Convenience wrapper that fills the available space with [playboardGlow] and hosts
 * [content] on top of it. Prefer the [playboardGlow] modifier directly where the caller
 * already has a suitable root container.
 */
@Composable
fun PlayboardBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().playboardGlow(PlayboardTheme.colors), content = content)
}

@Preview
@Composable
private fun PlayboardBackgroundDarkPreview() {
    PlayboardTheme(darkTheme = true) { PlayboardBackground {} }
}

@Preview
@Composable
private fun PlayboardBackgroundLightPreview() {
    PlayboardTheme(darkTheme = false) { PlayboardBackground {} }
}
