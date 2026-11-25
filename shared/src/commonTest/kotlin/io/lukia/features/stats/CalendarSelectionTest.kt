package io.lukia.features.stats

import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.ReadingHistory
import io.lukia.features.stats.domain.ReadingPeriod
import io.lukia.features.stats.presentation.CalendarSelection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the reading time card's calendar stands, on Wednesday 11 March 2026,
 * with reading from November 2025.
 */
class CalendarSelectionTest {

    private val today = LocalDate(2026, Month.MARCH, 11)

    private fun day(month: Month, day: Int, year: Int = 2026) = LocalDate(year, month, day)

    private fun historyOf(vararg days: LocalDate) =
        ReadingHistory(today = today, byDay = days.associateWith { 60_000L })

    private val history =
        historyOf(day(Month.NOVEMBER, 4, year = 2025), day(Month.FEBRUARY, 3), day(Month.FEBRUARY, 17))

    private fun month(on: LocalDate) = ReadingPeriod.containing(PeriodKind.Month, on)

    @Test
    fun itOpensOnThisMonthWithTodaySelected() {
        val selection = CalendarSelection()
        val shown = selection.shownPeriod(history)

        assertEquals(month(today), shown)
        assertEquals(today, selection.selectedDay(history, shown))
    }

    @Test
    fun aPastMonthOpensOnItsLastDayWithReading() {
        val february = CalendarSelection(month = day(Month.FEBRUARY, 1))

        assertEquals(day(Month.FEBRUARY, 17), february.selectedDay(history, february.shownPeriod(history)))
    }

    @Test
    fun aPastMonthWithoutReadingOpensOnItsLastDay() {
        val january = CalendarSelection(month = day(Month.JANUARY, 1))

        assertEquals(day(Month.JANUARY, 31), january.selectedDay(history, january.shownPeriod(history)))
    }

    @Test
    fun aTappedDayIsKeptWhileItsMonthIsShown() {
        val selection = CalendarSelection(day = day(Month.MARCH, 2))

        assertEquals(day(Month.MARCH, 2), selection.selectedDay(history, selection.shownPeriod(history)))
    }

    @Test
    fun zoomedOutTheYearOfTheMonthIsShown() {
        val selection = CalendarSelection(month = day(Month.DECEMBER, 1, year = 2025), zoomed = false)

        assertEquals(
            ReadingPeriod.containing(PeriodKind.Year, day(Month.JUNE, 1, year = 2025)),
            selection.shownPeriod(history),
        )
    }

    @Test
    fun aMonthBeforeTheFirstReadIsHeldToTheFirst() {
        val selection = CalendarSelection(month = day(Month.MARCH, 1, year = 2025))

        assertEquals(month(day(Month.NOVEMBER, 1, year = 2025)), selection.shownMonth(history))
    }

    @Test
    fun aMonthAfterThisOneIsHeldToThisOne() {
        val selection = CalendarSelection(month = day(Month.MARCH, 1, year = 2027))

        assertEquals(month(today), selection.shownMonth(history))
    }
}
