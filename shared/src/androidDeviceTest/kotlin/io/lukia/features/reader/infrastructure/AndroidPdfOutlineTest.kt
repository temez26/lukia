package io.lukia.features.reader.infrastructure

import androidx.test.platform.app.InstrumentationRegistry
import io.lukia.features.reader.infrastructure.pdf.MinimalPdf
import io.lukia.features.reader.infrastructure.pdf.PdfOutlineReader
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android reads a document's chapters by parsing the file, because
 * `PdfRenderer` has no outline API at all. The parser itself is common code and
 * is covered by the desktop tests; what only a device can show is this half -
 * the file being read off storage and the two halves meeting.
 *
 * Runs against the same hand-written PDF the desktop tests use, so a
 * disagreement between the two points at the platform rather than at the file.
 */
class AndroidPdfOutlineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val file = File(context.cacheDir, "outline-test.pdf").apply {
        writeBytes(MinimalPdf.withOutline())
    }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun readsTheChaptersOutOfTheFile() {
        openPdfPages(file.absolutePath).use { pages ->
            val outline = assertNotNull(pages.outline, "Android should read an outline")

            assertEquals(
                listOf(MinimalPdf.FIRST_CHAPTER_TITLE, MinimalPdf.SECOND_CHAPTER_TITLE),
                outline.map { it.title }
            )
            assertEquals(
                MinimalPdf.SECOND_CHAPTER_PAGE_INDEX,
                outline.last().pageIndex
            )
        }
    }

    /** The pages still come from the platform renderer, as they always did. */
    @Test
    fun stillOpensThePagesThemselves() {
        openPdfPages(file.absolutePath).use { pages ->
            assertEquals(MinimalPdf.PAGE_COUNT, pages.pageCount)
        }
    }

    @Test
    fun readsBytesFromAnywhereInTheFile() {
        FilePdfByteSource(file).use { source ->
            assertEquals(file.length(), source.size)

            val header = source.read(0, 8)
            assertEquals("%PDF-1.4", header.decodeToString())

            // A read that runs past the end comes back short rather than failing.
            val tail = source.read(source.size - 4, 64)
            assertTrue(tail.size <= 4)
        }
    }

    @Test
    fun aReadOutsideTheFileIsEmptyRatherThanAFailure() {
        FilePdfByteSource(file).use { source ->
            assertTrue(source.read(source.size + 100, 16).isEmpty())
            assertTrue(source.read(-1, 16).isEmpty())
        }
    }

    @Test
    fun aFileThatIsNotAPdfReportsNoOutline() {
        val broken = File(context.cacheDir, "broken.pdf").apply {
            writeText("this is not a pdf at all")
        }

        try {
            FilePdfByteSource(broken).use { source ->
                assertEquals(null, PdfOutlineReader(source).read())
            }
        } finally {
            broken.delete()
        }
    }
}
