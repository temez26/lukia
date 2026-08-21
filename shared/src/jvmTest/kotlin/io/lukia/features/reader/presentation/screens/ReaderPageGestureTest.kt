package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
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
