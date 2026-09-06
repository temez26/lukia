package io.lukia.features.reader.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What one finger on one word gets.
 *
 * The cases that matter are the ones where "split on spaces" is wrong: the
 * punctuation that is not part of the word, and the punctuation that is.
 */
class WordsTest {

    @Test
    fun aSentenceIsCutIntoTheWordsItIsMadeOf() {
        assertEquals(
            listOf("Call", "me", "Ishmael"),
            "Call me Ishmael.".onlySentence().words().map { it.text },
        )
    }

    /** The full stop is not part of the last word, and neither are the quotes. */
    @Test
    fun thePunctuationAroundAWordIsNotPartOfIt() {
        assertEquals(
            listOf("Run", "he", "shouted"),
            "\"Run!\" he shouted.".onlySentence().words().map { it.text },
        )
    }

    /**
     * The punctuation inside one is. Offering "don" and "t" as two things to
     * look up would be worse than offering nothing.
     */
    @Test
    fun anApostropheOrAHyphenInsideAWordKeepsItWhole() {
        assertEquals(
            listOf("It's", "self-evident", "he'd", "say"),
            "It's self-evident, he'd say.".onlySentence().words().map { it.text },
        )
    }

    /** A joiner with nothing after it closed a quotation rather than joining anything. */
    @Test
    fun aTrailingJoinerIsNotPartOfTheWord() {
        assertEquals(
            listOf("the", "whale", "was", "quiet"),
            "the 'whale' was quiet-".onlySentence().words().map { it.text },
        )
    }

    @Test
    fun aNumberIsOneWordRatherThanTwoAroundItsPoint() {
        assertEquals(listOf("It", "cost", "3.50"), "It cost 3.50".onlySentence().words().map { it.text })
    }

    /**
     * The offsets index the whole passage rather than the sentence, so a word
     * can be highlighted and replaced by the same code that handles a sentence.
     */
    @Test
    fun aWordKnowsWhereItIsInThePassageRatherThanInItsSentence() {
        val passage = "Call me Ishmael. Some years ago."
        val second = splitIntoSentences(passage)[1]

        val ago = second.words().last()

        assertEquals("ago", ago.text)
        assertEquals(passage.indexOf("ago"), ago.start)
        assertEquals(passage.indexOf("ago") + 3, ago.end)
    }

    /** A word is addressed through its sentence, so it has to know which. */
    @Test
    fun everyWordCarriesTheSentenceItBelongsTo() {
        val words = splitIntoSentences("Call me Ishmael. Some years ago.").words()

        assertEquals(listOf(0, 0, 0, 1, 1, 1), words.map { it.sentenceIndex })
        assertEquals(listOf(0, 1, 2, 0, 1, 2), words.map { it.index })
    }

    @Test
    fun theWordAtAnOffsetIsTheOneCoveringIt() {
        val passage = "Call me Ishmael."
        val words = splitIntoSentences(passage).words()

        assertEquals("Ishmael", words.wordAt(passage.indexOf("Ishmael") + 2)?.text)
        // The full stop belongs to no word, which is what makes a long press
        // there do nothing rather than translate the word before it.
        assertNull(words.wordAt(passage.length - 1))
    }

    private fun String.onlySentence(): Sentence = splitIntoSentences(this).first()
}
