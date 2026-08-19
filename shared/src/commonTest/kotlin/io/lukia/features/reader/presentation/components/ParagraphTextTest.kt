package io.lukia.features.reader.presentation.components

import androidx.compose.ui.text.font.FontStyle
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.domain.splitIntoSentences
import io.lukia.features.translate.domain.words
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParagraphTextTest {

    @Test
    fun aParagraphWithNothingTranslatedReadsExactlyAsTheBookWroteIt() {
        val runs = listOf(run("Call me Ishmael. "), run("Some years ago.", Emphasis.Italic))

        val drawn = paragraph(runs) { null }

        assertEquals("Call me Ishmael. Some years ago.", drawn.text.text)
    }

    @Test
    fun aTranslatedSentenceIsDrawnInPlaceOfTheWordsItStandsFor() {
        val runs = listOf(run("Call me Ishmael. Some years ago."))

        val drawn = paragraph(runs) { sentence ->
            if (sentence.index == 0) "Kutsukaa minua Ismaeliksi." else null
        }

        assertEquals("Kutsukaa minua Ismaeliksi. Some years ago.", drawn.text.text)
    }

    /** Emphasis around a translated sentence is the author's, and has to survive. */
    @Test
    fun theEmphasisEitherSideOfATranslationIsKept() {
        val runs = listOf(
            run("Call me Ishmael. "),
            run("Some", Emphasis.Italic),
            run(" years ago."),
        )

        val drawn = paragraph(runs) { sentence ->
            if (sentence.index == 0) "Kutsukaa minua." else null
        }

        assertEquals("Kutsukaa minua. Some years ago.", drawn.text.text)

        val italic = drawn.text.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertEquals("Some", drawn.text.text.substring(italic.start, italic.end))
    }

    /**
     * A translated sentence is set in the paragraph's own face. Emphasis inside
     * a sentence belongs to the words that carried it, and those words are gone.
     */
    @Test
    fun aTranslationDoesNotInheritTheEmphasisOfTheWordsItReplaced() {
        val runs = listOf(run("Call me Ishmael.", Emphasis.Italic), run(" Some years ago."))

        val drawn = paragraph(runs) { sentence ->
            if (sentence.index == 0) "Kutsukaa minua." else null
        }

        assertEquals(
            0,
            drawn.text.spanStyles.count { it.start < "Kutsukaa minua.".length },
        )
    }

    /** A sentence can be split across runs, and is still one sentence. */
    @Test
    fun aSentenceSpanningSeveralRunsIsReplacedWhole() {
        val runs = listOf(run("Call me "), run("Ishmael", Emphasis.Bold), run(". Some years ago."))

        val drawn = paragraph(runs) { sentence ->
            if (sentence.index == 0) "Kutsukaa minua Ismaeliksi." else null
        }

        assertEquals("Kutsukaa minua Ismaeliksi. Some years ago.", drawn.text.text)
    }

    @Test
    fun everySentenceCanBeFoundWhereItIsDrawn() {
        val runs = listOf(run("Call me Ishmael. Some years ago. It was cold."))

        val drawn = paragraph(runs) { null }
        val text = drawn.text.text

        assertEquals(0, drawn.sentenceAt(text.indexOf("Call")))
        assertEquals(1, drawn.sentenceAt(text.indexOf("Some")))
        assertEquals(2, drawn.sentenceAt(text.indexOf("It was")))
    }

    /**
     * Where each sentence ended up is also what the marks are drawn from, so a
     * sentence nobody replaced still has to report the stretch it occupies.
     */
    @Test
    fun eachSentenceReportsTheStretchOfTheDrawnParagraphItOccupies() {
        val runs = listOf(run("Hi. Some years ago."))

        val drawn = paragraph(runs) { sentence ->
            if (sentence.index == 0) "A very much longer opening indeed." else null
        }

        val text = drawn.text.text
        val replaced = drawn.sentences.single { it.index == 0 }
        val untouched = drawn.sentences.single { it.index == 1 }

        assertEquals("A very much longer opening indeed.", text.substring(replaced.start, replaced.end))
        assertEquals("Some years ago.", text.substring(untouched.start, untouched.end))
    }

    /**
     * The bug this class exists to prevent: replacing one sentence moves every
     * sentence after it, so a tap lower down would otherwise be read against
     * offsets the reader is no longer looking at.
     */
    @Test
    fun sentencesAfterATranslationAreFoundAtTheirNewPositions() {
        val runs = listOf(run("Hi. Some years ago. It was cold."))

        val drawn = paragraph(runs) { sentence ->
            if (sentence.index == 0) "A very much longer opening sentence indeed." else null
        }

        val text = drawn.text.text
        assertEquals(0, drawn.sentenceAt(text.indexOf("A very")))
        assertEquals(1, drawn.sentenceAt(text.indexOf("Some")))
        assertEquals(2, drawn.sentenceAt(text.indexOf("It was")))
    }

    /** The gap between two sentences belongs to neither of them. */
    @Test
    fun theSpaceAfterAFullStopBelongsToNoSentence() {
        val runs = listOf(run("Hi.  There."))

        val drawn = paragraph(runs) { null }

        assertNull(drawn.sentenceAt(drawn.text.text.indexOf("  ") + 1))
    }

    @Test
    fun anOffsetPastTheEndBelongsToNoSentence() {
        val drawn = paragraph(listOf(run("Hi."))) { null }

        assertNull(drawn.sentenceAt(drawn.text.text.length))
        assertNull(drawn.sentenceAt(999))
    }

    /**
     * A single word is replaced where it stands, leaving the sentence around it
     * exactly as the book set it - which is the whole difference between looking
     * a word up and translating the line it is in.
     */
    @Test
    fun oneTranslatedWordTakesThePlaceOfThatWordAlone() {
        val runs = listOf(run("Call me Ishmael."))

        val drawn = paragraph(runs, { null }) { _, word ->
            "kutsukaa".takeIf { word.text == "Call" }
        }

        assertEquals("kutsukaa me Ishmael.", drawn.text.text)
    }

    /** Where each word ended up, so a long press finds the one under it. */
    @Test
    fun everyWordIsFoundAtWhereItWasDrawn() {
        val runs = listOf(run("Call me Ishmael."))

        val drawn = paragraph(runs, { null }) { _, word ->
            "kutsukaa".takeIf { word.text == "Call" }
        }

        assertEquals(0, drawn.wordAt(0)?.span?.index)
        // "me" now begins nine characters in rather than five.
        assertEquals(1, drawn.wordAt(drawn.text.text.indexOf("me"))?.span?.index)
        assertEquals(2, drawn.wordAt(drawn.text.text.indexOf("Ishmael"))?.span?.index)
    }

    /**
     * A sentence translated whole has no words of its own left on the page, so
     * none are offered - which is what stops a long press inside a translation
     * from asking about a word of the translation.
     */
    @Test
    fun aSentenceTranslatedWholeOffersNoWordsInside() {
        val runs = listOf(run("Call me Ishmael. Some years ago."))

        val drawn = paragraph(runs) { sentence ->
            "Kutsukaa minua Ismaeliksi.".takeIf { sentence.index == 0 }
        }

        assertNull(drawn.wordAt(2), "a word of the translation was offered")
        // The sentence beside it is untouched, so its words are still there.
        assertEquals(1, drawn.wordAt(drawn.text.text.indexOf("Some"))?.sentenceIndex)
    }

    /** The gap between two words belongs to neither of them. */
    @Test
    fun theSpaceBetweenTwoWordsIsNotEitherOfThem() {
        val runs = listOf(run("Call me Ishmael."))
        val drawn = paragraph(runs) { null }

        assertNull(drawn.wordAt("Call".length))
    }

    @Test
    fun aParagraphWithNothingToReadDrawsNothing() {
        val drawn = paragraph(listOf(run("   "))) { null }

        assertEquals("   ", drawn.text.text)
        assertNull(drawn.sentenceAt(0))
    }

    private fun paragraph(
        runs: List<TextRun>,
        translationOf: (Sentence) -> String?,
    ): ParagraphText = paragraph(runs, translationOf) { _, _ -> null }

    private fun paragraph(
        runs: List<TextRun>,
        translationOf: (Sentence) -> String?,
        wordTranslationOf: (Sentence, Word) -> String?,
    ): ParagraphText {
        val sentences = splitIntoSentences(runs.plain())

        return buildParagraphText(
            runs = runs,
            sentences = sentences,
            words = sentences.words(),
        ) { sentence, word ->
            if (word == null) translationOf(sentence) else wordTranslationOf(sentence, word)
        }
    }

    private fun run(text: String, vararg emphasis: Emphasis) =
        TextRun(text = text, emphasis = emphasis.toSet())
}
