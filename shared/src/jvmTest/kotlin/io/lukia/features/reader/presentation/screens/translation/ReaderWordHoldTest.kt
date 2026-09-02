package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.features.translate.domain.text.TextAnchor
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Holding a word the reader has already looked up, which is what opens
 * everything else that word means.
 *
 * Its own class because it is a different question from what a gesture asks the
 * translator for - see [ReaderWordLookupTest] for that one. This is about the
 * setting that sits beside those: whether a hold on a word with an answer means
 * "tell me more about this word" whatever the hold is otherwise set to.
 *
 * The case that matters is a reader whose hold asks for the sentence. Before the
 * setting existed the hold resolved to the sentence around the word and the word
 * was never named, so none of this was reachable for them at all - a setting
 * about translating deciding a question about reading.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderWordHoldTest {

    /**
     * A word already answered is still named by the hold that reached it, and it
     * is the word rather than the sentence around it - which is what lets the
     * panel be about the right word.
     */
    @Test
    fun holdingATranslatedWordNamesThatWord() = runComposeUiTest {
        var held: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageHold = { held = it },
        )

        onNodeWithText("Kutsukaa me Ishmael.", substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertEquals(
            TextAnchor.Section(0, 0, 0, wordIndex = 0),
            assertNotNull(held).anchor,
        )
    }

    /**
     * The point of the setting: what else a word means is reachable however the
     * hold is set.
     *
     * Before it existed, a reader whose hold asked for the sentence could never
     * open a word at all - the hold resolved to the sentence around it and the
     * word was never named. Which is a setting about translating deciding a
     * question about reading.
     */
    @Test
    fun anAnsweredWordIsHeldEvenWhenTheHoldAsksForTheSentence() = runComposeUiTest {
        var held: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageHold = { held = it },
            gestures = TranslationGestures(
                tap = PassageUnit.Sentence,
                longPress = PassageUnit.Sentence,
            ),
        )

        onNodeWithText("Kutsukaa me Ishmael.", substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        val selection = assertNotNull(held, "the word was never named")
        assertEquals(PassageUnit.Word, selection.unit)
        assertEquals(TextAnchor.Section(0, 0, 0, wordIndex = 0), selection.anchor)
    }

    /** Even with the hold turned off entirely: this is not one of the units. */
    @Test
    fun anAnsweredWordIsHeldEvenWhenTheHoldIsOff() = runComposeUiTest {
        var held: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageHold = { held = it },
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
        )

        onNodeWithText("Kutsukaa me Ishmael.", substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertEquals(
            TextAnchor.Section(0, 0, 0, wordIndex = 0),
            assertNotNull(held, "the word was never named").anchor,
        )
    }

    /**
     * Turned off, the hold is the unit the reader set and nothing else - so it
     * is the sentence, raised as a plain selection rather than as a word to open.
     */
    @Test
    fun aReaderWhoTurnedTheExtraOffJustGetsTheirGesture() = runComposeUiTest {
        var selected: PassageSelection? = null
        var held: PassageSelection? = null

        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 0) to
                        PassageTranslation.Ready(Translation("Kutsukaa")),
                )
            },
            onPassageSelected = { selected = it },
            onPassageHold = { held = it },
            gestures = TranslationGestures(
                tap = PassageUnit.Sentence,
                longPress = PassageUnit.Sentence,
                wordDetails = false,
            ),
        )

        onNodeWithText("Kutsukaa me Ishmael.", substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertNull(held, "a word was opened after the reader turned that off")
        assertEquals(PassageUnit.Sentence, assertNotNull(selected).unit)
    }

    /**
     * And a word nobody has looked up is not a word with more to say: the hold
     * set to the sentence asks about the sentence, exactly as it says it will.
     */
    @Test
    fun aWordNobodyHasLookedUpIsNotTreatedAsOne() = runComposeUiTest {
        var selected: PassageSelection? = null
        var held: PassageSelection? = null

        setReader(
            onPassageSelected = { selected = it },
            onPassageHold = { held = it },
            gestures = TranslationGestures(
                tap = PassageUnit.Sentence,
                longPress = PassageUnit.Sentence,
            ),
        )

        onNodeWithText(opening, substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertNull(held, "an untouched word was treated as one with more to say")
        assertEquals(PassageUnit.Sentence, assertNotNull(selected).unit)
    }
}
