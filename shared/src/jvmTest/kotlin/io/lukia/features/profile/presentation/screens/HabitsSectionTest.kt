package io.lukia.features.profile.presentation.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.ReadingSession
import io.lukia.core.presentation.UiState
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.readingHabits
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test

/** How the reading time reads, on a fixed day so the week is always the same. */
@OptIn(ExperimentalTestApi::class)
class HabitsSectionTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)

    private fun sitting(daysAgo: Int, minutes: Int) = ReadingSession(
        location = "/library/book.pdf",
        startedAt = LocalDateTime(today.minus(daysAgo, DateTimeUnit.DAY), LocalTime(12, 0))
            .toInstant(zone)
            .toEpochMilliseconds(),
        durationMs = minutes * 60_000L,
    )

    private fun ComposeUiTest.setHabits(vararg sessions: ReadingSession) {
        setContent {
            LukiaTheme(darkTheme = false) {
                HabitsSection(
                    summary = UiState.Success(
                        ReadingSummary(
                            stats = ReadingSummary.Empty.stats,
                            recent = emptyList(),
                            habits = readingHabits(sessions.toList(), today, zone),
                        ),
                    ),
                    groupColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }

    @Test
    fun theTimeIsSaidInHoursAndMinutes() = runComposeUiTest {
        setHabits(sitting(daysAgo = 0, minutes = 95))

        onNodeWithContentDescription("Today: 1h 35m").assertIsDisplayed()
    }

    @Test
    fun theWeekAndTheWholeOfItAreShownBesideToday() = runComposeUiTest {
        setHabits(sitting(daysAgo = 0, minutes = 20), sitting(daysAgo = 30, minutes = 40))

        onNodeWithContentDescription("Today: 20m").assertIsDisplayed()
        onNodeWithContentDescription("This week: 20m").assertIsDisplayed()
        onNodeWithContentDescription("All time: 1h").assertIsDisplayed()
    }

    /** Seven bars, each of which says what it stands for. */
    @Test
    fun everyDayOfTheWeekIsOnTheChart() = runComposeUiTest {
        setHabits(sitting(daysAgo = 0, minutes = 20))

        onNodeWithContentDescription("tuesday: 20m").assertIsDisplayed()
        onNodeWithContentDescription("monday: None yet").assertIsDisplayed()
    }

    @Test
    fun daysInARowAreCounted() = runComposeUiTest {
        setHabits(
            sitting(daysAgo = 0, minutes = 10),
            sitting(daysAgo = 1, minutes = 10),
            sitting(daysAgo = 2, minutes = 10),
        )

        onNodeWithText("3 days in a row").assertIsDisplayed()
    }

    /** A record worth beating is worth mentioning; one already held is not. */
    @Test
    fun theBestRunIsShownOnlyWhileItIsAheadOfTheCurrentOne() = runComposeUiTest {
        setHabits(
            sitting(daysAgo = 0, minutes = 10),
            sitting(daysAgo = 20, minutes = 10),
            sitting(daysAgo = 21, minutes = 10),
            sitting(daysAgo = 22, minutes = 10),
        )

        onNodeWithText("Best: 3 days").assertIsDisplayed()
    }

    /** Nothing counted yet is nothing to show, rather than a card of zeroes. */
    @Test
    fun aReaderWhoHasNotBeenTimedYetSeesNoneOfThis() = runComposeUiTest {
        setHabits()

        onNodeWithText("Time spent reading").assertDoesNotExist()
    }
}
