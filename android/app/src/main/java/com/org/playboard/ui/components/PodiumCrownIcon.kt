package com.org.playboard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.org.playboard.R

/**
 * The crown that floats above the podium champion (#1), on both the live Board tab and the
 * shareable leaderboard image. Replaces the old `👑` emoji glyph, which rendered
 * inconsistently across device fonts and dropped out entirely when the share card was drawn
 * to an off-screen bitmap.
 *
 * A full-colour 3D illustration, so — like [MonthlyCrownIcon] — it is drawn untinted: its
 * gold reads as an award on both light and dark backgrounds, and tinting it to the brand
 * lime would flatten it into something that no longer looks like a prize.
 *
 * The caller sizes and positions it (the podium sits it on the champion's head, overlapping
 * the avatar's top edge). The source PNG is 256×256, comfortably above the pixels it occupies
 * at any podium size on an xxxhdpi screen, so it always downsamples (crisp) rather than
 * upsampling (soft).
 */
@Composable
fun PodiumCrownIcon(
    modifier: Modifier = Modifier,
    crownSize: Dp = CROWN_SIZE,
) {
    Image(
        painter = painterResource(R.drawable.ic_crown_3d),
        contentDescription = null, // the rank badge on the avatar already names the champion
        modifier = modifier.size(crownSize),
    )
}

private val CROWN_SIZE = 46.dp
