package io.lukia.features.reader.infrastructure.pdf

import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.domain.page.TextQuad
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Reading a page of a PDF as the things written on it.
 *
 * Laid out by hand rather than read from a file, like `PageSentencesTest` and
 * for the same reason: every question here - is this a heading, is that the page
 * number, is this one paragraph or two - is a question about size and position,
 * and can be asked without a PDF anywhere near it.
 */
class PdfPageBlocksTest {

    @Test
    fun linesSetCloseTogetherAreOneParagraph() {
        val blocks = pdfPageBlocks(
            line("Call me Ishmael. Some years ago,", top = 100f) +
                line("never mind how long precisely,", top = 112f),
            noPictures,
            page,
        )

        val paragraph = assertIs<TextBlock.Paragraph>(blocks.single())
        assertEquals(
            "Call me Ishmael. Some years ago, never mind how long precisely,",
            paragraph.text.plainText(),
        )
    }

    @Test
    fun aGapWideEnoughIsAParagraphBreak() {
        val blocks = pdfPageBlocks(
            line("The first paragraph ends here.", top = 100f) +
                line("A second one begins here.", top = 140f),
            noPictures,
            page,
        )

        assertEquals(2, blocks.size)
    }

    /**
     * Nothing in a PDF says "heading". What says it is that the type is larger
     * than the type around it, which is why the body text has to be there for
     * the comparison to mean anything.
     */
    @Test
    fun typeSetLargerThanTheBodyIsAHeading() {
        val blocks = pdfPageBlocks(
            line("Loomings", top = 100f, height = 18f, width = 11f) +
                line("Call me Ishmael. Some years ago, never mind how", top = 140f) +
                line("long precisely, having little or no money in my purse,", top = 152f),
            noPictures,
            page,
        )

        val heading = assertIs<TextBlock.Heading>(blocks.first())
        assertEquals("Loomings", heading.text.plainText())
        assertIs<TextBlock.Paragraph>(blocks.last())
    }

    /** A page of one size throughout is a page with no heading on it. */
    @Test
    fun bodyTextIsNotAHeadingHoweverShortItIs() {
        val blocks = pdfPageBlocks(
            line("A short line.", top = 100f) +
                line("Another paragraph of the same ordinary type.", top = 140f),
            noPictures,
            page,
        )

        assertTrue(blocks.all { it is TextBlock.Paragraph })
    }

    @Test
    fun aRunningHeadAtTheTopOfThePageIsNotPartOfTheBook() {
        val blocks = pdfPageBlocks(
            line("MOBY-DICK", top = 20f) +
                line("Call me Ishmael. Some years ago, never mind how", top = 200f) +
                line("long precisely, having little or no money in my purse,", top = 212f),
            noPictures,
            page,
        )

        val paragraph = assertIs<TextBlock.Paragraph>(blocks.single())
        assertTrue(paragraph.text.plainText().startsWith("Call me"))
    }

    @Test
    fun aPageNumberAtTheFootIsNotPartOfTheBookEither() {
        val blocks = pdfPageBlocks(
            line("Call me Ishmael. Some years ago, never mind how", top = 200f) +
                line("long precisely, having little or no money in my purse,", top = 212f) +
                line("42", top = 800f),
            noPictures,
            page,
        )

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is TextBlock.Paragraph)
    }

    /**
     * The margin rule is about page furniture, not about where a page happens
     * to start. A full paragraph near the top edge is text somebody came to
     * read.
     */
    @Test
    fun textNearTheEdgeSurvivesWhenItIsMoreThanOneLine() {
        val blocks = pdfPageBlocks(
            line("This paragraph begins very high up the page,", top = 20f) +
                line("and carries on for a second line.", top = 32f),
            noPictures,
            page,
        )

        assertEquals(1, blocks.size)
        assertIs<TextBlock.Paragraph>(blocks.single())
    }

    @Test
    fun bulletedLinesAreListItemsRatherThanOneParagraph() {
        val blocks = pdfPageBlocks(
            line("• the first point", top = 100f) +
                line("• the second point", top = 112f),
            noPictures,
            page,
        )

        assertEquals(2, blocks.size)

        val first = assertIs<TextBlock.ListItem>(blocks.first())
        assertEquals("the first point", first.text.plainText())
        assertEquals("•", first.marker)
    }

    /**
     * A picture belongs where it was printed. A PDF states no reading order, so
     * where it goes among the paragraphs is settled by where it was drawn - the
     * same thing the paragraphs themselves are ordered by.
     */
    @Test
    fun aPictureIsSetWhereItWasPrintedAmongTheParagraphs() {
        val blocks = pdfPageBlocks(
            line("The paragraph above it.", top = 100f) +
                line("The paragraph below it.", top = 500f),
            listOf(picture(top = 200f)),
            page,
        )

        assertIs<TextBlock.Paragraph>(blocks[0])
        assertIs<TextBlock.Figure>(blocks[1])
        assertIs<TextBlock.Paragraph>(blocks[2])
    }

    /**
     * A page whose only picture is the size of the paper, with words on top of
     * it, is a watermark or a tint block - the sheet rather than a picture on
     * it.
     */
    @Test
    fun aPictureTheSizeOfThePageUnderTheWordsIsNotShown() {
        val blocks = pdfPageBlocks(
            line("Words over a tinted background.", top = 100f),
            listOf(picture(top = 0f, left = 0f, width = 595f, height = 842f)),
            page,
        )

        assertEquals(1, blocks.size)
        assertIs<TextBlock.Paragraph>(blocks.single())
    }

    /** The same picture on a page of its own is a plate, and is what somebody came for. */
    @Test
    fun aFullPagePictureWithNoWordsOnItIsAPlate() {
        val blocks = pdfPageBlocks(
            emptyList(),
            listOf(picture(top = 0f, left = 0f, width = 595f, height = 842f)),
            page,
        )

        assertIs<TextBlock.Figure>(blocks.single())
    }

    private val page = PageSize(width = 595f, height = 842f)

    private val noPictures = emptyList<PageImage>()

    private fun picture(
        top: Float,
        left: Float = 100f,
        width: Float = 200f,
        height: Float = 150f,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ) = PageImage(
        bytes = bytes,
        bounds = TextQuad(
            left = left,
            top = top,
            right = left + width,
            bottom = top + height,
        ),
    )

    private fun line(
        text: String,
        top: Float,
        width: Float = 6f,
        height: Float = 10f,
    ) = text.mapIndexed { index, character ->
        PageGlyph(
            text = character.toString(),
            bounds = TextQuad(
                left = index * width,
                top = top,
                right = index * width + width,
                bottom = top + height,
            ),
        )
    }
}
