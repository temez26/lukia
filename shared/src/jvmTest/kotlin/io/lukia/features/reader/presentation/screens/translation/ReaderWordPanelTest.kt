package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.reader.presentation.translation.WordOnThePage
import io.lukia.features.reader.presentation.translation.WordSiteLink
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.lookup.WordMeaning
import io.lukia.features.translate.domain.lookup.WordSite
import kotlin.test.Test
import kotlin.test.assertEquals

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
        setReader(wordInquiry = { inquiry() })

        onNodeWithText("whale").assertExists()
        onNodeWithText("valas").assertExists()
        onNodeWithText("valaskala").assertExists()
    }

    @Test
    fun theChosenSiteIsOfferedByName() = runComposeUiTest {
        setReader(wordInquiry = { inquiry() })

        onNodeWithText("Open in Google").assertExists()
    }

    /**
     * A custom site nobody has given an address for has nowhere to open, and a
     * button that opens a browser on nothing is worse than no button.
     */
    @Test
    fun aSiteWithNowhereToOpenIsNotOffered() = runComposeUiTest {
        setReader(wordInquiry = { inquiry(site = null) })

        onNodeWithText("Open in", substring = true).assertDoesNotExist()
    }

    /**
     * The way back to the book's own word, which is what a second hold used to
     * do before a hold opened this panel - so nothing the gesture could do has
     * gone.
     */
    @Test
    fun theBooksOwnWordIsOneOfTheThingsOffered() = runComposeUiTest {
        setReader(wordInquiry = { inquiry() })

        onNodeWithText("Show the original").assertExists()
    }

    @Test
    fun aWordAlreadyBackToTheBooksIsOfferedItsTranslation() = runComposeUiTest {
        setReader(wordInquiry = { inquiry(onThePage = WordOnThePage.Original) })

        onNodeWithText("Show the translation").assertExists()
    }

    /** A dictionary with no entry is an ordinary answer, and the site still stands. */
    @Test
    fun aWordWithNoEntryStillOffersTheSite() = runComposeUiTest {
        setReader(
            wordInquiry = {
                inquiry(
                    entry = UiState.Failure(AppError.Unexpected("No dictionary entry for “whale”.")),
                )
            },
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
            wordInquiry = {
                inquiry(
                    onThePage = WordOnThePage.Failed(AppError.TranslationFailed),
                )
            },
        )

        onNodeWithText("The translation service did not answer. Try again.").assertExists()
        onNodeWithText("Try again").assertExists()
        onNodeWithText("Open in Google").assertExists()
        onNodeWithText("valas").assertExists()
    }

    /**
     * A reader whose word lookup *is* the dictionary gets one failure, not two.
     *
     * The service that could not answer the word in the paragraph is the same
     * service this panel just asked, and it comes back with the same sentence -
     * printed in both places it reads as two things having gone wrong.
     */
    @Test
    fun oneFailureFromOneServiceIsSaidOnce() = runComposeUiTest {
        val reason = "No dictionary entry for “whale”."

        setReader(
            wordInquiry = {
                inquiry(
                    entry = UiState.Failure(AppError.Unexpected(reason)),
                    onThePage = WordOnThePage.Failed(AppError.Unexpected(reason)),
                )
            },
        )

        assertEquals(
            1,
            onAllNodesWithText(reason).fetchSemanticsNodes().size,
            "the same failure was printed twice",
        )
    }

    /**
     * A model that refused the word while the dictionary answered it is the case
     * this panel exists for, and the reason is what makes sense of the button
     * offering to ask again.
     */
    @Test
    fun aDifferentFailureIsStillWorthSaying() = runComposeUiTest {
        setReader(
            wordInquiry = {
                inquiry(
                    onThePage = WordOnThePage.Failed(AppError.Unexpected("Your API key was refused.")),
                )
            },
        )

        onNodeWithText("Your API key was refused.").assertExists()
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
            wordInquiry = {
                inquiry(
                    entry = UiState.Success(WordEntry("value", meaning("arvo"))),
                )
            },
        )

        onNodeWithText("Looked up under “value”").assertExists()
    }

    /**
     * And in the wiki's own words where it had any. `monikkomuoto sanasta value`
     * is the half of the answer that cannot go over the word in the book, and the
     * half somebody learning the language held the word for.
     */
    @Test
    fun whatFormTheHeldWordIsShownAsThePageWroteIt() = runComposeUiTest {
        setReader(
            wordInquiry = { inquiry(word = "whales", entry = UiState.Success(formEntry)) },
        )

        onNodeWithText("monikkomuoto sanasta whale").assertExists()
        onNodeWithText("valas").assertExists()
    }

    @Test
    fun theWordTheSensesBelongToIsNamedAboveThem() = runComposeUiTest {
        setReader(
            wordInquiry = { inquiry(word = "whales", entry = UiState.Success(formEntry)) },
        )

        onNodeWithText("Substantiivi").assertExists()
    }

    /** What the entry lists beside its senses is what to look up next. */
    @Test
    fun theWordsListedBesideTheEntryAreShown() = runComposeUiTest {
        setReader(
            wordInquiry = { inquiry(word = "whales", entry = UiState.Success(formEntry)) },
        )

        onNodeWithText("Related words").assertExists()
        onNodeWithText("valaanpyynti").assertExists()
    }

    /**
     * A word held because it had no direct translation is being told why one line
     * up, in the form the wiki gave it. Printing the direction that got the reader
     * here would be the panel telling them to open the panel.
     */
    @Test
    fun theDirectionIntoThisPanelIsNotRepeatedInsideIt() = runComposeUiTest {
        val direction = "No direct translation — hold the word for more about it."

        setReader(
            wordInquiry = {
                inquiry(
                    word = "whales",
                    entry = UiState.Success(
                        WordEntry(
                            word = "whales",
                            meanings = emptyList(),
                            form = "monikkomuoto sanasta whale",
                        ),
                    ),
                    onThePage = WordOnThePage.Failed(AppError.Unexpected(direction)),
                )
            },
        )

        onNodeWithText(direction).assertDoesNotExist()
        onNodeWithText("monikkomuoto sanasta whale").assertExists()
    }

    @Test
    fun theFormIsNotShownWhenItIsTheWordThatWasHeld() = runComposeUiTest {
        setReader(wordInquiry = { inquiry() })

        onNodeWithText("Looked up under", substring = true).assertDoesNotExist()
    }

    /**
     * The panel leaves with the word still in it.
     *
     * Without that it empties the instant the word goes: the surface loses every
     * line it was sized by, drops to the height of its padding, and only then
     * fades - which reads as collapsing rather than closing.
     *
     * The clock is held so the closing can be looked at halfway through, the only
     * moment this is a question at all.
     */
    @Test
    fun theWordIsStillInThePanelWhileItCloses() = runComposeUiTest {
        var open by mutableStateOf(true)

        setReader(wordInquiry = { inquiry().takeIf { open } })

        onNodeWithText("whale").assertExists()

        mainClock.autoAdvance = false
        open = false
        mainClock.advanceTimeByFrame()

        onNodeWithText("whale").assertExists()
        onNodeWithText("valas").assertExists()
    }

    private fun meaning(vararg senses: String) = listOf(WordMeaning(null, senses.toList()))

    /** An entry arrived at through a form, with everything its page carried. */
    private val formEntry = WordEntry(
        word = "whale",
        meanings = listOf(
            WordMeaning("Substantiivi", listOf("valas", "valaskala")),
            WordMeaning("Verbi", listOf("valaanpyyntiin lähteä")),
        ),
        form = "monikkomuoto sanasta whale",
        related = listOf("valaanpyynti"),
    )

    private fun inquiry(
        word: String = "whale",
        entry: UiState<WordEntry> = UiState.Success(
            WordEntry("whale", meaning("valas", "valaskala")),
        ),
        onThePage: WordOnThePage = WordOnThePage.Translated,
        site: WordSiteLink? = WordSiteLink(
            site = WordSite.GoogleTranslate,
            url = "https://translate.google.com/?text=whale",
        ),
    ) = WordInquiry(
        word = word,
        entry = entry,
        onThePage = onThePage,
        site = site,
    )
}
