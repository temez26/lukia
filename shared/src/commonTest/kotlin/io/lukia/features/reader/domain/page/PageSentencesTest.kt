package io.lukia.features.reader.domain.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rebuilding a page's sentences out of the marks it is made of.
 *
 * The glyphs here are laid out by hand rather than read from a file, which is
 * the point: a PDF stores positions, and every question this code answers -
 * where a word ends, where a line ends, which rectangle a sentence covers - is
 * a question about geometry that can be asked without one.
 */
class PageSentencesTest {

    /** 10pt characters on a 12pt line, which is roughly a book. */
    private val glyphWidth = 6f
    private val glyphHeight = 10f

    @Test
    fun charactersSetSideBySideMakeAWord() {
        val sentences = pageSentences(line("Ishmael.", top = 0f))

        assertEquals(listOf("Ishmael."), sentences.map { it.text })
    }

    /**
     * A PDF stores no spaces between words, only gaps. Where the gap is wide
     * enough the words were separate ones.
     */
    @Test
    fun aWideGapBetweenCharactersIsASpace() {
        val glyphs = word("Call", startX = 0f, top = 0f) +
            word("me", startX = 40f, top = 0f)

        assertEquals("Call me", pageSentences(glyphs).single().text)
    }

    @Test
    fun aNarrowGapIsJustKerning() {
        val glyphs = word("Ca", startX = 0f, top = 0f) +
            word("ll", startX = 13f, top = 0f)

        assertEquals("Call", pageSentences(glyphs).single().text)
    }

    /** Text that already carries its spaces is not given a second set. */
    @Test
    fun spacesThePageAlreadyHasAreNotDoubled() {
        val sentences = pageSentences(line("Call me Ishmael.", top = 0f))

        assertEquals("Call me Ishmael.", sentences.single().text)
    }

    @Test
    fun linesAreReadInOrderAndJoined() {
        val glyphs = line("Call me", top = 0f) + line("Ishmael.", top = 14f)

        assertEquals("Call me Ishmael.", pageSentences(glyphs).single().text)
    }

    /**
     * Glyphs come off a page in whatever order the file drew them, which is not
     * always the order somebody reads them in.
     */
    @Test
    fun glyphsAreSortedIntoReadingOrderFirst() {
        val glyphs = (line("Ishmael.", top = 14f) + line("Call me", top = 0f)).reversed()

        assertEquals("Call me Ishmael.", pageSentences(glyphs).single().text)
    }

    /**
     * A word broken across a line break is one word. Joined with a space it
     * would reach the translator as two things it has to guess were one.
     */
    @Test
    fun aWordBrokenAcrossALineBreakIsPutBackTogether() {
        val glyphs = line("A whal-", top = 0f) + line("ing ship.", top = 14f)

        assertEquals("A whal-ing ship.", pageSentences(glyphs).single().text)
    }

    /** A line ending on a full stop is not a hyphenated word. */
    @Test
    fun anOrdinarySentenceBreakAcrossLinesStillGetsItsSpace() {
        val glyphs = line("She left.", top = 0f) + line("He stayed.", top = 14f)

        assertEquals(
            listOf("She left.", "He stayed."),
            pageSentences(glyphs).map { it.text },
        )
    }

    /** A superscript sits on the same line as the words it is attached to. */
    @Test
    fun aGlyphSetSmallerStaysOnItsLine() {
        val glyphs = line("Ishmael", top = 0f) +
            listOf(glyph("1", x = 42f, top = 0f, width = 3f, height = 5f))

        assertEquals("Ishmael1", pageSentences(glyphs).single().text)
    }

    /**
     * A PDF records no paragraphs, so the only evidence of where one thing ends
     * and the next begins is the space left between them. Without using it, a
     * heading runs into the paragraph under it and the two become one sentence
     * that ends at the paragraph's first full stop.
     */
    @Test
    fun aGapDownThePageEndsWhateverCameBeforeIt() {
        val glyphs = line("Chapter One", top = 0f) +
            line("It was cold. She left.", top = 40f)

        assertEquals(
            listOf("Chapter One", "It was cold.", "She left."),
            pageSentences(glyphs).map { it.text },
        )
    }

