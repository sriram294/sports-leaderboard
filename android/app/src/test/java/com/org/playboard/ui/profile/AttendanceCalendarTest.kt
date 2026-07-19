package com.org.playboard.ui.profile

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCalendarTest {

    @Test
    fun `heatmapMonths returns the last 3 months oldest first, ending this month`() {
        val months = heatmapMonths(LocalDate.of(2026, 7, 19))
        assertEquals(
            listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7)),
            months,
        )
    }

    @Test
    fun `heatmapMonths rolls across a year boundary`() {
        val months = heatmapMonths(LocalDate.of(2026, 1, 10))
        assertEquals(
            listOf(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1)),
            months,
        )
    }

    @Test
    fun `monthCells holds only the month's days, padded to whole Monday-first weeks`() {
        // 1 Jul 2026 is a Wednesday → 2 leading blanks; July has 31 days.
        val cells = monthCells(YearMonth.of(2026, 7))

        assertEquals(0, cells.size % 7)                       // whole weeks
        assertEquals(2, cells.takeWhile { it == null }.size)  // leading blanks before the 1st
        assertEquals(31, cells.count { it != null })          // exactly the month's days
        assertEquals(LocalDate.of(2026, 7, 1), cells[2])
        assertEquals(LocalDate.of(2026, 7, 31), cells.last { it != null })
        // No day from an adjacent month leaks in.
        assertTrue(cells.filterNotNull().all { it.month == java.time.Month.JULY })
    }

    @Test
    fun `heatmapWindow spans the first of the earliest month to the first after the latest`() {
        val months = listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7))
        val (from, to) = heatmapWindow(months, ZoneId.of("UTC"))
        assertEquals("2026-05-01T00:00:00Z", from)
        assertEquals("2026-08-01T00:00:00Z", to)
        assertTrue(from < to)
    }
}
