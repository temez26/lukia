package io.lukia.features.reader.presentation.components

import androidx.compose.ui.text.font.FontStyle
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.splitIntoSentences
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
            run(" years ago.")
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
            drawn.text.spanStyles.count { it.start < "Kutsukaa minua.".length }
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

    @Test
    fun aParagraphWithNothingToReadDrawsNothing() {
        val drawn = paragraph(listOf(run("   "))) { null }

        assertEquals("   ", drawn.text.text)
        assertNull(drawn.sentenceAt(0))
    }

    private fun paragraph(
        runs: List<TextRun>,
        translationOf: (Sentence) -> String?
    ): ParagraphText {
        val sentences = splitIntoSentences(runs.plain())

        return buildParagraphText(runs = runs, sentences = sentences, translationOf = translationOf)
    }

    private fun run(text: String, vararg emphasis: Emphasis) =
        TextRun(text = text, emphasis = emphasis.toSet())
}
