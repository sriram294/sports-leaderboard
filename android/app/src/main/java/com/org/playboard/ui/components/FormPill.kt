package com.org.playboard.ui.components
import com.org.playboard.ui.theme.PlayboardTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

/**
 * One W/L result chip — a green "W" or a red "L". Shared by the Stats tab's
 * RECENT FORM card and the Board tab's pinned form bar.
 */
@Composable
fun FormPill(isWin: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isWin) PlayboardTheme.colors.statWin else PlayboardTheme.colors.statLoss),
    ) {
        Text(
            text = if (isWin) "W" else "L",
            color = PlayboardTheme.colors.onBrand,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