    /** Ordinary leading between the lines of one paragraph is not a gap. */
    @Test
    fun theLeadingInsideAParagraphIsNotABreak() {
        val glyphs = line("Call me", top = 0f) +
            line("Ishmael and", top = 14f) +
            line("nothing else.", top = 28f)

        assertEquals(
            listOf("Call me Ishmael and nothing else."),
            pageSentences(glyphs).map { it.text },
        )
    }

    /**
     * The failure that started this: a block with no sentence punctuation in it
     * used to swallow everything below it down to the next full stop on the
     * page, producing one enormous passage nobody could translate.
     */
    @Test
    fun aBlockWithNoPunctuationDoesNotSwallowTheOneBelowIt() {
        val glyphs = line("function greet(name)", top = 0f) +
            line("return name", top = 14f) +
            line("That is how it works.", top = 60f)

        val sentences = pageSentences(glyphs)

        assertEquals(2, sentences.size)
        assertEquals("function greet(name) return name", sentences[0].text)
        assertEquals("That is how it works.", sentences[1].text)
    }

    /** Sentences stay numbered in reading order across the blocks they sit in. */
    @Test
    fun numberingRunsOnFromOneBlockToTheNext() {
        val glyphs = line("One. Two.", top = 0f) +
            line("Three.", top = 50f) +
            line("Four.", top = 100f)

        val sentences = pageSentences(glyphs)

        assertEquals(listOf(0, 1, 2, 3), sentences.map { it.index })
        assertEquals(listOf("One.", "Two.", "Three.", "Four."), sentences.map { it.text })
    }

    /** A word hyphenated across a block break was not one word. */
    @Test
    fun aHyphenAtTheEndOfABlockDoesNotJoinToTheNext() {
        val glyphs = line("A whal-", top = 0f) + line("ing ship.", top = 60f)

        assertEquals(
            listOf("A whal-", "ing ship."),
            pageSentences(glyphs).map { it.text },
        )
    }

    @Test
    fun eachSentenceCoversTheWordsItIsMadeOf() {
        val glyphs = line("Hi.", top = 0f) + word("There.", startX = 40f, top = 0f)

        val first = pageSentences(glyphs).first()
        val quad = first.quads.single()

        assertEquals("Hi.", first.text)
        assertEquals(0f, quad.left)
        assertEquals(3 * glyphWidth, quad.right)
        assertEquals(glyphHeight, quad.height)
    }

    /**
     * A sentence that wraps covers a ribbon of each line it runs across, not the
     * block between its far corners - which would swallow the lines between.
     */
    @Test
    fun aWrappedSentenceIsOneRectanglePerLine() {
        val glyphs = line("Call me", top = 0f) + line("Ishmael.", top = 14f)

        val quads = pageSentences(glyphs).single().quads

        assertEquals(2, quads.size)
        assertEquals(0f, quads[0].top)
        assertEquals(14f, quads[1].top)
    }

    /** An inferred space belongs to no glyph, so it covers no part of the page. */
    @Test
    fun aQuadDoesNotStretchAcrossTheGapBeforeTheNextSentence() {
        val glyphs = line("Hi.", top = 0f) + word("There.", startX = 200f, top = 0f)

        assertEquals(3 * glyphWidth, pageSentences(glyphs).first().quads.single().right)
    }

    @Test
    fun theSentenceAtAPointIsTheOneUnderIt() {
        val glyphs = line("Hi.", top = 0f) + word("There.", startX = 40f, top = 0f)
        val sentences = pageSentences(glyphs)

        assertEquals("Hi.", sentences.sentenceAt(x = 5f, y = 5f)?.text)
        assertEquals("There.", sentences.sentenceAt(x = 45f, y = 5f)?.text)
    }

