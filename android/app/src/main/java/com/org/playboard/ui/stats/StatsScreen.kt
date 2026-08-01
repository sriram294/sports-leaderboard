package com.org.playboard.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.MonthlyTrophy
import com.org.playboard.data.model.PartnerPairing
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.MonthlyCrownIcon
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.PlayboardBackground
import com.org.playboard.ui.theme.PlayboardTheme

/** Stats/Insights tab — group analytics dashboard (docs/requirements/06-stats.md). */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    StatsContent(
        state = uiState,
        onRetry = viewModel::retry,
        onPullRefresh = viewModel::onPullRefresh,
        onPartnersToggled = viewModel::onPartnersToggled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsContent(
    state: StatsUiState,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    onPartnersToggled: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
    ) {
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator(color = PlayboardTheme.colors.brand) }
            state.hasLoadFailed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load insights.", color = PlayboardTheme.colors.textMuted)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onRetry) { Text("Retry", color = PlayboardTheme.colors.brand) }
                }
            }
            state.noGroup -> CenteredMessage("Create or join a group to see insights.")
            !state.hasMatches -> CenteredMessage("Play some matches to see insights.")
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onPullRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    state.records?.let { item { RecordsCard(records = it) } }
                    if (state.monthlyWinners.isNotEmpty()) {
                        item { MonthlyWinnersCard(winners = state.monthlyWinners) }
                    }
                    item {
                        PartnerPairsCard(
                            expanded = state.partnersExpanded,
                            pairs = state.partnerPairs,
                            isLoading = state.isPartnersLoading,
                            loadFailed = state.partnersLoadFailed,
                            onToggle = onPartnersToggled,
                        )
                    }
                    state.biggestWin?.let { item { BiggestWinCard(biggestWin = it) } }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

// ---- Records -------------------------------------------------------------------

@Composable
private fun RecordsCard(records: Records) {
    InsightCard {
        SectionLabel("RECORDS")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = records.totalMatches.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, lineHeight = 34.sp),
                fontWeight = FontWeight.Bold,
                color = PlayboardTheme.colors.brand,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (records.totalMatches == 1) "match played" else "matches played",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = PlayboardTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        records.winLeader?.let {
            Spacer(Modifier.height(14.dp))
            LeaderRow("WIN LEADER", it, "${it.winRatePercent}%")
        }
        records.mostPoints?.let {
            Spacer(Modifier.height(10.dp))
            LeaderRow("MOST POINTS", it, it.pointsFor.toString())
        }
        records.mostActive?.let {
            Spacer(Modifier.height(10.dp))
            LeaderRow("MOST ACTIVE", it, "${it.gamesPlayed} games")
        }
        records.longestStreak?.let {
            Spacer(Modifier.height(10.dp))
            LeaderRow("LONGEST STREAK", it, "${it.bestStreak} in a row")
        }
        records.currentStreak?.let {
            Spacer(Modifier.height(10.dp))
            LeaderRow("HOT STREAK", it, "${it.currentStreak} in a row")
        }
    }
}

@Composable
private fun LeaderRow(label: String, player: PlayerRanking, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PlayboardTheme.colors.textMuted,
            modifier = Modifier.width(96.dp),
        )
        PlayerAvatar(
            displayName = player.displayName,
            photoUrl = player.photoUrl,
            avatarId = player.avatarId,
            avatarColorHex = player.avatarColor,
            size = 30.dp,
        )
        Text(
            text = player.displayName,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = PlayboardTheme.colors.brand,
        )
    }
}

// ---- Monthly winners -----------------------------------------------------------

/**
 * The group's roll of honour: who topped each completed month, newest first.
 *
 * Sits directly under RECORDS because it is the same kind of thing — a standing achievement
 * rather than a live figure. Months nobody qualified for are absent from the payload, so the
 * list can skip a month; that gap is honest and needs no placeholder.
 */
@Composable
private fun MonthlyWinnersCard(winners: List<MonthlyTrophy>) {
    InsightCard {
        SectionLabel("MONTHLY WINNERS")
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            winners.forEach { winner -> MonthlyWinnerTile(winner) }
        }
    }
}

/**
 * One winner: their avatar wearing the crown, name and month beneath.
 *
 * The crown sits inside the tile's bounds rather than hanging off the avatar, so the row can
 * scroll without the first and last crowns being sheared off at the card's padding.
 */
