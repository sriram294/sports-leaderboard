package com.org.playboard.data.model

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A month this player finished top of the group's leaderboard.
 *
 * Awarded once, server-side, when the month closes — never recomputed. A match edited after
 * the fact does not move a crown that has already been handed out.
 */
data class MonthlyTrophy(
    val month: YearMonth,
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val avatarId: String?,
    val avatarColor: String,
    /** Winning rating (0-100) as it stood when the month closed; null on older backends. */
    val rating: Double?,
    val gamesPlayed: Int?,
    val wins: Int?,
) {
    /**
     * Badge label, e.g. `JUL '26`.
     *
     * The year is not decoration: a shelf that spans more than twelve months would otherwise
     * show two identical `JUL` badges with no way to tell which season each belongs to.
     */
    val shortMonthLabel: String
        get() = month.format(SHORT_MONTH).uppercase(Locale.ENGLISH)

    /** Full label including the year, e.g. `July 2026` — used where space allows. */
    val fullMonthLabel: String
        get() = month.format(FULL_MONTH)

    private companion object {
        // Locale.ENGLISH, not the device locale: month names sit beside English UI copy
        // everywhere else in the app, so a device set to another language would otherwise
        // render a single mixed-language row.
        val SHORT_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM ''yy", Locale.ENGLISH)
        val FULL_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    }
}
