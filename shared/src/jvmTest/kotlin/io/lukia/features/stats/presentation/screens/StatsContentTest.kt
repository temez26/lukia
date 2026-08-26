package io.lukia.features.stats.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.library.TestBook
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.domain.readingSummary
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The reading statistics, as the reader meets them. */
@OptIn(ExperimentalTestApi::class)
class StatsContentTest {

    private var opened: String? = null
    private var retried = 0

    private fun summaryOf(vararg books: TestBook): UiState<ReadingSummary> = UiState.Success(
        readingSummary(
            documents = books.map(TestBook::document),
            progress = books.mapNotNull { entry ->
                entry.progress?.let { entry.document.document.location to it }
            }.toMap(),
        ),
    )

    private fun ComposeUiTest.setStats(summary: UiState<ReadingSummary>) {
        setContent {
            LukiaTheme(darkTheme = false) {
                StatsContent(
                    summary = summary,
                    onBack = {},
                    actions = StatsActions(
                        onOpenBook = { opened = it },
                        onRetry = { retried++ },
                    ),
                )
            }
        }
    }

    @Test
    fun thePageNamesItselfAndSaysWhereTheFiguresLive() = runComposeUiTest {
        setStats(summaryOf())

        onNodeWithText("Reading").assertIsDisplayed()
        onNodeWithText("Counted on this device, and kept here.").assertIsDisplayed()
    }

    @Test
    fun theFiguresAreCountedFromTheLibrary() = runComposeUiTest {
        setStats(
            summaryOf(
                book("Halfway", progress = partRead()),
                book("Done", progress = finished()),
                book("Waiting"),
            ),
        )

        onNodeWithContentDescription("1 Reading").assertIsDisplayed()
        onNodeWithContentDescription("1 Finished").assertIsDisplayed()
        onNodeWithContentDescription("1 Not started").assertIsDisplayed()
        onNodeWithContentDescription("3 Books").assertIsDisplayed()
    }

    @Test
    fun aLibraryWithNothingInItSaysSoRatherThanShowingZeroes() = runComposeUiTest {
        setStats(summaryOf())

        onNodeWithText("Nothing to count yet. Import a book and this fills in as you read.")
            .assertIsDisplayed()
    }

    @Test
    fun recentlyReadBooksSayWhereTheReaderGotTo() = runComposeUiTest {
        setStats(summaryOf(book("Halfway", progress = partRead())))

        onNodeWithText("Halfway").assertIsDisplayed()
        onNodeWithText("Page 21 of 100 · 21%").assertIsDisplayed()
    }

    @Test
    fun aBookNeverOpenedIsNotListedAsActivity() = runComposeUiTest {
        setStats(summaryOf(book("Waiting")))

        onNodeWithText("Recently read").assertDoesNotExist()
    }

    @Test
    fun aRecentBookOpensWhereItWasLeft() = runComposeUiTest {
        setStats(summaryOf(book("Halfway", progress = partRead())))

        onNodeWithText("Halfway").performClick()

        assertEquals("/library/Halfway.pdf", opened)
    }

    /**
     * The library figures are one section of a page rather than the page, so a
     * count that fails leaves the heading standing and offers the count again.
     */
    @Test
    fun aFailureToCountBooksLeavesThePageStanding() = runComposeUiTest {
        setStats(UiState.Failure(AppError.Unexpected()))

        onNodeWithText("Reading").assertIsDisplayed()
        onNodeWithText("Try again").performClick()

        assertEquals(1, retried)
    }
}
