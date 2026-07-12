package com.org.playboard.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.avatarColor
import com.org.playboard.ui.theme.BrandLime
import com.org.playboard.ui.theme.OnBrandLime
import com.org.playboard.ui.theme.PlayboardTheme
import com.org.playboard.ui.theme.StatLossRed
import com.org.playboard.ui.theme.StatWinGreen
import com.org.playboard.ui.theme.SurfaceDark
import com.org.playboard.ui.theme.TextMuted
import com.org.playboard.ui.theme.TextPrimary
import com.org.playboard.ui.theme.WinRateLowBlue
import com.org.playboard.ui.theme.WinRateMidAmber

/** Board (home) tab — see docs/requirements/02-board-leaderboard.md, docs/prototype/leaderboard.pdf. */
@Composable
fun BoardScreen(
    onPlayerClick: (String) -> Unit = {},
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    BoardContent(
        uiState = uiState,
        onSortColumnSelected = viewModel::onSortColumnSelected,
        onRetry = viewModel::refresh,
        onPullRefresh = viewModel::onPullRefresh,
        onPlayerClick = onPlayerClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardContent(
    uiState: BoardUiState,
    onSortColumnSelected: (RankingSortColumn) -> Unit,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    onPlayerClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        when {
            uiState.isLoading -> CenteredBox { CircularProgressIndicator(color = BrandLime) }
            uiState.hasLoadFailed -> LoadFailedState(onRetry = onRetry)
            uiState.selectedGroup == null -> NoGroupsState()
            uiState.rankings.isEmpty() -> NoMatchesState()
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onPullRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { TopPlayersPodium(podium = uiState.podium, onPlayerClick = onPlayerClick) }
                    item {
                        RankingsCard(
                            rows = uiState.tableRows,
                            sortColumn = uiState.sortColumn,
                            onSortColumnSelected = onSortColumnSelected,
                            onPlayerClick = onPlayerClick,
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopPlayersPodium(podium: List<PlayerRanking>, onPlayerClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(text = "TOP PLAYERS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            PodiumSlot(entry = podium.getOrNull(1), isChampion = false, onPlayerClick = onPlayerClick, modifier = Modifier.weight(1f))
            PodiumSlot(entry = podium.getOrNull(0), isChampion = true, onPlayerClick = onPlayerClick, modifier = Modifier.weight(1.2f))
            PodiumSlot(entry = podium.getOrNull(2), isChampion = false, onPlayerClick = onPlayerClick, modifier = Modifier.weight(1f))
        }
    }
}

/** One podium column; `entry == null` (fewer than 3 ranked players) leaves the slot empty. */
@Composable
private fun PodiumSlot(
    entry: PlayerRanking?,
    isChampion: Boolean,
    onPlayerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entry == null) {
        Spacer(modifier = modifier)
        return
    }
    val color = avatarColor(entry.avatarColor)
    val avatarSize: Dp = if (isChampion) 84.dp else 60.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlayerClick(entry.userId) },
    ) {
        // Rank badge overlapping the top of the avatar, as in the prototype.
        Box(contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.padding(top = 11.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.background(
                        // Soft glow behind the avatar in the player's color.
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = if (isChampion) 0.35f else 0.15f), Color.Transparent),
                        ),
                    ),
                ) {
                    PlayerAvatar(
                        displayName = entry.displayName,
                        photoUrl = entry.photoUrl,
                        avatarColorHex = entry.avatarColor,
                        size = avatarSize,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color),
            ) {
                Text(
                    text = entry.rank.toString(),
                    color = OnBrandLime,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    fontWeight = FontWeight.Bold,
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
                    if (isChampion) {
                        Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
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

// Fixed numeric column widths so the header row and data rows line up.
private val GpColumnWidth = 32.dp
private val WinsColumnWidth = 26.dp
private val LossesColumnWidth = 26.dp
private val PointsForColumnWidth = 42.dp
private val WinRateColumnWidth = 56.dp

@Composable
private fun RankingsCard(
    rows: List<PlayerRanking>,
    sortColumn: RankingSortColumn,
    onSortColumnSelected: (RankingSortColumn) -> Unit,
    onPlayerClick: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RANKINGS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(12.dp))
            RankingsHeaderRow(sortColumn = sortColumn, onSortColumnSelected = onSortColumnSelected)
            rows.forEach { row ->
                HorizontalDivider(color = TextMuted.copy(alpha = 0.12f))
                RankingRow(entry = row, onPlayerClick = onPlayerClick)
            }
        }
    }
}

@Composable
private fun RankingsHeaderRow(
    sortColumn: RankingSortColumn,
    onSortColumnSelected: (RankingSortColumn) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        HeaderLabel(text = "#", modifier = Modifier.width(24.dp))
        HeaderLabel(text = "PLAYER", modifier = Modifier.weight(1f))
        SortableHeaderLabel("GP", RankingSortColumn.GAMES_PLAYED, sortColumn, onSortColumnSelected, GpColumnWidth)
        SortableHeaderLabel("W", RankingSortColumn.WINS, sortColumn, onSortColumnSelected, WinsColumnWidth)
        SortableHeaderLabel("L", RankingSortColumn.LOSSES, sortColumn, onSortColumnSelected, LossesColumnWidth)
        SortableHeaderLabel("PF", RankingSortColumn.POINTS_FOR, sortColumn, onSortColumnSelected, PointsForColumnWidth)
        SortableHeaderLabel("WIN%", RankingSortColumn.WIN_RATE, sortColumn, onSortColumnSelected, WinRateColumnWidth)
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMuted) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun SortableHeaderLabel(
    text: String,
    column: RankingSortColumn,
    activeColumn: RankingSortColumn,
    onSortColumnSelected: (RankingSortColumn) -> Unit,
    width: Dp,
) {
    val isActive = column == activeColumn
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onSortColumnSelected(column) },
    ) {
        HeaderLabel(
            text = if (isActive) "$text ▾" else text,
            color = if (isActive) TextPrimary else TextMuted,
        )
    }
}

