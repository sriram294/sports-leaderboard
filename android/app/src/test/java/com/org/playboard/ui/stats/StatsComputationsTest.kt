package com.org.playboard.ui.stats

import com.org.playboard.data.model.Match
import com.org.playboard.data.model.MatchPlayer
import com.org.playboard.data.model.MatchSet
import com.org.playboard.data.model.MatchTeam
import com.org.playboard.data.model.PlayerRanking
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun p(id: String, name: String = id) = MatchPlayer(id, name, "#FF3D8A", null, null)

private fun match(
    id: String,
    t1: List<MatchPlayer>,
    t2: List<MatchPlayer>,
    winner: Int,
    sets: List<Pair<Int, Int>> = listOf(21 to 15),
    playedAt: String = "2026-07-09T06:58:00Z",
) = Match(
    id = id,
    playedAt = Instant.parse(playedAt),
    teams = listOf(MatchTeam(1, winner == 1, t1), MatchTeam(2, winner == 2, t2)),
    sets = sets.mapIndexed { i, (a, b) -> MatchSet(i + 1, a, b) },
)

private fun ranking(
    id: String,
    gp: Int,
    wins: Int,
    pf: Int,
    wr: Double,
    rank: Int = 1,
    currentStreak: Int = 0,
    bestStreak: Int = 0,
    pa: Int = 0,
) = PlayerRanking(rank, id, id, null, null, "#9ADE28", gp, wins, gp - wins, pf, pa, wr, currentStreak, bestStreak)

class StatsComputationsTest {

    // ---- biggest win ----

    @Test
    fun `biggest win is the largest total-points margin across sets`() {
        val matches = listOf(
            match("close", listOf(p("a"), p("b")), listOf(p("c"), p("d")), winner = 1, sets = listOf(21 to 19)),
            match("blowout", listOf(p("a"), p("b")), listOf(p("c"), p("d")), winner = 1, sets = listOf(21 to 4, 21 to 9)),
        )
        val biggest = computeBiggestWin(matches)!!
        assertEquals("blowout", biggest.match.id)
        assertEquals(29, biggest.margin) // (21+21) - (4+9)
    }

    @Test
    fun `biggest win is null without matches`() {
        assertNull(computeBiggestWin(emptyList()))
    }

    // ---- records ----

    @Test
    fun `records use match count and leaderboard extremes`() {
        val rankings = listOf(
            ranking("priya", gp = 6, wins = 6, pf = 252, wr = 1.0, rank = 1),
            ranking("raj", gp = 8, wins = 4, pf = 315, wr = 0.5, rank = 2),
        )
        val records = computeRecords(rankings, matchCount = 12)
        assertEquals(12, records.totalMatches)
        assertEquals("priya", records.winLeader?.userId) // top of the server sort, >=2 games
        assertEquals("raj", records.mostPoints?.userId)   // max pointsFor
        assertEquals("raj", records.mostActive?.userId)   // max gamesPlayed
    }

    @Test
    fun `win leader skips a small-sample leader but falls back when none qualifies`() {
        // Server-sorted: a 1-game 100% tops the list but shouldn't headline.
        val withQualifier = listOf(
            ranking("flash", gp = 1, wins = 1, pf = 21, wr = 1.0, rank = 1),
            ranking("steady", gp = 5, wins = 4, pf = 180, wr = 0.8, rank = 2),
        )
        assertEquals("steady", computeRecords(withQualifier, 6).winLeader?.userId)

        // Nobody has >= MIN_LEADER_GAMES -> fall back to the top-ranked entry.
        val allSmall = listOf(ranking("flash", gp = 1, wins = 1, pf = 21, wr = 1.0, rank = 1))
        assertEquals("flash", computeRecords(allSmall, 1).winLeader?.userId)
    }

    @Test
    fun `records surface the highest best and current win streaks`() {
        val rankings = listOf(
            ranking("priya", gp = 8, wins = 7, pf = 300, wr = 0.87, rank = 1, currentStreak = 3, bestStreak = 5),
            ranking("raj", gp = 8, wins = 5, pf = 315, wr = 0.62, rank = 2, currentStreak = -2, bestStreak = 6),
            ranking("dev", gp = 4, wins = 2, pf = 120, wr = 0.5, rank = 3, currentStreak = 1, bestStreak = 1),
        )
        val records = computeRecords(rankings, matchCount = 20)
        assertEquals("raj", records.longestStreak?.userId)  // best_streak 6 wins
        assertEquals("priya", records.currentStreak?.userId) // current win run 3 (raj -2, dev 1)
    }

    @Test
    fun `streak records skip runs below the minimum and negative current streaks`() {
        val rankings = listOf(
            ranking("a", gp = 3, wins = 1, pf = 40, wr = 0.33, currentStreak = -1, bestStreak = 1),
        )
        val records = computeRecords(rankings, matchCount = 3)
        assertNull(records.longestStreak)  // best streak 1 < MIN_STREAK
        assertNull(records.currentStreak)  // on a loss run
    }

    @Test
    fun `streak record ties break toward the higher-ranked player`() {
        val rankings = listOf(
            ranking("top", gp = 6, wins = 5, pf = 200, wr = 0.83, rank = 1, bestStreak = 4),
            ranking("next", gp = 6, wins = 5, pf = 200, wr = 0.83, rank = 2, bestStreak = 4),
        )
        assertEquals("top", computeRecords(rankings, 6).longestStreak?.userId)
    }

    @Test
    fun `empty leaderboard yields null leaders`() {
        val records = computeRecords(emptyList(), matchCount = 0)
        assertEquals(0, records.totalMatches)
        assertNull(records.winLeader)
        assertTrue(records.mostPoints == null && records.mostActive == null)
    }
}
