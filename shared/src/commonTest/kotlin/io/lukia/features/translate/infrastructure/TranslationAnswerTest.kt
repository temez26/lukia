package io.lukia.features.translate.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading a translation out of an answer that has not finished arriving.
 *
 * Every case here is a real moment in a stream: the buffer at some character
 * count, taken as it would be the instant that chunk landed. What is being
 * asserted is that what a reader sees is always a prefix of what they will end
 * up with - never a stray backslash, never half an escape, and never the
 * model's preamble mistaken for the sentence.
 */
class TranslationAnswerTest {

    @Test
    fun nothingIsShownBeforeTheKeyHasArrived() {
        assertNull(translationSoFar(""))
        assertNull(translationSoFar("{"))
        assertNull(translationSoFar("""{"transl"""))
    }

    @Test
    fun nothingIsShownBeforeTheValueHasBegun() {
        assertNull(translationSoFar("""{"translation""""))
        assertNull(translationSoFar("""{"translation":"""))
    }

    @Test
    fun asMuchOfTheSentenceAsHasArrivedIsShown() {
        assertEquals("", translationSoFar("""{"translation":""""))
        assertEquals("Kutsukaa", translationSoFar("""{"translation":"Kutsukaa"""))
        assertEquals(
            "Kutsukaa minua Ismaeliksi.",
            translationSoFar("""{"translation":"Kutsukaa minua Ismaeliksi.""""),
        )
    }

    @Test
    fun theSentenceStopsAtItsClosingQuoteRatherThanRunningIntoTheNote() {
        assertEquals(
            "Hei.",
            translationSoFar("""{"translation":"Hei.","note":"An informal greeting."}"""),
        )
    }

    /** The one case hand-written JSON always gets wrong. */
    @Test
    fun anEscapedQuoteIsPartOfTheSentenceRatherThanTheEndOfIt() {
        assertEquals(
            """He said "hello".""",
            translationSoFar("""{"translation":"He said \"hello\".","note":""}"""),
        )
    }

    @Test
    fun escapesAreReadAsTheCharactersTheyStandFor() {
        assertEquals(
            "One\nTwo\tThree\\Four/Five",
            translationSoFar("""{"translation":"One\nTwo\tThree\\Four\/Five"}"""),
        )
        assertEquals("Ismaël", translationSoFar("""{"translation":"Isma\u00ebl"}"""))
    }

    /**
     * An escape cut in half by the end of the buffer is a character still
     * arriving. Showing the backslash for one frame and replacing it on the next
     * is a flicker in the middle of a sentence somebody is reading.
     */
    @Test
    fun anEscapeStillArrivingIsNotShownHalfFinished() {
        assertEquals("Hei", translationSoFar("""{"translation":"Hei\"""))
        assertEquals("Isma", translationSoFar("""{"translation":"Isma\u00"""))
    }

    /**
     * A local model wraps its answer in a code fence or introduces it. The object
     * is found inside whatever came back rather than demanded to be all of it -
     * the same forgiveness [readTranslatedPassage] applies to a finished answer.
     */
    @Test
    fun anAnswerWrappedInProseIsStillRead() {
        assertEquals(
            "Hei.",
            translationSoFar("Sure! Here you go:\n```json\n{\"translation\": \"Hei.\""),
        )
    }

    /** Prose that never begins translating is not a translation of anything. */
    @Test
    fun proseWithNoAnswerInItShowsNothing() {
        assertNull(translationSoFar("I am unable to help with that."))
    }
}
