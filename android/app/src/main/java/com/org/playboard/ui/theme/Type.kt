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
 * The bold, fun display face used for the "layboard" login wordmark.
 */
val PaytoneOne = FontFamily(Font(R.font.paytone_one))

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    // Heavy branded headline (e.g. "PLAYBOARD") — no custom condensed
    // display font added yet, FontWeight.Black + letterSpacing approximates
    // the prototype's tight/heavy look with the system default family.
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp
    ),
    // Small tracked uppercase labels — sub-headline tag, fine print, etc.
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.sp
    )
)