    @Test
    fun aPointInTheMarginIsOnNoSentence() {
        val sentences = pageSentences(line("Hi.", top = 0f))

        assertNull(sentences.sentenceAt(x = 500f, y = 500f))
        assertNull(sentences.sentenceAt(x = 5f, y = 400f))
    }

    @Test
    fun aPageWithNoTextOnItHasNoSentences() {
        assertTrue(pageSentences(emptyList()).isEmpty())
        assertTrue(pageSentences(listOf(glyph(" ", x = 0f, top = 0f))).isEmpty())
    }

    @Test
    fun sentencesAreNumberedInReadingOrder() {
        val glyphs = line("One. Two.", top = 0f) + line("Three.", top = 14f)

        assertEquals(listOf(0, 1, 2), pageSentences(glyphs).map { it.index })
    }

    /**
     * A word on a page has a place of its own, which is the whole of what lets
     * somebody point at one. It cannot be found from the sentence's text after
     * the fact: a PDF records glyphs and where they landed, and this is the only
     * moment the two are still together.
     */
    @Test
    fun aSentenceKnowsTheWordsItIsMadeOf() {
        val sentence = pageSentences(line("Call me Ishmael.", top = 0f)).single()

        assertEquals(listOf("Call", "me", "Ishmael"), sentence.words.map { it.text })
        assertEquals(listOf(0, 1, 2), sentence.words.map { it.index })
    }

    @Test
    fun aWordCoversTheGlyphsItWasSetFrom() {
        val sentence = pageSentences(line("Call me Ishmael.", top = 0f)).single()

        val call = sentence.words.first()
        val ishmael = sentence.words.last()

        // "Call" is the first four glyphs; "Ishmael" starts at the ninth.
        assertEquals(0f, call.bounds.left)
        assertEquals(glyphWidth * 4, call.bounds.right)
        assertEquals(glyphWidth * 8, ishmael.bounds.left)
    }

    /** Which is what a tap on a page is answered with. */
    @Test
    fun theWordAtAPointIsTheOneCoveringIt() {
        val sentence = pageSentences(line("Call me Ishmael.", top = 0f)).single()

        assertEquals("me", sentence.wordAt(x = glyphWidth * 5.5f, y = glyphHeight / 2)?.text)
        assertEquals("Call", sentence.wordAt(x = glyphWidth * 1.5f, y = glyphHeight / 2)?.text)
    }

    /**
     * The space between two words belongs to neither, which is what makes a
     * gesture set to the word do nothing there rather than take the sentence.
     */
    @Test
    fun thereIsNoWordInTheSpaceBetweenTwoOfThem() {
        val sentence = pageSentences(line("Call me", top = 0f)).single()

        assertNull(sentence.wordAt(x = glyphWidth * 4.5f, y = glyphHeight / 2))
    }

    /** A word broken across a line covers a ribbon on each, exactly as a sentence does. */
    @Test
    fun aWordRunningOverALineBreakCoversBothOfThem() {
        val glyphs = word("exam-", startX = 0f, top = 0f) + word("ple here.", startX = 0f, top = 14f)

        val broken = pageSentences(glyphs).single().words.first()

        assertEquals("exam-ple", broken.text)
        assertEquals(2, broken.quads.size, "a word over two lines was drawn as one box")
    }

    private fun line(text: String, top: Float) = word(text, startX = 0f, top = top)

    private fun word(text: String, startX: Float, top: Float) =
        text.mapIndexed { index, character ->
            glyph(character.toString(), x = startX + index * glyphWidth, top = top)
        }

    private fun glyph(
        text: String,
        x: Float,
        top: Float,
        width: Float = glyphWidth,
        height: Float = glyphHeight,
    ) = PageGlyph(
        text = text,
        bounds = TextQuad(left = x, top = top, right = x + width, bottom = top + height),
    )
}
