package io.lukia.features.import.infrastructure.cover

import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.apache.pdfbox.rendering.PDFRenderer

internal actual fun renderPdfCover(pdfBytes: ByteArray): ByteArray? {
    return try {
        Loader.loadPDF(pdfBytes).use { document ->
            if (document.numberOfPages == 0) return null

            val image = PDFRenderer(document).renderImageWithDPI(0, 96f)

            ByteArrayOutputStream().use { output ->
                ImageIO.write(image, "png", output)
                output.toByteArray()
            }
        }
    } catch (e: Exception) {
        null
    }
}
