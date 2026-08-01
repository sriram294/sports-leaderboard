package com.org.playboard.ui.stats

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MonthlyTrophy
import com.org.playboard.data.model.Partner
import com.org.playboard.data.model.PlayerRanking

/**
 * Immutable state for the Stats/Insights tab (docs/requirements/06-stats.md): a
 * group-level analytics dashboard scoped to the active group. Records are all-time
 * (from the leaderboard + `Group.matchCount`); [biggestWin] is computed from the
 * recent window `MatchRepository.getMatches` returns (first page), so the UI labels
 * it as recent. The Partners card lets the user pick any player from [players] and
 * fetches that player's partner list on demand.
 */
data class StatsUiState(
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    /** A user-initiated pull-to-refresh is in flight (drives the pull indicator). */
    val isRefreshing: Boolean = false,
    /** No active group (the user hasn't created/joined one yet). */
    val noGroup: Boolean = false,
    val groupName: String? = null,
    /** Active group id, kept for the "Partners" card's on-expand fetch. */
    val groupId: String? = null,
    /** Whether the group has any recorded matches; gates the empty state. */
    val hasMatches: Boolean = false,
    val records: Records? = null,
    val biggestWin: BiggestWin? = null,
    /**
     * Who topped each completed month, newest first (last 6). Unlike the other sections
     * this is served, not computed — a crown is awarded once when the month closes and is
     * never recomputed, so it can't be derived from the current leaderboard.
     */
    val monthlyWinners: List<MonthlyTrophy> = emptyList(),
    /**
     * "Partners" card state — collapsed by default. Expanding shows a player picker
     * (defaulting to the signed-in user) populated from [players] — everyone on the
     * leaderboard, since a player with zero games can't have any partners either —
     * and fetches the selected player's partner list on demand, not eagerly with
     * the rest of the page.
     */
    val partnersExpanded: Boolean = false,
    val players: List<PlayerRanking> = emptyList(),
    val selectedPlayerId: String? = null,
    val partners: List<Partner> = emptyList(),
    val isPartnersLoading: Boolean = false,
    val partnersLoadFailed: Boolean = false,
)

/** All-time group records, derived from the leaderboard + `Group.matchCount`. */
data class Records(
    val totalMatches: Int,
    /** Top by win rate with at least [MIN_LEADER_GAMES] games, else the top-ranked entry. */
    val winLeader: PlayerRanking?,
    val mostPoints: PlayerRanking?,
    val mostActive: PlayerRanking?,
    /** Highest best (longest-ever) win streak; null unless someone reached [MIN_STREAK]. */
    val longestStreak: PlayerRanking? = null,
    /** Highest current win streak (who's hot now); null unless someone is on a [MIN_STREAK]+ run. */
    val currentStreak: PlayerRanking? = null,
)

/** The recent match with the largest total-points margin (summed across sets). */
data class BiggestWin(
    val match: Match,
    val margin: Int,
)
