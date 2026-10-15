package io.lukia.core.infrastructure.importing

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.imageio.ImageIO

/**
 * A document being imported, read straight off the file the reader picked.
 *
 * See [DocumentSource]: nothing here loads the file, and the reason is the file
 * itself - a scanned PDF on a desktop shelf is routinely over a hundred
 * megabytes, and what the import wants out of it is a picture of its first page.
 */
class JvmDocumentSource(private val file: File) : DocumentSource {

    private val handle = RandomAccessFile(file, "r")

    override val size: Long = handle.length()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= size || length <= 0) return ByteArray(0)

        val count = minOf(length.toLong(), size - offset).toInt()
        val buffer = ByteArray(count)

        // No bytes rather than a failure, for the reason the reader's own source
        // answers that way: everything reading through here is looking for
        // something optional - a cover, a title - and a window that could not be
        // read is a book that imports without one.
        return try {
            handle.seek(offset)
            handle.readFully(buffer)
            buffer
        } catch (ignored: IOException) {
            ByteArray(0)
        }
    }

    /**
     * Only the one failure this promises to absorb.
     *
     * PDFBox raises [IOException] for a file it cannot make sense of - malformed,
     * truncated, or not a PDF - and that is what "this book has no cover" means.
     * Anything else is a fault in this code or the renderer and belongs to
     * `extractCover`, which records what it absorbed against the import before
     * returning null. A catch-all here would return the same null and write nothing.
     */
    override fun renderFirstPage(): ByteArray? {
        return try {
            // The file rather than its bytes. PDFBox reads through its own
            // buffered handle on it, so what a page costs to draw is the page
            // rather than the book.
            Loader.loadPDF(file).use { document ->
                if (document.numberOfPages == 0) return null

                val renderer = PDFRenderer(document).apply {
                    // A scanned page holds a photograph of a page: five thousand
                    // pixels across, where what is being drawn is six hundred.
                    // Left off, PDFBox decodes every one of those before throwing
                    // most of them away - a hundred and fifteen megabytes to make
                    // a picture two megapixels wide. On, it reads every nth pixel
                    // and the cover is indistinguishable.
                    isSubsamplingAllowed = true
                }

                val image = renderer.renderImageWithDPI(0, COVER_DPI)

                ByteArrayOutputStream().use { output ->
                    ImageIO.write(image, "png", output)
                    output.toByteArray()
                }
            }
        } catch (ignored: IOException) {
            null
        }
    }

    override fun close() {
        runCatching { handle.close() }
    }
}

/** Screen resolution: the cover is shrunk to 800 pixels after this in any case. */
private const val COVER_DPI = 96f
