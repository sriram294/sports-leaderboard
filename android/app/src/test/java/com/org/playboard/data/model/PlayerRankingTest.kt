package com.org.playboard.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the display/derivation logic on [PlayerRanking]. */
class PlayerRankingTest {

    private fun ranking(pointsFor: Int = 100, pointsAgainst: Int = 90, winRate: Double = 0.5) =
        PlayerRanking(
            rank = 1,
            userId = "u1",
            displayName = "P1",
            photoUrl = null,
            avatarColor = "#9ADE28",
            gamesPlayed = 6,
            wins = 3,
            losses = 3,
            pointsFor = pointsFor,
            pointsAgainst = pointsAgainst,
            winRate = winRate,
        )

    @Test
    fun `win rate rounds to the nearest whole percent`() {
        assertEquals(43, ranking(winRate = 0.4286).winRatePercent)
        assertEquals(42, ranking(winRate = 0.4211).winRatePercent)
        assertEquals(83, ranking(winRate = 0.8333).winRatePercent)
        assertEquals(100, ranking(winRate = 1.0).winRatePercent)
        assertEquals(0, ranking(winRate = 0.0).winRatePercent)
    }

    /**
     * The bug this replaced: truncation showed 6/14 (42.86%) and 8/19 (42.11%) both as
     * "42%", so two players the server ranks apart looked tied on screen.
     */
    @Test
    fun `rates that used to collide when truncated now read apart`() {
        val higher = ranking(winRate = 6.0 / 14)
        val lower = ranking(winRate = 8.0 / 19)

        assertEquals(43, higher.winRatePercent)
        assertEquals(42, lower.winRatePercent)
    }

    @Test
    fun `points difference subtracts against from for`() {
        assertEquals(25, ranking(pointsFor = 125, pointsAgainst = 100).pointsDiff)
        assertEquals(-25, ranking(pointsFor = 100, pointsAgainst = 125).pointsDiff)
        assertEquals(0, ranking(pointsFor = 100, pointsAgainst = 100).pointsDiff)
    }

    @Test
    fun `points difference label signs positive values only`() {
        assertEquals("+25", ranking(pointsFor = 125, pointsAgainst = 100).pointsDiffLabel)
        assertEquals("-25", ranking(pointsFor = 100, pointsAgainst = 125).pointsDiffLabel)
        assertEquals("0", ranking(pointsFor = 100, pointsAgainst = 100).pointsDiffLabel)
    }
}
