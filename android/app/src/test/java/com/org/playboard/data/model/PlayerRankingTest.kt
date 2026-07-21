package com.org.playboard.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the display/derivation logic on [PlayerRanking]. */
class PlayerRankingTest {

    private fun ranking(
        pointsFor: Int = 100,
        pointsAgainst: Int = 90,
        winRate: Double = 0.5,
        gamesPlayed: Int = 6,
        wins: Int = 3,
        rating: Double? = 40.0,
        provisional: Boolean = false,
    ) = PlayerRanking(
        rank = 1,
        userId = "u1",
        displayName = "P1",
        photoUrl = null,
        avatarId = null,
        avatarColor = "#9ADE28",
        gamesPlayed = gamesPlayed,
        wins = wins,
        losses = gamesPlayed - wins,
        pointsFor = pointsFor,
        pointsAgainst = pointsAgainst,
        winRate = winRate,
        rating = rating,
        provisional = provisional,
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

    @Test
    fun `rating shows to one decimal`() {
        assertEquals("43.5", ranking(rating = 43.4999).ratingLabel)
        assertEquals("0.0", ranking(rating = 0.0).ratingLabel)
        assertEquals("100.0", ranking(rating = 100.0).ratingLabel)
    }

    @Test
    fun `a provisional player shows prov instead of a rating`() {
        assertEquals("prov", ranking(rating = 48.7, provisional = true).ratingLabel)
    }

    @Test
    fun `a pre-rating backend falls back to the win rate`() {
        // rating == null means the backend predates ratings. It must not be confused with
        // a genuine 0.0, which is why the field is nullable rather than defaulted.
        assertEquals("50%", ranking(rating = null, winRate = 0.5).ratingLabel)
    }

    @Test
    fun `games needed counts down and never goes negative`() {
        assertEquals(3, ranking(gamesPlayed = 7).gamesNeeded(10))
        assertEquals(0, ranking(gamesPlayed = 10).gamesNeeded(10))
        assertEquals(0, ranking(gamesPlayed = 37).gamesNeeded(10))
    }

    @Test
    fun `a ranked row ends with the signed points difference`() {
        // The difference is the first tiebreak between equal ratings, so it has to be
        // visible — otherwise two players on 43.5 have no visible reason for their order.
        val line = ranking(gamesPlayed = 37, wins = 22, winRate = 0.5946, pointsFor = 800, pointsAgainst = 724)
            .secondaryLine(minGamesToRank = 10)

        assertEquals("37 games \u00b7 22-15 \u00b7 59% \u00b7 +76", line)
    }

    @Test
    fun `a provisional row ends with the games it still needs`() {
        val line = ranking(
            gamesPlayed = 7, wins = 6, winRate = 0.8571,
            rating = 48.7, provisional = true, pointsFor = 150, pointsAgainst = 120,
        ).secondaryLine(minGamesToRank = 10)

        assertEquals("7 games \u00b7 6-1 \u00b7 86% \u00b7 3 more to rank", line)
    }
}