@Composable
private fun MonthlyWinnerTile(winner: MonthlyTrophy) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(WINNER_TILE_WIDTH),
    ) {
        Box {
            PlayerAvatar(
                displayName = winner.displayName,
                photoUrl = winner.photoUrl,
                avatarId = winner.avatarId,
                avatarColorHex = winner.avatarColor,
                size = WINNER_AVATAR_SIZE,
                // Leaves the top-right corner clear for the crown to overlap into.
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            )
            MonthlyCrownIcon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .rotate(20f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = winner.displayName,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = winner.shortMonthLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private val WINNER_AVATAR_SIZE = 60.dp

/** Avatar plus the crown's corner offset, with room either side for a rotated crown to bleed. */
private val WINNER_TILE_WIDTH = 76.dp

// ---- Partners --------------------------------------------------------------

/**
 * Collapsed by default — every pair's games-together count is only fetched from
 * the server once the user taps to expand, not eagerly with the rest of the page.
 */
@Composable
private fun PartnerPairsCard(
    expanded: Boolean,
    pairs: List<PartnerPairing>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onToggle: () -> Unit,
) {
    InsightCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
        ) {
            SectionLabel("PARTNERS")
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
            )
        }
        if (!expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap to see who's partnered with whom",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
            )
        } else if (isLoading) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = PlayboardTheme.colors.brand, modifier = Modifier.height(20.dp))
        } else if (loadFailed) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Couldn't load partners. Tap to retry.",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
            )
        } else if (pairs.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No completed matches with a teammate yet.",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
            )
        } else {
            Spacer(Modifier.height(12.dp))
            pairs.forEachIndexed { index, pair ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                PartnerPairRow(pair)
            }
        }
    }
}

@Composable
private fun PartnerPairRow(pair: PartnerPairing) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row {
            PlayerAvatar(
                displayName = pair.player1DisplayName,
                photoUrl = null,
                avatarId = pair.player1AvatarId,
                avatarColorHex = pair.player1AvatarColor,
                size = 44.dp,
            )
            Spacer(Modifier.width(8.dp))
            PlayerAvatar(
                displayName = pair.player2DisplayName,
                photoUrl = null,
                avatarId = pair.player2AvatarId,
                avatarColorHex = pair.player2AvatarColor,
                size = 44.dp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${pair.player1DisplayName} & ${pair.player2DisplayName}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.SemiBold,
                color = PlayboardTheme.colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = "${pair.winsTogether}W / ${pair.gamesTogether} games together",
                style = MaterialTheme.typography.labelSmall,
                color = PlayboardTheme.colors.textMuted,
            )
        }
        Text(
            text = "${pair.winRatePercent}%",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 22.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.Bold,
            color = PlayboardTheme.colors.brand,
        )
    }
}

// ---- Biggest win ---------------------------------------------------------------

@Composable
private fun BiggestWinCard(biggestWin: BiggestWin) {
    val match = biggestWin.match
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("BIGGEST WIN · recent")
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlayboardTheme.colors.brand)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "+${biggestWin.margin} pts",
                    color = PlayboardTheme.colors.onBrand,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TeamLine(team = match.team(1), isWinner = match.winningTeamNo == 1)
        Spacer(Modifier.height(6.dp))
        TeamLine(team = match.team(2), isWinner = match.winningTeamNo == 2)
        if (match.sets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = match.sets.joinToString(", ") { "${it.team1Score}-${it.team2Score}" },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = PlayboardTheme.colors.brand,
            )
        }
    }
}

