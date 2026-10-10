package io.lukia.features.reader.infrastructure

import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfDocumentPagesTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-pages-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun readsEveryPage() {
        openTestPdf(pdfFile(pageCount = 3).absolutePath).use { pages ->
            assertEquals(3, pages.pageCount)
            assertEquals(3, pages.pageSizes.size)
        }
    }

    @Test
    fun reportsThePageShape() {
        openTestPdf(pdfFile(pageCount = 1).absolutePath).use { pages ->
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

        openTestPdf(file.absolutePath).use { pages ->
            val page = pages.pageSizes.first()

            assertEquals(PDRectangle.A4.height, page.width)
            assertEquals(PDRectangle.A4.width, page.height)
            assertTrue(page.aspectRatio > 1f, "a quarter-turned A4 page renders landscape")
        }
    }

    @Test
    fun rendersAPageAtTheRequestedWidth() = runTest {
        openTestPdf(pdfFile(pageCount = 1).absolutePath).use { pages ->
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
        openTestPdf(pdfFile(pageCount = 1).absolutePath).use { pages ->
            assertNull(pages.renderPage(index = 7, targetWidthPx = 600))
        }
    }

    @Test
    fun readsTheDocumentsOwnChapters() {
        val file = pdfFile(pageCount = 6) { document ->
            document.outline(
                "Introduction" to 0,
                "The Middle" to 2,
                "The End" to 5,
            )
        }

        openTestPdf(file.absolutePath).use { pages ->
            assertEquals(
                listOf("Introduction", "The Middle", "The End"),
                pages.outline?.map { it.title },
            )
            assertEquals(listOf(0, 2, 5), pages.outline?.map { it.pageIndex })
        }
    }

    /** Nesting becomes a level, so the panel can indent without a tree. */
    @Test
    fun nestedSectionsCarryTheirDepth() {
        val file = pdfFile(pageCount = 4) { document ->
            val outline = PDDocumentOutline()
            document.documentCatalog.documentOutline = outline

            val chapter = document.outlineItem("Chapter One", pageIndex = 0)
            outline.addLast(chapter)
            chapter.addLast(document.outlineItem("A Section", pageIndex = 1))
            chapter.addLast(document.outlineItem("Another Section", pageIndex = 2))
        }

        openTestPdf(file.absolutePath).use { pages ->
            assertEquals(
                listOf(0, 1, 1),
                pages.outline?.map { it.level },
            )
            assertEquals(
                listOf("Chapter One", "A Section", "Another Section"),
                pages.outline?.map { it.title },
            )
        }
    }

    /**
     * The chapter list is read as the book is opened, not the first time
     * somebody asks for it.
     *
     * Asserted by asking after the file has been given back, the one way from
     * outside to tell the two apart. Everything else that reads this document is
     * serialised against everything that draws from it, and a plain `val` cannot
     * join that - so the list finishing before the document is shared is what
     * keeps a chapter list off a page being drawn, and what lets an opening the
     * reader has abandoned ask for one without reaching into a closed document.
     */
    @Test
    fun theChaptersAreReadAsTheBookOpensRatherThanWhenAskedFor() {
        val file = pdfFile(pageCount = 6) { document ->
            document.outline("Introduction" to 0, "The End" to 5)
        }

        val pages = openTestPdf(file.absolutePath)
        pages.close()

        assertEquals(listOf("Introduction", "The End"), pages.outline?.map { it.title })
    }

    /** Empty, not null: the document genuinely has no table of contents. */
    @Test
    fun aDocumentWithoutAnOutlineHasNoChapters() {
        openTestPdf(pdfFile(pageCount = 2).absolutePath).use { pages ->
            assertEquals(emptyList(), pages.outline)
        }
    }

    /** Null rather than a throw, which is how every other format says the same. */
    @Test
    fun aFileThatIsNotAPdfDoesNotOpen() {
        val file = File(directory, "not-a.pdf").apply { writeText("not a pdf") }

        assertNull(openPdfPages(file.absolutePath))
    }

    /** Adds a flat outline, one entry per title, pointing at the given pages. */
    private fun PDDocument.outline(vararg entries: Pair<String, Int>) {
        val outline = PDDocumentOutline()
        documentCatalog.documentOutline = outline

        entries.forEach { (title, pageIndex) ->
            outline.addLast(outlineItem(title, pageIndex))
        }
    }

    private fun PDDocument.outlineItem(title: String, pageIndex: Int): PDOutlineItem =
        PDOutlineItem().apply {
            this.title = title
            destination = PDPageFitWidthDestination().apply { page = getPage(pageIndex) }
        }

    private fun pdfFile(
        pageCount: Int,
        configure: (PDDocument) -> Unit = {},
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
