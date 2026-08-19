package com.org.playboard.ui.profile

import com.org.playboard.data.model.MonthlyFinish
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyFinishesChartTest {
    @Test
    fun `rank one is highest and gaps break line segments`() {
        val finishes = listOf(
            finish("2026-06", 3, 6),
            finish("2026-07", 1, 5),
            finish("2026-08", null, 8),
            finish("2026-09", 2, 8),
            finish("2026-10", 4, 8),
        )

        val geometry = finishGeometry(finishes)

        assertEquals(8, geometry.lowerBound)
        assertEquals(0f, geometry.points[1].y)
        assertEquals(listOf(0 to 1, 3 to 4), geometry.segments.map { it.from.index to it.to.index })
    }

    @Test
    fun `empty and populated summaries describe every month`() {
        val emptySummary = finishesAccessibilitySummary(listOf(finish("2026-06", null, 4)))
        assertTrue(emptySummary.startsWith("Finishing positions are recorded after each month closes."))
        assertTrue(emptySummary.contains("June 2026: no qualified finish"))
        val summary = finishesAccessibilitySummary(listOf(finish("2026-06", 2, 4), finish("2026-07", null, 5)))
        assertTrue(summary.contains("June 2026: #2 of 4"))
        assertTrue(summary.contains("July 2026: no qualified finish"))
    }

    private fun finish(month: String, rank: Int?, players: Int) =
        MonthlyFinish(YearMonth.parse(month), rank, players)
}
