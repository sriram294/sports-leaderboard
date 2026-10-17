package com.org.playboard.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Raw palette values. The app supports a dark (default) and a light theme;
// UI reads these through [PlayboardColors] via `PlayboardTheme.colors.*` rather
// than referencing the raw values directly, so a theme switch flips every
// screen at once. That includes the exported leaderboard share card, which is
// rendered inside a real PlayboardTheme and so follows the user's theme.
// ---------------------------------------------------------------------------

// Playboard's dashboard palette. The hues come from the supplied visual direction:
// lime drives achievement and the primary action, wisteria supplies the cool UI
// layer, Persian orange flags losses and warnings, and blue-black keeps cards calm.
// The light palette deliberately remains tinted rather than reverting to generic white.
val DarkBrand = Color(0xFFDDDE68)
val DarkOnBrand = Color(0xFF2B2D3B)
val LightBrand = Color(0xFF676386)
val LightOnBrand = Color(0xFFFFFFFF)

// Surfaces — a layered midnight dashboard rather than a flat black canvas.
val DarkBackground = Color(0xFF1F212E)
val DarkSurface = Color(0xFF36394D)
val LightBackground = Color(0xFFF3F2FA)
val LightSurface = Color(0xFFFFFFFF)

// Ambient background glow — two low-opacity radial light bleeds painted over the
// flat [DarkBackground]/[LightBackground] base by PlayboardBackground. Alpha is
// baked into the constant so the whole effect is tunable from one place. These
// stay faint on purpose: the leaderboard is dense numeric content, and the light
// theme's muted text is only ~4.9:1 on its background before any tint is added.
val DarkGlowWarm = Color(0x26A5B2EB)
val DarkGlowCool = Color(0x26676386)
val LightGlowWarm = Color(0x29A5B2EB)
val LightGlowCool = Color(0x1F676386)

// Text
val DarkTextPrimary = Color(0xFFF8F8FC)
val DarkTextMuted = Color(0xFFC5C7D6)
val LightTextPrimary = Color(0xFF2B2D3B)
val LightTextMuted = Color(0xFF676386)

// Stat accents (leaderboard.pdf) — wins/losses columns and win-rate tiers. The
// light variants are darkened so they keep contrast against a white surface.
val DarkStatWin = Color(0xFF55C879)
val DarkStatLoss = Color(0xFFE76670)
val DarkWinRateMid = Color(0xFFA5B2EB)
val DarkWinRateLow = Color(0xFF8FD3E8)
val LightStatWin = Color(0xFF238446)
val LightStatLoss = Color(0xFFBF3F49)
val LightWinRateMid = Color(0xFF676FAD)
val LightWinRateLow = Color(0xFF367890)

/**
 * The app's semantic color palette. UI reads these via `PlayboardTheme.colors`;
 * [DarkPlayboardColors] / [LightPlayboardColors] supply the per-theme values.
 * Slots map 1:1 to the colors the screens actually use, including the semantic
 * stat/win-rate accents that don't fit Material's [androidx.compose.material3.ColorScheme] roles.
 */
data class PlayboardColors(
    val background: Color,
    val surface: Color,
    /** Warm brand-tinted light bleed drawn behind the app header. Alpha is baked in. */
    val glowWarm: Color,
    /** Cooler counter-bleed drawn low and to the right. Alpha is baked in. */
    val glowCool: Color,
    val textPrimary: Color,
    val textMuted: Color,
    /** Brand accent — used both as a fill and as accent text/icons on [background]. */
    val brand: Color,
    /** Content color that sits on top of a [brand] fill. */
    val onBrand: Color,
    /** Playful card fills, kept separate from semantic win/loss state. */
    val accentWisteria: Color,
    val accentOrange: Color,
    /** Dark text/icons that sit on the bright accent-card fills. */
    val onAccent: Color,
    val statWin: Color,
    val statLoss: Color,
    val winRateMid: Color,
    val winRateLow: Color,
)

val DarkPlayboardColors = PlayboardColors(
    background = DarkBackground,
    surface = DarkSurface,
    glowWarm = DarkGlowWarm,
    glowCool = DarkGlowCool,
    textPrimary = DarkTextPrimary,
    textMuted = DarkTextMuted,
    brand = DarkBrand,
    onBrand = DarkOnBrand,
    accentWisteria = Color(0xFFA5B2EB),
    accentOrange = Color(0xFFDA935D),
    onAccent = DarkBackground,
    statWin = DarkStatWin,
    statLoss = DarkStatLoss,
    winRateMid = DarkWinRateMid,
    winRateLow = DarkWinRateLow,
)

val LightPlayboardColors = PlayboardColors(
    background = LightBackground,
    surface = LightSurface,
    glowWarm = LightGlowWarm,
    glowCool = LightGlowCool,
    textPrimary = LightTextPrimary,
    textMuted = LightTextMuted,
    brand = LightBrand,
    onBrand = LightOnBrand,
    accentWisteria = Color(0xFFA5B2EB),
    accentOrange = Color(0xFFDA935D),
    onAccent = DarkBackground,
    statWin = LightStatWin,
    statLoss = LightStatLoss,
    winRateMid = LightWinRateMid,
    winRateLow = LightWinRateLow,
)
