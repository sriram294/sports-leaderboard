package com.org.playboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import com.org.playboard.R
import com.org.playboard.ui.theme.PlayboardTheme

/**
 * The crown awarded for topping a month, with the month beneath it.
 *
 * The crown is a full-colour illustration, so it is drawn with no tint — unlike the app's
 * monochrome glyphs, which are tinted to the theme. Its gold reads as an award on both light
 * and dark backgrounds unaided, and tinting it to the brand lime would flatten it into
 * something that no longer looks like a prize.
 */
@Composable
fun MonthlyCrownBadge(
    monthLabel: String,
    modifier: Modifier = Modifier,
    crownSize: Dp = CROWN_SIZE,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_crown),
            contentDescription = null, // the month label beside it already names the award
            modifier = Modifier.size(crownSize),
        )
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textMuted,
        )
    }
}

/**
 * The source PNG is 100×100 in `drawable-nodpi`, so density scaling is off and 25.dp is
 * exactly 1:1 on an xxxhdpi device. Drawing it larger resamples up and visibly softens the
 * edges — a bigger treatment needs a bigger export, not a bigger size here.
 */
private val CROWN_SIZE = 25.dp
