package com.org.playboard.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.org.playboard.ui.theme.PlayboardTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// Cell geometry — small squares like a GitHub contribution grid.
private val CELL = 13.dp
private val CELL_GAP = 3.dp
private val ROW_PITCH = CELL + CELL_GAP        // one weekday row's vertical stride
private val MONTH_GAP = 12.dp                   // blank space between month blocks
private val Y_AXIS_WIDTH = 30.dp               // room for "Wed"
private val HEADER_HEIGHT = 16.dp              // month-label row height

/** Monday-first weekday labels for the Y axis (the enum is ISO order, MONDAY first). */
private val WEEKDAYS: List<DayOfWeek> = DayOfWeek.values().toList()

/** The [count] calendar months ending with [today]'s month, oldest first. Pure/testable. */
fun heatmapMonths(today: LocalDate, count: Int = 3): List<YearMonth> {
    val current = YearMonth.from(today)
    return (0 until count).map { current.minusMonths((count - 1 - it).toLong()) }
}

/**
 * The cells of [month] laid out **Monday-first**: leading `null` blanks for the weekday the
 * 1st falls on, then each day, padded with trailing `null`s to a whole number of weeks.
 * Chunked into 7s these become the month's Mon..Sun week-columns. Pure so it's testable.
 */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // MONDAY=1 → 0 … SUNDAY=7 → 6
    val cells = ArrayList<LocalDate?>(leadingBlanks + month.lengthOfMonth())
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

/**
 * The `[from, to)` window covering [months], as ISO-8601 instant strings in device-local
 * time (mirrors `LeaderboardTimeRange.window()`): first day of the earliest month through
 * the first day after the latest month.
 */
fun heatmapWindow(months: List<YearMonth>, zone: ZoneId): Pair<String, String> {
    val from = months.first().atDay(1).atStartOfDay(zone).toInstant()
    val to = months.last().plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
    return from.toString() to to.toString()
}

/**
 * A GitHub-style attendance heatmap over [months]: each month is its own block of Mon..Sun
 * week-columns showing **only that month's days** (no adjacent-month/padding cells),
 * separated by a gap. Weekday labels run down the Y axis, month labels across the top.
 * A day the player played (`day in [activeDays]`) is a filled brand square, else faint.
 */
@Composable
fun AttendanceCalendar(
    months: List<YearMonth>,
    activeDays: Set<LocalDate>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "ACTIVITY",
            style = MaterialTheme.typography.labelSmall,
            color = PlayboardTheme.colors.textMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PlayboardTheme.colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                WeekdayAxis()
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    months.forEachIndexed { index, month ->
                        if (index > 0) Spacer(Modifier.width(MONTH_GAP))
                        MonthBlock(month = month, activeDays = activeDays)
                    }
                }
            }
        }
    }
}

/** Fixed left column: all 7 weekday initials aligned to the grid rows. */
@Composable
private fun WeekdayAxis() {
    Column(modifier = Modifier.width(Y_AXIS_WIDTH)) {
        Spacer(Modifier.height(HEADER_HEIGHT)) // clears the month-label row
        WEEKDAYS.forEach { day ->
            Box(modifier = Modifier.height(ROW_PITCH), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = PlayboardTheme.colors.textMuted,
                )
            }
        }
    }
}

/** One month: a label over its Mon..Sun week-columns; only the month's own days get a cell. */
@Composable
private fun MonthBlock(month: YearMonth, activeDays: Set<LocalDate>) {
    val weekColumns = monthCells(month).chunked(7)
    Column {
        Box(modifier = Modifier.height(HEADER_HEIGHT)) {
            Text(
                text = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = PlayboardTheme.colors.textMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true),
            )
        }
        Row {
            weekColumns.forEach { column ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                    modifier = Modifier.padding(end = CELL_GAP),
                ) {
                    column.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.size(CELL)) // no cell outside the month
                        } else {
                            DaySquare(active = day in activeDays)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySquare(active: Boolean) {
    val fill = if (active) PlayboardTheme.colors.brand else PlayboardTheme.colors.textMuted.copy(alpha = 0.18f)
    Box(
        modifier = Modifier
            .size(CELL)
            .clip(RoundedCornerShape(3.dp))
            .background(fill),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun AttendanceCalendarPreview() {
    val months = heatmapMonths(LocalDate.of(2026, 7, 19))
    val active = setOf(
        LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 20),
        LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 20),
        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 27),
        LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 13),
    )
    PlayboardTheme {
        Box(Modifier.padding(20.dp)) {
            AttendanceCalendar(months = months, activeDays = active)
        }
    }
}