@Composable
private fun RankingRow(entry: PlayerRanking, onPlayerClick: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayerClick(entry.userId) }
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = entry.rank.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = rankColor(entry.rank),
            modifier = Modifier.width(24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            PlayerAvatar(
                displayName = entry.displayName,
                photoUrl = entry.photoUrl,
                avatarColorHex = entry.avatarColor,
                size = 32.dp,
            )
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        StatCell(text = entry.gamesPlayed.toString(), color = TextPrimary, width = GpColumnWidth)
        StatCell(text = entry.wins.toString(), color = StatWinGreen, width = WinsColumnWidth)
        StatCell(text = entry.losses.toString(), color = StatLossRed, width = LossesColumnWidth)
        StatCell(text = entry.pointsFor.toString(), color = TextPrimary, width = PointsForColumnWidth)
        StatCell(
            text = "${entry.winRatePercent}%",
            color = winRateColor(entry.winRatePercent),
            width = WinRateColumnWidth,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatCell(text: String, color: Color, width: Dp, fontWeight: FontWeight = FontWeight.Medium) {
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

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LoadFailedState(onRetry: () -> Unit) {
    CenteredBox {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Couldn't load the leaderboard.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun NoGroupsState() {
    CenteredBox {
        Text(
            text = "You're not in a group yet.\nUse the group switcher above to create or join one.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun NoMatchesState() {
    CenteredBox {
        Text(
            text = "No matches recorded yet.\nRankings appear after the first match.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewGroups = listOf(
    Group(id = "g1", name = "Saturday Smashers", avatarColor = "#C7EA2B", memberCount = 6, matchCount = 10, myRole = "owner"),
)

private val previewRankings = listOf(
    PlayerRanking(1, "u1", "Priya", null, "#FF3D8A", 6, 6, 0, 252, 1.0),
    PlayerRanking(2, "u2", "Dev", null, "#3DB4FF", 6, 5, 1, 245, 0.83),
    PlayerRanking(3, "u3", "Raj", null, "#9ADE28", 8, 4, 4, 315, 0.5),
    PlayerRanking(4, "u4", "Marcus", null, "#FF8A3D", 7, 2, 5, 265, 0.29),
    PlayerRanking(5, "u5", "Kiran", null, "#EAC72B", 7, 2, 5, 263, 0.29),
    PlayerRanking(6, "u6", "Sam", null, "#8A6CFF", 6, 1, 5, 226, 0.17),
)

private val previewState = BoardUiState(
    isLoading = false,
    selectedGroup = previewGroups.first(),
    rankings = previewRankings,
)

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun BoardContentPreview() {
    PlayboardTheme {
        BoardContent(
            uiState = previewState,
            onSortColumnSelected = {},
            onRetry = {},
            onPullRefresh = {},
            onPlayerClick = {},
        )
    }
}
