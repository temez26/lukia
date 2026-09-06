package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.screens.translation.opening
import io.lukia.features.reader.presentation.screens.translation.translated
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.settings.TranslationGestures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What a tap and a hold mean on a page that is a picture.
 *
 * The gesture settings are the same ones the reflowable reader honours, but
 * the coordinates have to be carried back into the file to find out what was
 * pointed at - so each setting is worth proving here as well.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderPageGestureTest {

    @Test
    fun aTapSetToTheWordAsksAboutTheWordOnAPageToo() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageOfWords),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Word, longPress = null),
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

        setPageReader(
            sentences = listOf(pageWithAGapInTheMiddle),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Word, longPress = null),
        )
        tapPageTwo()

        assertNull(selected, "a tap between two words took the sentence around them")
    }

    /** And the sentence when that is what it is set to, on the same page. */
    @Test
    fun aTapSetToTheSentenceStillAsksAboutTheSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageOfWords),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
        )
        tapPageTwo()

        val selection = assertNotNull(selected, "nothing was selected")
        assertEquals(opening, selection.passage)
        assertEquals(TextAnchor.Page(1, sentenceIndex = 0), selection.anchor)
    }

    /**
     * And the paragraph. A PDF records none of its own - what this reads as a
     * block is inferred from the space left between lines - but that inference
     * is made anyway, and a page's block is a thing an anchor into it can name.
     */
    @Test
    fun aTapSetToTheParagraphTakesTheParagraphOnAPage() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageOfWords, secondBlock),
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Paragraph, longPress = null),
        )
        tapPageTwo()

        val selection = assertNotNull(selected, "nothing was selected")
        assertEquals(PassageUnit.Paragraph, selection.unit)
        assertEquals(opening, selection.passage, "the other block came with it")
        assertEquals(
            TextAnchor.Page(1, sentenceIndex = null, blockIndex = 0),
            selection.anchor,
        )
    }

    /**
     * A paragraph already translated has none of its own sentences left on the
     * page, so a gesture inside it addresses the paragraph whatever it was set
     * to - which is what puts the page's own words back. Without this, tapping
     * the answer asked about a sentence hidden underneath it.
     */
    @Test
    fun aGestureInsideATranslatedParagraphAsksAboutTheParagraph() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageOfWords),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = null, blockIndex = 0) to
                        PassageTranslation.Ready(Translation(translated)),
                )
            },
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
        )
        tapPageTwo()

        assertEquals(PassageUnit.Paragraph, assertNotNull(selected).unit)
    }

    /**
     * A tap on the translation itself puts the book's own words back.
     *
     * This is the whole of what a reader does with a translation once they have
     * read it, and on a page it used to do nothing. The panel is not drawn
     * inside the words it replaces - a page is a picture and cannot be re-set,
     * so the translation is laid over it, starting at the passage's corner and
     * running as far as it needs. Hit-testing the passage's own rectangles
     * therefore missed nearly all of what the reader could see and tap on.
     */
    @Test
    fun tappingASentencesTranslationOnAPagePutsTheSentenceBack() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(
            // A sentence in a corner of the page, far smaller than its own
            // translation - so the panel is almost entirely off it.
            sentences = listOf(cornerWord),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = 0) to
                        PassageTranslation.Ready(Translation(translated)),
                )
            },
            onPassageSelected = { selected = it },
        )

        onNodeWithText(translated, substring = true).performTouchInput { click() }

        assertEquals(
            TextAnchor.Page(1, sentenceIndex = 0),
            assertNotNull(selected, "tapping the translation asked about nothing").anchor,
        )
    }

    /**
     * And a word's translation asks about that word, whatever the tap is set to.
     *
     * The worst case of the same fault, and the one that was reported: a word's
     * panel is several times the size of the word under it, so a tap on the
     * translation landed on the sentence around it - and a tap set to the
     * sentence then translated the sentence rather than putting the word back.
     */
    @Test
    fun tappingAWordsTranslationOnAPagePutsThatWordBack() = runComposeUiTest {
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageWithATinyWord),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
        )

        onNodeWithText("Kutsukaa", substring = true).performTouchInput { click() }

        val selection = assertNotNull(selected, "tapping the translation asked about nothing")

        assertEquals(PassageUnit.Word, selection.unit)
        assertEquals(TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 0), selection.anchor)
    }

    /** A translated word stands over its own words, not over the sentence around them. */
    @Test
    fun aTranslatedWordIsDrawnOverThatWordOnly() = runComposeUiTest {
        setPageReader(
            sentences = listOf(pageOfWords),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 1) to
                        PassageTranslation.Ready(Translation("Ismaeliksi")),
                )
            },
        )

        onNodeWithText("Ismaeliksi", substring = true).assertExists()
        // The sentence itself is untouched, so nothing covers the rest of it.
        assertEquals(
            0,
            onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size,
        )
    }
}
