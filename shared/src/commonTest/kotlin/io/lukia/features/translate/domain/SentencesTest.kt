package io.lukia.features.translate.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentencesTest {

    @Test
    fun aParagraphIsCutAtItsFullStops() {
        val sentences = splitIntoSentences("Call me Ishmael. Some years ago I went to sea.")

        assertEquals(
            listOf("Call me Ishmael.", "Some years ago I went to sea."),
            sentences.map { it.text },
        )
    }

    @Test
    fun questionsAndExclamationsEndSentencesToo() {
        val sentences = splitIntoSentences("Who is there? Nobody! Come in.")

        assertEquals(listOf("Who is there?", "Nobody!", "Come in."), sentences.map { it.text })
    }

    /**
     * The offsets are what the reader highlights with, so they have to point at
     * the sentence in the text it came from and not at a copy of it.
     */
    @Test
    fun aSentenceKnowsWhereItSits() {
        val text = "Call me Ishmael. Some years ago."
        val sentences = splitIntoSentences(text)

        sentences.forEach { sentence ->
            assertEquals(sentence.text, text.substring(sentence.start, sentence.end))
        }

        assertEquals(listOf(0, 1), sentences.map { it.index })
    }

    /** Whitespace between sentences belongs to neither of them. */
    @Test
    fun theGapBetweenSentencesIsNotPartOfEither() {
        val sentences = splitIntoSentences("First.    Second.")

        assertEquals(listOf("First.", "Second."), sentences.map { it.text })
        assertEquals(10, sentences[1].start)
    }

    @Test
    fun anEllipsisIsOneEndingRatherThanThree() {
        val sentences = splitIntoSentences("He hesitated... Then he spoke.")

        assertEquals(listOf("He hesitated...", "Then he spoke."), sentences.map { it.text })
    }

    @Test
    fun stackedPunctuationIsOneEnding() {
        val sentences = splitIntoSentences("You did what?! I told you.")

        assertEquals(listOf("You did what?!", "I told you."), sentences.map { it.text })
    }

    /** The full stop inside a quotation ends the quotation, not the sentence. */
    @Test
    fun aClosingQuoteBelongsToTheSentenceItCloses() {
        val sentences = splitIntoSentences(""""Run!" he shouted. Nobody moved.""")

        assertEquals(listOf(""""Run!" he shouted.""", "Nobody moved."), sentences.map { it.text })
    }

    /**
     * The other half of the dialogue rule: when what follows the quotation is a
     * new sentence rather than a speech tag, it really is a new sentence.
     */
    @Test
    fun quotedSpeechEndsWhereTheNextSentenceStarts() {
        val sentences = splitIntoSentences(""""Run!" He was already gone.""")

        assertEquals(listOf(""""Run!"""", "He was already gone."), sentences.map { it.text })
    }

    @Test
    fun aDecimalPointIsNotAFullStop() {
        val sentences = splitIntoSentences("It cost 3.14 pounds. That was too much.")

        assertEquals(
            listOf("It cost 3.14 pounds.", "That was too much."),
            sentences.map { it.text },
        )
    }

    @Test
    fun titlesDoNotEndSentences() {
        val sentences = splitIntoSentences("Mr. Bennet was among the earliest. Mrs. Bennet was not.")

        assertEquals(
            listOf("Mr. Bennet was among the earliest.", "Mrs. Bennet was not."),
            sentences.map { it.text },
        )
    }

    @Test
    fun initialsDoNotEndSentences() {
        val sentences = splitIntoSentences("J. R. R. Tolkien wrote it. He took years.")

        assertEquals(
            listOf("J. R. R. Tolkien wrote it.", "He took years."),
            sentences.map { it.text },
        )
    }

    /** A passage with no punctuation at all is still one thing worth tapping. */
    @Test
    fun textWithNoPunctuationIsASingleSentence() {
        val sentences = splitIntoSentences("Chapter One")

        assertEquals(listOf("Chapter One"), sentences.map { it.text })
    }

    @Test
    fun aFinalFragmentWithoutAFullStopIsKept() {
        val sentences = splitIntoSentences("She left. He stayed behind")

        assertEquals(listOf("She left.", "He stayed behind"), sentences.map { it.text })
    }

    @Test
    fun textWithNothingToReadInItYieldsNoSentences() {
        assertTrue(splitIntoSentences("").isEmpty())
        assertTrue(splitIntoSentences("   \n  ").isEmpty())
        assertTrue(splitIntoSentences("... ...").isEmpty())
    }

    @Test
    fun indicesAreConsecutiveFromZero() {
        val sentences = splitIntoSentences("One. Two. Three. Four.")

        assertEquals(listOf(0, 1, 2, 3), sentences.map { it.index })
    }

    @Test
    fun theSentenceAtAnOffsetIsTheOneCoveringIt() {
        val text = "Call me Ishmael. Some years ago."
        val sentences = splitIntoSentences(text)

        assertEquals(0, sentences.sentenceAt(0)?.index)
        assertEquals(0, sentences.sentenceAt(15)?.index)
        assertEquals(1, sentences.sentenceAt(17)?.index)
        assertEquals(1, sentences.sentenceAt(text.length - 1)?.index)
    }

    /** A tap landing in the gap between sentences has not chosen one. */
    @Test
    fun anOffsetOutsideEverySentenceMatchesNothing() {
        val sentences = splitIntoSentences("First.    Second.")

        assertNull(sentences.sentenceAt(7))
        assertNull(sentences.sentenceAt(999))
    }
}
