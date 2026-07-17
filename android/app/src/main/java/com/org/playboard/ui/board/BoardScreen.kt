package com.org.playboard.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.org.playboard.R
import com.org.playboard.data.model.Group
import com.org.playboard.data.model.PlayerRanking
import com.org.playboard.ui.components.PlayerAvatar
import com.org.playboard.ui.components.avatarColor
import com.org.playboard.ui.share.renderAndShareLeaderboard
import com.org.playboard.ui.theme.DarkPlayboardColors
import com.org.playboard.ui.theme.LocalPlayboardColors
import com.org.playboard.ui.theme.PlayboardTheme
import kotlinx.coroutines.launch

/** Board (home) tab — see docs/requirements/02-board-leaderboard.md, docs/prototype/leaderboard.pdf. */
@Composable
fun BoardScreen(
    onPlayerClick: (String) -> Unit = {},
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Render the shared image in whatever theme the app is currently showing.
    val darkTheme = LocalPlayboardColors.current == DarkPlayboardColors
    BoardContent(
        uiState = uiState,
        onSortColumnSelected = viewModel::onSortColumnSelected,
        onTimeRangeSelected = viewModel::onTimeRangeSelected,
        onRetry = viewModel::refresh,
        onPullRefresh = viewModel::onPullRefresh,
        onPlayerClick = onPlayerClick,
        onShare = {
            val group = uiState.selectedGroup ?: return@BoardContent
            scope.launch { renderAndShareLeaderboard(context, group, uiState.rankings, darkTheme) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardContent(
    uiState: BoardUiState,
    onSortColumnSelected: (RankingSortColumn) -> Unit,
    onTimeRangeSelected: (LeaderboardTimeRange) -> Unit,
    onRetry: () -> Unit,
    onPullRefresh: () -> Unit,
    onPlayerClick: (String) -> Unit,
    onShare: () -> Unit,
) {
    // Measure the pinned form bar so the list reserves exactly enough bottom space
    // to scroll clear of it; falls back to the pre-existing slack when the bar is hidden.
    var formBarHeightPx by remember { mutableIntStateOf(0) }
    val formBarHeight = if (uiState.showFormBar) {
        with(LocalDensity.current) { formBarHeightPx.toDp() }
    } else {
        0.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp),
    ) {
        when {
            uiState.isLoading -> CenteredBox { CircularProgressIndicator(color = PlayboardTheme.colors.brand) }
            uiState.hasLoadFailed -> LoadFailedState(onRetry = onRetry)
            uiState.selectedGroup == null -> NoGroupsState()
            else -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onPullRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    // Owns the bottom slack (was a trailing Spacer item): 24.dp reproduces the
                    // old 16.dp arrangement + 8.dp spacer, plus room for the form bar overlay.
                    contentPadding = PaddingValues(bottom = formBarHeight + 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // The header (and its time-range toggle) always shows, even for an
                    // empty window, so the user can switch ranges when e.g. "This Week"
                    // has no matches yet but "All Time" does.
                    item {
                        TopPlayersHeader(
                            selectedRange = uiState.selectedTimeRange,
                            onTimeRangeSelected = onTimeRangeSelected,
                            onShare = onShare,
                        )
                    }
                    if (uiState.rankings.isEmpty()) {
                        item { NoMatchesBlock(range = uiState.selectedTimeRange) }
                    } else {
                        item { PodiumRow(podium = uiState.podium, onPlayerClick = onPlayerClick) }
                        item {
                            RankingsCard(
                                rows = uiState.tableRows,
                                sortColumn = uiState.sortColumn,
                                onSortColumnSelected = onSortColumnSelected,
                                onPlayerClick = onPlayerClick,
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = uiState.showFormBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    // Clears the bottom bar's lime "+", which MainScreen lifts ~10.dp above the
                    // nav bar and the Scaffold draws over this content.
                    .padding(bottom = 12.dp)
                    .onSizeChanged { formBarHeightPx = it.height },
            ) {
                FormBar(results = uiState.recentForm)
            }
        }
    }
}

/** "TOP PLAYERS" label with the subtle calendar-window dropdown, and the share action. */
@Composable
private fun TopPlayersHeader(
    selectedRange: LeaderboardTimeRange,
    onTimeRangeSelected: (LeaderboardTimeRange) -> Unit,
    onShare: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Text(text = "TOP PLAYERS", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
        Spacer(modifier = Modifier.width(6.dp))
        TimeRangeSelector(selected = selectedRange, onSelected = onTimeRangeSelected)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = "Share leaderboard",
                tint = PlayboardTheme.colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Subtle calendar-window selector: a muted label + caret that opens a small dropdown of
 * the three ranges. Deliberately low-key (no filled pills) so it reads as a refinement of
 * the "TOP PLAYERS" heading rather than a primary control.
 */
@Composable
private fun TimeRangeSelector(
    selected: LeaderboardTimeRange,
    onSelected: (LeaderboardTimeRange) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(text = selected.label, style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
            Text(text = " ▾", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PlayboardTheme.colors.surface),
        ) {
            // Default (This Month) listed first; All Time last.
            listOf(LeaderboardTimeRange.MONTH, LeaderboardTimeRange.WEEK, LeaderboardTimeRange.ALL_TIME).forEach { range ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (range == selected) PlayboardTheme.colors.brand else PlayboardTheme.colors.textPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(range)
                    },
                )
            }
        }
    }
}

/** Menu / label wording for each calendar window. */
private val LeaderboardTimeRange.label: String
    get() = when (this) {
        LeaderboardTimeRange.MONTH -> "This Month"
        LeaderboardTimeRange.WEEK -> "This Week"
        LeaderboardTimeRange.ALL_TIME -> "All Time"
    }

/** The top-3 podium row. */
@Composable
private fun PodiumRow(
    podium: List<PlayerRanking>,
    onPlayerClick: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        PodiumSlot(entry = podium.getOrNull(1), isChampion = false, onPlayerClick = onPlayerClick, modifier = Modifier.weight(1f))
        PodiumSlot(entry = podium.getOrNull(0), isChampion = true, onPlayerClick = onPlayerClick, modifier = Modifier.weight(1.2f))
        PodiumSlot(entry = podium.getOrNull(2), isChampion = false, onPlayerClick = onPlayerClick, modifier = Modifier.weight(1f))
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
                        avatarId = entry.avatarId,
                        avatarColorHex = entry.avatarColor,
                        size = avatarSize,
                    )
                }
            }
            val medalRes = when (entry.rank) {
                1 -> R.drawable.ic_podium_gold
                2 -> R.drawable.ic_podium_silver
                3 -> R.drawable.ic_podium_bronze
                else -> null
            }
            if (medalRes != null) {
                // The medal art already carries the ribbon + rank numeral, so it replaces
                // the plain numeric badge for the top three.
                Image(
                    painter = painterResource(id = medalRes),
                    contentDescription = "Rank ${entry.rank}",
                    modifier = Modifier.size(if (isChampion) 34.dp else 28.dp),
                )
            } else {
                // Defensive fallback (rank > 3, not reached today since podium = top 3).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
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
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = PlayboardTheme.colors.textPrimary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isChampion) color.copy(alpha = 0.08f) else PlayboardTheme.colors.surface,
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
                    color = if (isChampion) color else PlayboardTheme.colors.textPrimary,
                )

                Text(text = "WIN RATE", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.wins}W · ${entry.losses}L",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = PlayboardTheme.colors.textMuted,
                )
            }
        }
    }
}

