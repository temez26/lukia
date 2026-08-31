package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.plainText
import io.lukia.features.reader.infrastructure.pdf.readPdfAsText
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSAttributedString
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.drawInRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A PDF read back as text on iOS, through PDFKit.
 *
 * These are written against whole sentences rather than glyph positions because
 * the two things that were wrong were only visible as sentences. PDFKit numbers
 * the characters of a page in one space and writes the page's text in another -
 * the text has a line break at the end of every line and the numbering does not
 * - so reading them as the same number handed each line the boxes of the line
 * above, one letter further out on every line down the page. And PDFKit reports
 * the ink a character actually marks rather than the height of its type, which
 * made every paragraph stand taller than the middle of its own page and so read
 * as a heading.
 *
 * Both come out as text that is technically present and impossible to read,
 * which no assertion about a rectangle would have caught.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class PdfKitPageTextTest {

    @Test
    fun aParagraphReadsBackAsTheSentencesThatWereWrittenInIt() = runTest {
        val blocks = blocksOf(page { draw(OPENING, size = BODY_SIZE, top = 180.0) })

        assertEquals(OPENING, blocks.filterIsInstance<TextBlock.Paragraph>().single().words())
    }

    /**
     * The line break is what the two index spaces disagree about, so a
     * paragraph only goes wrong from its second line onwards - and further
     * wrong with every line after that.
     */
    @Test
    fun everyLineOfALongParagraphKeepsItsOwnLetters() = runTest {
        val blocks = blocksOf(
            page { draw(OPENING + " " + CONTINUED, size = BODY_SIZE, top = 180.0, height = 120.0) },
        )

        val paragraph = blocks.filterIsInstance<TextBlock.Paragraph>().single()

        assertEquals("$OPENING $CONTINUED", paragraph.words())
    }

    @Test
    fun prosePrintedAtOneSizeIsProseRatherThanHeadings() = runTest {
        val blocks = blocksOf(
            page {
                draw(OPENING, size = BODY_SIZE, top = 180.0)
                draw(CONTINUED, size = BODY_SIZE, top = 300.0)
            },
        )

        assertTrue(
            blocks.none { it is TextBlock.Heading },
            "body text came back as headings: ${blocks.map { it::class.simpleName }}",
        )
    }

    /**
     * The case the ink heights really cost. A long paragraph is safe whatever
     * its measured height, because nothing that runs to four lines and a
     * hundred and forty characters is taken for a title - so what the ink broke
     * was the short paragraph, which is most of the dialogue in a novel and the
     * last paragraph of every section.
     */
    @Test
    fun aShortParagraphIsStillAParagraph() = runTest {
        val blocks = blocksOf(
            page {
                draw(OPENING, size = BODY_SIZE, top = 180.0)
                draw(ASIDE, size = BODY_SIZE, top = 300.0, height = 20.0)
                draw(CONTINUED, size = BODY_SIZE, top = 380.0)
            },
        )

        val aside = blocks.filterIsInstance<TextBlock.Paragraph>().map { it.words() }

        assertTrue(
            ASIDE in aside,
            "a short line of prose came back as ${blocks.map { it::class.simpleName }}",
        )
    }

    @Test
    fun linesSetTogetherAreOneParagraphAndOnesSetApartAreTwo() = runTest {
        val blocks = blocksOf(
            page {
                draw(OPENING, size = BODY_SIZE, top = 180.0)
                draw(CONTINUED, size = BODY_SIZE, top = 300.0)
            },
        )

        val paragraphs = blocks.filterIsInstance<TextBlock.Paragraph>()

        assertEquals(2, paragraphs.size)
        assertEquals(OPENING, paragraphs.first().words())
        assertEquals(CONTINUED, paragraphs.last().words())
    }

    @Test
    fun aTitleSetLargerThanItsPageIsAHeading() = runTest {
        val blocks = blocksOf(
            page {
                draw("Loomings", size = 20.0, top = 120.0, height = 30.0)
                draw(OPENING, size = BODY_SIZE, top = 180.0)
            },
        )

        val heading = blocks.filterIsInstance<TextBlock.Heading>().single()

        assertEquals("Loomings", heading.text.plainText())
        assertEquals(1, heading.level)
    }

    /**
     * The height every rule downstream is measured against. Ink would give an
     * `l` half as much again as the `e` beside it, and a line of prose a third
     * more than the page it sits on.
     */
    @Test
    fun everyCharacterOfALineIsGivenTheHeightOfItsType() = runTest {
        val path = write(page { draw(OPENING, size = BODY_SIZE, top = 180.0) })
        val glyphs = openPdfPages(path).use { it.pageText(0) }

        NSFileManager.defaultManager.removeItemAtPath(path, null)
        assertNotNull(glyphs)

        val firstLine = glyphs.filter { it.bounds.bottom == glyphs.first().bounds.bottom }

        assertTrue(firstLine.size > 1, "a line of one character proves nothing")
        assertEquals(1, firstLine.map { it.bounds.height }.distinct().size)
    }

    private suspend fun blocksOf(document: NSData): List<TextBlock> {
        val path = write(document)
        val content = openPdfPages(path).use { readPdfAsText(it) }

        NSFileManager.defaultManager.removeItemAtPath(path, null)

        return assertNotNull(content).sections.flatMap { it.blocks }
    }

    private fun write(document: NSData): String {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString() + ".pdf"

        document.writeToFile(path, atomically = true)

        return path
    }

    private fun TextBlock.Paragraph.words(): String = text.plainText()

    private fun page(draw: () -> Unit): NSData {
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT))

        return renderer.PDFDataWithActions { context ->
            context?.beginPage()
            draw()
        }
    }

    /** Set in a column narrow enough that everything here wraps over several lines. */
    private fun draw(text: String, size: Double, top: Double, height: Double = 60.0) {
        val attributes = mapOf<Any?, Any?>(NSFontAttributeName to UIFont.systemFontOfSize(size))

        NSAttributedString.create(text, attributes)
            .drawInRect(CGRectMake(MARGIN, top, COLUMN_WIDTH, height))
    }

    private companion object {
        const val PAGE_WIDTH = 612.0
        const val PAGE_HEIGHT = 792.0
        const val MARGIN = 72.0
        const val COLUMN_WIDTH = 400.0
        const val BODY_SIZE = 12.0

        const val OPENING = "Call me Ishmael. Some years ago, never mind how long precisely, " +
            "having little or no money in my purse, I thought I would sail about a little."

        /** Short enough and low enough to be mistaken for a title. */
        const val ASIDE = "He rammed his hat down and left."

        const val CONTINUED = "It is a way I have of driving off the spleen, and regulating " +
            "the circulation. Whenever I find myself growing grim about the mouth."
    }
}
