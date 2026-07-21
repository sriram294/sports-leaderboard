package com.org.playboard.ui.board

import com.org.playboard.data.model.PlayerRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Display ordering for the rankings table. The property that matters is that a
 * provisional player can never surface above a ranked one, whatever the table is
 * sorted by — sorting on games played would otherwise float a newcomer straight back
 * to the top, which is the behaviour the threshold exists to prevent.
 */
class BoardUiStateTest {

    private fun player(
        rank: Int,
        name: String,
        games: Int,
        wins: Int,
        rating: Double,
        provisional: Boolean = false,
        pointsFor: Int = 100,
        pointsAgainst: Int = 100,
    ) = PlayerRanking(
        rank = rank,
        userId = "u-$name",
        displayName = name,
        photoUrl = null,
        avatarId = null,
        avatarColor = "#9ADE28",
        gamesPlayed = games,
        wins = wins,
        losses = games - wins,
        pointsFor = pointsFor,
        pointsAgainst = pointsAgainst,
        winRate = wins.toDouble() / games,
        rating = rating,
        provisional = provisional,
    )

    // Sriram/Mani/Dinesh ranked; mugu provisional with the best raw rate and fewest games.
    private val rankings = listOf(
        player(1, "Sriram", 37, 22, 43.5, pointsFor = 800, pointsAgainst = 724),
        player(2, "Mani", 41, 24, 43.4, pointsFor = 800, pointsAgainst = 803),
        player(3, "Dinesh", 50, 27, 40.4, pointsFor = 900, pointsAgainst = 796),
        player(4, "mugu", 7, 6, 48.7, provisional = true, pointsFor = 150, pointsAgainst = 120),
    )

    private fun state(metric: RankingSortMetric) =
        BoardUiState(rankings = rankings, sortMetric = metric, minGamesToRank = 10, isLoading = false)

    @Test
    fun `provisional players stay below ranked ones for every metric`() {
        RankingSortMetric.entries.forEach { metric ->
            val rows = state(metric).tableRows
            assertEquals("all rows present for $metric", 4, rows.size)
            assertTrue(
                "mugu must be last under $metric, was ${rows.map { it.displayName }}",
                rows.last().displayName == "mugu",
            )
        }
    }

    @Test
    fun `sorting by games played does not float a newcomer to the top`() {
        // mugu has the fewest games so this is not the discriminating case on its own;
        // what it pins is that the ranked block re-sorts while mugu stays put.
        val rows = state(RankingSortMetric.GAMES).tableRows

        assertEquals(listOf("Dinesh", "Mani", "Sriram", "mugu"), rows.map { it.displayName })
    }

    @Test
    fun `sorting by win rate would otherwise put the newcomer first`() {
        // 86% is the best rate on the board — the exact case that used to top the table.
        val rows = state(RankingSortMetric.WIN_RATE).tableRows

        assertEquals("mugu", rows.last().displayName)
        assertEquals("Sriram", rows.first().displayName)
    }

    @Test
    fun `rating order is the server order, untouched`() {
        assertEquals(
            rankings.map { it.displayName },
            state(RankingSortMetric.RATING).tableRows.map { it.displayName },
        )
    }

    @Test
    fun `points difference sorts the ranked block`() {
        val rows = state(RankingSortMetric.POINTS_DIFF).tableRows

        // Dinesh +104, Sriram +76, Mani -3, then mugu regardless of his +30.
        assertEquals(listOf("Dinesh", "Sriram", "Mani", "mugu"), rows.map { it.displayName })
    }

    @Test
    fun `podium never crowns a provisional player`() {
        val podium = state(RankingSortMetric.RATING).podium

        assertEquals(listOf("Sriram", "Mani", "Dinesh"), podium.map { it.displayName })
    }

    @Test
    fun `podium shrinks rather than backfilling from the provisional block`() {
        // Only two ranked players: the podium shows two, it does not promote a
        // provisional player to fill the third slot.
        val thin = BoardUiState(
            rankings = listOf(
                player(1, "Sriram", 37, 22, 43.5),
                player(2, "Mani", 41, 24, 43.4),
                player(3, "mugu", 7, 6, 48.7, provisional = true),
            ),
            minGamesToRank = 10,
            isLoading = false,
        )

        assertEquals(listOf("Sriram", "Mani"), thin.podium.map { it.displayName })
    }

    @Test
    fun `metric cycling wraps back to rating`() {
        var metric = RankingSortMetric.RATING
        repeat(RankingSortMetric.entries.size) { metric = metric.next() }

        assertEquals(RankingSortMetric.RATING, metric)
    }
}
