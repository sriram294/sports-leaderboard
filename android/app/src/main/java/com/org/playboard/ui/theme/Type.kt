package com.org.playboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.org.playboard.R

/**
 * Bundled Paytone One (SIL Open Font License — see docs/licenses/PaytoneOne-OFL.txt).
 * The bold, fun display face used for the "layboard" login wordmark. It stays a
 * wordmark-only face; every other string in the app is [Manrope].
 */
val PaytoneOne = FontFamily(Font(R.font.paytone_one))

/**
 * Bundled Manrope (SIL Open Font License — see docs/licenses/Manrope-OFL.txt) — the
 * single UI face for every screen.
 *
 * Chosen because Playboard is a dense, small-text app (13sp is by far the most common
 * size in the codebase, then 14/11/12): Manrope's tall x-height and open apertures stay
 * legible at row size, and its rounded geometric skeleton is the text-weight sibling of
 * the Paytone One wordmark, so the brand and the UI read as one family.
 *
 * These are **static instances** cut from the upstream variable font, one file per
 * weight. That is deliberate: the variable font's `wght` axis defaults to 200
 * (ExtraLight) and Compose can only apply variation settings on API 26+, so shipping the
 * variable file would render the whole app in hairline ExtraLight on minSdk 24/25 devices.
 *
 * Manrope tops out at 800, so [FontWeight.Black] resolves to the ExtraBold instance.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/**
 * Tabular (fixed-width) figures. Manrope's default figures are proportional, which makes
 * a leaderboard column visibly jitter left and right from row to row as the digits
 * change. Applied to every style because nearly every screen stacks numbers — ratings,
 * win rates, scores, streaks — into columns.
 */
private const val TabularFigures = "tnum"

/** Applies the app's UI face and figure style to a Material style. */
private fun TextStyle.playboard(): TextStyle =
    copy(fontFamily = Manrope, fontFeatureSettings = TabularFigures)

private val Default = Typography()

/**
 * The app's Material 3 type scale, entirely in [Manrope].
 *
 * Sizes and line heights are Material's defaults except for the three styles the app had
 * already tuned — [Typography.displayLarge], [Typography.bodyLarge] and
 * [Typography.labelSmall] — whose metrics are preserved exactly so swapping the face does
 * not reflow existing screens.
 */
val Typography = Typography(
    // Heavy branded headline (e.g. "PLAYBOARD").
    displayLarge = Default.displayLarge.playboard().copy(
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = Default.displayMedium.playboard(),
    displaySmall = Default.displaySmall.playboard(),
    headlineLarge = Default.headlineLarge.playboard(),
    headlineMedium = Default.headlineMedium.playboard(),
    headlineSmall = Default.headlineSmall.playboard(),
    titleLarge = Default.titleLarge.playboard(),
    titleMedium = Default.titleMedium.playboard(),
    titleSmall = Default.titleSmall.playboard(),
    bodyLarge = Default.bodyLarge.playboard().copy(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = Default.bodyMedium.playboard(),
    bodySmall = Default.bodySmall.playboard(),
    labelLarge = Default.labelLarge.playboard(),
    labelMedium = Default.labelMedium.playboard(),
    // Small tracked uppercase labels — sub-headline tag, fine print, section eyebrows.
    labelSmall = Default.labelSmall.playboard().copy(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.sp,
    ),
)
