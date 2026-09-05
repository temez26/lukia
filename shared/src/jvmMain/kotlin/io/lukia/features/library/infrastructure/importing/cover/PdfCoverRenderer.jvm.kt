package io.lukia.features.library.infrastructure.importing.cover

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Only the one failure this promises to absorb.
 *
 * PDFBox raises [IOException] for the file it cannot make sense of - malformed,
 * truncated, or not a PDF at all - and that is what "this book has no cover"
 * means here. Anything else is a fault in this code or in the renderer, and it
 * belongs to `extractCover`, which is already the boundary: it records what it
 * absorbed against the import before returning null. A catch-all here would
 * return the same null and write nothing down.
 */
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
    } catch (ignored: IOException) {
        null
    }
}
