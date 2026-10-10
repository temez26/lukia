package io.lukia.features.library.infrastructure.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A document being imported, read through the descriptor its provider hands out.
 *
 * See [DocumentSource]. This is the platform the whole arrangement is for: an app
 * on Android gets a heap of a couple of hundred megabytes whatever the phone has,
 * and the books people import are scanned PDFs of about that size. Loading one to
 * find out what it is called is not slow here, it is fatal.
 *
 * A descriptor rather than the stream a content URI more usually gives, because
 * everything the import wants sits at a known offset in the file - and because
 * [PdfRenderer] takes exactly this and nothing else. A provider that will not open
 * one is a document that imports without a cover, which is the answer an
 * unreadable file has always given here.
 */
internal class AndroidDocumentSource private constructor(
    private val context: Context,
    private val uri: Uri,
    private val descriptor: ParcelFileDescriptor,
) : DocumentSource {

    private val channel = FileInputStream(descriptor.fileDescriptor).channel

    // A provider need not know how large its document is, and answers -1 when it
    // does not. The channel does, for anything that has a descriptor at all.
    override val size: Long =
        descriptor.statSize.takeIf { it >= 0 } ?: runCatching { channel.size() }.getOrDefault(0)

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= size || length <= 0) return ByteArray(0)

        val count = minOf(length.toLong(), size - offset).toInt()
        val buffer = ByteBuffer.allocate(count)

        return try {
            // Positioned reads rather than seeks, so nothing here depends on
            // where the descriptor's own offset was left. A short read is the end
            // of the file arriving early, which is a truncated book rather than a
            // failure - see the contract on [io.lukia.core.infrastructure.ByteSource].
            var at = offset
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, at)
                if (read <= 0) break
                at += read
            }

            buffer.array().let { if (buffer.position() == count) it else it.copyOf(buffer.position()) }
        } catch (ignored: IOException) {
            ByteArray(0)
        }
    }

    /**
     * Only the two failures this promises to absorb.
     *
     * [PdfRenderer] raises [IOException] for a file it cannot make sense of and
     * [SecurityException] for one it is not allowed to open - a password-protected
     * book - and between them that is what "this book has no cover" means here.
     * Anything else is a fault in this code, and it belongs to `extractCover`, which
     * is already the boundary: it records what it absorbed against the import before
     * returning null. A catch-all here would return the same null and write nothing
     * down, which is why the same file failed silently on Android and desktop while
     * iOS reported it.
     *
     * Opened a second time rather than handed the descriptor above, because
     * [PdfRenderer] takes ownership of what it is given and closes it - and the
     * metadata is read through this source after the cover is drawn from it. A
     * second descriptor on the same file is cheap; a source whose bytes went away
     * halfway through reading it is a book imported with a cover and no title.
     */
    override fun renderFirstPage(): ByteArray? {
        val ownDescriptor = runCatching {
            context.contentResolver.openFileDescriptor(uri, READ_ONLY)
        }.getOrNull() ?: return null

        return try {
            // Handed a descriptor rather than bytes. This used to load the whole
            // document into a ByteArray and write that back out to a temporary
            // file, so a hundred-megabyte book cost its own size in heap and
            // again on disk before a single pixel was drawn.
            PdfRenderer(ownDescriptor).use { renderer ->
                renderer.takeIf { it.pageCount > 0 }?.firstPageAsPng()
            }
        } catch (ignored: IOException) {
            null
        } catch (ignored: SecurityException) {
            null
        } finally {
            // [PdfRenderer] closes what it was given, but only once it has been
            // constructed - and it is the constructor that rejects a file it
            // cannot read. Closing again is a no-op; not closing at all leaks a
            // descriptor for every book in the folder that would not open.
            runCatching { ownDescriptor.close() }
        }
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { descriptor.close() }
    }

    companion object {

        /**
         * The document at [uri], or null where its provider will not open a
         * descriptor onto it - a stream-only provider, or a document that has
         * gone away since it was picked.
         */
        fun open(context: Context, uri: Uri): AndroidDocumentSource? {
            val descriptor = runCatching {
                context.contentResolver.openFileDescriptor(uri, READ_ONLY)
            }.getOrNull() ?: return null

            return runCatching { AndroidDocumentSource(context, uri, descriptor) }
                .getOrElse {
                    descriptor.close()
                    null
                }
        }

        private const val READ_ONLY = "r"
    }
}

/** The first page drawn at its own size, as a PNG. */
private fun PdfRenderer.firstPageAsPng(): ByteArray = openPage(0).use { page ->
    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

    // PdfRenderer draws the page's ink and nothing else, so whatever it does not
    // cover keeps the bitmap's own transparent background - which comes out black
    // the moment the cover is stored in a format without an alpha channel. The
    // paper has to be painted first.
    bitmap.eraseColor(Color.WHITE)

    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

    try {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
            output.toByteArray()
        }
    } finally {
        // A page of a scanned book is several megabytes of ARGB, and what is kept
        // from it is a PNG on its way to being shrunk to eight hundred pixels.
        // Left for the collector to notice, that is the allocation the next book
        // in the folder finds no room beside.
        bitmap.recycle()
    }
}

/** Ignored for PNG, which is lossless, but the parameter is not optional. */
private const val PNG_QUALITY = 100
