package io.lukia.features.reader.infrastructure

import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * How large a picture is kept, in pixels along its longer side.
 *
 * A book's plates are stored at print resolution, which is several times more
 * than any screen will draw them at, and every one of them is held in memory
 * for as long as the book is open. Shrinking to something no screen exceeds
 * costs nothing anybody can see and is the difference between a converted art
 * book and one that cannot be opened.
 */
private const val MAX_FIGURE_PIXELS = 1600

/** Enough to keep a diagram's lines clean without storing a photograph twice. */
private const val JPEG_QUALITY = 0.82f

/**
 * How much encoded picture is remembered while a book is being converted.
 *
 * The same budget the converted book's own pictures are held to - see
 * `PdfFigures` - and for the same reason: what is kept here is the very arrays
 * handed to it, so a book within its budget costs one copy between the two and
 * a book past it stops growing both at once.
 */
private const val MAX_REMEMBERED_BYTES = 32 * 1024 * 1024

/**
 * Every picture a book has already been asked for, so that each one is decoded
 * once however many pages draw it.
 *
 * This is the difference between converting a book and waiting for one. A
 * publisher's mark, a journal's banner, a watermark: PDFs draw these on every
 * page, and a picture is not a picture until it has been decoded out of whatever
 * the file packed it in and encoded back into something the reader can hold. On
 * a three-hundred page book that was three hundred decodes and three hundred
 * JPEGs of one logo, of which all but one were then thrown away by the very code
 * that noticed they were the same.
 *
 * Keyed by the object the file keeps the picture in rather than by its bytes,
 * which makes the saving arrive before the work rather than after: a PDF stores
 * one copy of a repeated image and names it from each page, so two pages drawing
 * the same mark reach the same object and this answers without decoding.
 *
 * Lives exactly as long as the conversion does - the document is opened to be
 * read as text and closed at the end of it - so there is no eviction here, only
 * a ceiling.
 */
internal class PdfFigureBytes {

    private val remembered = mutableMapOf<COSBase, ByteArray>()
    private var rememberedBytes = 0

    /**
     * This picture as bytes the reader can decode, decoding it only if this
     * book has not produced it already.
     *
     * Null when the picture cannot be read at all, which costs that picture
     * rather than the page it was on.
     */
    fun of(image: PDImage): ByteArray? {
        val key = (image as? PDImageXObject)?.cosObject

        key?.let { remembered[it] }?.let { return it }

        val bytes = runCatching { image.encoded() }.getOrNull() ?: return null

        if (key != null && rememberedBytes + bytes.size <= MAX_REMEMBERED_BYTES) {
            remembered[key] = bytes
            rememberedBytes += bytes.size
        }

        return bytes
    }
}

/**
 * The picture as bytes the reader can decode, shrunk to something a screen can
 * use.
 *
 * Shrunk on the way out of the file wherever the file will do it: PDFBox can
 * decode every second, fourth or eighth pixel, and a plate stored at print
 * resolution is otherwise decoded whole - a three-thousand pixel square is
 * thirty-six megabytes built to be thrown away by the next line. A saving in
 * memory rather than time, since the file is read through either way; what it
 * avoids is the peak, which on a phone is the difference between a slow
 * conversion and a killed one. Subsampling is exact rather than smooth, so what
 * it leaves is still trimmed to the ceiling by the scaling below.
 *
 * JPEG unless the image has transparency to keep, which is the trade a browser
 * makes with the same images: a photograph as PNG is several times the size for
 * a difference nobody can see, while a diagram with a see-through background
 * loses its background as JPEG.
 */
private fun PDImage.encoded(): ByteArray? {
    val decoded = decoded() ?: return null
    val shrunk = decoded.shrunkToFit()
    val bytes = ByteArrayOutputStream()

    if (shrunk.colorModel.hasAlpha()) {
        ImageIO.write(shrunk, "png", bytes)
    } else {
        writeJpeg(shrunk, bytes)
    }

    return bytes.toByteArray()
}

/**
 * The picture decoded, at no more resolution than is going to be kept.
 *
 * Only an image the file stores as an object of its own can be subsampled. An
 * inline image is written into the page's own program, which is a place small
 * marks go, and it is decoded whole.
 */
private fun PDImage.decoded(): BufferedImage? = when (this) {
    is PDImageXObject -> getImage(null, subsampling(width, height))
    else -> image
}

/**
 * How many pixels of the file make one pixel of the picture kept.
 *
 * Halvings rather than any factor, because that is what an exact subsample is:
 * taking every third pixel of a photograph leaves the interference pattern
 * between the two grids, which halving never does. So this is the largest power
 * of two that still leaves the picture above the ceiling, and the ordinary
 * scaling takes it the rest of the way down.
 */
private fun subsampling(width: Int, height: Int): Int {
    var factor = 1

    while (maxOf(width, height) / (factor * 2) >= MAX_FIGURE_PIXELS) factor *= 2

    return factor
}

private fun BufferedImage.shrunkToFit(): BufferedImage {
    val longest = maxOf(width, height)
    if (longest <= MAX_FIGURE_PIXELS) return this

    val scale = MAX_FIGURE_PIXELS.toFloat() / longest
    val target = BufferedImage(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        if (colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
    )

    val canvas = target.createGraphics()
    canvas.drawImage(this, 0, 0, target.width, target.height, null)
    canvas.dispose()

    return target
}

private fun writeJpeg(image: BufferedImage, into: ByteArrayOutputStream) {
    // JPEG cannot carry an alpha channel, and an image that has one - even an
    // unused one - is written as garbled colour rather than refused.
    val opaque = if (image.colorModel.hasAlpha()) image.withoutAlpha() else image

    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val parameters = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = JPEG_QUALITY
    }

    ImageIO.createImageOutputStream(into).use { stream ->
        writer.output = stream
        writer.write(null, IIOImage(opaque, null, null), parameters)
        writer.dispose()
    }
}

private fun BufferedImage.withoutAlpha(): BufferedImage {
    val opaque = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val canvas = opaque.createGraphics()
    canvas.drawImage(this, 0, 0, null)
    canvas.dispose()

    return opaque
}
