package com.org.playboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.org.playboard.ui.theme.PlayboardTheme

/**
 * A row of small win/loss dots — the leaderboard's per-player form trend, oldest result
 * on the left. Renders nothing for an empty list.
 */
@Composable
fun FormDots(results: List<Boolean>, modifier: Modifier = Modifier, dotSize: Dp = 6.dp) {
    if (results.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        results.forEach { isWin ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (isWin) PlayboardTheme.colors.statWin else PlayboardTheme.colors.statLoss),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FormDotsPreview() {
    PlayboardTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            FormDots(results = listOf(true, false, true, true, false, true, true, true, false, true))
        }
    }
}
