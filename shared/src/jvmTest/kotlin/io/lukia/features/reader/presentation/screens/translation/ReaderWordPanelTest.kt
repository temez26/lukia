package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.reader.presentation.translation.WordSiteLink
import io.lukia.features.translate.domain.lookup.WordEntry
import kotlin.test.Test

/**
 * What holding an already-answered word opens: everything else that word means,
 * and a way through to somebody else's page about it.
 *
 * The panel is the whole of what the broader lookup adds, and the two halves are
 * offered independently - a dictionary with no entry still leaves the site worth
 * going to, and a reader who has not set a site still gets the entry.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderWordPanelTest {

    @Test
    fun theWordAndEverySenseOfItAreShown() = runComposeUiTest {
        setReader(wordInquiry = inquiry())

        onNodeWithText("whale").assertExists()
        onNodeWithText("valas").assertExists()
        onNodeWithText("valaskala").assertExists()
    }

    @Test
    fun theChosenSiteIsOfferedByName() = runComposeUiTest {
        setReader(wordInquiry = inquiry())

        onNodeWithText("Open in Google").assertExists()
    }

    /**
     * A custom site nobody has given an address for has nowhere to open, and a
     * button that opens a browser on nothing is worse than no button.
     */
    @Test
    fun aSiteWithNowhereToOpenIsNotOffered() = runComposeUiTest {
        setReader(wordInquiry = inquiry(site = null))

        onNodeWithText("Open in", substring = true).assertDoesNotExist()
    }

    /**
     * The way back to the book's own word, which is what a second hold used to
     * do before a hold opened this panel - so nothing the gesture could do has
     * gone.
     */
    @Test
    fun theBooksOwnWordIsOneOfTheThingsOffered() = runComposeUiTest {
        setReader(wordInquiry = inquiry())

        onNodeWithText("Show the original").assertExists()
    }

    @Test
    fun aWordAlreadyBackToTheBooksIsOfferedItsTranslation() = runComposeUiTest {
        setReader(wordInquiry = inquiry(showingOriginal = true))

        onNodeWithText("Show the translation").assertExists()
    }

    /** A dictionary with no entry is an ordinary answer, and the site still stands. */
    @Test
    fun aWordWithNoEntryStillOffersTheSite() = runComposeUiTest {
        setReader(
            wordInquiry = inquiry(
                entry = UiState.Failure(AppError.Unexpected("No dictionary entry for “whale”.")),
            ),
        )

        onNodeWithText("No dictionary entry", substring = true).assertExists()
        onNodeWithText("Open in Google").assertExists()
    }

    private fun inquiry(
        entry: UiState<WordEntry> = UiState.Success(
            WordEntry("whale", listOf("valas", "valaskala")),
        ),
        showingOriginal: Boolean = false,
        site: WordSiteLink? = WordSiteLink(
            label = "Google",
            url = "https://translate.google.com/?text=whale",
        ),
    ) = WordInquiry(
        word = "whale",
        entry = entry,
        showingOriginal = showingOriginal,
        site = site,
    )
}
