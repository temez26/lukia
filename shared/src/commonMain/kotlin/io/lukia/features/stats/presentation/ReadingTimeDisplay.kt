package io.lukia.features.stats.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.infrastructure.WeekdayForm
import io.lukia.core.infrastructure.weekdayName
import io.lukia.resources.Res
import io.lukia.resources.stats_days_in_a_row
import io.lukia.resources.stats_no_run
import io.lukia.resources.stats_time_dash
import io.lukia.resources.stats_time_hours
import io.lukia.resources.stats_time_hours_minutes
import io.lukia.resources.stats_time_minutes
import io.lukia.resources.stats_time_none
import io.lukia.resources.stats_time_under_a_minute
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Milliseconds in the units this reads out in. */
private const val MillisPerMinute = 60_000L
private const val MinutesPerHour = 60L

/**
 * Past this many hours the minutes are dropped.
 *
 * "12h" says as much as "12h 04m" and is half as much to read on a row that
 * already carries three of these. Below it the minutes are most of the figure
 * and cannot go.
 */
private const val HoursPastWhichMinutesStopMattering = 10

/**
 * A stretch of reading, as somebody would say it.
 *
 * Rounded down to whole minutes, and never up to a unit that has not been
 * reached: forty seconds is "under a minute" rather than "1m", because an app
 * that credits a reader with a minute they did not read is an app whose numbers
 * cannot be trusted about the hours either.
 *
 * Hours drop the minutes past ten hours - "12h" says as much as "12h 04m" and is
 * half as much to read on a row that already carries three of these.
 *
 * Four whole forms rather than a number with a unit stuck on: where the unit
 * goes, and whether it takes a space, is not the same in every language.
 */
@Composable
internal fun readingTime(millis: Long): String {
    if (millis <= 0) return stringResource(Res.string.stats_time_none)

    val minutes = millis / MillisPerMinute

    if (minutes < 1) return stringResource(Res.string.stats_time_under_a_minute)

    val hours = minutes / MinutesPerHour
    val rest = minutes % MinutesPerHour

    return when {
        hours <= 0 -> stringResource(Res.string.stats_time_minutes, minutes)
        hours >= HoursPastWhichMinutesStopMattering ->
            stringResource(Res.string.stats_time_hours, hours)

        rest == 0L -> stringResource(Res.string.stats_time_hours, hours)
        else -> stringResource(Res.string.stats_time_hours_minutes, hours, rest)
    }
}

/**
 * The same stretch where a dash is better than a sentence.
 *
 * A figure standing under its own label has the label to explain it, so "None
 * yet" is one word too many there; an em dash reads as nothing without reading
 * as a nought, which on a row of statistics is the difference between "you have
 * not read today" and "you failed today".
 */
@Composable
internal fun readingTimeOrDash(millis: Long): String = if (millis <= 0) {
    stringResource(Res.string.stats_time_dash)
} else {
    readingTime(millis)
}

/**
 * A day, as the label under one bar of the chart.
 *
 * The platform's own abbreviation rather than the first three letters of the
 * full name. Truncating is right for English and wrong almost everywhere else:
 * an abbreviation is not always a prefix, and a script with no three-letter form
 * has none to cut. What each platform gives back is what its own calendars show.
 */
internal fun dayLabel(date: LocalDate): String =
    weekdayName(date.dayOfWeek, WeekdayForm.Short)

/** The day spelled out, for the sentence a screen reader is given. */
internal fun dayName(date: LocalDate): String =
    weekdayName(date.dayOfWeek, WeekdayForm.Full)

/**
 * How many days in a row, said as a sentence rather than as a number with a
 * label under it.
 *
 * Nought is its own string rather than a plural form: "No run going" is a
 * different thing to say rather than a different way of counting, and somebody
 * who has not opened a book today has not failed at anything.
 */
@Composable
internal fun streakLine(days: Int): String = if (days <= 0) {
    stringResource(Res.string.stats_no_run)
} else {
    pluralStringResource(Res.plurals.stats_days_in_a_row, days, days)
}
