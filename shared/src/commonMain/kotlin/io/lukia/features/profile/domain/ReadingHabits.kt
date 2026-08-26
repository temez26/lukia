package io.lukia.features.profile.domain

import io.lukia.core.domain.ReadingSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How many days the chart at the top of the profile shows.
 *
 * A week, because a week is the unit people actually keep reading in - one
 * quiet Tuesday means nothing next to the shape of a fortnight, and a month of
 * bars on a phone is a row of hairlines. Seven bars are also seven labels, which
 * fit across the narrowest window this app runs in.
 */
private const val ChartDays = 7

/** One day's reading, for the chart and for the runs counted through it. */
data class DayReading(val date: LocalDate, val durationMs: Long) {
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
    /** The last seven days including today, which is what [days] covers. */
    val weekMs: Long,
    /** Oldest first, one entry per day, including the days with nothing on them. */
    val days: List<DayReading>,
    /** Days read in a row, counting back from today - or from yesterday, see below. */
    val currentStreak: Int,
    val longestStreak: Int,
    val sittings: Int,
    val longestDayMs: Long,
) {

    /** True until the app has recorded any reading at all. */
    val isEmpty: Boolean get() = totalMs <= 0

    /** The busiest of the seven days, which is what the chart scales itself to. */
    val busiestDayMs: Long get() = days.maxOfOrNull { it.durationMs } ?: 0L

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
 * Reduces every sitting ever recorded to what the profile shows.
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

    val week = (ChartDays - 1 downTo 0).map { back ->
        val date = today.minusDays(back)

        DayReading(date = date, durationMs = byDay[date] ?: 0L)
    }

    return ReadingHabits(
        totalMs = sessions.sumOf { it.durationMs },
        todayMs = byDay[today] ?: 0L,
        weekMs = week.sumOf { it.durationMs },
        days = week,
        currentStreak = currentStreak(readDays, today),
        longestStreak = longestStreak(readDays),
        sittings = sessions.size,
        longestDayMs = byDay.values.maxOrNull() ?: 0L,
    )
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
