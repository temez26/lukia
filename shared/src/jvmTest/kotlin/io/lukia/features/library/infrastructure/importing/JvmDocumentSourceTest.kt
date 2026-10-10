package io.lukia.features.library.infrastructure.importing

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the import reads a document on the desktop: through a handle on the file,
 * and never by loading it.
 */
class JvmDocumentSourceTest {

    @Test
    fun readsAWindowOutOfTheMiddleOfAFile() {
        val document = ByteArray(1000) { it.toByte() }

        openedDocument(document) { source ->
            assertEquals(1000L, source.size)
            assertContentEquals(document.copyOfRange(400, 410), source.read(400, 10))
        }
    }

    @Test
    fun readingPastTheEndAnswersWithWhatIsThere() {
        val document = ByteArray(16) { it.toByte() }

        openedDocument(document) { source ->
            assertContentEquals(document.copyOfRange(10, 16), source.read(10, 100))
            assertContentEquals(ByteArray(0), source.read(16, 8))
            assertContentEquals(ByteArray(0), source.read(-1, 8))
        }
    }

    @Test
    fun rendersTheFirstPageAsPng() {
        val cover = openedDocument(singlePagePdf()) { it.renderFirstPage() }

        assertNotNull(cover)
        assertTrue(cover.isNotEmpty())
        assertEquals(0x89.toByte(), cover[0])
        assertEquals('P'.code.toByte(), cover[1])
        assertEquals('N'.code.toByte(), cover[2])
        assertEquals('G'.code.toByte(), cover[3])
    }

    /** The renderer must not disturb the handle the rest of the import reads through. */
    @Test
    fun theSourceIsStillReadableAfterACoverHasBeenDrawnFromIt() {
        val pdf = singlePagePdf()

        openedDocument(pdf) { source ->
            assertNotNull(source.renderFirstPage())
            assertContentEquals(pdf.copyOfRange(0, 8), source.read(0, 8))
        }
    }

    @Test
    fun returnsNullForCorruptPdf() {
        assertNull(openedDocument("not a pdf".encodeToByteArray()) { it.renderFirstPage() })
    }

    private fun singlePagePdf(): ByteArray {
        val document = PDDocument()

        return document.use {
            it.addPage(PDPage())

            val bytes = java.io.ByteArrayOutputStream()
            it.save(bytes)
            bytes.toByteArray()
        }
    }
}
