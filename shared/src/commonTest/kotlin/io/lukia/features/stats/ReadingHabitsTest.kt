package io.lukia.features.stats

import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.stats.domain.readingHabits
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the reader's sittings add up to, on a fixed day in a fixed place.
 *
 * Both are fixed on purpose: "how many days in a row" is a question whose answer
 * changes at midnight, and a test that asked it of the real clock would be a
 * test that failed overnight.
 *
 * The day chosen is a Tuesday, so the calendar week the chart draws - Monday to
 * Sunday - has one day behind today, five ahead of it, and a boundary two days
 * back that a rolling seven-day window would not have had.
 */
class ReadingHabitsTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)
    private val monday = LocalDate(2026, 8, 24)
    private val sunday = LocalDate(2026, 8, 30)

    /** A sitting of [minutes], [daysAgo] days before [today], at noon. */
    private fun sitting(daysAgo: Int, minutes: Int, location: String = "/library/book.pdf") =
        ReadingSession(
            location = location,
            startedAt = LocalDateTime(
                date = today.minus(daysAgo, DateTimeUnit.DAY),
                time = kotlinx.datetime.LocalTime(12, 0),
            ).toInstant(zone).toEpochMilliseconds(),
            durationMs = minutes * 60_000L,
        )

    private fun habitsOf(vararg sessions: ReadingSession) =
        readingHabits(sessions.toList(), today, zone)

    @Test
    fun nothingReadIsNothingToReport() {
        val habits = habitsOf()

        assertTrue(habits.isEmpty)
        assertEquals(0, habits.currentStreak)
    }

    @Test
    fun everySittingAddsToTheTotal() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 40, minutes = 10))

        assertEquals(30 * 60_000L, habits.totalMs)
    }

    @Test
    fun todayCountsOnlyTodaysSittings() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 1, minutes = 45))

        assertEquals(20 * 60_000L, habits.todayMs)
    }

    /**
     * The chart is the calendar week rather than the last seven days, which is
     * what stops its labels coming out in a different order every morning.
     */
    @Test
    fun theChartRunsMondayToSundayOfThisWeek() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 10))

        assertEquals(7, habits.days.size)
        assertEquals(monday, habits.days.first().date)
        assertEquals(sunday, habits.days.last().date)
    }

    @Test
    fun theWeekStopsAtItsOwnMonday() {
        val habits = habitsOf(
            sitting(daysAgo = 0, minutes = 10),
            sitting(daysAgo = 1, minutes = 10),
            // The Sunday before, which belongs to last week rather than this one.
            sitting(daysAgo = 2, minutes = 99),
        )

        assertEquals(20 * 60_000L, habits.weekMs)
    }

    @Test
    fun theDaysStillToComeAreMarkedAsSuch() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 10))

        assertEquals(5, habits.days.count { it.isFuture })
        assertEquals(listOf(today), habits.days.filter { it.isToday }.map { it.date })
        assertFalse(habits.days.first { it.date == monday }.isFuture)
    }

    /**
     * Divided by the days lived through rather than by seven, so a good Monday
     * is not quartered by the five days nobody has reached yet.
     */
    @Test
    fun theDailyAverageIgnoresTheDaysStillToCome() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 30), sitting(daysAgo = 1, minutes = 10))

        assertEquals(20 * 60_000L, habits.dailyAverageMs)
    }

    @Test
    fun aDayWithNothingOnItIsStillADayOnTheChart() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 10))

        assertEquals(1, habits.days.count { it.hasReading })
        assertEquals(6, habits.days.count { !it.hasReading })
    }

    @Test
    fun sittingsOnTheSameDayAreOneDay() {
        val habits = habitsOf(
            sitting(daysAgo = 1, minutes = 10),
            sitting(daysAgo = 1, minutes = 25, location = "/library/other.pdf"),
        )

        assertEquals(35 * 60_000L, habits.days.first { it.date == monday }.durationMs)
        assertEquals(2, habits.sittings)
    }

    @Test
    fun daysReadInARowAreCountedBackFromToday() {
        val habits = habitsOf(
            sitting(daysAgo = 0, minutes = 5),
            sitting(daysAgo = 1, minutes = 5),
            sitting(daysAgo = 2, minutes = 5),
            sitting(daysAgo = 4, minutes = 5),
        )

        assertEquals(3, habits.currentStreak)
    }

    /**
     * Somebody looking at this at breakfast has not yet failed to read today,
     * and their run should not be over because it is nine in the morning.
     */
    @Test
    fun aRunSurvivesADayThatHasNotHappenedYet() {
        val habits = habitsOf(sitting(daysAgo = 1, minutes = 5), sitting(daysAgo = 2, minutes = 5))

        assertEquals(2, habits.currentStreak)
    }

    @Test
    fun aGapOfTwoDaysEndsTheRun() {
        val habits = habitsOf(sitting(daysAgo = 2, minutes = 5), sitting(daysAgo = 3, minutes = 5))

        assertEquals(0, habits.currentStreak)
    }

    @Test
    fun theLongestRunIsRememberedEvenAfterItIsBroken() {
        val habits = habitsOf(
            sitting(daysAgo = 0, minutes = 5),
            sitting(daysAgo = 10, minutes = 5),
            sitting(daysAgo = 11, minutes = 5),
            sitting(daysAgo = 12, minutes = 5),
            sitting(daysAgo = 13, minutes = 5),
        )

        assertEquals(1, habits.currentStreak)
        assertEquals(4, habits.longestStreak)
    }

    /** A book opened and closed again is not a day's reading. */
    @Test
    fun aSittingOfNoLengthDoesNotHoldARunTogether() {
        val habits = habitsOf(
            sitting(daysAgo = 0, minutes = 5),
            sitting(daysAgo = 1, minutes = 0),
            sitting(daysAgo = 2, minutes = 5),
        )

        assertEquals(1, habits.currentStreak)
    }

    /**
     * The chart scales to the busiest day of the week it draws; the best day
     * ever is counted over every day there has been.
     */
    @Test
    fun theBusiestDayOfTheWeekIsNotTheBestDayEver() {
        val habits = habitsOf(sitting(daysAgo = 0, minutes = 10), sitting(daysAgo = 30, minutes = 90))

        assertEquals(10 * 60_000L, habits.busiestDayMs)
        assertEquals(90 * 60_000L, habits.longestDayMs)
    }
}
