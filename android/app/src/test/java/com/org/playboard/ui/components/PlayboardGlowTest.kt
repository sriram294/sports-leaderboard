package com.org.playboard.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.org.playboard.ui.theme.DarkPlayboardColors
import com.org.playboard.ui.theme.LightPlayboardColors
import com.org.playboard.ui.theme.PlayboardColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the ambient background glow. The glow is a draw-phase effect with no visible
 * logic, so the risk isn't that it breaks — it's that someone brightens it until text
 * stops being readable. These tests pin the contrast floor and the placement rules.
 */
class PlayboardGlowTest {

    // --- WCAG contrast helpers -------------------------------------------------

    /** Relative luminance per WCAG 2.1, on a 0f..1f channel scale. */
    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** WCAG contrast ratio between two opaque colors, 1.0..21.0. */
    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Source-over composite of a translucent [top] onto an opaque [bottom]. */
    private fun composite(top: Color, bottom: Color): Color {
        val a = top.alpha
        return Color(
            red = top.red * a + bottom.red * (1 - a),
            green = top.green * a + bottom.green * (1 - a),
            blue = top.blue * a + bottom.blue * (1 - a),
        )
    }

    /**
     * Both glows at full strength over [colors]' background, i.e. the brightest point of
     * each gradient — the worst case for text sitting on top of it.
     */
    private fun peakBackgrounds(colors: PlayboardColors) = listOf(
        "warm" to composite(colors.glowWarm, colors.background),
        "cool" to composite(colors.glowCool, colors.background),
    )

    // --- Contrast --------------------------------------------------------------

    @Test
    fun `text stays readable at the brightest point of every glow`() {
        // 4.5:1 is the WCAG AA floor for body text. textMuted on the light theme is the
        // binding constraint here — it starts at only ~4.9:1 on flat #FAFAFA, so the
        // light glow has very little headroom before it drops under the floor.
        listOf(DarkPlayboardColors, LightPlayboardColors).forEach { colors ->
            peakBackgrounds(colors).forEach { (glowName, background) ->
                listOf("textPrimary" to colors.textPrimary, "textMuted" to colors.textMuted)
                    .forEach { (textName, text) ->
                        val ratio = contrastRatio(text, background)
                        assertTrue(
                            "$textName over the $glowName glow is only ${"%.2f".format(ratio)}:1, below the 4.5:1 floor",
                            ratio >= 4.5,
                        )
                    }
            }
        }
    }

    @Test
    fun `glow alphas stay within the tuned band`() {
        // A tripwire against "just bumping it a bit": below 0.05 the effect is invisible,
        // and the light theme's muted text runs out of contrast headroom not far above
        // 0.18. The contrast test above is the real floor; this just keeps the values in
        // the range that was actually eyeballed on device.
        listOf(DarkPlayboardColors, LightPlayboardColors).forEach { colors ->
            listOf("glowWarm" to colors.glowWarm, "glowCool" to colors.glowCool).forEach { (name, glow) ->
                assertTrue(
                    "$name alpha ${glow.alpha} is outside the 0.05..0.18 band",
                    glow.alpha in 0.05f..0.18f,
                )
            }
        }
    }

    // --- Geometry --------------------------------------------------------------

    private val phone = Size(1080f, 2400f)

    @Test
    fun `warm glow sits behind the header`() {
        val warm = glowSpecs(phone, DarkPlayboardColors).first()
        assertTrue("warm glow should sit in the left half", warm.center.x < phone.width / 2)
        assertTrue("warm glow should be centered at or above the top edge", warm.center.y <= 0f)
    }

    @Test
    fun `cool glow sits low and to the right`() {
        val cool = glowSpecs(phone, DarkPlayboardColors)[1]
        assertTrue("cool glow should sit right of 90% width", cool.center.x > phone.width * 0.9f)
        assertTrue("cool glow should sit below 70% height", cool.center.y > phone.height * 0.7f)
    }

    @Test
    fun `radii scale with width so the share card matches the phone`() {
        // The share card renders at 460dp wide with wrap-content height, a very different
        // aspect ratio from a phone. Radii keyed off width keep the look consistent.
        val narrow = glowSpecs(Size(460f, 1800f), DarkPlayboardColors)
        val wide = glowSpecs(Size(920f, 1800f), DarkPlayboardColors)
        narrow.zip(wide).forEach { (n, w) ->
            assertEquals("radius should scale linearly with width", n.radius * 2f, w.radius, 0.01f)
        }
    }

    @Test
    fun `both glows are produced for every palette`() {
        listOf(DarkPlayboardColors, LightPlayboardColors).forEach { colors ->
            val specs = glowSpecs(phone, colors)
            assertEquals(2, specs.size)
            assertEquals(colors.glowWarm, specs[0].color)
            assertEquals(colors.glowCool, specs[1].color)
        }
    }
}
