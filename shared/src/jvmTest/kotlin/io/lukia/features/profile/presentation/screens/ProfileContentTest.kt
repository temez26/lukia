package io.lukia.features.profile.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.library.TestBook
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.domain.readingSummary
import io.lukia.features.profile.presentation.components.face
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The reader's own page, as they meet it. */
@OptIn(ExperimentalTestApi::class)
class ProfileContentTest {

    private var opened: String? = null
    private var renamedTo: String? = null
    private var retried = 0

    private fun summaryOf(vararg books: TestBook): UiState<ReadingSummary> = UiState.Success(
        readingSummary(
            documents = books.map(TestBook::document),
            progress = books.mapNotNull { entry ->
                entry.progress?.let { entry.document.document.location to it }
            }.toMap(),
        ),
    )

    private fun ComposeUiTest.setProfile(
        summary: UiState<ReadingSummary>,
        username: String = "Ada Lovelace",
    ) {
        setContent {
            LukiaTheme(darkTheme = false) {
                val profile = UserProfile(username = username)

                ProfileContent(
                    profile = profile,
                    face = profile.face(),
                    summary = summary,
                    onBack = {},
                    actions = ProfileActions(
                        onRename = { renamedTo = it },
                        onOpenBook = { opened = it },
                        onRetry = { retried++ },
                    ),
                )
            }
        }
    }

    @Test
    fun itShowsTheReaderByNameAndByMonogram() = runComposeUiTest {
        setProfile(summaryOf())

        onAllNodesWithText("Ada Lovelace").onFirst().assertIsDisplayed()
        onAllNodesWithText("AL").onFirst().assertIsDisplayed()
    }

    @Test
    fun itSaysWhereTheProfileIsKept() = runComposeUiTest {
        setProfile(summaryOf())

        onNodeWithText("Reading on this device").assertIsDisplayed()
    }

    @Test
    fun theFiguresAreCountedFromTheLibrary() = runComposeUiTest {
        setProfile(
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
        setProfile(summaryOf())

        onNodeWithText("Nothing to count yet. Import a book and this fills in as you read.")
            .assertIsDisplayed()
    }

    @Test
    fun recentlyReadBooksSayWhereTheReaderGotTo() = runComposeUiTest {
        setProfile(summaryOf(book("Halfway", progress = partRead())))

        onNodeWithText("Halfway").assertIsDisplayed()
        onNodeWithText("Page 21 of 100 · 21%").assertIsDisplayed()
    }

    @Test
    fun aBookNeverOpenedIsNotListedAsActivity() = runComposeUiTest {
        setProfile(summaryOf(book("Waiting")))

        onNodeWithText("Recently read").assertDoesNotExist()
    }

    @Test
    fun aRecentBookOpensWhereItWasLeft() = runComposeUiTest {
        setProfile(summaryOf(book("Halfway", progress = partRead())))

        onNodeWithText("Halfway").performClick()

        assertEquals("/library/Halfway.pdf", opened)
    }

    /**
     * The name is above the figures on the page but not above them in what it
     * depends on: a library that cannot be counted must still leave a reader
     * looking at their own profile.
     */
    @Test
    fun aFailureToCountBooksLeavesTheProfileStanding() = runComposeUiTest {
        setProfile(UiState.Failure(AppError.Unexpected()))

        onAllNodesWithText("Ada Lovelace").onFirst().assertIsDisplayed()
        onNodeWithText("Try again").performClick()

        assertEquals(1, retried)
    }

    /** Reported as it is typed, like every other setting this app stores. */
    @Test
    fun theNameCanBeChangedFromHere() = runComposeUiTest {
        setProfile(summaryOf())

        onNodeWithContentDescription("Your name").performTextReplacement("Grace Hopper")

        assertEquals("Grace Hopper", renamedTo)
    }
}
