package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.pdf.MinimalPdf
import io.lukia.features.reader.domain.page.pageSentences
import io.lukia.features.reader.domain.page.sentenceAt
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading text back out of a real PDF, through the real library.
 *
 * The geometry of turning glyphs into sentences is settled in
 * `PageSentencesTest` against glyphs laid out by hand. What is left to prove is
 * the part only a file can answer: that this platform hands over the characters
 * at all, that their boxes are the right way up, and that they land where the
 * text was actually drawn.
 */
class PdfPageTextTest {

    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-pagetext-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun theWordsOnAPageComeBackInReadingOrder() = runTest {
        val pages = openPdf("Call me Ishmael." to 700f)

        val glyphs = assertNotNull(pages.pageText(0), "no text layer")
        val sentence = pageSentences(glyphs).single()

        assertEquals("Call me Ishmael.", sentence.text)
        pages.close()
    }

    @Test
    fun severalLinesReadAsOneSentenceWhenThatIsWhatTheyAre() = runTest {
        val pages = openPdf("Call me" to 700f, "Ishmael." to 680f)

        val sentences = pageSentences(assertNotNull(pages.pageText(0)))

        assertEquals(listOf("Call me Ishmael."), sentences.map { it.text })
        pages.close()
    }

    /**
     * The one thing a hand-laid-out test cannot check: PDF measures upwards from
     * the bottom of the page and every screen measures downwards, so a quad that
     * came back unconverted would put a translation at the far end of the page
     * from the words it belongs to.
     */
    @Test
    fun quadsAreMeasuredDownwardsFromTheTopOfThePage() = runTest {
        val pages = openPdf("Near the top." to 740f, "Near the bottom." to 60f)

        val sentences = pageSentences(assertNotNull(pages.pageText(0)))
        val top = sentences.first { it.text.contains("top") }
        val bottom = sentences.first { it.text.contains("bottom") }

        assertTrue(top.bounds.top < bottom.bounds.top, "$top came out below $bottom")
        assertTrue(top.bounds.top < 120f, "the top line was not near the top: ${top.bounds}")
        pages.close()
    }

    @Test
    fun aSentenceCanBeFoundAtThePointItWasDrawnAt() = runTest {
        val pages = openPdf("Call me Ishmael." to 700f)

        val sentences = pageSentences(assertNotNull(pages.pageText(0)))
        val bounds = sentences.single().bounds

        assertEquals(
            "Call me Ishmael.",
            sentences.sentenceAt(
                x = bounds.left + bounds.width / 2,
                y = bounds.top + bounds.height / 2,
            )?.text,
        )
        assertNull(sentences.sentenceAt(x = bounds.left, y = bounds.bottom + 100f))
        pages.close()
    }

    /** A page that is a picture has no text layer, and that is not a failure. */
    @Test
    fun aPageWithNothingWrittenOnItHasNoText() = runTest {
        val pages = openPdf()

        assertNull(pages.pageText(0))
        pages.close()
    }

    @Test
    fun askingForAPageThatIsNotThereIsNotACrash() = runTest {
        val pages = openPdf("Words." to 700f)

        assertNull(pages.pageText(9))
        pages.close()
    }

    private fun openPdf(vararg lines: Pair<String, Float>) = openTestPdf(
        File(root, "book-${lines.hashCode()}.pdf").also { file ->
            PDDocument().use { document ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)

                if (lines.isNotEmpty()) {
                    PDPageContentStream(document, page).use { content ->
                        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)

                        lines.forEach { (text, y) ->
                            content.beginText()
                            content.setFont(font, 12f)
                            content.newLineAtOffset(60f, y)
                            content.showText(text)
                            content.endText()
                        }
                    }
                }

                document.save(file)
            }
        }.absolutePath,
    )

    /**
     * The hand-written fixture the device tests read, checked here where it can
     * actually be run.
     *
     * `AndroidPdfPageTextTest` reads the same bytes through pdfbox-android and
     * `PdfKitPageTextTest` through PDFKit, and neither runs on CI. A fixture
     * that quietly stopped drawing anything would leave both of them asserting
     * about an empty page, and nobody would find out until somebody plugged in a
     * phone. This is the one place that fails first.
     */
    @Test
    fun theHandWrittenFixtureHasWordsOnIt() = runTest {
        val file = File(root, "hand-written.pdf").apply {
            writeBytes(MinimalPdf.withText("Near the top." to 740, "Near the bottom." to 60))
        }

        openTestPdf(file.absolutePath).use { pages ->
            val sentences = pageSentences(assertNotNull(pages.pageText(0), "no text layer"))

            // The same two assertions AndroidPdfPageTextTest makes, so that a
            // fixture which stopped drawing fails here first.
            assertEquals(listOf("Near the top.", "Near the bottom."), sentences.map { it.text })
            assertTrue(sentences.first().bounds.top < 120f, "the top line was not near the top")
        }
    }
}
