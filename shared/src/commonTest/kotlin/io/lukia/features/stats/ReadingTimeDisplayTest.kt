package io.lukia.features.stats

import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.readingTime
import io.lukia.features.stats.presentation.readingTimeOrDash
import io.lukia.features.stats.presentation.streakLine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/** How the figures on the statistics page are said. */
class ReadingTimeDisplayTest {

    private val monday = LocalDate(2026, 8, 24)

    @Test
    fun aStretchTooShortToCountIsNotRoundedUpToAMinute() {
        assertEquals("Under a minute", readingTime(40_000L))
        assertEquals("None yet", readingTime(0L))
    }

    @Test
    fun minutesBecomeHoursAndMinutes() {
        assertEquals("35m", readingTime(35 * 60_000L))
        assertEquals("1h 35m", readingTime(95 * 60_000L))
        assertEquals("2h", readingTime(120 * 60_000L))
    }

    /** Past ten hours the minutes are noise on a row that carries three figures. */
    @Test
    fun longStretchesDropTheirMinutes() {
        assertEquals("12h", readingTime(12 * 60 * 60_000L + 4 * 60_000L))
    }

    /** A figure standing under its own label has the label to explain it. */
    @Test
    fun nothingUnderALabelIsADashRatherThanASentence() {
        assertEquals("—", readingTimeOrDash(0L))
        assertEquals("20m", readingTimeOrDash(20 * 60_000L))
    }

    /**
     * The whole point of three letters: seven single ones give T twice and S
     * twice, and a week with two pairs of identical labels reads as unordered.
     */
    @Test
    fun theSevenDayLabelsAreAllDifferentFromEachOther() {
        val week = (0 until 7).map { dayLabel(monday.plus(it, DateTimeUnit.DAY)) }

        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), week)
        assertEquals(week.size, week.toSet().size)
    }

    /** A nought beside the word "streak" reads as a failure rather than a count. */
    @Test
    fun aRunIsSaidAsASentenceRatherThanANumber() {
        assertEquals("No run going", streakLine(0))
        assertEquals("1 day in a row", streakLine(1))
        assertEquals("4 days in a row", streakLine(4))
    }
}