@Composable
private fun TeamLine(team: MatchTeam?, isWinner: Boolean) {
    if (team == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isWinner) "W" else " ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PlayboardTheme.colors.brand,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = team.players.joinToString(" & ") { it.displayName },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isWinner) PlayboardTheme.colors.textPrimary else PlayboardTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

// ---- Shared bits ---------------------------------------------------------------

@Composable
private fun InsightCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(text: String) {
    CenteredBox {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = PlayboardTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

// ---- Previews ------------------------------------------------------------------

private fun previewRanking(
    rank: Int,
    id: String,
    name: String,
    gp: Int,
    wins: Int,
    pf: Int,
    wr: Double,
    currentStreak: Int = 0,
    bestStreak: Int = 0,
    pa: Int = 0,
) = PlayerRanking(rank, id, name, null, null, "#9ADE28", gp, wins, gp - wins, pf, pa, wr, currentStreak, bestStreak)

private val previewState = StatsUiState(
    isLoading = false,
    hasMatches = true,
    groupName = "Saturday Smashers",
    records = Records(
        totalMatches = 12,
        winLeader = previewRanking(1, "u1", "Priya", 6, 6, 252, 1.0, currentStreak = 6, bestStreak = 6),
        mostPoints = previewRanking(3, "u3", "Raj", 8, 4, 315, 0.5),
        mostActive = previewRanking(3, "u3", "Raj", 8, 4, 315, 0.5),
        longestStreak = previewRanking(1, "u1", "Priya", 6, 6, 252, 1.0, currentStreak = 6, bestStreak = 6),
        currentStreak = previewRanking(1, "u1", "Priya", 6, 6, 252, 1.0, currentStreak = 6, bestStreak = 6),
    ),
    partnersExpanded = true,
    partnerPairs = listOf(
        PartnerPairing(
            player1Id = "u1", player1DisplayName = "Priya", player1AvatarId = null, player1AvatarColor = "#FF3D8A",
            player2Id = "u2", player2DisplayName = "Dev", player2AvatarId = null, player2AvatarColor = "#3DB4FF",
            gamesTogether = 4, winsTogether = 4, winRate = 1.0,
        ),
        PartnerPairing(
            player1Id = "u3", player1DisplayName = "Raj", player1AvatarId = null, player1AvatarColor = "#9ADE28",
            player2Id = "u4", player2DisplayName = "Kiran", player2AvatarId = null, player2AvatarColor = "#EAC72B",
            gamesTogether = 2, winsTogether = 0, winRate = 0.0,
        ),
    ),
    biggestWin = BiggestWin(
        match = Match(
            id = "m1",
            playedAt = java.time.Instant.parse("2026-07-09T06:58:00Z"),
            teams = listOf(
                MatchTeam(1, true, listOf(MatchPlayer("u1", "Priya", "#FF3D8A", null, null), MatchPlayer("u2", "Dev", "#3DB4FF", null, null))),
                MatchTeam(2, false, listOf(MatchPlayer("u3", "Raj", "#9ADE28", null, null), MatchPlayer("u4", "Kiran", "#EAC72B", null, null))),
            ),
            sets = listOf(MatchSet(1, 21, 4), MatchSet(2, 21, 9)),
        ),
        margin = 29,
    ),
    // Different winners across months, and a deliberate gap at May — a month nobody
    // eligible played is absent from the payload rather than rendered as a blank row.
    monthlyWinners = listOf(
        MonthlyTrophy(
            month = java.time.YearMonth.of(2026, 7),
            userId = "u1",
            displayName = "Priya",
            photoUrl = null,
            avatarId = null,
            avatarColor = "#FF3D8A",
            rating = 64.8,
            gamesPlayed = 20,
            wins = 15,
        ),
        MonthlyTrophy(
            month = java.time.YearMonth.of(2026, 6),
            userId = "u3",
            displayName = "Raj",
            photoUrl = null,
            avatarId = null,
            avatarColor = "#9ADE28",
            rating = 57.1,
            gamesPlayed = 18,
            wins = 11,
        ),
        MonthlyTrophy(
            month = java.time.YearMonth.of(2026, 4),
            userId = "u2",
            displayName = "Deenesh Dhadha",
            photoUrl = null,
            avatarId = null,
            avatarColor = "#3DB4FF",
            rating = 55.3,
            gamesPlayed = 16,
            wins = 10,
        ),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 980)
@Composable
private fun StatsContentPreview() {
    PlayboardTheme {
        PlayboardBackground {
            StatsContent(state = previewState, onRetry = {}, onPullRefresh = {}, onPartnersToggled = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 400)
@Composable
private fun StatsEmptyPreview() {
    PlayboardTheme {
        PlayboardBackground {
            StatsContent(
                state = StatsUiState(isLoading = false, hasMatches = false, groupName = "New Crew"),
                onRetry = {},
                onPullRefresh = {},
                onPartnersToggled = {},
            )
        }
    }
}
