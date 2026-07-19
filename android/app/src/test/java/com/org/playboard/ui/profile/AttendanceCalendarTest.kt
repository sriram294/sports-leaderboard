package com.org.playboard.ui.profile

import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCalendarTest {

    @Test
    fun `monthCells pads to whole Monday-first weeks with leading blanks`() {
        // 1 Jul 2026 is a Wednesday → 2 leading blanks (Mon, Tue); July has 31 days.
        val cells = monthCells(YearMonth.of(2026, 7))

        assertEquals(35, cells.size)            // 2 + 31 = 33 → padded to 5 full weeks
        assertEquals(0, cells.size % 7)
        assertEquals(2, cells.takeWhile { it == null }.size)
        assertEquals(31, cells.count { it != null })
        assertEquals(YearMonth.of(2026, 7).atDay(1), cells[2])   // first real day sits after the blanks
        assertEquals(YearMonth.of(2026, 7).atDay(31), cells.last { it != null })
    }

    @Test
    fun `monthCells has no leading blanks when the month starts on Monday`() {
        // 1 Jun 2026 is a Monday.
        val cells = monthCells(YearMonth.of(2026, 6))
        assertEquals(YearMonth.of(2026, 6).atDay(1), cells.first())
    }

    @Test
    fun `currentMonthWindow spans local midnight of the first to the next first`() {
        val (from, to) = currentMonthWindow(YearMonth.of(2026, 7), ZoneId.of("UTC"))
        assertEquals("2026-07-01T00:00:00Z", from)
        assertEquals("2026-08-01T00:00:00Z", to)
        assertTrue(from < to)
    }

    @Test
    fun `currentMonthWindow rolls over the year at December`() {
        val (from, to) = currentMonthWindow(YearMonth.of(2026, 12), ZoneId.of("UTC"))
        assertEquals("2026-12-01T00:00:00Z", from)
        assertEquals("2027-01-01T00:00:00Z", to)
    }
}
