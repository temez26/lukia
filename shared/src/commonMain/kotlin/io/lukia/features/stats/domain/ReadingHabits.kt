package io.lukia.features.stats.domain

import io.lukia.core.domain.reading.ReadingSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Monday to Sunday, which is the week [readingHabits] charts. */
private const val DaysInWeek = 7

/**
 * One day's reading, for the chart and for the runs counted through it.
 *
 * [isFuture] exists because the chart shows a calendar week rather than the last
 * seven days: on a Tuesday, Wednesday to Sunday are drawn as columns that have
 * not happened yet. A day nobody has reached is not a day with no reading on it,
 * and the two must not be drawn - or averaged - the same way.
 */
data class DayReading(
    val date: LocalDate,
    val durationMs: Long,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
) {
    val hasReading: Boolean get() = durationMs > 0
}

/**
 * What the reader's time adds up to.
 *
 * Every figure here comes from sittings the app recorded while a book was
 * actually open - see [ReadingSession]. Nothing is estimated from page counts,
 * and there is no figure for reading done before the app began counting, which
 * is why a long-standing library can show a large shelf and a small clock.
 */
data class ReadingHabits(
    val totalMs: Long,
    val todayMs: Long,
    /** Monday to today of the week [days] covers, so it grows as the week does. */
    val weekMs: Long,
    /** Monday first, one entry per day, including the days still to come. */
    val days: List<DayReading>,
    /** Days read in a row, counting back from today - or from yesterday, see below. */
    val currentStreak: Int,
    val longestStreak: Int,
    val sittings: Int,
    val longestDayMs: Long,
) {

    /** True until the app has recorded any reading at all. */
    val isEmpty: Boolean get() = totalMs <= 0

    /** The busiest day of the week shown, which is what the chart scales itself to. */
    val busiestDayMs: Long get() = days.maxOfOrNull { it.durationMs } ?: 0L

    /**
     * The week's reading spread over the days of it that have happened.
     *
     * Divided by the days reached rather than by seven, so a good Monday is not
     * quartered by the six days nobody has lived through yet. Days with nothing
     * on them still count: a day off is part of how much somebody reads.
     */
    val dailyAverageMs: Long
        get() = days.count { !it.isFuture }.takeIf { it > 0 }?.let { weekMs / it } ?: 0L

    companion object {
        val Empty = ReadingHabits(
            totalMs = 0,
            todayMs = 0,
            weekMs = 0,
            days = emptyList(),
            currentStreak = 0,
            longestStreak = 0,
            sittings = 0,
            longestDayMs = 0,
        )
    }
}

/**
 * Reduces every sitting ever recorded to what the statistics show.
 *
 * A pure function of the sittings and the day it is asked on, so the same
 * history gives the same answer wherever it is asked and can be tested without
 * a clock or a database.
 *
 * [zone] is the reader's own, because a day is a local thing: a sitting at
 * eleven at night in Helsinki is that day's reading and not the next one's,
 * whatever UTC thinks.
 */
internal fun readingHabits(
    sessions: List<ReadingSession>,
    today: LocalDate,
    zone: TimeZone,
): ReadingHabits {
    if (sessions.isEmpty()) return ReadingHabits.Empty

    val byDay = sessions
        .groupBy { Instant.fromEpochMilliseconds(it.startedAt).toLocalDateTime(zone).date }
        .mapValues { (_, sittings) -> sittings.sumOf { it.durationMs } }

    // A sitting of no length is a book opened and closed again, which is not a
    // day's reading and must not hold a run together.
    val readDays = byDay.filterValues { it > 0 }.keys

    val week = weekOf(today).map { date ->
        DayReading(
            date = date,
            durationMs = byDay[date] ?: 0L,
            isToday = date == today,
            isFuture = date > today,
        )
    }

    return ReadingHabits(
        totalMs = sessions.sumOf { it.durationMs },
        todayMs = byDay[today] ?: 0L,
        weekMs = week.filterNot { it.isFuture }.sumOf { it.durationMs },
        days = week,
        currentStreak = currentStreak(readDays, today),
        longestStreak = longestStreak(readDays),
        sittings = sessions.size,
        longestDayMs = byDay.values.maxOrNull() ?: 0L,
    )
}

/**
 * The calendar week [day] falls in, Monday first.
 *
 * A calendar week rather than the last seven days, because seven bars ending on
 * today are seven weekdays in an order that changes daily - a chart whose labels
 * read "Thu Fri Sat Sun Mon Tue Wed" is read as broken before it is read as
 * rolling. Monday first because that is where a week starts everywhere this app
 * is written for, and because it keeps the two days off together at the end
 * where the shape of a weekend is visible.
 */
private fun weekOf(day: LocalDate): List<LocalDate> {
    val monday = day.minusDays(day.dayOfWeek.isoDayNumber - 1)

    return (0 until DaysInWeek).map { monday.plusDays(it) }
}

/**
 * Days read in a row, counting back from [today].
 *
 * A day that has not been read yet does not break the run. Somebody looking at
 * this at breakfast has not yet failed to read today, and telling them their
 * eleven-day streak is over because it is nine in the morning would be both
 * wrong and discouraging - so the count starts at today when there is reading on
 * it and at yesterday when there is not.
 */
private fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in days) today else today.minusDays(1)
    var run = 0

    while (day in days) {
        run++
        day = day.minusDays(1)
    }

    return run
}

/** The longest run of consecutive days there has ever been. */
private fun longestStreak(days: Set<LocalDate>): Int {
    if (days.isEmpty()) return 0

    var longest = 0
    var run = 0
    var previous: LocalDate? = null

    days.sorted().forEach { day ->
        run = if (previous?.plusDays(1) == day) run + 1 else 1
        previous = day
        longest = maxOf(longest, run)
    }

    return longest
}

private fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)

private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
