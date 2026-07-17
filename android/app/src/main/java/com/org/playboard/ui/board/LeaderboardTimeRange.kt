package com.org.playboard.ui.board

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The window the Board leaderboard is scoped to. Calendar-based, not rolling:
 * [MONTH] is the current calendar month, [WEEK] the current calendar week
 * (starting Monday). [MONTH] is the default.
 */
enum class LeaderboardTimeRange {
    WEEK, MONTH, ALL_TIME;

    /**
     * The `[from, to)` window as ISO-8601 instant strings, computed in device-local
     * time so calendar boundaries land on local midnight. `null` for [ALL_TIME]
     * (the backend then reads the all-time snapshot). Both the day and the zone are
     * injectable to keep the math unit-testable.
     */
    fun window(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<String, String>? {
        val (start, end) = when (this) {
            ALL_TIME -> return null
            MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
        }
        return start.atStartOfDay(zone).toInstant().toString() to
            end.atStartOfDay(zone).toInstant().toString()
    }
}