// Fixed numeric column widths so the header row and data rows line up.
private val GpColumnWidth = 32.dp
private val WinsColumnWidth = 26.dp
private val LossesColumnWidth = 26.dp
// Wider than the PF column it replaced: values carry a sign ("+135", "-104").
private val PointsDiffColumnWidth = 46.dp
private val WinRateColumnWidth = 56.dp

@Composable
private fun RankingsCard(
    rows: List<PlayerRanking>,
    sortColumn: RankingSortColumn,
    onSortColumnSelected: (RankingSortColumn) -> Unit,
    onPlayerClick: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = PlayboardTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RANKINGS", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
            Spacer(modifier = Modifier.height(12.dp))
            RankingsHeaderRow(sortColumn = sortColumn, onSortColumnSelected = onSortColumnSelected)
            rows.forEach { row ->
                HorizontalDivider(color = PlayboardTheme.colors.textMuted.copy(alpha = 0.12f))
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
        SortableHeaderLabel("DIFF", RankingSortColumn.POINTS_DIFF, sortColumn, onSortColumnSelected, PointsDiffColumnWidth)
        SortableHeaderLabel("WIN%", RankingSortColumn.WIN_RATE, sortColumn, onSortColumnSelected, WinRateColumnWidth)
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier = Modifier, color: Color = PlayboardTheme.colors.textMuted) {
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
            color = if (isActive) PlayboardTheme.colors.textPrimary else PlayboardTheme.colors.textMuted,
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
                avatarId = entry.avatarId,
                avatarColorHex = entry.avatarColor,
                size = 32.dp,
            )
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PlayboardTheme.colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        StatCell(text = entry.gamesPlayed.toString(), color = PlayboardTheme.colors.textPrimary, width = GpColumnWidth)
        StatCell(text = entry.wins.toString(), color = PlayboardTheme.colors.statWin, width = WinsColumnWidth)
        StatCell(text = entry.losses.toString(), color = PlayboardTheme.colors.statLoss, width = LossesColumnWidth)
        StatCell(text = entry.pointsDiffLabel, color = pointsDiffColor(entry.pointsDiff), width = PointsDiffColumnWidth)
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

@Composable
private fun rankColor(rank: Int): Color = when (rank) {
    1 -> PlayboardTheme.colors.brand
    2 -> PlayboardTheme.colors.textPrimary
    3 -> PlayboardTheme.colors.winRateMid
    else -> PlayboardTheme.colors.textMuted
}

@Composable
private fun winRateColor(percent: Int): Color = when {
    percent >= 50 -> PlayboardTheme.colors.brand
    percent >= 25 -> PlayboardTheme.colors.winRateMid
    else -> PlayboardTheme.colors.winRateLow
}

// Matches the W/L columns: outscoring opponents reads green, being outscored red.
@Composable
private fun pointsDiffColor(diff: Int): Color = when {
    diff > 0 -> PlayboardTheme.colors.statWin
    diff < 0 -> PlayboardTheme.colors.statLoss
    else -> PlayboardTheme.colors.textMuted
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
                color = PlayboardTheme.colors.textMuted,
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
            color = PlayboardTheme.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * Empty-state block shown under the header when the selected window has no matches.
 * The copy is range-aware so "This Week" with no play reads differently from a group
 * that has never recorded a match (All Time).
 */
@Composable
private fun NoMatchesBlock(range: LeaderboardTimeRange) {
    val message = when (range) {
        LeaderboardTimeRange.WEEK -> "No matches this week yet.\nRecord one to rank this week."
        LeaderboardTimeRange.MONTH -> "No matches this month yet.\nRecord one to rank this month."
        LeaderboardTimeRange.ALL_TIME -> "No matches recorded yet.\nRankings appear after the first match."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = PlayboardTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewGroups = listOf(
    Group(id = "g1", name = "Saturday Smashers", avatarColor = "#C7EA2B", memberCount = 6, matchCount = 10, myRole = "owner"),
)

private val previewRankings = listOf(
    PlayerRanking(1, "u1", "Priya", null, null, "#FF3D8A", 6, 6, 0, 252, 180, 1.0),
    PlayerRanking(2, "u2", "Dev", null, null, "#3DB4FF", 6, 5, 1, 245, 205, 0.83),
    PlayerRanking(3, "u3", "Raj", null, null, "#9ADE28", 8, 4, 4, 315, 310, 0.5),
    PlayerRanking(4, "u4", "Marcus", null, null, "#FF8A3D", 7, 2, 5, 265, 290, 0.29),
    PlayerRanking(5, "u5", "Kiran", null, null, "#EAC72B", 7, 2, 5, 263, 295, 0.29),
    PlayerRanking(6, "u6", "Sam", null, null, "#8A6CFF", 6, 1, 5, 226, 270, 0.17),
)

private val previewState = BoardUiState(
    isLoading = false,
    selectedGroup = previewGroups.first(),
    rankings = previewRankings,
    recentForm = listOf(true, true, false, true, false),
)

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun BoardContentPreview() {
    PlayboardTheme {
        BoardContent(
            uiState = previewState,
            onSortColumnSelected = {},
            onTimeRangeSelected = {},
            onRetry = {},
            onPullRefresh = {},
            onPlayerClick = {},
            onShare = {},
        )
    }
}
