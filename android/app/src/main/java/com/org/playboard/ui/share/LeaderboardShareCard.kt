package com.org.playboard.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.board.LeaderboardHeaderRow
import com.org.playboard.ui.board.LeaderboardRow
import com.org.playboard.ui.board.RankingSortMetric
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.playboardGlow
import com.org.playboard.ui.components.avatarColor
import com.org.playboard.ui.theme.PlayboardTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A self-contained, branded snapshot of a group's leaderboard, built to be rendered
 * off-screen and captured to a PNG for sharing (see [renderAndShareLeaderboard]).
 *
 * Mirrors the Board tab's visual language (podium + rankings) but is non-interactive
 * and draws avatars as colored initials only ([PlayerAvatar] with `photoUrl = null`),
 * so it renders synchronously with no async image load to wait on during capture.
 */
@Composable
fun LeaderboardShareCard(
    group: Group,
    rankings: List<PlayerRanking>,
    minGamesToRank: Int,
    modifier: Modifier = Modifier,
    date: LocalDate = LocalDate.now(),
) {
    // Ranked players only, on both the podium and the table — a provisional player
    // shouldn't be crowned in an image that outlives the moment.
    val rows = topRankings(rankings)
    val podium = rows.take(3)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .playboardGlow(PlayboardTheme.colors)
            .padding(15.dp),
    ) {
        // Header — wordmark, group name, date.
        Text(
            text = "PLAYBOARD",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = PlayboardTheme.colors.brand,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineSmall,
            color = PlayboardTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Leaderboard · ${date.format(DATE_FORMAT)}",
            style = MaterialTheme.typography.bodyMedium,
            color = PlayboardTheme.colors.textMuted,
        )

        Spacer(modifier = Modifier.height(24.dp))
        SharePodium(podium = podium)

        Spacer(modifier = Modifier.height(24.dp))
        ShareRankingsCard(rows = rows, minGamesToRank = minGamesToRank)
    }
}

@Composable
private fun SharePodium(podium: List<PlayerRanking>) {
    Text(text = "TOP PLAYERS", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        SharePodiumSlot(entry = podium.getOrNull(1), isChampion = false, modifier = Modifier.weight(1f))
        SharePodiumSlot(entry = podium.getOrNull(0), isChampion = true, modifier = Modifier.weight(1.2f))
        SharePodiumSlot(entry = podium.getOrNull(2), isChampion = false, modifier = Modifier.weight(1f))
    }
}

/**
 * One podium column; `entry == null` (fewer than 3 ranked players) leaves the slot empty.
 *
 * Mirrors the Board tab's [com.org.playboard.ui.board.PodiumSlot]: the champion (#1) sits
 * center, larger and crowned; runners-up flank it a step lower. Each avatar wears a soft
 * glow + color ring with the rank number in a small badge at the bottom edge, and below it
 * only the name and win rate.
 */
@Composable
private fun SharePodiumSlot(entry: PlayerRanking?, isChampion: Boolean, modifier: Modifier = Modifier) {
    if (entry == null) {
        Spacer(modifier = modifier)
        return
    }
    val color = avatarColor(entry.avatarColor)
    val avatarSize: Dp = if (isChampion) 94.dp else 64.dp
    val badgeSize: Dp = if (isChampion) 28.dp else 22.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        // Crown floats above the champion; runners-up reserve the same height so all three
        // avatars still bottom-align into a clean podium tier.
        if (isChampion) {
            Text(text = "👑", fontSize = 22.sp)
        } else {
            Spacer(modifier = Modifier.height(26.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = badgeSize / 2)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = if (isChampion) 0.35f else 0.18f), Color.Transparent),
                        ),
                    )
                    .padding(6.dp),
            ) {
                PlayerAvatar(
                    displayName = entry.displayName,
                    photoUrl = null,
                    avatarColorHex = entry.avatarColor,
                    modifier = Modifier.border(
                        width = if (isChampion) 3.dp else 2.dp,
                        color = color,
                        shape = CircleShape,
                    ),
                    size = avatarSize,
                )
            }
            // Numbered rank badge, tucked at the bottom-center of the avatar. The outer ring
            // separates it from the avatar's color ring behind it. Slightly translucent so it
            // tints with the ambient glow behind it instead of showing as a flat patch.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(PlayboardTheme.colors.background.copy(alpha = 0.85f))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(color),
            ) {
                Text(
                    text = entry.rank.toString(),
                    color = PlayboardTheme.colors.onBrand,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (entry.rating != null) "${entry.ratingLabel} rating" else "${entry.winRatePercent}% win rate",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            color = if (isChampion) color else PlayboardTheme.colors.textMuted,
            fontWeight = if (isChampion) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShareRankingsCard(rows: List<PlayerRanking>, minGamesToRank: Int) {
    // Same table as the Board, via the shared component — these were two hand-maintained
    // copies that had already drifted (WIN% 52dp here vs 56dp there, and a rank header
    // 6dp wider than its own cells).
    Surface(shape = RoundedCornerShape(20.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RANKINGS", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
            Spacer(modifier = Modifier.height(12.dp))
            // Nothing is tappable in an exported image, so no caret and no ripple.
            LeaderboardHeaderRow(metric = RankingSortMetric.RATING, onMetricTap = null)
            rows.forEach { row ->
                HorizontalDivider(color = PlayboardTheme.colors.textMuted.copy(alpha = 0.12f))
                LeaderboardRow(
                    entry = row,
                    minGamesToRank = minGamesToRank,
                    metric = RankingSortMetric.RATING,
                    // The offscreen renderer can't fetch network images.
                    showPhoto = false,
                )
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

// deenesh and Mani are the tiebreak case: both 50%, and Mani has the higher PF but
// the worse difference, so he ranks below.
private val previewRankings = listOf(
    PlayerRanking(1, "u1", "Sriram", null, null, "#F59E0B", 6, 5, 1, 122, 95, 0.83),
    PlayerRanking(2, "u2", "deenesh", null, null, "#3DB4FF", 6, 3, 3, 107, 102, 0.50),
    PlayerRanking(3, "u3", "Mani", null, null, "#9ADE28", 6, 3, 3, 108, 110, 0.50),
    PlayerRanking(4, "u4", "Dinesh K", null, null, "#C026D3", 7, 3, 4, 135, 140, 0.42),
    PlayerRanking(5, "u5", "Balaji", null, null, "#FB923C", 5, 2, 3, 93, 100, 0.40),
    PlayerRanking(6, "u6", "Pari", null, null, "#8A6CFF", 6, 2, 4, 101, 112, 0.33),
)

// 460dp matches LeaderboardShareRenderer's real render width; the old 380 made the
// preview a misleadingly narrow proxy.
@Preview(widthDp = 460, heightDp = 900, showBackground = true)
@Composable
private fun LeaderboardShareCardPreview() {
    PlayboardTheme {
        LeaderboardShareCard(
            group = Group(id = "g1", name = "Old Monk Badminton", avatarColor = "#9ADE28", memberCount = 8, matchCount = 20, myRole = "owner"),
            rankings = previewRankings,
            minGamesToRank = 3,
            date = LocalDate.of(2026, 7, 14),
        )
    }
}
