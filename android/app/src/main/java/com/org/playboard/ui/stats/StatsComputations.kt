package com.org.playboard.ui.stats

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.PlayerRanking
import kotlin.math.abs

/** Minimum games before a player can be the win-rate "Win leader" record. */
internal const val MIN_LEADER_GAMES = 2

/** Minimum games together before a pair qualifies as the best partnership. */
internal const val MIN_PARTNERSHIP_GAMES = 2

/** How many recent results a player's "form" shows. */
internal const val FORM_WINDOW = 5

/**
 * All-time [Records] from the server-sorted [rankings] (win rate desc, then wins
 * desc) and the group's [matchCount]. The win leader takes the top entry with at
 * least [MIN_LEADER_GAMES] games so a lone 1-game 100% doesn't headline, falling
 * back to the top-ranked entry when nobody qualifies yet.
 */
internal fun computeRecords(rankings: List<PlayerRanking>, matchCount: Int): Records =
    Records(
        totalMatches = matchCount,
        winLeader = rankings.firstOrNull { it.gamesPlayed >= MIN_LEADER_GAMES } ?: rankings.firstOrNull(),
        mostPoints = rankings.maxByOrNull { it.pointsFor },
        mostActive = rankings.maxByOrNull { it.gamesPlayed },
    )

/**
 * The teammate pair with the best win rate together across [matches] (min
 * [MIN_PARTNERSHIP_GAMES] games, tie-broken by games played). Each match team is
 * one pairing; pairs are keyed by their two user ids order-independently.
 */
internal fun computeBestPartnership(matches: List<Match>): BestPartnership? {
    class Agg(val p1: MatchPlayer, val p2: MatchPlayer) {
        var games = 0
        var wins = 0
        val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games
    }

    val byPair = LinkedHashMap<Pair<String, String>, Agg>()
    for (match in matches) {
        for (team in match.teams) {
            val players = team.players
            for (i in players.indices) {
                for (j in i + 1 until players.size) {
                    val a = players[i]
                    val b = players[j]
                    val ordered = if (a.userId <= b.userId) a to b else b to a
                    val key = ordered.first.userId to ordered.second.userId
                    val agg = byPair.getOrPut(key) { Agg(ordered.first, ordered.second) }
                    agg.games++
                    if (team.isWinner) agg.wins++
                }
            }
        }
    }

    return byPair.values
        .filter { it.games >= MIN_PARTNERSHIP_GAMES }
        .maxWithOrNull(compareBy({ it.winRate }, { it.games }))
        ?.let { BestPartnership(it.p1, it.p2, it.games, it.wins, it.winRate) }
}

/**
 * Each ranked player's last [FORM_WINDOW] results, most-recent-first — [matches]
 * arrive newest-first, so their order is preserved. Players with no match in the
 * window are omitted so the section stays meaningful.
 */
internal fun computeRecentForm(matches: List<Match>, rankings: List<PlayerRanking>): List<PlayerForm> =
    rankings.mapNotNull { rank ->
        val results = matches
            .mapNotNull { match ->
                match.teams.firstOrNull { team -> team.players.any { it.userId == rank.userId } }?.isWinner
            }
            .take(FORM_WINDOW)
        if (results.isEmpty()) {
            null
        } else {
            PlayerForm(
                player = MatchPlayer(rank.userId, rank.displayName, rank.avatarColor, rank.photoUrl),
                results = results,
            )
        }
    }

/**
 * The match with the largest total-points margin (each team's set scores summed,
 * then the absolute difference). `null` when there are no matches.
 */
internal fun computeBiggestWin(matches: List<Match>): BiggestWin? =
    matches
        .map { it to it.pointsMargin() }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.let { BiggestWin(it.first, it.second) }

private fun Match.pointsMargin(): Int =
    abs(sets.sumOf { it.team1Score } - sets.sumOf { it.team2Score })
