package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.domain.page.TextQuad
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.screens.pages.cornerWord
import io.lukia.features.reader.presentation.screens.pages.doubleTapPageTwo
import io.lukia.features.reader.presentation.screens.pages.setPageReader
import io.lukia.features.reader.presentation.screens.pages.tapPageTwo
import io.lukia.features.reader.presentation.screens.pages.wholePage
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.translate.domain.Translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A fixed-layout page as somebody actually uses it.
 *
 * A PDF's page is a picture, so none of this can work the way the reflowable
 * reader does: what is proven here is that a tap is translated back into the
 * file's own coordinates and lands on the right sentence, and that a translation
 * is drawn over the page rather than beside it.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderPageTranslationTest {

    @Test
    fun tappingASentenceOnAPageAsksForThatSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(sentences = listOf(wholePage), onPassageSelected = { selected = it })
        tapPageTwo()

        val selection = assertNotNull(selected, "no sentence was selected")
        assertEquals(opening, selection.passage)
        assertEquals(TextAnchor.Page(pageIndex = 1, sentenceIndex = 0), selection.anchor)
    }

    /**
     * The rest of the page travels with the sentence. A page has no chapter of
     * its own to name, so what is around the words is all the context there is.
     */
    @Test
    fun theRestOfThePageTravelsWithTheSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        val page = listOf(
            wholePage,
            PageSentence(1, "Some years ago.", listOf(TextQuad(0f, 280f, 100f, 300f))),
        )

        setPageReader(sentences = page, onPassageSelected = { selected = it })
        tapPageTwo()

        assertEquals(
            "$opening Some years ago.",
            assertNotNull(selected).surroundingText,
        )
    }

    /**
     * A tap that landed on no words is a tap on the page, and two of them put
     * the chrome away - which is what that has always meant, and translating has
     * not taken it over.
     */
    @Test
    fun tappingAwayFromTheWordsStillPutsTheChromeAway() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(sentences = listOf(cornerWord), onPassageSelected = { selected = it })
        doubleTapPageTwo()
        waitForIdle()

        assertNull(selected, "the margin was read as a sentence")
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size,
        )
    }

    /** A page with no text layer at all - a scan - behaves like a page of margin. */
    @Test
    fun aScannedPageWithNoTextLayerStillTogglesTheChrome() = runComposeUiTest {
        setPageReader(sentences = emptyList())

        doubleTapPageTwo()
        waitForIdle()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aTranslatedSentenceIsDrawnOverThePage() = runComposeUiTest {
        setPageReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Ready(Translation(translated)))
            },
        )

        onNodeWithText(translated, substring = true).assertExists()
    }

    @Test
    fun aTranslatorsNoteIsDrawnWithIt() = runComposeUiTest {
        setPageReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(
                    TextAnchor.Page(0, 0) to PassageTranslation.Ready(
                        Translation(translated, note = "A remark about a word."),
                    ),
                )
            },
        )

        onNodeWithText("A remark about a word.", substring = true).assertExists()
    }

    /** Asking for the original back takes the panel off the page. */
    @Test
    fun flippingBackUncoversThePage() = runComposeUiTest {
        setPageReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(
                    TextAnchor.Page(0, 0) to PassageTranslation.Ready(
                        translation = Translation(translated),
                        showingOriginal = true,
                    ),
                )
            },
        )

        assertEquals(
            0,
            onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size,
        )
    }

    /**
     * A failure has to say what went wrong. Marked only by a tint it looks
     * exactly like a sentence still being translated, which leaves somebody
     * staring at a highlight wondering whether anything is happening at all.
     */
    @Test
    fun aFailedTranslationSaysWhyOnThePage() = runComposeUiTest {
        setPageReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(
                    TextAnchor.Page(0, 0) to PassageTranslation.Failed(
                        AppError.Unexpected("Your OpenAI account is out of API credit."),
                    ),
                )
            },
        )

        onNodeWithText("out of API credit", substring = true).assertExists()
    }

    /** A sentence still in flight leaves the page readable underneath. */
    @Test
    fun aSentenceStillBeingTranslatedIsNotCoveredOver() = runComposeUiTest {
        setPageReader(
            sentences = listOf(wholePage),
            translations = { mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Loading()) },
        )

        assertEquals(
            0,
            onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
                .count { it.config.toString().contains(opening) },
        )
    }

    /**
     * The whole of what a reader sees after tapping, in order: a page with
     * nothing on it, the same page while the answer is on its way, and then the
     * translation over the words.
     *
     * Worth composing rather than asserting on the states themselves, because
     * the overlay walks away from a page nobody has touched without looking at
     * it - and staying walked away once it *had* been touched would be a
     * translation that arrived and was never drawn.
     */
    @Test
    fun aPageIsMarkedOnlyOnceItsTranslationArrives() = runComposeUiTest {
        var translations by mutableStateOf<Map<TextAnchor, PassageTranslation>>(emptyMap())

        setPageReader(sentences = listOf(wholePage), translations = { translations })

        assertEquals(0, onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size)

        translations = mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Loading())
        waitForIdle()

        // Still nothing over the words: the page is readable while they wait.
        assertEquals(0, onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size)

        translations = mapOf(
            TextAnchor.Page(0, 0) to PassageTranslation.Ready(Translation(translated)),
        )
        waitForIdle()

        onNodeWithText(translated, substring = true).assertExists()
    }

    /** Nothing is drawn over a page nobody has asked about. */
    @Test
    fun anUntouchedPageIsLeftExactlyAsItIs() = runComposeUiTest {
        setPageReader(sentences = listOf(wholePage))

        assertEquals(
            0,
            onAllNodes(hasText(opening, substring = true)).fetchSemanticsNodes().size,
        )
    }

    /**
     * The complaint this was written for: a page set to translate a word was
     * translating the whole sentence, because the words of a page had no places
     * of their own to be pointed at.
     */
}
