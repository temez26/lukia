package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.WorkingCopy
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.TextQuad
import io.lukia.features.translate.domain.PageSentence
import io.lukia.features.translate.domain.PageWord
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.shared.presentation.theme.LukiaTheme
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

    private val opening = "Call me Ishmael."
    private val translated = "Kutsukaa minua Ismaeliksi."

    /** Covers the whole page, so a tap anywhere on it is a tap on these words. */
    private val wholePage = PageSentence(
        index = 0,
        text = opening,
        quads = listOf(TextQuad(left = 0f, top = 0f, right = 800f, bottom = 300f))
    )

    /**
     * The same sentence with its words placed, as a real page gives them.
     *
     * The second word covers the middle of the page, which is where a tap on the
     * page node lands - so a tap that reaches the words at all reaches that one,
     * and a tap that only reaches the sentence is visibly a different answer.
     */
    private val pageOfWords = PageSentence(
        index = 0,
        text = opening,
        quads = listOf(TextQuad(left = 0f, top = 0f, right = 800f, bottom = 300f)),
        words = listOf(
            PageWord(
                index = 0,
                text = "Call",
                quads = listOf(TextQuad(left = 0f, top = 0f, right = 200f, bottom = 300f))
            ),
            PageWord(
                index = 1,
                text = "Ishmael",
                quads = listOf(TextQuad(left = 220f, top = 0f, right = 800f, bottom = 300f))
            )
        )
    )

    /** The same page with nothing under the middle of it, which is where a tap lands. */
    private val pageWithAGapInTheMiddle = pageOfWords.copy(
        words = pageOfWords.words.map { word ->
            word.copy(
                quads = listOf(TextQuad(left = 0f, top = 0f, right = 20f, bottom = 20f))
            )
        }
    )

    /** A word in the very corner, so the middle of the page is not on it. */
    private val cornerWord = PageSentence(
        index = 0,
        text = opening,
        quads = listOf(TextQuad(left = 0f, top = 0f, right = 8f, bottom = 6f))
    )

    /**
     * Page two rather than page one throughout: the first page opens underneath
     * the floating chrome, so its middle is not somewhere a tap can be put.
     */
    @Test
    fun tappingASentenceOnAPageAsksForThatSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(sentences = listOf(wholePage), onPassageSelected = { selected = it })
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
            PageSentence(1, "Some years ago.", listOf(TextQuad(0f, 280f, 100f, 300f)))
        )

        setReader(sentences = page, onPassageSelected = { selected = it })
        tapPageTwo()

        assertEquals(
            "$opening Some years ago.",
            assertNotNull(selected).surroundingText
        )
    }

    /**
     * A tap that landed on no words is a tap on the page. Putting the chrome
     * away is what that has always meant, and translating has not taken it over.
     */
    @Test
    fun tappingAwayFromTheWordsStillPutsTheChromeAway() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(sentences = listOf(cornerWord), onPassageSelected = { selected = it })
        tapPageTwo()
        waitForIdle()

        assertNull(selected, "the margin was read as a sentence")
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size
        )
    }

    /** A page with no text layer at all - a scan - behaves like a page of margin. */
    @Test
    fun aScannedPageWithNoTextLayerStillTogglesTheChrome() = runComposeUiTest {
        setReader(sentences = emptyList())

        tapPageTwo()
        waitForIdle()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size
        )
    }

    @Test
    fun aTranslatedSentenceIsDrawnOverThePage() = runComposeUiTest {
        setReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Ready(Translation(translated)))
            }
        )

        onNodeWithText(translated, substring = true).assertExists()
    }

    @Test
    fun aTranslatorsNoteIsDrawnWithIt() = runComposeUiTest {
        setReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(
                    TextAnchor.Page(0, 0) to PassageTranslation.Ready(
                        Translation(translated, note = "A remark about a word.")
                    )
                )
            }
        )

        onNodeWithText("A remark about a word.", substring = true).assertExists()
    }

    /** Asking for the original back takes the panel off the page. */
    @Test
    fun flippingBackUncoversThePage() = runComposeUiTest {
        setReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(
                    TextAnchor.Page(0, 0) to PassageTranslation.Ready(
                        translation = Translation(translated),
                        showingOriginal = true
                    )
                )
            }
        )

        assertEquals(
            0,
            onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size
        )
    }

    /**
     * A failure has to say what went wrong. Marked only by a tint it looks
     * exactly like a sentence still being translated, which leaves somebody
     * staring at a highlight wondering whether anything is happening at all.
     */
    @Test
    fun aFailedTranslationSaysWhyOnThePage() = runComposeUiTest {
        setReader(
            sentences = listOf(wholePage),
            translations = {
                mapOf(
                    TextAnchor.Page(0, 0) to PassageTranslation.Failed(
                        AppError.Unexpected("Your OpenAI account is out of API credit.")
                    )
                )
            }
        )

        onNodeWithText("out of API credit", substring = true).assertExists()
    }

    /** A sentence still in flight leaves the page readable underneath. */
    @Test
    fun aSentenceStillBeingTranslatedIsNotCoveredOver() = runComposeUiTest {
        setReader(
            sentences = listOf(wholePage),
            translations = { mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Loading()) }
        )

        assertEquals(
            0,
            onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
                .count { it.config.toString().contains(opening) }
        )
    }

    /**
     * The whole of what a reader sees after tapping, in order: a page with
     * nothing on it, the same page while the answer is on its way, and then the
     * translation over the words.
     *
     * Worth composing rather than asserting on the states themselves because
     * the overlay walks away from a page nobody has touched without looking at
     * it - and a page that stayed walked away from once it *had* been touched
     * would be a translation that arrived and was never drawn.
     */
    @Test
    fun aPageIsMarkedOnlyOnceItsTranslationArrives() = runComposeUiTest {
        var translations by mutableStateOf<Map<TextAnchor, PassageTranslation>>(emptyMap())

        setReader(sentences = listOf(wholePage), translations = { translations })

        assertEquals(0, onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size)

        translations = mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Loading())
        waitForIdle()

        // Still nothing over the words: the page is readable while they wait.
        assertEquals(0, onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size)

        translations = mapOf(
            TextAnchor.Page(0, 0) to PassageTranslation.Ready(Translation(translated))
        )
        waitForIdle()

        onNodeWithText(translated, substring = true).assertExists()
    }

    /** Nothing is drawn over a page nobody has asked about. */
    @Test
    fun anUntouchedPageIsLeftExactlyAsItIs() = runComposeUiTest {
        setReader(sentences = listOf(wholePage))

        assertEquals(
            0,
            onAllNodes(hasText(opening, substring = true)).fetchSemanticsNodes().size
        )
    }

    /**
     * The complaint this was written for: a page set to translate a word was
     * translating the whole sentence, because the words of a page had no places
     * of their own to be pointed at.
     */
    @Test
    fun aTapSetToTheWordAsksAboutTheWordOnAPageToo() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            sentences = listOf(pageOfWords),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Word, longPress = null)
        )
        tapPageTwo()

        val selection = assertNotNull(selected, "nothing was selected")
        assertEquals("Ishmael", selection.passage)
        assertEquals(PassageUnit.Word, selection.unit)
        assertEquals(TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 1), selection.anchor)
    }


    /**
     * The space between two words belongs to neither, so a gesture set to the
     * word does nothing there rather than quietly taking the sentence around it
     * - which is the behaviour the setting exists to give.
     */
    @Test
    fun aTapSetToTheWordBetweenTwoOfThemAsksForNothing() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            sentences = listOf(pageWithAGapInTheMiddle),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Word, longPress = null)
        )
        tapPageTwo()

        assertNull(selected, "a tap between two words took the sentence around them")
    }

    /** And the sentence when that is what it is set to, on the same page. */
    @Test
    fun aTapSetToTheSentenceStillAsksAboutTheSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            sentences = listOf(pageOfWords),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null)
        )
        tapPageTwo()

        val selection = assertNotNull(selected, "nothing was selected")
        assertEquals(opening, selection.passage)
        assertEquals(TextAnchor.Page(1, sentenceIndex = 0), selection.anchor)
    }

    /**
     * A page records no paragraphs - what this reads as a block is inferred from
     * the space between lines, and is not something an anchor into a page can
     * name - so the sentence is what a paragraph gesture gets here.
     */
    @Test
    fun aTapSetToTheParagraphTakesTheSentenceOnAPage() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            sentences = listOf(pageOfWords),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Paragraph, longPress = null)
        )
        tapPageTwo()

        assertEquals(PassageUnit.Sentence, assertNotNull(selected).unit)
    }

    /** A translated word stands over its own words, not over the sentence around them. */
    @Test
    fun aTranslatedWordIsDrawnOverThatWordOnly() = runComposeUiTest {
        setReader(
            sentences = listOf(pageOfWords),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 1) to
                        PassageTranslation.Ready(Translation("Ismaeliksi"))
                )
            }
        )

        onNodeWithText("Ismaeliksi", substring = true).assertExists()
        // The sentence itself is untouched, so nothing covers the rest of it.
        assertEquals(
            0,
            onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size
        )
    }

    private fun ComposeUiTest.tapPageTwo() =
        onNodeWithContentDescription("Page 2 of 2").performTouchInput { click() }

    /**
     * The translations are read inside the composition rather than captured, so
     * a test can change them and watch the page follow - which is what actually
     * happens when an answer comes back.
     */
    private fun ComposeUiTest.setReader(
        sentences: List<PageSentence>,
        translations: () -> Map<TextAnchor, PassageTranslation> = { emptyMap() },
        onPassageSelected: (PassageSelection) -> Unit = {},
        gestures: TranslationGestures = TranslationGestures.Default
    ) {
        setContent {
            LukiaTheme {
                ReaderContent(
                    uiState = UiState.Success(book()),
                    settings = ReaderSettings(),
                    outline = ReaderOutline.Chapters(emptyList()),
                    translations = translations(),
                    onBack = {},
                    onRetry = {},
                    onSettingsChange = {},
                    onPassageSelected = onPassageSelected,
                    gestures = gestures,
                    sentencesOnPage = { sentences },
                    renderPage = { _, _ -> null }
                )
            }
        }
    }

    private fun book() = ReaderDocument(
        info = DocumentInfo(
            document = PickedDocument(
                fileName = "moby-dick.pdf",
                extension = "pdf",
                location = "/library/moby-dick.pdf"
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L,
            metadata = BookMetadata(title = "Moby-Dick")
        ),
        workingCopy = WorkingCopy(
            sourceLocation = "/library/moby-dick.pdf",
            path = "/workspace/moby-dick.pdf"
        ),
        content = PagedContent(TwoWidePages)
    )

    /**
     * Wide and short, so both pages fit the test window and each is composed.
     * Real pages are taller; nothing here depends on their proportions.
     */
    private object TwoWidePages : DocumentPages {

        override val outline: List<OutlineEntry> = emptyList()

        override val pageSizes: List<PageSize> =
            List(2) { PageSize(width = 800f, height = 300f) }

        override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

        override fun close() = Unit
    }
}
