package io.lukia.features.library

import io.lukia.features.library.presentation.model.AddedAgo
import io.lukia.features.library.presentation.model.addedAgo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How long ago a book was added, counted in the reader's calendar days. */
class AddedAgoTest {

    private val zone = TimeZone.of("Europe/Helsinki")
    private val today = LocalDate(2026, 10, 21)

    private fun at(date: LocalDate, hour: Int = 12) =
        LocalDateTime(date, LocalTime(hour, 0)).toInstant(zone).toEpochMilliseconds()

    private fun ago(date: LocalDate, hour: Int = 12) = addedAgo(at(date, hour), today, zone)

    @Test
    fun aBookWithNoRecordHasNoAge() {
        assertNull(addedAgo(null, today, zone))
    }

    @Test
    fun lateLastNightIsYesterday() {
        assertEquals(AddedAgo.Today, ago(today, hour = 0))
        assertEquals(AddedAgo.Yesterday, ago(LocalDate(2026, 10, 20), hour = 23))
    }

    @Test
    fun daysThenWeeksThenMonthsThenYears() {
        assertEquals(AddedAgo.Days(6), ago(LocalDate(2026, 10, 15)))
        assertEquals(AddedAgo.Weeks(1), ago(LocalDate(2026, 10, 14)))
        assertEquals(AddedAgo.Weeks(4), ago(LocalDate(2026, 9, 22)))
        assertEquals(AddedAgo.Months(1), ago(LocalDate(2026, 9, 21)))
        assertEquals(AddedAgo.Months(11), ago(LocalDate(2025, 11, 1)))
        assertEquals(AddedAgo.Years(2), ago(LocalDate(2024, 10, 1)))
    }

    @Test
    fun aDateInTheFutureIsToday() {
        assertEquals(AddedAgo.Today, ago(LocalDate(2026, 10, 25)))
    }
}
