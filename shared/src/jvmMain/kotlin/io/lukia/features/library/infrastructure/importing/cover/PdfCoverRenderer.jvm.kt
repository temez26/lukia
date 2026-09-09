package io.lukia.features.library.infrastructure.importing.cover

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Only the one failure this promises to absorb.
 *
 * PDFBox raises [IOException] for a file it cannot make sense of - malformed,
 * truncated, or not a PDF - and that is what "this book has no cover" means.
 * Anything else is a fault in this code or the renderer and belongs to
 * `extractCover`, which records what it absorbed against the import before
 * returning null. A catch-all here would return the same null and write nothing.
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
