package com.org.playboard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.R
import com.org.playboard.ui.theme.PaytoneOne
import com.org.playboard.ui.theme.PlayboardTheme

/** Aspect ratio of [R.drawable.logo_playboard_racket] — keeps the racket undistorted. */
private const val RacketAspectRatio = 441f / 770f

/**
 * The Playboard wordmark: the racket logo stands in for the "P", with "layboard"
 * set in the bold Paytone One display face. The racket's handle reads as the
 * letter's stem, so the two pieces must stay adjacent and vertically centered.
 *
 * Used large on the login screen and small in the app header on every tab.
 */
@Composable
fun AppWordmark(
    modifier: Modifier = Modifier,
    logoHeight: Dp = 60.dp,
    fontSize: TextUnit = 46.sp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_playboard_racket),
            contentDescription = "Playboard",
            modifier = Modifier
                .height(logoHeight)
                .aspectRatio(RacketAspectRatio),
        )
        // Scales with the mark so the racket keeps hugging the "l" at any size.
        Spacer(modifier = Modifier.width(logoHeight * 0.07f))
        Text(
            text = "layboard",
            fontFamily = PaytoneOne,
            fontSize = fontSize,
            color = PlayboardTheme.colors.brand,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun AppWordmarkPreview() {
    PlayboardTheme {
        AppWordmark(logoHeight = 28.dp, fontSize = 22.sp)
    }
}
