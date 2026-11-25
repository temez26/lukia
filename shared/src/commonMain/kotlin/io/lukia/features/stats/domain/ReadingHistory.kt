package io.lukia.features.stats.domain

import io.lukia.core.domain.reading.ReadingSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How much of the calendar the statistics page looks at at once. */
enum class PeriodKind { Month, Year }

/** A calendar month or year, both ends included. */
data class ReadingPeriod(
    val kind: PeriodKind,
    val start: LocalDate,
    val end: LocalDate,
) {
    val length: Int get() = start.daysUntil(end) + 1

    operator fun contains(day: LocalDate): Boolean = day in start..end

    /** The period [steps] whole periods later, or earlier when negative. */
    fun shiftedBy(steps: Int): ReadingPeriod = when (kind) {
        PeriodKind.Month -> containing(kind, start.plus(steps, DateTimeUnit.MONTH))
        PeriodKind.Year -> containing(kind, start.plus(steps, DateTimeUnit.YEAR))
    }

    companion object {
        fun containing(kind: PeriodKind, day: LocalDate): ReadingPeriod = when (kind) {
            PeriodKind.Month -> {
                val first = LocalDate(day.year, day.month, 1)
                ReadingPeriod(kind, first, first.plus(1, DateTimeUnit.MONTH).minusDays(1))
            }

            PeriodKind.Year -> ReadingPeriod(
                kind,
                LocalDate(day.year, Month.JANUARY, 1),
                LocalDate(day.year + 1, Month.JANUARY, 1).minusDays(1),
            )
        }
    }
}

/**
 * Every day there is reading on, and the day it is now: what any month or year
 * on the page is worked out from.
 *
 * Grouped by day once per count rather than once per period, because the reader
 * pages back and forth through periods far more often than the count is redone.
 */
data class ReadingHistory(
    val today: LocalDate,
    val byDay: Map<LocalDate, Long>,
    /** How many sittings each day was read in. */
    val sittingsByDay: Map<LocalDate, Int> = emptyMap(),
) {
    /** The months paging can reach: from the first one read in to today's. */
    val months: List<ReadingPeriod>
        get() {
            val current = ReadingPeriod.containing(PeriodKind.Month, today)
            var month = ReadingPeriod.containing(PeriodKind.Month, firstDay ?: today)

            return buildList {
                while (month.start <= current.start) {
                    add(month)
                    month = month.shiftedBy(1)
                }
            }
        }

    /** The years paging can reach, first to this one. */
    val years: List<Int> get() = ((firstDay ?: today).year..today.year).toList()

    /** The first day anything was read on, which is as far back as paging goes. */
    val firstDay: LocalDate? = byDay.filterValues { it > 0 }.keys.minOrNull()

    val isEmpty: Boolean get() = firstDay == null

    companion object {
        val Empty = ReadingHistory(today = LocalDate(1970, Month.JANUARY, 1), byDay = emptyMap())
    }
}

internal fun readingHistory(
    sessions: List<ReadingSession>,
    today: LocalDate,
    zone: TimeZone,
): ReadingHistory = ReadingHistory(
    today = today,
    byDay = dailyTotals(sessions, zone),
    sittingsByDay = sessions.groupingBy { it.localDate(zone) }.eachCount(),
)

/** One month of a year, for the year's chart. */
data class MonthReading(
    val month: Month,
    val durationMs: Long,
    val isCurrent: Boolean,
    val isFuture: Boolean,
)

/**
 * What one week, month or year holds.
 *
 * Averages divide by [daysReached], the days of the period that have happened,
 * for the reason [ReadingHabits.dailyAverageMs] does.
 */
