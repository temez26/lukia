package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.height
import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.stats.domain.readingHabits
import io.lukia.features.stats.domain.readingHistory
import io.lukia.features.stats.presentation.CalendarSelection
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

    private companion object {
        const val TimeCard = "timeCard"
    }

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)
    private val wasDefault = Locale.getDefault()

    @BeforeTest
    fun useEnglish() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    private fun sitting(daysAgo: Int, minutes: Int) = ReadingSession(
        location = "/library/book.pdf",
        startedAt = LocalDateTime(today.minus(daysAgo, DateTimeUnit.DAY), LocalTime(12, 0))
            .toInstant(zone)
            .toEpochMilliseconds(),
        durationMs = minutes * 60_000L,
    )

    private fun habitsOf(sessions: List<ReadingSession>) = readingHabits(sessions, today, zone)

    /** The card as the screen holds it: a selection that the controls change. */
    private fun ComposeUiTest.setReadingTime(vararg sessions: ReadingSession) {
        setContent {
            var selection by remember { mutableStateOf(CalendarSelection()) }

            LukiaTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ReadingTimeSection(
                        modifier = Modifier.testTag(TimeCard),
                        habits = habitsOf(sessions.toList()),
                        history = readingHistory(sessions.toList(), today, zone),
                        selection = selection,
                        actions = StatsActions(
                            onShowMonth = { selection = selection.copy(month = it, day = null) },
                            onSelectDay = { selection = selection.copy(day = it) },
                            onZoomInto = { selection = CalendarSelection(month = it) },
                            onZoomOut = { selection = selection.copy(day = it, zoomed = false) },
                            onZoomIn = { selection = selection.copy(zoomed = true) },
                        ),
                        groupColor = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }

    @Test
    fun itOpensOnTodayInThisMonth() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 84))

        onNodeWithText("August 2026").assertIsDisplayed()
        onNodeWithContentDescription("Tuesday 25 Aug: 1h 24m").assertIsDisplayed()
        onNodeWithContentDescription("Tuesday 25: 1h 24m").performScrollTo().assertIsSelected()
    }

    @Test
    fun theMonthIsTotalledUnderTheCalendar() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 10, minutes = 40))

        onNodeWithContentDescription("1h Time read").assertExists()
        onNodeWithContentDescription("2 of 25 Days read").assertExists()
        onNodeWithContentDescription("Monday 31: still to come").assertExists()
    }

    @Test
    fun daysInARowRideBesideTheDay() = runComposeUiTest {
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
    fun tappingADayReadsOutItsExactTime() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 5), sitting(daysAgo = 10, minutes = 40))

        onNodeWithContentDescription("Saturday 15: 40m").performScrollTo().performClick()

        onNodeWithContentDescription("Saturday 15: 40m").assertIsSelected()
        onNodeWithContentDescription("Saturday 15 Aug: 40m").assertExists()
    }

    /** 40 minutes on a day of a month averaging under two. */
    @Test
    fun aDayIsComparedWithTheAverageDay() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 5), sitting(daysAgo = 10, minutes = 40))

        onNodeWithContentDescription("Saturday 15: 40m").performScrollTo().performClick()

        onNodeWithText("38m more than your average day").assertExists()
    }

    /** A day with nothing read is a rest, not a shortfall: no comparison, and no change in height. */
    @Test
    fun aDayWithoutReadingIsNotComparedAndTheCardKeepsItsHeight() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 30))
        val today = onNodeWithTag(TimeCard).getUnclippedBoundsInRoot().height

        onNodeWithContentDescription("Monday 24: None yet").performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("less than your average day", substring = true).assertDoesNotExist()
        assertEquals(today, onNodeWithTag(TimeCard).getUnclippedBoundsInRoot().height)
    }

    /** The month before opens on its last day with reading rather than on nothing. */
    @Test
    fun theMonthBeforeIsOneStepBack() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 10), sitting(daysAgo = 40, minutes = 25))

        onNodeWithContentDescription("Show earlier").performClick()
        waitForIdle()

        onNodeWithText("July 2026").assertIsDisplayed()
        onNodeWithContentDescription("Thursday 16 Jul: 25m").assertIsDisplayed()
    }

    @Test
    fun aSwipeTurnsToTheMonthBefore() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 10), sitting(daysAgo = 40, minutes = 25))

        onNodeWithContentDescription("Tuesday 25: 10m").performTouchInput { swipeRight() }
        waitForIdle()

        onNodeWithText("July 2026").assertIsDisplayed()
    }

    @Test
    fun theCurrentMonthHasNothingLaterToShow() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 10))

        onNodeWithContentDescription("Show later").assertIsNotEnabled()
    }

    /**
     * The 25 days of August so far against the first 25 of July. Below the
     * calendar, past the foot of the test window, so asked for rather than seen.
     */
    @Test
    fun aMonthIsComparedWithTheSameDaysOfTheMonthBefore() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 60), sitting(daysAgo = 31, minutes = 30))

        onNodeWithText("Compared with the same days of July: +100%").assertExists()
    }

    @Test
    fun zoomingOutShowsTheYearAndItsBestMonth() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 60, minutes = 90))

        onNodeWithText("August 2026").performClick()
        waitForIdle()

        onNodeWithText("2026").assertIsDisplayed()
        onNodeWithContentDescription("Read on 2 days in 2026").assertExists()
        onNodeWithContentDescription("Jun Best month").assertExists()
        // The day selected in the month stays selected in the year.
        onNodeWithContentDescription("Tuesday 25 Aug: 20m").assertIsDisplayed()
    }

    /** The year's name goes back into the month it was opened from, the same day selected. */
    @Test
    fun theYearsNameGoesBackIntoTheMonth() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 10, minutes = 40))

        onNodeWithContentDescription("Saturday 15: 40m").performScrollTo().performClick()
        onNodeWithText("August 2026").performClick()
        waitForIdle()
        onNodeWithText("2026").performClick()
        waitForIdle()

        onNodeWithText("August 2026").assertIsDisplayed()
        onNodeWithContentDescription("Saturday 15: 40m").performScrollTo().assertIsSelected()
    }

    /** The year adds its sittings, best month, best day and longest run to a month's figures. */
    @Test
    fun aYearCountsItsSittingsBestDayAndLongestRun() = runComposeUiTest {
        setReadingTime(
            sitting(daysAgo = 0, minutes = 20),
            sitting(daysAgo = 1, minutes = 10),
            sitting(daysAgo = 60, minutes = 90),
        )

        onNodeWithText("August 2026").performClick()
        waitForIdle()

        onNodeWithContentDescription("2h in 3 sittings Time read").assertExists()
        onNodeWithContentDescription("1h 30m Best day").assertExists()
        onNodeWithContentDescription("2 days in a row Longest run").assertExists()
        onNodeWithContentDescription("Jun Best month").assertExists()
    }

    /** A month of the year opens out of it, on its own default day. */
    @Test
    fun aMonthOfTheYearOpensOnATap() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 60, minutes = 90))

        onNodeWithText("August 2026").performClick()
        onNodeWithContentDescription("Show June").performClick()
        waitForIdle()

        onNodeWithText("June 2026").assertIsDisplayed()
        onNodeWithContentDescription("Friday 26: 1h 30m").performScrollTo().assertIsSelected()
    }

    /** Months with nothing to open - before the first reading, or still to come - do nothing. */
    @Test
    fun monthsOutsideTheReadingCannotBeOpened() = runComposeUiTest {
        setReadingTime(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 60, minutes = 90))

        onNodeWithText("August 2026").performClick()
        waitForIdle()

        onNodeWithContentDescription("Show May").assertIsNotEnabled()
        onNodeWithContentDescription("Show December").assertIsNotEnabled()
        onNodeWithContentDescription("Show July").assertIsEnabled()
    }

    /** Nothing counted yet is nothing to show, rather than a card of zeroes. */
    @Test
    fun aReaderWhoHasNotBeenTimedYetSeesNoneOfThis() = runComposeUiTest {
        setReadingTime()

        onNodeWithText("Reading time").assertDoesNotExist()
    }
}
