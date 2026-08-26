package io.lukia.features.stats.presentation

import kotlinx.datetime.LocalDate

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

/** How much of a weekday's name fits under a bar of the chart - see [dayLabel]. */
private const val DayLabelLetters = 3

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
 */
internal fun readingTime(millis: Long): String {
    if (millis <= 0) return "None yet"

    val minutes = millis / MillisPerMinute

    if (minutes < 1) return "Under a minute"

    val hours = minutes / MinutesPerHour
    val rest = minutes % MinutesPerHour

    return when {
        hours <= 0 -> "${minutes}m"
        hours >= HoursPastWhichMinutesStopMattering -> "${hours}h"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}m"
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
internal fun readingTimeOrDash(millis: Long): String =
    if (millis <= 0) "—" else readingTime(millis)

/**
 * A day, as the label under one bar of the chart.
 *
 * Three letters rather than one. Seven single letters spell out T and T and S
 * and S, so a week reads as a row with two pairs of identical labels in it and
 * gives no clue which Tuesday is which - which is exactly the moment a chart
 * stops looking ordered. Three letters fit across the narrowest window this app
 * runs in and each one names a day on sight.
 *
 * Taken from the day's own name rather than from a table of translations this
 * app does not have - the rest of its chrome is in English too, and a lookup
 * that only ever answers in one language is a lookup pretending to be more.
 */
internal fun dayLabel(date: LocalDate): String = dayName(date).take(DayLabelLetters)

/** The day spelled out, for the sentence a screen reader is given. */
internal fun dayName(date: LocalDate): String =
    date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }

/**
 * How many days in a row, said as a sentence rather than as a number with a
 * label under it.
 *
 * "1 day" rather than "1 days", and "No run going" rather than a zero - a nought
 * beside the word "streak" reads as a failure, and somebody who has not opened a
 * book today has not failed at anything.
 */
internal fun streakLine(days: Int): String = when (days) {
    0 -> "No run going"
    1 -> "1 day in a row"
    else -> "$days days in a row"
}