data class PeriodReading(
    val period: ReadingPeriod,
    /** Every day of the period, including those still to come. */
    val days: List<DayReading>,
    /** January to December for a year, and empty for a week or a month. */
    val months: List<MonthReading>,
    val totalMs: Long,
    val daysReached: Int,
    val daysRead: Int,
    /** Times a book was opened and read in the days reached. */
    val sittings: Int,
    val longestRun: Int,
    /**
     * What the period before held over the same number of days, or null when
     * nothing was read in them and there is nothing to compare with.
     */
    val previousMs: Long?,
    /** True when today falls in this period, so [previousMs] is "by this point". */
    val isCurrent: Boolean,
    val hasEarlier: Boolean,
    val hasLater: Boolean,
) {
    val busiestDayMs: Long get() = days.maxOfOrNull { it.durationMs } ?: 0L

    val dailyAverageMs: Long get() = if (daysReached > 0) totalMs / daysReached else 0L

    val busiestMonth: MonthReading? get() = months.filter { it.durationMs > 0 }.maxByOrNull { it.durationMs }

    /** The rise or fall against [previousMs], in whole percent. */
    val changePercent: Int?
        get() = previousMs?.let { previous -> ((totalMs - previous) * PercentScale / previous).toInt() }
}

private const val PercentScale = 100L

/**
 * The period of [kind] that lies [offset] periods from the one today is in -
 * zero for the current one, negative for those before it.
 */
fun ReadingHistory.periodReading(kind: PeriodKind, offset: Int): PeriodReading =
    periodReading(ReadingPeriod.containing(kind, today).shiftedBy(offset))

/** What [period] holds - any week, month or year, not only one paged to. */
fun ReadingHistory.periodReading(period: ReadingPeriod): PeriodReading {
    val kind = period.kind
    val current = ReadingPeriod.containing(kind, today)
    val reached = period.daysUpTo(today)
    val total = reached.sumOf { byDay[it] ?: 0L }

    return PeriodReading(
        period = period,
        days = period.allDays().map { date ->
            DayReading(
                date = date,
                durationMs = byDay[date] ?: 0L,
                isToday = date == today,
                isFuture = date > today,
            )
        },
        months = if (kind == PeriodKind.Year) monthsOf(period) else emptyList(),
        totalMs = total,
        daysReached = reached.size,
        daysRead = reached.count { (byDay[it] ?: 0L) > 0 },
        sittings = reached.sumOf { sittingsByDay[it] ?: 0 },
        longestRun = longestStreak(reached.filter { (byDay[it] ?: 0L) > 0 }.toSet()),
        previousMs = previousSpan(period, reached.size).sumOf { byDay[it] ?: 0L }.takeIf { it > 0 },
        isCurrent = today in period,
        hasEarlier = firstDay?.let { period.start > ReadingPeriod.containing(kind, it).start } ?: false,
        hasLater = period.start < current.start,
    )
}

/**
 * The day selected when a period opens: today when it holds today, otherwise
 * its last day with reading, otherwise its last day.
 */
fun ReadingHistory.defaultDay(period: ReadingPeriod): LocalDate = when {
    today in period -> today
    else -> period.allDays().lastOrNull { (byDay[it] ?: 0L) > 0 } ?: period.end
}

/**
 * The opening [days] of the period before [period], so a month nine days old is
 * held against the first nine days of the month before rather than all of it.
 */
private fun previousSpan(period: ReadingPeriod, days: Int): List<LocalDate> {
    val previous = period.shiftedBy(-1)

    return previous.allDays().take(days)
}

private fun ReadingHistory.monthsOf(year: ReadingPeriod): List<MonthReading> =
    Month.entries.map { month ->
        val period = ReadingPeriod.containing(PeriodKind.Month, LocalDate(year.start.year, month, 1))

        MonthReading(
            month = month,
            durationMs = period.daysUpTo(today).sumOf { byDay[it] ?: 0L },
            isCurrent = today in period,
            isFuture = period.start > today,
        )
    }

private fun ReadingPeriod.allDays(): List<LocalDate> = (0 until length).map { start.plusDays(it) }

private fun ReadingPeriod.daysUpTo(today: LocalDate): List<LocalDate> = allDays().filter { it <= today }
