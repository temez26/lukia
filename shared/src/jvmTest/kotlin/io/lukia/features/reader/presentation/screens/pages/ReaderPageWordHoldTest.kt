package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.settings.TranslationGestures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Holding a word on a page of a fixed-layout document, which is what opens
 * everything else that word means.
 *
 * The panel matters more here than anywhere else in the app. A PDF's page is a
 * picture and cannot be re-set, so a translated word is *covered* by the answer
 * drawn over it - and the answer is several times the size of the word. Almost
 * every gesture aimed at such a word therefore lands on the panel rather than on
 * the page, and if the panel did not tell a hold from a tap the whole feature
 * would be unreachable on a PDF.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderPageWordHoldTest {

    @Test
    fun holdingAWordOnAPageIsRaisedAsAHold() = runComposeUiTest {
        var held: PassageSelection? = null
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageOfWords),
            onPassageSelected = { selected = it },
            onPassageHold = { held = it },
        )

        onNodeWithContentDescription("Page 2 of 2").performTouchInput { longClick() }

        val selection = assertNotNull(held, "holding a word raised nothing")
        assertEquals(PassageUnit.Word, selection.unit)
        assertEquals(TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 1), selection.anchor)
        assertNull(selected, "a hold was also read as a plain selection")
    }

    /** The gesture the reader set is what decides: a hold set to the sentence is one. */
    @Test
    fun holdingASentenceIsStillJustTheSentence() = runComposeUiTest {
        var held: PassageSelection? = null
        var selected: PassageSelection? = null

        setPageReader(
            sentences = listOf(wholePage),
            onPassageSelected = { selected = it },
            onPassageHold = { held = it },
            gestures = TranslationGestures(tap = null, longPress = PassageUnit.Sentence),
        )

        onNodeWithContentDescription("Page 2 of 2").performTouchInput { longClick() }

        assertNull(held, "a sentence was offered a word's panel")
        assertEquals(PassageUnit.Sentence, assertNotNull(selected).unit)
    }

    /** The case the whole panel exists for: the word is under its own translation. */
    @Test
    fun holdingAWordsTranslationOnAPageIsRaisedAsAHold() = runComposeUiTest {
        var held: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageWithATinyWord),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageHold = { held = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
        )

        onNodeWithText("Kutsukaa", substring = true).performTouchInput { longClick() }

        val selection = assertNotNull(held, "holding the translation raised nothing")
        assertEquals(PassageUnit.Word, selection.unit)
        assertEquals(TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 0), selection.anchor)
    }

    /** And a tap on it still puts the page's own word back, exactly as it did. */
    @Test
    fun tappingAWordsTranslationStillPutsThatWordBack() = runComposeUiTest {
        var selected: PassageSelection? = null
        var held: PassageSelection? = null

        setPageReader(
            sentences = listOf(pageWithATinyWord),
            translations = {
                mapOf(
                    TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageSelected = { selected = it },
            onPassageHold = { held = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
        )

        onNodeWithText("Kutsukaa", substring = true).performTouchInput { click() }

        assertEquals(
            TextAnchor.Page(1, sentenceIndex = 0, wordIndex = 0),
            assertNotNull(selected).anchor,
        )
        assertNull(held, "a tap opened the word's panel")
    }
}
