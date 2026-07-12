package com.org.playboard.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.StatWinGreen
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary

/** Stats/Insights tab — group analytics dashboard (docs/requirements/06-stats.md). */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    StatsContent(
        state = uiState,
        onRetry = viewModel::retry,
        onPullRefresh = viewModel::onPullRefresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsContent(state: StatsUiState, onRetry: () -> Unit, onPullRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator(color = BrandLime) }
            state.hasLoadFailed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load insights.", color = TextMuted)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onRetry) { Text("Retry", color = BrandLime) }
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
                    state.bestPartnership?.let { item { BestPartnershipCard(partnership = it) } }
                    if (state.recentForm.isNotEmpty()) {
                        item { RecentFormCard(form = state.recentForm) }
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
                color = BrandLime,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (records.totalMatches == 1) "match played" else "matches played",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = TextMuted,
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
    }
}

@Composable
private fun LeaderRow(label: String, player: PlayerRanking, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(96.dp),
        )
        PlayerAvatar(
            displayName = player.displayName,
            photoUrl = player.photoUrl,
            avatarColorHex = player.avatarColor,
            size = 30.dp,
        )
        Text(
            text = player.displayName,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = BrandLime,
        )
    }
}

// ---- Best partnership ----------------------------------------------------------

@Composable
private fun BestPartnershipCard(partnership: BestPartnership) {
    InsightCard {
        SectionLabel("BEST PARTNERSHIP · recent")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row {
                PlayerAvatar(
                    displayName = partnership.player1.displayName,
                    photoUrl = partnership.player1.photoUrl,
                    avatarColorHex = partnership.player1.avatarColor,
                    size = 44.dp,
                )
                Spacer(Modifier.width(8.dp))
                PlayerAvatar(
                    displayName = partnership.player2.displayName,
                    photoUrl = partnership.player2.photoUrl,
                    avatarColorHex = partnership.player2.avatarColor,
                    size = 44.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${partnership.player1.displayName} & ${partnership.player2.displayName}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = "${partnership.winsTogether}W / ${partnership.gamesTogether} games together",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Text(
                text = "${partnership.winRatePercent}%",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 22.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.Bold,
                color = BrandLime,
            )
        }
    }
}

// ---- Recent form ---------------------------------------------------------------

@Composable
private fun RecentFormCard(form: List<PlayerForm>) {
    InsightCard {
        SectionLabel("RECENT FORM · recent")
        Spacer(Modifier.height(4.dp))
        form.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                PlayerAvatar(
                    displayName = row.player.displayName,
                    photoUrl = row.player.photoUrl,
                    avatarColorHex = row.player.avatarColor,
                    size = 30.dp,
                )
                Text(
                    text = row.player.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.results.forEach { isWin -> FormPill(isWin = isWin) }
                }
            }
        }
    }
}

@Composable
private fun FormPill(isWin: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (isWin) StatWinGreen else StatLossRed),
    ) {
        Text(
            text = if (isWin) "W" else "L",
            color = OnBrandLime,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
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
                    .background(BrandLime)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "+${biggestWin.margin} pts",
                    color = OnBrandLime,
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
                color = BrandLime,
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
            color = BrandLime,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = team.players.joinToString(" & ") { it.displayName },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isWinner) TextPrimary else TextMuted,
            maxLines = 1,
        )
    }
}

// ---- Shared bits ---------------------------------------------------------------

@Composable
private fun InsightCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = TextMuted)
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
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

// ---- Previews ------------------------------------------------------------------

private fun previewRanking(rank: Int, id: String, name: String, gp: Int, wins: Int, pf: Int, wr: Double) =
    PlayerRanking(rank, id, name, null, "#9ADE28", gp, wins, gp - wins, pf, wr)

private val previewState = StatsUiState(
    isLoading = false,
    hasMatches = true,
    groupName = "Saturday Smashers",
    records = Records(
        totalMatches = 12,
        winLeader = previewRanking(1, "u1", "Priya", 6, 6, 252, 1.0),
        mostPoints = previewRanking(3, "u3", "Raj", 8, 4, 315, 0.5),
        mostActive = previewRanking(3, "u3", "Raj", 8, 4, 315, 0.5),
    ),
    bestPartnership = BestPartnership(
        player1 = MatchPlayer("u1", "Priya", "#FF3D8A", null),
        player2 = MatchPlayer("u2", "Dev", "#3DB4FF", null),
        gamesTogether = 4,
        winsTogether = 4,
        winRate = 1.0,
    ),
    recentForm = listOf(
        PlayerForm(MatchPlayer("u1", "Priya", "#FF3D8A", null), listOf(true, true, false, true, true)),
        PlayerForm(MatchPlayer("u3", "Raj", "#9ADE28", null), listOf(false, true, false)),
    ),
    biggestWin = BiggestWin(
        match = Match(
            id = "m1",
            playedAt = java.time.Instant.parse("2026-07-09T06:58:00Z"),
            teams = listOf(
                MatchTeam(1, true, listOf(MatchPlayer("u1", "Priya", "#FF3D8A", null), MatchPlayer("u2", "Dev", "#3DB4FF", null))),
                MatchTeam(2, false, listOf(MatchPlayer("u3", "Raj", "#9ADE28", null), MatchPlayer("u4", "Kiran", "#EAC72B", null))),
            ),
            sets = listOf(MatchSet(1, 21, 4), MatchSet(2, 21, 9)),
        ),
        margin = 29,
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 900)
@Composable
private fun StatsContentPreview() {
    PlayboardTheme {
        StatsContent(state = previewState, onRetry = {}, onPullRefresh = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, heightDp = 400)
@Composable
private fun StatsEmptyPreview() {
    PlayboardTheme {
        StatsContent(
            state = StatsUiState(isLoading = false, hasMatches = false, groupName = "New Crew"),
            onRetry = {},
            onPullRefresh = {},
        )
    }
}
