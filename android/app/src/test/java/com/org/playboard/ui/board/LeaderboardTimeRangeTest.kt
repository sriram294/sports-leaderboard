package com.org.playboard.ui.board

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderboardTimeRangeTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `all time has no window`() {
        assertNull(LeaderboardTimeRange.ALL_TIME.window())
    }

    @Test
    fun `month spans the first of this month to the first of next`() {
        val window = LeaderboardTimeRange.MONTH.window(today = LocalDate.of(2026, 7, 17), zone = utc)
        assertEquals("2026-07-01T00:00:00Z" to "2026-08-01T00:00:00Z", window)
    }

    @Test
    fun `month rolls the year over in December`() {
        val window = LeaderboardTimeRange.MONTH.window(today = LocalDate.of(2026, 12, 9), zone = utc)
        assertEquals("2026-12-01T00:00:00Z" to "2027-01-01T00:00:00Z", window)
    }

    @Test
    fun `only month and all-time windows exist`() {
        // A weekly window was removed with the rating change: one or two sessions is too
        // few games for a confidence-adjusted rating to separate anyone.
        assertEquals(
            listOf(LeaderboardTimeRange.MONTH, LeaderboardTimeRange.ALL_TIME),
            LeaderboardTimeRange.entries.toList(),
        )
    }

    @Test
    fun `boundaries honor the device zone, not UTC`() {
        // Asia/Kolkata is UTC+05:30, so local month-start is 18:30 the previous day in UTC.
        val window = LeaderboardTimeRange.MONTH.window(
            today = LocalDate.of(2026, 7, 17),
            zone = ZoneId.of("Asia/Kolkata"),
        )
        assertEquals("2026-06-30T18:30:00Z" to "2026-07-31T18:30:00Z", window)
    }
}
