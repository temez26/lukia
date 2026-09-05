package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.page.PageContent
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.plainText
import io.lukia.features.reader.infrastructure.pdf.readPdfAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.image.BufferedImage
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The conversion divided between real handles on a real file, on real threads.
 *
 * Its counterpart in `commonTest` covers the same ground against a fake, and
 * runs the coroutines on a test dispatcher that interleaves them one at a time.
 * That is the right place to pin down what the division has to produce; this is
 * the place to find out whether PDFBox minds four of it at once, which only a
 * real document on a real thread pool can answer.
 */
class PdfAsTextInParallelTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-parallel-test-${Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun theBookIsTheSameHoweverManyHandlesReadIt() = runBlocking {
        val file = book(pages = 40)

        val alone = withContext(Dispatchers.Default) {
            openTestPdf(file.absolutePath).use { readPdfAsText(it) }
        }

        val divided = withContext(Dispatchers.Default) {
            val readers = List(3) { openPdfPageContent(file.absolutePath) }

            try {
                openTestPdf(file.absolutePath).use { readPdfAsText(it, readers) }
            } finally {
                readers.forEach { it.close() }
            }
        }

        assertSameBook(requireNotNull(alone), requireNotNull(divided))
    }

    /** The mark on every page is still found to be the letterhead. */
    @Test
    fun theLetterheadIsStillRecognisedAcrossHandles() = runBlocking {
        val file = book(pages = 40)

        val content = withContext(Dispatchers.Default) {
            val readers = List(3) { openPdfPageContent(file.absolutePath) }

            try {
                openTestPdf(file.absolutePath).use { readPdfAsText(it, readers) }
            } finally {
                readers.forEach { it.close() }
            }
        }

        assertTrue(
            requireNotNull(content).sections.none { section ->
                section.blocks.any { it is TextBlock.Figure }
            },
            "the mark on every page was re-set into the book",
        )
    }

    /** A handle opened only to read pages reads the same pages as the document. */
    @Test
    fun aReadingHandleReadsWhatTheDocumentReads() = runBlocking {
        val file = book(pages = 3)

        val throughDocument = openTestPdf(file.absolutePath).use { it.pageText(1) }
        val throughContent: List<*>? =
            openPdfPageContent(file.absolutePath).use { (it as PageContent).pageText(1) }

        assertEquals(throughDocument, throughContent)
    }

    private fun assertSameBook(alone: ReflowableContent, divided: ReflowableContent) {
        assertEquals(alone.sections.size, divided.sections.size, "a different number of pages")

        alone.sections.zip(divided.sections).forEachIndexed { page, (expected, actual) ->
            assertEquals(
                expected.blocks.map { it.readable() },
                actual.blocks.map { it.readable() },
                "page ${page + 1} came back different",
            )
        }
    }

    /** [TextBlock.Figure] is its own identity, so a picture is compared by its bytes. */
    private fun TextBlock.readable(): String = when (this) {
        is TextBlock.Figure -> "figure:${bytes.toList()}"
        is TextBlock.Heading -> "heading:${text.plainText()}"
        is TextBlock.Paragraph -> "paragraph:${text.plainText()}"
        is TextBlock.ListItem -> "item:$marker:${text.plainText()}"
        is TextBlock.Quote -> "quote:${text.plainText()}"
        is TextBlock.Code -> "code:$text"
        TextBlock.Separator -> "rule"
    }

    private fun book(pages: Int): File {
        val file = File(directory, "book.pdf")

        PDDocument().use { document ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val letterhead = mark(document)

            repeat(pages) { index ->
                val page = PDPage()
                document.addPage(page)

                PDPageContentStream(document, page).use { content ->
                    content.drawImage(letterhead, 60f, 700f, 120f, 40f)
                    content.beginText()
                    content.setFont(font, 11f)
                    content.setLeading(14f)
                    content.newLineAtOffset(60f, 640f)

                    // Every third page is left mid-sentence, so the join across
                    // the page break has to happen between two readers' runs as
                    // well as inside them.
                    val lines = if (index % 3 == 0) {
                        listOf("Page ${index + 1} opens with a sentence that runs on")
                    } else {
                        listOf("Page ${index + 1}. A line of ordinary prose.", "And another one.")
                    }

                    lines.forEach {
                        content.showText(it)
                        content.newLine()
                    }

                    content.endText()
                }
            }

            document.save(file)
        }

        return file
    }

    private fun mark(document: PDDocument): PDImageXObject {
        val image = BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB)

        repeat(48) { y -> repeat(48) { x -> image.setRGB(x, y, ((x + y) and 0xFF) * 0x010101) } }

        return LosslessFactory.createFromImage(document, image)
    }
}
