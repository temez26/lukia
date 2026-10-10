package io.lukia.features.reader.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGPDFContentStreamRef
import platform.CoreGraphics.CGPDFDataFormat
import platform.CoreGraphics.CGPDFDictionaryRef
import platform.CoreGraphics.CGPDFStreamCopyData
import platform.CoreGraphics.CGPDFStreamRef

/**
 * Every picture a book has already been asked for, so that each one is decoded
 * once however many pages draw it.
 *
 * The same saving the other two platforms make - see `PdfFigureBytes` on the
 * desktop for what it is worth. What differs is how a picture is recognised:
 * PDFBox hands over the object the file keeps an image in, so two pages drawing
 * one mark reach the same object. CoreGraphics hands over a pointer whose
 * lifetime it does not describe, and one reused for a different stream would
 * show one picture in place of another - a bug nobody could explain from what
 * they were looking at. So a picture is known here by its own bytes.
 *
 * Which costs something, and is why [MAX_FIGURE_BYTES] exists: the bytes must be
 * unpacked to be compared, and holding them is what makes the comparison exact
 * rather than a hash two pictures could share. This cache is for furniture - a
 * mark, a banner, a watermark - which is small. A plate is not repeated, so
 * remembering one would cost the memory and save nothing.
 */
@OptIn(ExperimentalForeignApi::class)
internal class CoreGraphicsFigureBytes {

    private val remembered = mutableMapOf<FigureSource, ByteArray>()
    private var rememberedBytes = 0

    /**
     * This picture as bytes the reader can decode, decoding it only if this
     * book has not produced it already.
     *
     * Null when the picture cannot be read at all, which costs that picture
     * rather than the page it was on.
     */
    fun of(
        stream: CGPDFStreamRef,
        dictionary: CGPDFDictionaryRef,
        content: CGPDFContentStreamRef,
    ): ByteArray? {
        val source = source(stream, dictionary)

        source?.let { remembered[it] }?.let { return it }

        val bytes = coreGraphicsImageBytes(stream, dictionary, content) ?: return null

        if (source != null) remember(source, bytes)

        return bytes
    }

    private fun remember(source: FigureSource, bytes: ByteArray) {
        val cost = source.size + bytes.size

        if (rememberedBytes + cost > MAX_REMEMBERED_BYTES) return

        remembered[source] = bytes
        rememberedBytes += cost
    }

    /**
     * What this picture is made of, or null when it is too large to be worth
     * knowing - see [MAX_FIGURE_BYTES].
     *
     * The mask travels with it. A PDF keeps transparency in a second image
     * beside the first, so two pictures whose own samples are identical are
     * still two pictures when one of them is see-through.
     */
    private fun source(stream: CGPDFStreamRef, dictionary: CGPDFDictionaryRef): FigureSource? {
        val image = stream.bytes() ?: return null
        val mask = dictionary.pdfStream("SMask")?.bytes()

        return FigureSource(image, mask).takeIf { it.size <= MAX_FIGURE_BYTES }
    }

    private companion object {
        /** Comfortably above any mark, banner or watermark; well below a plate. */
        const val MAX_FIGURE_BYTES = 4 * 1024 * 1024

        /** The same budget the converted book's own pictures are held to. */
        const val MAX_REMEMBERED_BYTES = 32 * 1024 * 1024
    }
}

/**
 * One picture as the file holds it, and what tells two of them apart.
 *
 * Compared by content rather than by hash: a hash decides which bucket to look
 * in, and the arrays themselves decide whether what is in it is this picture.
 */
private class FigureSource(private val image: ByteArray, private val mask: ByteArray?) {

    val size: Int get() = image.size + (mask?.size ?: 0)

    override fun hashCode(): Int = 31 * image.contentHashCode() + (mask?.contentHashCode() ?: 0)

    override fun equals(other: Any?): Boolean {
        if (other !is FigureSource) return false

        return image.contentEquals(other.image) &&
            (mask?.contentEquals(other.mask) ?: (other.mask == null))
    }
}

/**
 * A stream's contents, unpacked out of whatever the file packed them in.
 *
 * The same call the decoder makes, and the reason a repeated picture still
 * costs something here: CoreGraphics offers no way to ask what a stream holds
 * without being given it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun CGPDFStreamRef.bytes(): ByteArray? = memScoped {
    val format = alloc<CGPDFDataFormat.Var>()
    val data = CGPDFStreamCopyData(this@bytes, format.ptr) ?: return null
    val bytes = data.toByteArray()

    CFRelease(data)

    bytes
}
