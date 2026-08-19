package com.org.playboard.data.model

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A player's frozen finishing position in one captured completed month. */
data class MonthlyFinish(
    val month: YearMonth,
    val rank: Int?,
    val qualifiedPlayers: Int,
) {
    val shortMonthLabel: String get() = month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))

    val accessibilityLabel: String get() {
        val monthName = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        return if (rank == null) "$monthName: no qualified finish"
        else "$monthName: #$rank of $qualifiedPlayers"
    }
}
