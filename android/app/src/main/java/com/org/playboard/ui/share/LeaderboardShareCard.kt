package com.org.playboard.ui.share

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.R
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.avatarColor
import com.org.playboard.ui.theme.BackgroundDark
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.StatWinGreen
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary
import com.org.playboard.ui.theme.WinRateLowBlue
import com.org.playboard.ui.theme.WinRateMidAmber
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
    modifier: Modifier = Modifier,
    date: LocalDate = LocalDate.now(),
) {
    val podium = rankings.take(3)
    val rows = topRankings(rankings)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .padding(15.dp),
    ) {
        // Header — wordmark, group name, date.
        Text(
            text = "PLAYBOARD",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = BrandLime,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Leaderboard · ${date.format(DATE_FORMAT)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(24.dp))
        SharePodium(podium = podium)

        Spacer(modifier = Modifier.height(24.dp))
        ShareRankingsCard(rows = rows)
    }
}

@Composable
private fun SharePodium(podium: List<PlayerRanking>) {
    Text(text = "TOP PLAYERS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        SharePodiumSlot(entry = podium.getOrNull(1), isChampion = false, modifier = Modifier.weight(1f))
        SharePodiumSlot(entry = podium.getOrNull(0), isChampion = true, modifier = Modifier.weight(1.2f))
        SharePodiumSlot(entry = podium.getOrNull(2), isChampion = false, modifier = Modifier.weight(1f))
    }
}

/** One podium column; `entry == null` (fewer than 3 ranked players) leaves the slot empty. */
@Composable
private fun SharePodiumSlot(entry: PlayerRanking?, isChampion: Boolean, modifier: Modifier = Modifier) {
    if (entry == null) {
        Spacer(modifier = modifier)
        return
    }
    val color = avatarColor(entry.avatarColor)
    val avatarSize: Dp = if (isChampion) 84.dp else 60.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.padding(top = 11.dp)) {
                PlayerAvatar(
                    displayName = entry.displayName,
                    photoUrl = null,
                    avatarColorHex = entry.avatarColor,
                    size = avatarSize,
                )
            }
            val medalRes = when (entry.rank) {
                1 -> R.drawable.ic_podium_gold
                2 -> R.drawable.ic_podium_silver
                3 -> R.drawable.ic_podium_bronze
                else -> null
            }
            if (medalRes != null) {
                Image(
                    painter = painterResource(id = medalRes),
                    contentDescription = null,
                    modifier = Modifier.size(if (isChampion) 34.dp else 28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isChampion) color.copy(alpha = 0.08f) else SurfaceDark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .then(
                    if (isChampion) Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    else Modifier,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = "${entry.winRatePercent}%",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = if (isChampion) 34.sp else 24.sp,
                        lineHeight = if (isChampion) 36.sp else 26.sp,
                    ),
                    color = if (isChampion) color else TextPrimary,
                )
                Text(text = "WIN RATE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.wins}W · ${entry.losses}L",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
        }
    }
}

// Fixed numeric column widths so the header row and data rows line up. Kept compact so
// the flexible PLAYER column keeps enough room for full names.
private val GpColumnWidth = 32.dp
private val WinsColumnWidth = 26.dp
private val LossesColumnWidth = 26.dp
// Wider than the PF column it replaced: values carry a sign ("+135", "-104").
private val PointsDiffColumnWidth = 46.dp
private val WinRateColumnWidth = 52.dp

@Composable
private fun ShareRankingsCard(rows: List<PlayerRanking>) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RANKINGS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                ShareHeaderLabel("#", Modifier.width(28.dp))
                ShareHeaderLabel("PLAYER", Modifier.weight(1f))
                ShareHeaderLabel("GP", Modifier.width(GpColumnWidth), TextAlign.End)
                ShareHeaderLabel("W", Modifier.width(WinsColumnWidth), TextAlign.End)
                ShareHeaderLabel("L", Modifier.width(LossesColumnWidth), TextAlign.End)
                ShareHeaderLabel("DIFF", Modifier.width(PointsDiffColumnWidth), TextAlign.End)
                ShareHeaderLabel("WIN%", Modifier.width(WinRateColumnWidth), TextAlign.End)
            }
            rows.forEach { row ->
                HorizontalDivider(color = TextMuted.copy(alpha = 0.12f))
                ShareRankingRow(entry = row)
            }
        }
    }
}

@Composable
private fun ShareHeaderLabel(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = TextMuted,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun ShareRankingRow(entry: PlayerRanking) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Text(
            text = entry.rank.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = rankColor(entry.rank),
            modifier = Modifier.width(22.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            PlayerAvatar(
                displayName = entry.displayName,
                photoUrl = null,
                avatarColorHex = entry.avatarColor,
                size = 32.dp,
            )
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        ShareStatCell(entry.gamesPlayed.toString(), TextPrimary, GpColumnWidth)
        ShareStatCell(entry.wins.toString(), StatWinGreen, WinsColumnWidth)
        ShareStatCell(entry.losses.toString(), StatLossRed, LossesColumnWidth)
        ShareStatCell(entry.pointsDiffLabel, pointsDiffColor(entry.pointsDiff), PointsDiffColumnWidth)
        ShareStatCell("${entry.winRatePercent}%", winRateColor(entry.winRatePercent), WinRateColumnWidth, FontWeight.Bold)
    }
}

@Composable
private fun ShareStatCell(text: String, color: Color, width: Dp, fontWeight: FontWeight = FontWeight.Medium) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
        fontWeight = fontWeight,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.width(width),
    )
}

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> BrandLime
    2 -> TextPrimary
    3 -> WinRateMidAmber
    else -> TextMuted
}

private fun winRateColor(percent: Int): Color = when {
    percent >= 50 -> BrandLime
    percent >= 25 -> WinRateMidAmber
    else -> WinRateLowBlue
}

// Matches the W/L columns: outscoring opponents reads green, being outscored red.
private fun pointsDiffColor(diff: Int): Color = when {
    diff > 0 -> StatWinGreen
    diff < 0 -> StatLossRed
    else -> TextMuted
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

// deenesh and Mani are the tiebreak case: both 50%, and Mani has the higher PF but
// the worse difference, so he ranks below.
private val previewRankings = listOf(
    PlayerRanking(1, "u1", "Sriram", null, "#F59E0B", 6, 5, 1, 122, 95, 0.83),
    PlayerRanking(2, "u2", "deenesh", null, "#3DB4FF", 6, 3, 3, 107, 102, 0.50),
    PlayerRanking(3, "u3", "Mani", null, "#9ADE28", 6, 3, 3, 108, 110, 0.50),
    PlayerRanking(4, "u4", "Dinesh K", null, "#C026D3", 7, 3, 4, 135, 140, 0.42),
    PlayerRanking(5, "u5", "Balaji", null, "#FB923C", 5, 2, 3, 93, 100, 0.40),
    PlayerRanking(6, "u6", "Pari", null, "#8A6CFF", 6, 2, 4, 101, 112, 0.33),
)

@Preview(widthDp = 380, heightDp = 900, showBackground = true)
@Composable
private fun LeaderboardShareCardPreview() {
    PlayboardTheme {
        LeaderboardShareCard(
            group = Group(id = "g1", name = "Old Monk Badminton", avatarColor = "#9ADE28", memberCount = 8, matchCount = 20, myRole = "owner"),
            rankings = previewRankings,
            date = LocalDate.of(2026, 7, 14),
        )
    }
}
