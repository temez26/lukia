package io.lukia.features.import.infrastructure.cover

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfCoverRendererTest {

    @Test
    fun rendersFirstPageAsPng() {
        val pdfBytes = buildPdf()

        val cover = renderPdfCover(pdfBytes)

        assertNotNull(cover)
        assertTrue(cover.isNotEmpty())
        assertEquals(0x89.toByte(), cover[0])
        assertEquals('P'.code.toByte(), cover[1])
        assertEquals('N'.code.toByte(), cover[2])
        assertEquals('G'.code.toByte(), cover[3])
    }

    @Test
    fun returnsNullForCorruptPdf() {
        assertNull(renderPdfCover("not a pdf".encodeToByteArray()))
    }

    private fun buildPdf(): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(output)
        }
        return output.toByteArray()
    }
}
