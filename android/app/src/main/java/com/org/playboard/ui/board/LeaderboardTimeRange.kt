package com.org.playboard.ui.board

import java.time.LocalDate
import java.time.ZoneId

/**
 * The window the Board leaderboard is scoped to. Calendar-based, not rolling:
 * [MONTH] is the current calendar month, and is the default.
 *
 * There is deliberately no weekly window. Ratings are computed over the selected window,
 * and a week is only one or two sessions — too few games for a confidence-adjusted rating
 * to separate anyone, so every player would sit bunched at the bottom of the scale.
 */
enum class LeaderboardTimeRange {
    MONTH, ALL_TIME;

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
        }
        return start.atStartOfDay(zone).toInstant().toString() to
            end.atStartOfDay(zone).toInstant().toString()
    }
}
