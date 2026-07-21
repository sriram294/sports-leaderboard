package com.org.playboard.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.theme.PlayboardTheme

/**
 * The leaderboard table, shared by the Board screen and the exported share image.
 *
 * <p>Rows are two lines — name above a muted summary — rather than the seven fixed-width
 * numeric columns this replaced. That layout was out of room: on a 360dp phone the numeric
 * columns left roughly 56dp for the name once the avatar was subtracted, and names clipped.
 * Two lines are also width-elastic, which is what lets the board (~340dp) and the share
 * card (460dp) finally share one implementation instead of two copies that had already
 * drifted apart.
 */

/** Width of the rank gutter, shared by header and rows so they line up. */
private val RankColumnWidth = 24.dp

/** Width of the right-hand metric column. Fits "100.0" and "prov". */
private val MetricColumnWidth = 62.dp

/** What the big right-hand number shows, and what the table is sorted by. */
enum class RankingSortMetric(val label: String) {
    RATING("RATING"),
    WIN_RATE("WIN%"),
    GAMES("GAMES"),
    POINTS_DIFF("DIFF");

    /** The next metric in the cycle — the header is a single tappable control, not a row of them. */
    fun next(): RankingSortMetric = entries[(ordinal + 1) % entries.size]
}

/**
 * Slim header: a rank gutter, the player column, and the tappable metric label.
 *
 * @param onMetricTap `null` renders a static label with no caret and no ripple — the share
 *   card, where nothing is interactive.
 */
@Composable
fun LeaderboardHeaderRow(
    metric: RankingSortMetric,
    onMetricTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        HeaderLabel(text = "#", modifier = Modifier.width(RankColumnWidth))
        HeaderLabel(text = "PLAYER", modifier = Modifier.weight(1f))
        val metricModifier = if (onMetricTap != null) {
            Modifier
                .width(MetricColumnWidth)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onMetricTap)
        } else {
            Modifier.width(MetricColumnWidth)
        }
        HeaderLabel(
            text = if (onMetricTap != null) "${metric.label} ▾" else metric.label,
            color = PlayboardTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = metricModifier,
        )
    }
}

/**
 * One player's row.
 *
 * @param minGamesToRank the group's threshold, used to render "N more to rank".
 * @param showPhoto false for the share card — the offscreen renderer can't load network images.
 * @param onClick null makes the row inert (share card).
 */
@Composable
fun LeaderboardRow(
    entry: PlayerRanking,
    minGamesToRank: Int,
    metric: RankingSortMetric,
    modifier: Modifier = Modifier,
    showPhoto: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(
            // Provisional players aren't ranked, so they show a dash rather than a number
            // they haven't earned — the strongest signal that the block below is different.
            text = if (entry.provisional) "—" else entry.rank.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (entry.provisional) PlayboardTheme.colors.textMuted else rankColor(entry.rank),
            modifier = Modifier.width(RankColumnWidth),
        )
        PlayerAvatar(
            displayName = entry.displayName,
            photoUrl = if (showPhoto) entry.photoUrl else null,
            avatarId = entry.avatarId,
            avatarColorHex = entry.avatarColor,
            size = 32.dp,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.provisional) {
                    PlayboardTheme.colors.textMuted
                } else {
                    PlayboardTheme.colors.textPrimary
                },
                maxLines = 1,
                // The old board row had no overflow set and hard-clipped long names.
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.secondaryLine(minGamesToRank),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = PlayboardTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = entry.metricLabel(metric),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            color = entry.metricColor(metric),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(MetricColumnWidth),
        )
    }
}

/** The right-hand value for the active [metric]. */
private fun PlayerRanking.metricLabel(metric: RankingSortMetric): String = when {
    // Whatever the metric, an unranked player's headline is that they're unranked.
    provisional -> "prov"
    metric == RankingSortMetric.RATING -> ratingLabel
    metric == RankingSortMetric.WIN_RATE -> "$winRatePercent%"
    metric == RankingSortMetric.GAMES -> gamesPlayed.toString()
    else -> pointsDiffLabel
}

@Composable
private fun PlayerRanking.metricColor(metric: RankingSortMetric): Color = when {
    provisional -> PlayboardTheme.colors.textMuted
    metric == RankingSortMetric.WIN_RATE -> winRateColor(winRatePercent)
    metric == RankingSortMetric.POINTS_DIFF -> pointsDiffColor(pointsDiff)
    metric == RankingSortMetric.GAMES -> PlayboardTheme.colors.textPrimary
    rating == null -> winRateColor(winRatePercent) // pre-rating backend: showing win%
    else -> ratingColor(rating)
}

@Composable
private fun HeaderLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PlayboardTheme.colors.textMuted,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
internal fun rankColor(rank: Int): Color = when (rank) {
    1 -> PlayboardTheme.colors.brand
    2 -> PlayboardTheme.colors.textPrimary
    3 -> PlayboardTheme.colors.winRateMid
    else -> PlayboardTheme.colors.textMuted
}

@Composable
internal fun winRateColor(percent: Int): Color = when {
    percent >= 50 -> PlayboardTheme.colors.brand
    percent >= 25 -> PlayboardTheme.colors.winRateMid
    else -> PlayboardTheme.colors.winRateLow
}

/**
 * Rating tiers. Deliberately *not* win%'s 50/25 thresholds: a Wilson lower bound is always
 * below the raw rate, so a strong player in an active group lands around 40-45 and a
 * dominant one rarely clears 60. Reusing the win% scale would paint the entire board
 * mid-tier and never award the top colour to anyone.
 */
@Composable
internal fun ratingColor(rating: Double): Color = when {
    rating >= 40.0 -> PlayboardTheme.colors.brand
    rating >= 25.0 -> PlayboardTheme.colors.winRateMid
    else -> PlayboardTheme.colors.winRateLow
}

/** Matches the W/L colours: outscoring opponents reads green, being outscored red. */
@Composable
internal fun pointsDiffColor(diff: Int): Color = when {
    diff > 0 -> PlayboardTheme.colors.statWin
    diff < 0 -> PlayboardTheme.colors.statLoss
    else -> PlayboardTheme.colors.textMuted
}
