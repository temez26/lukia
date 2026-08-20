package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cutting a block in two, for the blocks a book ships that are taller than any
 * screen.
 */
class BlockSlicingTest {

    @Test
    fun aParagraphIsCutWhereItIsAskedToBe() {
        val (head, tail) = paragraph("one two three").cutAt(7)!!

        assertEquals("one two", (head as TextBlock.Paragraph).text.plainText())
        assertEquals(" three", (tail as TextBlock.Paragraph).text.plainText())
    }

    /** A cut through the middle of an italicised phrase leaves both halves italic. */
    @Test
    fun bothHalvesKeepTheMarksTheyWereWrittenWith() {
        val block = TextBlock.Paragraph(
            listOf(
                TextRun("plain "),
                TextRun("emphasised words", setOf(Emphasis.Italic)),
            ),
        )

        val (head, tail) = block.cutAt(16)!!

        assertEquals("plain emphasised", (head as TextBlock.Paragraph).text.plainText())
        assertEquals(setOf(Emphasis.Italic), head.text.last().emphasis)
        assertEquals(setOf(Emphasis.Italic), (tail as TextBlock.Paragraph).text.single().emphasis)
    }

    /** The bullet belongs to the item; drawing it again reads as a second item. */
    @Test
    fun theContinuationOfAListItemShowsNoMarker() {
        val item = TextBlock.ListItem(listOf(TextRun("a long item")), marker = "1.")

        val (head, tail) = item.cutAt(6)!!

        assertEquals("1.", (head as TextBlock.ListItem).marker)
        assertEquals("", (tail as TextBlock.ListItem).marker)
    }

    /**
     * Half a line of code on each of two pages is neither of them readable, and
     * the lines are the one thing about it that must not move.
     */
    @Test
    fun preformattedTextIsCutAtALineBreak() {
        val (head, tail) = TextBlock.Code("first\nsecond\nthird").cutAt(9)!!

        assertEquals("first", (head as TextBlock.Code).text)
        assertEquals("second\nthird", (tail as TextBlock.Code).text)
    }

    @Test
    fun thereIsNothingInsideAFigureOrASeparatorToCut() {
        assertNull(TextBlock.Separator.cutAt(1))
        assertNull(TextBlock.Figure(bytes = ByteArray(4), description = null).cutAt(1))
    }

    @Test
    fun aCutThatWouldLeaveNothingBehindIsNoCut() {
        assertNull(paragraph("short").cutAt(0))
        assertNull(paragraph("short").cutAt(5))
        assertNull(TextBlock.Code("one line only").cutAt(4))
    }

    /**
     * A sentence that is half on each of two pages is counted to both, which is
     * the honest answer when half of it really is on each.
     */
    @Test
    fun aSliceKnowsHowManySentencesRanOntoThePagesBeforeIt() {
        val text = "One sentence. Two sentence. Three sentence."

        assertEquals(0, sentencesBefore(text, 0))
        assertEquals(1, sentencesBefore(text, 13))
        assertEquals(1, sentencesBefore(text, 20))
        assertEquals(2, sentencesBefore(text, 27))
    }
}
