package com.org.playboard.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.components.FormPill
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted

/**
 * The pinned "YOUR FORM" bar (docs/requirements/02-board-leaderboard.md): the
 * signed-in user's most recent results, newest first, floating over the
 * leaderboard. Display only — deliberately not clickable.
 */
@Composable
internal fun FormBar(results: List<Boolean>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        // On the near-black background the shadow is barely visible, so the border
        // is what actually makes the bar read as floating over the content.
        border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.16f)),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = "YOUR FORM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Last ${results.size} ${if (results.size == 1) "match" else "matches"}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            // newest-first list, laid out left-to-right → newest pill on the left.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { FormPill(isWin = it) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FormBarPreview() {
    PlayboardTheme {
        FormBar(results = listOf(true, true, false, true, false))
    }
}
