package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.stats.domain.readingHabits
import io.lukia.features.stats.domain.readingHistory
import io.lukia.features.stats.presentation.PeriodSelection
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the reading time reads, on a fixed Tuesday so the week is always the same
 * one - Monday the 24th to Sunday the 30th of August 2026.
 *
 * In English, because month and weekday names come from the platform.
 */
@OptIn(ExperimentalTestApi::class)
class TimeSectionsTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)
    private val wasDefault = Locale.getDefault()

    @BeforeTest
    fun useEnglish() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    private companion object {
        const val BelowTheCard = "belowTheCard"
    }

    private fun sitting(daysAgo: Int, minutes: Int) = ReadingSession(
        location = "/library/book.pdf",
        startedAt = LocalDateTime(today.minus(daysAgo, DateTimeUnit.DAY), LocalTime(12, 0))
            .toInstant(zone)
            .toEpochMilliseconds(),
        durationMs = minutes * 60_000L,
    )

    private fun habitsOf(sessions: List<ReadingSession>) = readingHabits(sessions, today, zone)

    /**
     * The card as the screen holds it: a selection that the controls change, and
     * a mark below it where the next card would start.
     */
    private fun ComposeUiTest.setReadingTime(vararg sessions: ReadingSession) {
        setContent {
            var selection by remember { mutableStateOf(PeriodSelection()) }

            LukiaTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ReadingTimeSection(
                        habits = habitsOf(sessions.toList()),
                        history = readingHistory(sessions.toList(), today, zone),
                        selection = selection,
                        actions = StatsActions(
                            onSelectPeriod = { selection = PeriodSelection(it) },
                            onStepPeriod = { selection = selection.copy(offset = selection.offset + it) },
                        ),
                        groupColor = MaterialTheme.colorScheme.surface,
                    )

                    Spacer(modifier = Modifier.height(1.dp).testTag(BelowTheCard))
                }
            }
        }
    }

    private fun ComposeUiTest.belowTheCard() = onNodeWithTag(BelowTheCard).getUnclippedBoundsInRoot().top

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
        setReadingTime(sitting(daysAgo = 0, minutes = 95))

        onNodeWithContentDescription("Today: 1h 35m").assertIsDisplayed()
    }

    @Test
    fun theWeekIsTotalledAndAveragedUnderTheChart() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 30), sitting(daysAgo = 1, minutes = 10))

        onNodeWithText("This week").assertIsDisplayed()
        onNodeWithContentDescription("40m Time read").assertIsDisplayed()
        onNodeWithContentDescription("20m Daily average").assertIsDisplayed()
        onNodeWithContentDescription("2 of 2 Days read").assertIsDisplayed()
    }

    /**
     * Seven bars, Monday first, each of which says what it stands for - and the
     * days nobody has reached yet say so rather than reporting nothing read.
     */
    @Test
    fun everyDayOfTheCalendarWeekIsOnTheChart() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20))

        onNodeWithContentDescription("Tuesday: 20m").assertIsDisplayed()
        onNodeWithContentDescription("Monday: None yet").assertIsDisplayed()
        onNodeWithContentDescription("Sunday: still to come").assertIsDisplayed()
    }

    @Test
    fun daysInARowRideBesideToday() = runComposeUiTest {
        setReadingTime(
            sitting(daysAgo = 0, minutes = 10),
            sitting(daysAgo = 1, minutes = 10),
            sitting(daysAgo = 2, minutes = 10),
        )

        onNodeWithText("3 days in a row").assertIsDisplayed()
    }

    @Test
    fun noRunGoingIsLeftUnsaid() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 3, minutes = 10))

        onNodeWithText("No run going").assertDoesNotExist()
    }

    @Test
    fun theWeekBeforeIsOneStepBack() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 10), sitting(daysAgo = 7, minutes = 25))

        onNodeWithContentDescription("Show earlier").performClick()

        onNodeWithText("Last week").assertIsDisplayed()
        onNodeWithContentDescription("25m Time read").assertIsDisplayed()
    }

    @Test
    fun theCurrentPeriodHasNothingLaterToShow() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 10))

        onNodeWithContentDescription("Show later").assertIsNotEnabled()
    }

    @Test
    fun aMonthIsACalendarOfItsDays() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 10, minutes = 40))

        onNodeWithText("Month").performClick()

        onNodeWithText("August 2026").assertIsDisplayed()
        onNodeWithContentDescription("Tuesday 25: 20m").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Saturday 15: 40m").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("Monday 31: still to come").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("2 of 25 Days read").assertExists()
    }

    /**
     * The 25 days of August so far against the first 25 of July. Below the
     * calendar, past the foot of the test window, so asked for rather than seen.
     */
    @Test
    fun aMonthIsComparedWithTheSameDaysOfTheMonthBefore() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 60), sitting(daysAgo = 31, minutes = 30))

        onNodeWithText("Month").performClick()

        onNodeWithText("Compared with the same days of July: +100%").assertExists()
    }

    @Test
    fun aYearIsItsMonthsAndEveryDayInIt() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 60, minutes = 90))

        onNodeWithText("Year").performClick()

        onNodeWithText("2026").assertIsDisplayed()
        onNodeWithContentDescription("June: 1h 30m").assertIsDisplayed()
        onNodeWithContentDescription("Read on 2 days in 2026").assertIsDisplayed()
        onNodeWithContentDescription("Jun Best month").assertIsDisplayed()
    }

    /**
     * Whichever span is shown - including July, a month a week shorter on the
     * calendar than August - the card below starts in the same place.
     */
    @Test
    fun theCardKeepsItsHeightWhateverItShows() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 40, minutes = 30))
        val week = belowTheCard()

        onNodeWithText("Month").performClick()
        assertEquals(week, belowTheCard())

        onNodeWithContentDescription("Show earlier").performClick()
        onNodeWithText("July 2026").assertExists()
        assertEquals(week, belowTheCard())

        onNodeWithText("Year").performClick()
        assertEquals(week, belowTheCard())
    }

    /** Nothing counted yet is nothing to show, rather than a card of zeroes. */
    @Test
    fun aReaderWhoHasNotBeenTimedYetSeesNoneOfThis() = runComposeUiTest {
        setReadingTime()

        onNodeWithText("Reading time").assertDoesNotExist()
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
