package com.org.playboard.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.theme.PlayboardTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Monday-first weekday initials for the calendar header. */
private val WEEKDAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")

private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

/**
 * The cells of [month] laid out for a **Monday-first** 7-column grid: leading `null`
 * blanks for the weekday the 1st falls on, then each day, padded with trailing `null`s
 * to a whole number of weeks. Pure so the layout is unit-testable.
 */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1 // MONDAY=1 → 0 blanks … SUNDAY=7 → 6
    val cells = ArrayList<LocalDate?>(leadingBlanks + month.lengthOfMonth())
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

/**
 * The current calendar month's `[from, to)` window as ISO-8601 instant strings, computed
 * in device-local time so the boundaries land on local midnight (mirrors
 * `LeaderboardTimeRange.window()`). Injectable for tests.
 */
fun currentMonthWindow(
    month: YearMonth = YearMonth.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<String, String> {
    val start = month.atDay(1).atStartOfDay(zone).toInstant()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
    return start.toString() to end.toString()
}

/**
 * A GitHub-style attendance grid for a single [month]: a Monday-first calendar of small
 * squares under a weekday header. Every day the player played (`day in [activeDays]`) is
 * a filled brand square, the rest are faint. No day numbers — the density reads as "how
 * often they showed up". Display-only.
 */
@Composable
fun AttendanceCalendar(
    month: YearMonth,
    activeDays: Set<LocalDate>,
    modifier: Modifier = Modifier,
) {
    val cells = monthCells(month)
    Column(modifier = modifier) {
        Text(
            text = "ACTIVITY · ${month.format(monthTitleFormatter)}",
            style = MaterialTheme.typography.labelSmall,
            color = PlayboardTheme.colors.textMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PlayboardTheme.colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    WEEKDAY_INITIALS.forEach { initial ->
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = PlayboardTheme.colors.textMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                cells.chunked(7).forEach { week ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
                    ) {
                        week.forEach { day ->
                            DaySquare(active = day != null && day in activeDays, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySquare(active: Boolean, modifier: Modifier) {
    val fill = if (active) PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted.copy(alpha = 0.18f)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(fill),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun AttendanceCalendarPreview() {
    val month = YearMonth.of(2026, 7)
    val active = listOf(3, 5, 8, 12, 13, 19, 20, 26)
        .mapTo(mutableSetOf()) { month.atDay(it) }
    PlayboardTheme {
        Box(Modifier.padding(20.dp)) {
            AttendanceCalendar(month = month, activeDays = active)
        }
    }
}
