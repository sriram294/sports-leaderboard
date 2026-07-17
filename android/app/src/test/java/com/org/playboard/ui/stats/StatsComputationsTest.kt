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

    // ---- best partnership ----

    @Test
    fun `best partnership picks the highest win rate above the min games`() {
        val matches = listOf(
            // Priya+Dev: 2 games, 2 wins -> 1.0
            match("m1", listOf(p("priya"), p("dev")), listOf(p("raj"), p("kiran")), winner = 1),
            match("m2", listOf(p("priya"), p("dev")), listOf(p("raj"), p("kiran")), winner = 1),
            // Raj+Kiran: 2 games, 0 wins -> 0.0 (already above; lower rate)
        )
        val best = computeBestPartnership(matches)!!
        assertEquals(setOf("priya", "dev"), setOf(best.player1.userId, best.player2.userId))
        assertEquals(2, best.gamesTogether)
        assertEquals(2, best.winsTogether)
        assertEquals(1.0, best.winRate, 0.0001)
    }

    @Test
    fun `best partnership excludes pairs below the minimum games`() {
        val matches = listOf(
            match("m1", listOf(p("a"), p("b")), listOf(p("c"), p("d")), winner = 1),
        )
        // Every pair has only 1 game together -> nobody qualifies.
        assertNull(computeBestPartnership(matches))
    }

    @Test
    fun `best partnership tie-breaks by games played`() {
        val matches = listOf(
            // a+b: 3 games all won -> 1.0
            match("m1", listOf(p("a"), p("b")), listOf(p("x"), p("y")), winner = 1),
            match("m2", listOf(p("a"), p("b")), listOf(p("x"), p("y")), winner = 1),
            match("m3", listOf(p("a"), p("b")), listOf(p("x"), p("y")), winner = 1),
            // c+d: 2 games all won -> 1.0 but fewer games
            match("m4", listOf(p("c"), p("d")), listOf(p("x"), p("y")), winner = 1),
            match("m5", listOf(p("c"), p("d")), listOf(p("x"), p("y")), winner = 1),
        )
        val best = computeBestPartnership(matches)!!
        assertEquals(setOf("a", "b"), setOf(best.player1.userId, best.player2.userId))
        assertEquals(3, best.gamesTogether)
    }

    // ---- recent form ----

    @Test
    fun `recent form keeps last five newest-first and reflects the player's result`() {
        // matches passed newest-first (as the repo returns)
        val matches = (1..7).map { i ->
            // priya on team 1; wins the odd-numbered (newest) ones
            match("m$i", listOf(p("priya"), p("dev")), listOf(p("raj"), p("kiran")), winner = if (i % 2 == 1) 1 else 2)
        }
        val form = computeRecentForm(matches, listOf(ranking("priya", 7, 4, 100, 0.57)))
        val priya = form.single()
        assertEquals(5, priya.results.size)                       // capped at FORM_WINDOW
        assertEquals(listOf(true, false, true, false, true), priya.results) // newest-first
    }

    @Test
    fun `recent form omits players with no matches in the window`() {
        val matches = listOf(
            match("m1", listOf(p("priya"), p("dev")), listOf(p("raj"), p("kiran")), winner = 1),
        )
        val form = computeRecentForm(
            matches,
            listOf(ranking("priya", 1, 1, 21, 1.0), ranking("ghost", 0, 0, 0, 0.0)),
        )
        assertEquals(listOf("priya"), form.map { it.player.userId })
    }

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
