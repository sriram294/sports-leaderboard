package com.org.playboard.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Raw palette values. The app supports a dark (default) and a light theme;
// UI reads these through [PlayboardColors] via `PlayboardTheme.colors.*` rather
// than referencing the raw values directly, so a theme switch flips every
// screen at once. That includes the exported leaderboard share card, which is
// rendered inside a real PlayboardTheme and so follows the user's theme.
// ---------------------------------------------------------------------------

// Brand accent — the dark value's hex matches the backend's AvatarColorPicker
// palette (com.org.playboard.common.AvatarColorPicker), an intentional tie-in.
// Lime on white is illegible, so the light theme uses a darker lime that reads
// both as accent text on a light background and as a button fill (with white on it).
val DarkBrand = Color(0xFF9ADE28)
val DarkOnBrand = Color(0xFF0A0A0A)
val LightBrand = Color(0xFF4E8C0A)
val LightOnBrand = Color(0xFFFFFFFF)

// Surfaces
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF141414)
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)

// Ambient background glow — two low-opacity radial light bleeds painted over the
// flat [DarkBackground]/[LightBackground] base by PlayboardBackground. Alpha is
// baked into the constant so the whole effect is tunable from one place. These
// stay faint on purpose: the leaderboard is dense numeric content, and the light
// theme's muted text is only ~4.9:1 on its background before any tint is added.
val DarkGlowWarm = Color(0x269ADE28)
val DarkGlowCool = Color(0x1A5B8CFF)
// The light theme deliberately reuses the *bright* lime rather than LightBrand —
// the darker #4E8C0A reads as muddy olive when washed over near-white.
val LightGlowWarm = Color(0x2B9ADE28)
val LightGlowCool = Color(0x172563EB)

// Text
val DarkTextPrimary = Color(0xFFF5F5F5)
val DarkTextMuted = Color(0xFF9E9E9E)
val LightTextPrimary = Color(0xFF1A1A1A)
val LightTextMuted = Color(0xFF6B6B6B)

// Stat accents (leaderboard.pdf) — wins/losses columns and win-rate tiers. The
// light variants are darkened so they keep contrast against a white surface.
val DarkStatWin = Color(0xFF4ADE80)
val DarkStatLoss = Color(0xFFF87171)
val DarkWinRateMid = Color(0xFFFACC15)
val DarkWinRateLow = Color(0xFF60A5FA)
val LightStatWin = Color(0xFF16A34A)
val LightStatLoss = Color(0xFFDC2626)
val LightWinRateMid = Color(0xFFCA8A04)
val LightWinRateLow = Color(0xFF2563EB)

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
    statWin = LightStatWin,
    statLoss = LightStatLoss,
    winRateMid = LightWinRateMid,
    winRateLow = LightWinRateLow,
)
