package io.lukia.features.stats

import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.ReadingHistory
import io.lukia.features.stats.domain.ReadingPeriod
import io.lukia.features.stats.domain.periodReading
import io.lukia.features.stats.domain.readingHistory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Months and years paged back from a fixed day.
 *
 * Wednesday 11 March 2026: mid-week, mid-month, early in a year, and in a month
 * after a February of 28 days, so a month held against the one before is cut to
 * the same length.
 */
class ReadingHistoryTest {

    private val today = LocalDate(2026, Month.MARCH, 11)

    private fun historyOf(vararg days: Pair<LocalDate, Int>) =
        ReadingHistory(today = today, byDay = days.associate { (day, minutes) -> day to minutes * 60_000L })

    private fun day(month: Month, day: Int, year: Int = 2026) = LocalDate(year, month, day)

    @Test
    fun aMonthEndsOnItsLastDay() {
        val february = ReadingPeriod.containing(PeriodKind.Month, day(Month.FEBRUARY, 14))

        assertEquals(day(Month.FEBRUARY, 1), february.start)
        assertEquals(day(Month.FEBRUARY, 28), february.end)
    }

    @Test
    fun steppingBackFromAMonthLandsOnTheWholeMonthBefore() {
        val march = ReadingPeriod.containing(PeriodKind.Month, today)

        assertEquals(ReadingPeriod.containing(PeriodKind.Month, day(Month.JANUARY, 1)), march.shiftedBy(-2))
    }

    @Test
    fun theCurrentMonthCountsOnlyTheDaysThatHaveHappened() {
        val month = historyOf(day(Month.MARCH, 2) to 30, day(Month.MARCH, 11) to 10).periodReading(PeriodKind.Month, 0)

        assertEquals(31, month.days.size)
        assertEquals(11, month.daysReached)
        assertEquals(2, month.daysRead)
        assertEquals(40 * 60_000L, month.totalMs)
        assertEquals(40 * 60_000L / 11, month.dailyAverageMs)
        assertTrue(month.days.last().isFuture)
    }

    @Test
    fun aPastMonthHasEveryDayReached() {
        val february = historyOf(day(Month.FEBRUARY, 3) to 20).periodReading(PeriodKind.Month, -1)

        assertEquals(28, february.daysReached)
        assertTrue(february.days.none { it.isFuture })
        assertFalse(february.isCurrent)
    }

    @Test
    fun theLongestRunStaysInsideThePeriod() {
        val month = historyOf(
            day(Month.FEBRUARY, 28) to 10,
            day(Month.MARCH, 1) to 10,
            day(Month.MARCH, 2) to 10,
            day(Month.MARCH, 5) to 10,
        ).periodReading(PeriodKind.Month, 0)

        assertEquals(2, month.longestRun)
    }

    /** Eleven days of March against the first eleven of February, not all of it. */
    @Test
    fun theCurrentPeriodIsComparedWithTheSameDaysOfThePeriodBefore() {
        val month = historyOf(
            day(Month.FEBRUARY, 5) to 40,
            day(Month.FEBRUARY, 20) to 500,
            day(Month.MARCH, 4) to 60,
        ).periodReading(PeriodKind.Month, 0)

        assertEquals(40 * 60_000L, month.previousMs)
        assertEquals(50, month.changePercent)
    }

    @Test
    fun nothingReadInThePeriodBeforeLeavesNothingToCompare() {
        val month = historyOf(day(Month.MARCH, 4) to 60).periodReading(PeriodKind.Month, 0)

        assertNull(month.previousMs)
        assertNull(month.changePercent)
    }

    @Test
    fun aYearIsTwelveMonthsWithTheBusiestPickedOut() {
        val year = historyOf(
            day(Month.JANUARY, 10) to 30,
            day(Month.FEBRUARY, 10) to 90,
            day(Month.MARCH, 10) to 20,
        ).periodReading(PeriodKind.Year, 0)

        assertEquals(12, year.months.size)
        assertEquals(Month.FEBRUARY, year.busiestMonth?.month)
        assertTrue(year.months[Month.MARCH.ordinal].isCurrent)
        assertTrue(year.months[Month.APRIL.ordinal].isFuture)
        assertEquals(365, year.days.size)
    }

    @Test
    fun pagingStopsAtTheCurrentPeriodAndAtTheFirstReading() {
        val history = historyOf(day(Month.JANUARY, 20) to 15)

        val current = history.periodReading(PeriodKind.Month, 0)
        val first = history.periodReading(PeriodKind.Month, -2)

        assertFalse(current.hasLater)
        assertTrue(current.hasEarlier)
        assertTrue(first.hasLater)
        assertFalse(first.hasEarlier)
    }

    @Test
    fun aHistoryWithNoReadingHasNowhereToPageBackTo() {
        val month = historyOf().periodReading(PeriodKind.Month, 0)

        assertFalse(month.hasEarlier)
        assertFalse(month.hasLater)
        assertEquals(31, month.days.size)
    }

    @Test
    fun pagingReachesEveryMonthFromTheFirstReadToThisOne() {
        val history = historyOf(day(Month.NOVEMBER, 20, year = 2025) to 15)

        assertEquals(day(Month.NOVEMBER, 1, year = 2025), history.months.first().start)
        assertEquals(day(Month.MARCH, 1), history.months.last().start)
        assertEquals(5, history.months.size)
        assertEquals(listOf(2025, 2026), history.years)
    }

    @Test
    fun aPeriodCountsTheSittingsInTheDaysReached() {
        val history = readingHistory(
            listOf(
                sittingOn(day(Month.MARCH, 2), minutes = 10),
                sittingOn(day(Month.MARCH, 2), minutes = 20),
                sittingOn(day(Month.MARCH, 9), minutes = 5),
                sittingOn(day(Month.FEBRUARY, 9), minutes = 5),
            ),
            today,
            TimeZone.UTC,
        )

        assertEquals(3, history.periodReading(PeriodKind.Month, 0).sittings)
        assertEquals(4, history.periodReading(PeriodKind.Year, 0).sittings)
    }

    private fun sittingOn(date: LocalDate, minutes: Int) = ReadingSession(
        location = "/library/book.pdf",
        startedAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds(),
        durationMs = minutes * 60_000L,
    )

    @Test
    fun withNothingReadOnlyThisMonthCanBeShown() {
        assertEquals(listOf(ReadingPeriod.containing(PeriodKind.Month, today)), historyOf().months)
    }
}
