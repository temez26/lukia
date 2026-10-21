package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.domain.readingHabits
import io.lukia.features.stats.presentation.dayTime
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** The glance at the week on Home, on a fixed Tuesday - see `TimeSectionsTest`. */
@OptIn(ExperimentalTestApi::class)
class WeekGlanceTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 8, 25)

    private fun habits(minutes: Int): ReadingHabits = readingHabits(
        sessions = listOfNotNull(
            ReadingSession(
                location = "/library/book.pdf",
                startedAt = LocalDateTime(today, LocalTime(12, 0)).toInstant(zone).toEpochMilliseconds(),
                durationMs = minutes * 60_000L,
            ).takeIf { minutes > 0 },
        ),
        today = today,
        zone = zone,
    )

    @Test
    fun itSaysTheWeeksTimeAndOpensTheStatistics() = runComposeUiTest {
        var opened = false

        setContent {
            LukiaTheme(darkTheme = false) {
                WeekGlanceContent(habits = habits(minutes = 200), onClick = { opened = true })
            }
        }

        onNodeWithContentDescription("This week: 3h 20m").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun aWeekWithNoReadingSaysSo() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                WeekGlanceContent(habits = habits(minutes = 0), onClick = {})
            }
        }

        onNodeWithContentDescription("This week: None yet").assertIsDisplayed()
    }

    /** Each bar carries its day's time, and less than a minute carries none rather than rounding up. */
    @Test
    fun aDayIsLabelledWithItsMinutes() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                Column {
                    Text(dayTime(25 * 60_000L))
                    Text("[" + dayTime(30_000L) + "]")
                }
            }
        }

        onNodeWithText("25m").assertIsDisplayed()
        onNodeWithText("[]").assertIsDisplayed()
    }
}
