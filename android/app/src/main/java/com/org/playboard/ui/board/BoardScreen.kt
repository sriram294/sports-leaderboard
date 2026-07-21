package com.org.playboard.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.org.playboard.ui.components.PlayboardBackground
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
        onCycleSortMetric = viewModel::onCycleSortMetric,
        onTimeRangeSelected = viewModel::onTimeRangeSelected,
        onRetry = viewModel::refresh,
        onPullRefresh = viewModel::onPullRefresh,
        onPlayerClick = onPlayerClick,
        onShare = {
            val group = uiState.selectedGroup ?: return@BoardContent
            scope.launch { renderAndShareLeaderboard(context, group, uiState.rankings, uiState.minGamesToRank, darkTheme) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardContent(
    uiState: BoardUiState,
    onCycleSortMetric: () -> Unit,
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
                                minGamesToRank = uiState.minGamesToRank,
                                metric = uiState.sortMetric,
                                onMetricTap = onCycleSortMetric,
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
                    // This overlay shares its Box with the LazyColumn, so rows genuinely scroll
                    // behind it — the fill is what hides them, not decoration. A scrim rather
                    // than a flat block, so rows fade out under the bar instead of being cut.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PlayboardTheme.colors.background.copy(alpha = 0f),
                                PlayboardTheme.colors.background.copy(alpha = 0.92f),
                                PlayboardTheme.colors.background,
                            ),
                        ),
                    )
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
            .padding(top = 10.dp),
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
            listOf(LeaderboardTimeRange.MONTH, LeaderboardTimeRange.ALL_TIME).forEach { range ->
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

/**
 * One podium column; `entry == null` (fewer than 3 ranked players) leaves the slot empty.
 *
 * The champion (#1) sits center, larger and crowned; runners-up flank it a step lower.
 * Each avatar wears a soft glow + ring in the player's color, with the rank number in a
 * small circular badge tucked at the bottom edge. Below: the name and win rate only.
 */
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
    val avatarSize: Dp = if (isChampion) 94.dp else 64.dp
    val badgeSize: Dp = if (isChampion) 28.dp else 22.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlayerClick(entry.userId) }
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        // Crown floats above the champion; runners-up reserve the same height so all three
        // avatars still bottom-align into a clean podium tier.
        if (isChampion) {
            Text(text = "👑", fontSize = 22.sp)
        } else {
            Spacer(modifier = Modifier.height(26.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Avatar with a soft radial glow behind it, a color ring around it, and the rank
        // badge overlapping its bottom edge.
        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // Extra room so the glow and the badge that dips below the avatar aren't clipped.
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
                    photoUrl = entry.photoUrl,
                    avatarId = entry.avatarId,
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
            // separates it from the avatar's color ring behind it. Kept slightly translucent
            // so it tints with whatever part of the ambient glow sits behind it rather than
            // showing as a flat patch of the base color.
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
            // The rating, not the win rate — the podium is ordered by rating, and two
            // players can share a rounded win% while sitting in a definite order.
            text = if (entry.rating != null) "${entry.ratingLabel} rating" else "${entry.winRatePercent}% win rate",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            color = if (isChampion) color else PlayboardTheme.colors.textMuted,
            fontWeight = if (isChampion) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun RankingsCard(
    rows: List<PlayerRanking>,
    minGamesToRank: Int,
    metric: RankingSortMetric,
    onMetricTap: () -> Unit,
    onPlayerClick: (String) -> Unit,
) {
    // The hairline edge is what makes the card read as sitting *above* the ambient glow;
    // without it the surface and the background are close enough to blur together.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PlayboardTheme.colors.surface,
        border = BorderStroke(1.dp, PlayboardTheme.colors.textMuted.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "RANKINGS", style = MaterialTheme.typography.labelSmall, color = PlayboardTheme.colors.textMuted)
            Spacer(modifier = Modifier.height(12.dp))
            LeaderboardHeaderRow(metric = metric, onMetricTap = onMetricTap)
            var previousWasRanked = true
            rows.forEach { row ->
                // A slightly stronger rule marks the ranked/provisional boundary, so the
                // block below reads as a separate group rather than more of the table.
                val boundary = previousWasRanked && row.provisional
                HorizontalDivider(
                    color = PlayboardTheme.colors.textMuted.copy(alpha = if (boundary) 0.28f else 0.12f),
                )
                LeaderboardRow(
                    entry = row,
                    minGamesToRank = minGamesToRank,
                    metric = metric,
                    onClick = { onPlayerClick(row.userId) },
                )
                previousWasRanked = !row.provisional
            }
        }
    }
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

// Covers the three cases most likely to break the two-line layout and that no test can
// catch (there are no Compose UI tests): a name long enough to need ellipsis, two ratings
// that round to the same displayed value, and a provisional block.
private val previewRankings = listOf(
    PlayerRanking(1, "u1", "Priya", null, null, "#FF3D8A", 37, 22, 15, 800, 724, 0.59, rating = 43.5),
    PlayerRanking(2, "u2", "Bartholomew Fitzgerald-Smythe", null, null, "#3DB4FF", 41, 24, 17, 800, 803, 0.59, rating = 43.4),
    PlayerRanking(3, "u3", "Raj", null, null, "#9ADE28", 50, 27, 23, 900, 796, 0.54, rating = 40.4),
    PlayerRanking(4, "u4", "Marcus", null, null, "#FF8A3D", 30, 13, 17, 600, 619, 0.43, rating = 27.4),
    PlayerRanking(5, "u5", "Kiran", null, null, "#EAC72B", 26, 9, 17, 500, 573, 0.35, rating = 19.4),
    PlayerRanking(6, "u6", "mugu", null, null, "#8A6CFF", 7, 6, 1, 150, 120, 0.86, rating = 48.7, provisional = true),
    PlayerRanking(7, "u7", "Sam", null, null, "#C026D3", 3, 1, 2, 60, 70, 0.33, rating = 6.7, provisional = true),
)

private val previewState = BoardUiState(
    isLoading = false,
    selectedGroup = previewGroups.first(),
    rankings = previewRankings,
    minGamesToRank = 10,
    recentForm = listOf(true, true, false, true, false),
)

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun BoardContentPreview() {
    PlayboardTheme {
        PlayboardBackground {
            BoardContent(
                uiState = previewState,
                onCycleSortMetric = {},
                onTimeRangeSelected = {},
                onRetry = {},
                onPullRefresh = {},
                onPlayerClick = {},
                onShare = {},
            )
        }
    }
}
