package io.lukia.features.stats.presentation.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.ReadingSession
import io.lukia.features.stats.domain.readingHabits
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test

/**
 * How the reading time reads, on a fixed Tuesday so the week is always the same
 * one - Monday the 24th to Sunday the 30th of August 2026.
 */
@OptIn(ExperimentalTestApi::class)
class TimeSectionsTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)

    private fun sitting(daysAgo: Int, minutes: Int) = ReadingSession(
        location = "/library/book.pdf",
        startedAt = LocalDateTime(today.minus(daysAgo, DateTimeUnit.DAY), LocalTime(12, 0))
            .toInstant(zone)
            .toEpochMilliseconds(),
        durationMs = minutes * 60_000L,
    )

    private fun habitsOf(sessions: List<ReadingSession>) = readingHabits(sessions, today, zone)

    private fun ComposeUiTest.setWeek(vararg sessions: ReadingSession) {
        setContent {
            LukiaTheme(darkTheme = false) {
                ThisWeekSection(
                    habits = habitsOf(sessions.toList()),
                    groupColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }

    private fun ComposeUiTest.setAllTime(vararg sessions: ReadingSession) {
        setContent {
            LukiaTheme(darkTheme = false) {
                AllTimeSection(
                    habits = habitsOf(sessions.toList()),
                    groupColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }

    @Test
    fun todayLeadsTheCardInHoursAndMinutes() = runComposeUiTest {
        setWeek(sitting(daysAgo = 0, minutes = 95))

        onNodeWithContentDescription("Today: 1h 35m").assertIsDisplayed()
    }

    @Test
    fun theWeekIsTotalledAndAveragedUnderTheChart() = runComposeUiTest {
        setWeek(sitting(daysAgo = 0, minutes = 30), sitting(daysAgo = 1, minutes = 10))

        onNodeWithContentDescription("40m This week").assertIsDisplayed()
        onNodeWithContentDescription("20m Daily average").assertIsDisplayed()
    }

    /**
     * Seven bars, Monday first, each of which says what it stands for - and the
     * days nobody has reached yet say so rather than reporting nothing read.
     */
    @Test
    fun everyDayOfTheCalendarWeekIsOnTheChart() = runComposeUiTest {
        setWeek(sitting(daysAgo = 0, minutes = 20))

        onNodeWithContentDescription("Tuesday: 20m").assertIsDisplayed()
        onNodeWithContentDescription("Monday: None yet").assertIsDisplayed()
        onNodeWithContentDescription("Sunday: still to come").assertIsDisplayed()
    }

    @Test
    fun daysInARowRideBesideToday() = runComposeUiTest {
        setWeek(
            sitting(daysAgo = 0, minutes = 10),
            sitting(daysAgo = 1, minutes = 10),
            sitting(daysAgo = 2, minutes = 10),
        )

        onNodeWithText("3 days in a row").assertIsDisplayed()
    }

    /** Nothing counted yet is nothing to show, rather than a card of zeroes. */
    @Test
    fun aReaderWhoHasNotBeenTimedYetSeesNoneOfThis() = runComposeUiTest {
        setWeek()

        onNodeWithText("This week").assertDoesNotExist()
    }

    @Test
    fun theAllTimeCardCountsSittingsAndTheBestDayThereHasBeen() = runComposeUiTest {
        setAllTime(
            sitting(daysAgo = 0, minutes = 20),
            sitting(daysAgo = 40, minutes = 90),
        )

        onNodeWithContentDescription("1h 50m Time read").assertIsDisplayed()
        onNodeWithContentDescription("2 Sittings").assertIsDisplayed()
        onNodeWithContentDescription("1h 30m Best day").assertIsDisplayed()
    }

    @Test
    fun theLongestRunIsKeptWithTheAllTimeFigures() = runComposeUiTest {
        setAllTime(
            sitting(daysAgo = 20, minutes = 10),
            sitting(daysAgo = 21, minutes = 10),
            sitting(daysAgo = 22, minutes = 10),
        )

        onNodeWithContentDescription("Longest run: 3 days in a row").assertIsDisplayed()
    }
}
