package com.org.playboard.data.stats

import com.org.playboard.data.remote.dto.MonthlyFinishDto
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyFinishMappingTest {
    @Test
    fun `maps ranked and gap months and rejects malformed months`() {
        val ranked = MonthlyFinishDto("2026-09", 3, 8).toMonthlyFinishOrNull()!!
        val gap = MonthlyFinishDto("2026-10", null, 8).toMonthlyFinishOrNull()!!

        assertEquals(YearMonth.of(2026, 9), ranked.month)
        assertEquals(3, ranked.rank)
        assertNull(gap.rank)
        assertNull(MonthlyFinishDto("not-a-month", 1, 3).toMonthlyFinishOrNull())
    }
}
