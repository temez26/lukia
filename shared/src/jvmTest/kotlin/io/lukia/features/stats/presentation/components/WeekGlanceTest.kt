package io.lukia.features.stats.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.domain.readingHabits
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertTrue

/** The glance at the week on Home, on a fixed Tuesday - see `TimeSectionsTest`. */
@OptIn(ExperimentalTestApi::class)
class WeekGlanceTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)

    private fun habits(minutes: Int): ReadingHabits = readingHabits(
        sessions = listOf(
            ReadingSession(
                location = "/library/book.pdf",
                startedAt = LocalDateTime(today, LocalTime(12, 0)).toInstant(zone).toEpochMilliseconds(),
                durationMs = minutes * 60_000L,
            ),
        ),
        today = today,
        zone = zone,
    )

    @Test
    fun itSaysTheWeeksTimeAndOpensTheStatistics() = runComposeUiTest {
        var opened = false

        setContent {
            LukiaTheme(darkTheme = false) {
                WeekGlanceCard(habits = habits(minutes = 200), onClick = { opened = true })
            }
        }

        onNodeWithContentDescription("This week: 3h 20m").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun aWeekWithNoReadingSaysSo() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                WeekGlanceCard(habits = ReadingHabits.Empty, onClick = {})
            }
        }

        onNodeWithContentDescription("This week: None yet").assertIsDisplayed()
    }
}
