package com.org.playboard.ui.stats

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.PlayerRanking

/**
 * Immutable state for the Stats/Insights tab (docs/requirements/06-stats.md): a
 * group-level analytics dashboard scoped to the active group. Records are all-time
 * (from the leaderboard + `Group.matchCount`); the match-derived sections
 * ([bestPartnership], [recentForm], [biggestWin]) are computed from the recent
 * window `MatchRepository.getMatches` returns (first page), so the UI labels them
 * as recent.
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    /** A user-initiated pull-to-refresh is in flight (drives the pull indicator). */
    val isRefreshing: Boolean = false,
    /** No active group (the user hasn't created/joined one yet). */
    val noGroup: Boolean = false,
    val groupName: String? = null,
    /** Whether the group has any recorded matches; gates the empty state. */
    val hasMatches: Boolean = false,
    val records: Records? = null,
    val bestPartnership: BestPartnership? = null,
    val recentForm: List<PlayerForm> = emptyList(),
    val biggestWin: BiggestWin? = null,
)

/** All-time group records, derived from the leaderboard + `Group.matchCount`. */
data class Records(
    val totalMatches: Int,
    /** Top by win rate with at least [MIN_LEADER_GAMES] games, else the top-ranked entry. */
    val winLeader: PlayerRanking?,
    val mostPoints: PlayerRanking?,
    val mostActive: PlayerRanking?,
)

/** The teammate pair with the best win rate together (min [MIN_PARTNERSHIP_GAMES] games). */
data class BestPartnership(
    val player1: MatchPlayer,
    val player2: MatchPlayer,
    val gamesTogether: Int,
    val winsTogether: Int,
    val winRate: Double,
) {
    val winRatePercent: Int get() = (winRate * 100).toInt()
}

/** One ranked player's recent results, most-recent-first (`true` = win). */
data class PlayerForm(
    val player: MatchPlayer,
    val results: List<Boolean>,
)

/** The recent match with the largest total-points margin (summed across sets). */
data class BiggestWin(
    val match: Match,
    val margin: Int,
)
