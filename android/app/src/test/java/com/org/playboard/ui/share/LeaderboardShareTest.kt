package com.org.playboard.ui.share

import com.org.playboard.data.model.PlayerRanking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure share-image helpers (no Android dependencies). */
class LeaderboardShareTest {

    private fun ranking(rank: Int) =
        PlayerRanking(rank, "u$rank", "P$rank", null, "#9ADE28", 6, 3, 3, 100, 0.5)

    @Test
    fun `topRankings caps at ten rows`() {
        val all = (1..15).map { ranking(it) }
        val top = topRankings(all)
        assertEquals(MAX_SHARE_ROWS, top.size)
        assertEquals(1, top.first().rank)
        assertEquals(10, top.last().rank)
    }

    @Test
    fun `topRankings keeps everything when fewer than ten`() {
        val all = (1..4).map { ranking(it) }
        assertEquals(all, topRankings(all))
    }

    @Test
    fun `topRankings handles empty`() {
        assertEquals(emptyList<PlayerRanking>(), topRankings(emptyList()))
    }

    @Test
    fun `shareImageFileName strips non-alphanumerics from the group id`() {
        assertEquals("leaderboard-abc123.png", shareImageFileName("abc-123"))
        assertEquals(
            "leaderboard-550e8400e29b41d4a716446655440000.png",
            shareImageFileName("550e8400-e29b-41d4-a716-446655440000"),
        )
    }
}
