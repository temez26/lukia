package io.lukia.features.reader.infrastructure

import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.image.BufferedImage
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdfPageImagesTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-page-images-test-${Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun readsAPicturePlacedOnAPage() = runTest {
        openPdfPages(bookWithAMarkOnEveryPage(pages = 1).absolutePath).use { pages ->
            val images = pages.pageImages(0)

            assertEquals(1, images.size)
            assertTrue(images.single().bytes.isNotEmpty())
        }
    }

    /**
     * The saving that makes converting an illustrated book affordable: a
     * publisher's mark is drawn on every page of a book and stored once in the
     * file, and decoding it per page was most of what reading the book cost.
     *
     * Asserted as the same array rather than as equal bytes, because equal bytes
     * would also be what decoding it three times produces - and it is the
     * decoding that this is about.
     */
    @Test
    fun aMarkDrawnOnEveryPageIsReadOnce() = runTest {
        openPdfPages(bookWithAMarkOnEveryPage(pages = 3).absolutePath).use { pages ->
            val first = pages.pageImages(0).single().bytes

            repeat(2) { page ->
                assertSame(first, pages.pageImages(page + 1).single().bytes)
            }
        }
    }

    /** Two different pictures stay two pictures, however alike they are stored. */
    @Test
    fun differentPicturesAreReadSeparately() = runTest {
        val file = pdfFile { document ->
            repeat(2) { index ->
                val page = PDPage()
                document.addPage(page)

                PDPageContentStream(document, page).use { content ->
                    content.drawImage(mark(document, shade = index), 40f, 600f, 120f, 120f)
                }
            }
        }

        openPdfPages(file.absolutePath).use { pages ->
            val first = pages.pageImages(0).single().bytes
            val second = pages.pageImages(1).single().bytes

            assertTrue(!first.contentEquals(second), "two marks, two pictures")
        }
    }

    private fun bookWithAMarkOnEveryPage(pages: Int): File = pdfFile { document ->
        val mark = mark(document, shade = 0)

        repeat(pages) {
            val page = PDPage()
            document.addPage(page)

            PDPageContentStream(document, page).use { content ->
                content.drawImage(mark, 40f, 600f, 120f, 120f)
            }
        }
    }

    private fun mark(document: PDDocument, shade: Int): PDImageXObject {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)

        repeat(64) { y ->
            repeat(64) { x -> image.setRGB(x, y, ((x + y + shade * 40) and 0xFF) * 0x010101) }
        }

        return LosslessFactory.createFromImage(document, image)
    }

    private fun pdfFile(fill: (PDDocument) -> Unit): File {
        val file = File(directory, "book-${Random.nextInt(until = Int.MAX_VALUE)}.pdf")

        PDDocument().use { document ->
            fill(document)
            document.save(file)
        }

        return file
    }
}
