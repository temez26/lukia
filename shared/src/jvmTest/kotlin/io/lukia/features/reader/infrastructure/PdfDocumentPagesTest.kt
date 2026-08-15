package io.lukia.features.reader.infrastructure

import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfDocumentPagesTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-pages-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}"
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun readsEveryPage() {
        openPdfPages(pdfFile(pageCount = 3).absolutePath).use { pages ->
            assertEquals(3, pages.pageCount)
            assertEquals(3, pages.pageSizes.size)
        }
    }

    @Test
    fun reportsThePageShape() {
        openPdfPages(pdfFile(pageCount = 1).absolutePath).use { pages ->
            val page = pages.pageSizes.first()

            assertEquals(PDRectangle.A4.width, page.width)
            assertEquals(PDRectangle.A4.height, page.height)
            assertTrue(page.aspectRatio < 1f, "A4 portrait is taller than it is wide")
        }
    }

    /**
     * A quarter-turned page reports its box unrotated but renders rotated, so
     * taking the box at face value would reserve a portrait slot for a page that
     * arrives landscape.
     */
    @Test
    fun aRotatedPageReportsTheShapeItRendersAt() {
        val file = pdfFile(pageCount = 1) { document ->
            document.getPage(0).rotation = 90
        }

        openPdfPages(file.absolutePath).use { pages ->
            val page = pages.pageSizes.first()

            assertEquals(PDRectangle.A4.height, page.width)
            assertEquals(PDRectangle.A4.width, page.height)
            assertTrue(page.aspectRatio > 1f, "a quarter-turned A4 page renders landscape")
        }
    }

    @Test
    fun rendersAPageAtTheRequestedWidth() = runTest {
        openPdfPages(pdfFile(pageCount = 1).absolutePath).use { pages ->
            val rendered = pages.renderPage(index = 0, targetWidthPx = 600)

            assertNotNull(rendered)
            assertEquals(600, rendered.width)
            // The page's own proportions are kept, which is what lets the reader
            // reserve a page's space before drawing it.
            assertTrue(rendered.height > rendered.width)
        }
    }

    @Test
    fun aPageThatIsNotThereRendersAsNothing() = runTest {
        openPdfPages(pdfFile(pageCount = 1).absolutePath).use { pages ->
            assertNull(pages.renderPage(index = 7, targetWidthPx = 600))
        }
    }

    @Test
    fun aFileThatIsNotAPdfDoesNotOpen() {
        val file = File(directory, "not-a.pdf").apply { writeText("not a pdf") }

        assertFails { openPdfPages(file.absolutePath) }
    }

    private fun pdfFile(
        pageCount: Int,
        configure: (PDDocument) -> Unit = {}
    ): File {
        val file = File(directory, "book-$pageCount-${kotlin.random.Random.nextInt()}.pdf")

        PDDocument().use { document ->
            repeat(pageCount) { document.addPage(PDPage(PDRectangle.A4)) }
            configure(document)
            document.save(file)
        }

        return file
    }
}
