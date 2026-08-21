package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pointing at one word rather than at the sentence around it, and what the
 * gesture settings make a tap and a hold mean.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderWordLookupTest {

    @Test
    fun holdingAWordAsksAboutThatWord() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(opening, substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        val selection = assertNotNull(selected, "no word was selected")
        assertEquals("Call", selection.passage)
        assertEquals(TextAnchor.Section(0, 0, 0, wordIndex = 0), selection.anchor)
    }

    /** A word travels with the paragraph around it, exactly as a sentence does. */
    @Test
    fun aWordArrivesWithTheParagraphAroundIt() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(opening, substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertEquals("$opening $second", assertNotNull(selected).surroundingText)
    }

    /** One translated word stands in its sentence, and the sentence stays the book's. */
    @Test
    fun aTranslatedWordIsShownInsideTheBooksOwnSentence() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 2) to
                        PassageTranslation.Ready(Translation("Ismaeliksi")),
                )
            },
        )

        onNodeWithText("Call me Ismaeliksi.", substring = true).assertExists()
    }

    /**
     * Asking for what is on screen asks about the paragraphs on it, once each.
     *
     * Paragraphs rather than the sentences in them, because that is nearly the
     * whole cost of the feature. Measured against a model running on the
     * reader's own machine, on one block of twelve sentences:
     *
     *     a request per sentence, four at once   44s, 7,500 tokens
     *     the paragraph, in one request          12s, 1,000 tokens
     *
     * A request per sentence sends the standing instructions, the book and the
     * surrounding paragraph again every time - so the page is read once per
     * sentence printed on it.
     */
    @Test
    fun askingForWhatIsOnScreenAsksAboutTheParagraphsOnIt() = runComposeUiTest {
        val requested = mutableListOf<PassageSelection>()
        var translate by mutableStateOf(TranslateOnScreen.Idle)

        setReader(
            onPassagesRequested = { requested += it },
            translateOnScreen = { translate },
        )

        // Nothing until it is asked for: this is not a standing order.
        assertEquals(0, requested.size)

        translate = translate.requested()
        waitForIdle()

        val passages = requested.map { it.passage }

        // The two sentences of the opening block, asked about as the one
        // paragraph they are.
        assertTrue(passages.contains("$opening $second"), "$passages")
        assertFalse(passages.contains(opening), "a sentence was asked about on its own: $passages")

        assertTrue(
            requested.all { it.unit == PassageUnit.Paragraph },
            "something other than a paragraph was asked about: $requested",
        )
        assertEquals(passages.size, passages.distinct().size, "a paragraph was asked about twice")
    }

    /**
     * Each gesture does one thing, always.
     *
     * An earlier version had a tap mean the word when the word under it had
     * already been translated, which reads well as a sentence and terribly as a
     * gesture - the same finger in the same place did different things depending
     * on what had happened there before. A tap set to the sentence means the
     * sentence, whatever is underneath it.
     */
    @Test
    fun aTapMeansWhatItIsSetToWhateverIsUnderIt() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageSelected = { selected = it },
        )

        onNodeWithText("Kutsukaa me Ishmael.", substring = true).clickFirstWords()

        assertEquals(TextAnchor.Section(0, 0, 0), assertNotNull(selected).anchor)
    }

    /** Which is also how a translated word is put back: the gesture that asked. */
    @Test
    fun holdingATranslatedWordAsksAboutThatWordAgain() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageSelected = { selected = it },
        )

        onNodeWithText("Kutsukaa me Ishmael.", substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertEquals(
            TextAnchor.Section(0, 0, 0, wordIndex = 0),
            assertNotNull(selected).anchor,
        )
    }

    /** A gesture set to the paragraph asks about all of it, as one passage. */
    @Test
    fun aGestureSetToTheParagraphAsksAboutTheWholeBlock() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = PassageUnit.Paragraph, longPress = null),
        )

        onNodeWithText(opening, substring = true).clickFirstWords()

        val selection = assertNotNull(selected, "nothing was selected")
        assertEquals(TextAnchor.Section(0, 0, sentenceIndex = null), selection.anchor)
        assertEquals("$opening $second", selection.passage)
        assertEquals(PassageUnit.Paragraph, selection.unit)
    }

    /** A translated paragraph stands in for every sentence that was in it. */
    @Test
    fun aTranslatedParagraphReplacesTheWholeBlock() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, sentenceIndex = null) to
                        PassageTranslation.Ready(Translation("Koko kappale suomeksi.")),
                )
            },
        )

        onNodeWithText("Koko kappale suomeksi.", substring = true).assertExists()
        assertEquals(
            0,
            onAllNodes(hasText(opening, substring = true)).fetchSemanticsNodes().size,
        )
    }

    /** Off is off - a gesture turned off reaches nothing, not something narrower. */
    @Test
    fun aGestureTurnedOffAsksForNothing() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            onPassageSelected = { selected = it },
            gestures = TranslationGestures(tap = null, longPress = null),
        )

        onNodeWithText(opening, substring = true).clickFirstWords()
        onNodeWithText(opening, substring = true).performTouchInput { longClick(Offset(4f, 4f)) }

        assertNull(selected, "a gesture that was turned off still translated something")
    }

    /** A word nobody has touched is part of its sentence, and a tap still means that. */
    @Test
    fun tappingAnUntouchedWordStillAsksForTheSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 2) to
                        PassageTranslation.Ready(Translation("Ismaeliksi")),
                )
            },
            onPassageSelected = { selected = it },
        )

        // "Call" is untouched; the translated word is the third one.
        onNodeWithText("Call me Ismaeliksi.", substring = true).clickFirstWords()

        assertEquals(TextAnchor.Section(0, 0, 0), assertNotNull(selected).anchor)
    }

    /**
     * A word asked about on its own is usually the ambiguous one, so its remark
     * is the one most worth reading - and it names the word it is about, since
     * the translation is standing where that word was.
     */
    @Test
    fun aRemarkAboutASingleWordIsShownUnderTheParagraph() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 2) to PassageTranslation.Ready(
                        Translation("Ismaeliksi", note = "kept as the book writes it"),
                    ),
                )
            },
        )

        onNodeWithText("Ishmael: kept as the book writes it", substring = true).assertExists()
    }

    /** A word that could not be translated says why, exactly as a sentence does. */
    @Test
    fun aWordThatFailedSaysWhyUnderTheParagraph() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to PassageTranslation.Failed(
                        AppError.Unexpected("That API key was not accepted."),
                    ),
                )
            },
        )

        onNodeWithText("was not accepted", substring = true).assertExists()
        // And the word is still there to read, and to tap again.
        onNodeWithText(opening, substring = true).assertExists()
    }

    /**
     * A word is only on the page while its sentence's own words are. Once the
     * sentence is translated whole, a remark about a word underneath it is a
     * remark about nothing the reader can see.
     */
    @Test
    fun aWordsRemarkGoesAwayWhenItsSentenceIsTranslatedWhole() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(Translation(translated)),
                    TextAnchor.Section(0, 0, 0, wordIndex = 2) to PassageTranslation.Ready(
                        Translation("Ismaeliksi", note = "kept as the book writes it"),
                    ),
                )
            },
        )

        assertEquals(
            0,
            onAllNodes(hasText("kept as the book writes it", substring = true))
                .fetchSemanticsNodes()
                .size,
        )
    }

    /**
     * A tap the reader has turned off is still a tap on the page, and a tap on
     * the page has always reached the chrome. Swallowing it left somebody who
     * only wanted to look words up with no way to get the toolbar back over
     * their own text.
     */
}
