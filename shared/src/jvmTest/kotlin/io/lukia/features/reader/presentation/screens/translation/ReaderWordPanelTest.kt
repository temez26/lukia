package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.reader.presentation.translation.WordOnThePage
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
        setReader(wordInquiry = inquiry(onThePage = WordOnThePage.Original))

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

    /**
     * A word whose lookup failed is why the panel opens at all for one - the
     * service that could not answer it is the last thing worth asking again, and
     * everything else here still works.
     */
    @Test
    fun aWordThatCouldNotBeLookedUpSaysWhyAndOffersAnotherGo() = runComposeUiTest {
        setReader(
            wordInquiry = inquiry(
                onThePage = WordOnThePage.Failed("The translation service did not answer."),
            ),
        )

        onNodeWithText("The translation service did not answer.").assertExists()
        onNodeWithText("Try again").assertExists()
        onNodeWithText("Open in Google").assertExists()
        onNodeWithText("valas").assertExists()
    }

    /**
     * A book is written in inflected words, and a wiki's page for one of them
     * often only says which word it is a form of - so the entry that comes back
     * is the base form's, and saying so is what lets the reader trust it.
     */
    @Test
    fun theFormTheEntryWasFoundUnderIsShownWhenItDiffers() = runComposeUiTest {
        setReader(
            wordInquiry = inquiry(
                entry = UiState.Success(WordEntry("value", listOf("arvo"))),
            ),
        )

        onNodeWithText("Looked up under “value”").assertExists()
    }

    @Test
    fun theFormIsNotShownWhenItIsTheWordThatWasHeld() = runComposeUiTest {
        setReader(wordInquiry = inquiry())

        onNodeWithText("Looked up under", substring = true).assertDoesNotExist()
    }

    private fun inquiry(
        entry: UiState<WordEntry> = UiState.Success(
            WordEntry("whale", listOf("valas", "valaskala")),
        ),
        onThePage: WordOnThePage = WordOnThePage.Translated,
        site: WordSiteLink? = WordSiteLink(
            label = "Google",
            url = "https://translate.google.com/?text=whale",
        ),
    ) = WordInquiry(
        word = "whale",
        entry = entry,
        onThePage = onThePage,
        site = site,
    )
}
