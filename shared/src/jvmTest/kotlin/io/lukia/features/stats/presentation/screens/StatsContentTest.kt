package io.lukia.features.stats.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.library.TestBook
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.stats.domain.BookTime
import io.lukia.features.stats.domain.FinishedBook
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.domain.ReadingStats
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.domain.YearBooks
import io.lukia.features.stats.domain.readingStats
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The reading statistics, as the reader meets them. */
@OptIn(ExperimentalTestApi::class)
class StatsContentTest {

    private var opened: String? = null
    private var retried = 0
    private val wasDefault = Locale.getDefault()

    @BeforeTest
    fun useEnglish() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    private fun summaryOf(vararg books: TestBook): UiState<ReadingSummary> = UiState.Success(
        ReadingSummary(
            stats = readingStats(
                documents = books.map(TestBook::document),
                progress = books.mapNotNull { entry ->
                    entry.progress?.let { entry.document.document.location to it }
                }.toMap(),
            ),
        ),
    )

    /** A reader with some time counted, and [books] as this year's books. */
    private fun withBooks(books: YearBooks): UiState<ReadingSummary> = UiState.Success(
        ReadingSummary(
            stats = ReadingStats.Empty,
            habits = ReadingHabits.Empty.copy(totalMs = 60_000L),
            books = books,
        ),
    )

    private fun ComposeUiTest.setStats(summary: UiState<ReadingSummary>, syncedWithAccount: Boolean = false) {
        setContent {
            LukiaTheme(darkTheme = false) {
                StatsContent(
                    summary = summary,
                    onBack = {},
                    syncedWithAccount = syncedWithAccount,
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
    fun signedInThePageSaysTheFiguresComeFromEveryDevice() = runComposeUiTest {
        setStats(summaryOf(), syncedWithAccount = true)

        onNodeWithText("Counted on every device signed in to your account.").assertIsDisplayed()
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
    fun thisYearsMostReadBooksSayHowLongWentOnEach() = runComposeUiTest {
        val halfway = book("Halfway", progress = partRead()).document
        setStats(
            withBooks(
                YearBooks(
                    2026,
                    listOf(BookTime(halfway.document.location, "Halfway", 95 * 60_000L, halfway)),
                    emptyList(),
                ),
            ),
        )

        onNodeWithText("Books in 2026").performScrollTo().assertIsDisplayed()
        onNodeWithText("Halfway").performScrollTo().assertIsDisplayed()
        onNodeWithText("1h 35m").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMostReadBookOpensFromTheList() = runComposeUiTest {
        val halfway = book("Halfway", progress = partRead()).document
        setStats(
            withBooks(
                YearBooks(2026, listOf(BookTime(halfway.document.location, "Halfway", 60_000L, halfway)), emptyList()),
            ),
        )

        onNodeWithText("Halfway").performScrollTo().performClick()

        assertEquals("/library/Halfway.pdf", opened)
    }

    @Test
    fun aBookNoLongerOnTheShelfIsNamedButDoesNotOpen() = runComposeUiTest {
        setStats(withBooks(YearBooks(2026, listOf(BookTime("/library/Gone.pdf", "Gone", 60_000L)), emptyList())))

        onNodeWithText("Gone").performScrollTo().performClick()

        assertNull(opened)
    }

    @Test
    fun finishedBooksAreDatedByTheirLastSitting() = runComposeUiTest {
        val done = book("Done", progress = finished()).document
        setStats(
            withBooks(YearBooks(2026, emptyList(), listOf(FinishedBook(done, LocalDate(2026, Month.FEBRUARY, 9))))),
        )

        onNodeWithText("Finished").performScrollTo().performClick()

        onNodeWithText("Done").performScrollTo().assertIsDisplayed()
        onNodeWithText("Finished 9 Feb").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEmptyYearSaysSoInEachList() = runComposeUiTest {
        setStats(withBooks(YearBooks(2026, emptyList(), emptyList())))

        onNodeWithText("Nothing read yet this year.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Finished").performScrollTo().performClick()
        onNodeWithText("No books finished yet this year.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aReaderWithNoTimeCountedHasNoBooksCard() = runComposeUiTest {
        setStats(summaryOf(book("Halfway", progress = partRead())))

        onNodeWithText("Books in 2026").assertDoesNotExist()
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
