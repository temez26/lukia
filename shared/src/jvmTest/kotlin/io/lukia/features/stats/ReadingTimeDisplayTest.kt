package io.lukia.features.stats

import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.readingTime
import io.lukia.features.stats.presentation.readingTimeOrDash
import io.lukia.features.stats.presentation.streakLine
import io.lukia.shared.composed
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the figures on the statistics page are said.
 *
 * In `jvmTest` rather than `commonTest` for two reasons that arrived together:
 * the words come out of the resources now and so need a composition, and the day
 * names come from the platform and so need a locale to be set.
 */
class ReadingTimeDisplayTest {

    private val monday = LocalDate(2026, 8, 24)
    private val wasDefault: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun aStretchTooShortToCountIsNotRoundedUpToAMinute() {
        assertEquals("Under a minute", composed { readingTime(40_000L) })
        assertEquals("None yet", composed { readingTime(0L) })
    }

    @Test
    fun minutesBecomeHoursAndMinutes() {
        assertEquals("35m", composed { readingTime(35 * 60_000L) })
        assertEquals("1h 35m", composed { readingTime(95 * 60_000L) })
        assertEquals("2h", composed { readingTime(120 * 60_000L) })
    }

    /** Past ten hours the minutes are noise on a row that carries three figures. */
    @Test
    fun longStretchesDropTheirMinutes() {
        assertEquals("12h", composed { readingTime(12 * 60 * 60_000L + 4 * 60_000L) })
    }

    /** A figure standing under its own label has the label to explain it. */
    @Test
    fun nothingUnderALabelIsADashRatherThanASentence() {
        assertEquals("—", composed { readingTimeOrDash(0L) })
        assertEquals("20m", composed { readingTimeOrDash(20 * 60_000L) })
    }

    /**
     * The whole point of an abbreviation: seven single letters give T twice and
     * S twice, and a week with two pairs of identical labels reads as unordered.
     */
    @Test
    fun theSevenDayLabelsAreAllDifferentFromEachOther() {
        Locale.setDefault(Locale.ENGLISH)

        val week = week()

        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), week)
        assertEquals(week.size, week.toSet().size)
    }

    /**
     * The reason this stopped being a table. The labels used to be the first
     * three letters of Kotlin's own enum constant, which is English whatever the
     * device is set to.
     *
     * German is the case that shows truncation could never have worked: its
     * abbreviations are two letters and a full stop, so three letters of the
     * full name would have given "Mon", "Die", "Mit" - English's shape wearing
     * German's spelling.
     */
    @Test
    fun theDaysAreNamedInTheLanguageTheDeviceIsSetTo() {
        Locale.setDefault(Locale.GERMAN)

        val week = week()

        assertEquals(listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So."), week)
    }

    /** A nought beside the word "streak" reads as a failure rather than a count. */
    @Test
    fun aRunIsSaidAsASentenceRatherThanANumber() {
        assertEquals("No run going", composed { streakLine(0) })
        assertEquals("1 day in a row", composed { streakLine(1) })
        assertEquals("4 days in a row", composed { streakLine(4) })
    }

    private fun week() = (0 until 7).map { dayLabel(monday.plus(it, DateTimeUnit.DAY)) }
}
