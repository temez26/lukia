package io.lukia.features.reader.infrastructure

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.infrastructure.pdf.PdfRect
import io.lukia.features.reader.infrastructure.pdf.isWorthShowing
import io.lukia.features.reader.infrastructure.pdf.pdfImageBounds
import java.io.ByteArrayOutputStream

/** See the desktop's own constant: no screen draws a plate larger. */
private const val MAX_FIGURE_PIXELS = 1600

/** PNG is lossless, so the number is only how hard it tries to pack it. */
private const val PNG_QUALITY = 100
private const val JPEG_QUALITY = 82

/** The same budget the converted book's pictures are held to - see `PdfFigures`. */
private const val MAX_REMEMBERED_BYTES = 32 * 1024 * 1024

/**
 * See `PdfBoxPageImages.kt` on the desktop: the same walk, in this library.
 *
 * [figures] belongs to the document rather than to the page, and matters more
 * here than anywhere: this is the platform where converting a long book is
 * slowest, and a mark printed on every leaf was being decoded on every leaf.
 */
internal fun androidPageImages(page: PDPage, figures: AndroidFigureBytes): List<PageImage> =
    runCatching {
        ImageCollector(page, figures).also { it.processPage(page) }.images
    }.getOrDefault(emptyList())

/** See `PdfFigureBytes` on the desktop, which this mirrors in the other library. */
internal class AndroidFigureBytes {

    private val remembered = mutableMapOf<COSBase, ByteArray>()
    private var rememberedBytes = 0

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
 * use - see the desktop's own encoder for the trade between the two formats and
 * for why the file is asked to do the first of the shrinking.
 */
private fun PDImage.encoded(): ByteArray? {
    val decoded = decoded() ?: return null
    val shrunk = decoded.shrunkToFit()
    val bytes = ByteArrayOutputStream()

    if (shrunk.hasAlpha()) {
        shrunk.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)
    } else {
        shrunk.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
    }

    // The scaled copy only: what it was scaled from is the library's, which
    // keeps a soft reference to it and would hand back a recycled bitmap.
    if (shrunk !== decoded) shrunk.recycle()

    return bytes.toByteArray()
}

private fun PDImage.decoded(): Bitmap? = when (this) {
    is PDImageXObject -> getImage(null, subsampling(width, height))
    else -> image
}

/** See the desktop's own: the largest halving that still clears the ceiling. */
private fun subsampling(width: Int, height: Int): Int {
    var factor = 1

    while (maxOf(width, height) / (factor * 2) >= MAX_FIGURE_PIXELS) factor *= 2

    return factor
}

private fun Bitmap.shrunkToFit(): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= MAX_FIGURE_PIXELS) return this

    val scale = MAX_FIGURE_PIXELS.toFloat() / longest

    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private class ImageCollector(
    private val page: PDPage,
    private val figures: AndroidFigureBytes,
) : PDFGraphicsStreamEngine(page) {

    val images = mutableListOf<PageImage>()

    override fun drawImage(pdImage: PDImage) {
        // A stencil is a shape painted in the current colour - a mask, a logo
        // cut out of one ink. It carries no picture of its own to show.
        if (pdImage.isStencil) return

        val matrix = graphicsState.currentTransformationMatrix
        val box = page.cropBox

        val bounds = pdfImageBounds(
            drawn = PdfRect(
                x = matrix.translateX - box.lowerLeftX,
                y = matrix.translateY - box.lowerLeftY,
                width = matrix.scalingFactorX,
                height = matrix.scalingFactorY,
            ),
            // The box the file states, not the shape the page is shown at: the
            // turning is what `rotation` is for.
            page = PageSize(width = box.width, height = box.height),
            rotation = page.rotation,
        )

        // Asked before the image is decoded: a page can carry hundreds of
        // hairlines and gradient tiles, and decoding each one to find out it is
        // two points tall is most of what reading a page would cost.
        if (!bounds.isWorthShowing()) return

        figures.of(pdImage)?.let { images += PageImage(it, bounds) }
    }

    // Everything the page paints that is not a picture. The engine runs the
    // whole content stream either way; these are where the rest of it goes.
    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) = Unit

    override fun clip(windingRule: Path.FillType) = Unit

    override fun moveTo(x: Float, y: Float) = Unit

    override fun lineTo(x: Float, y: Float) = Unit

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Unit

    override fun getCurrentPoint(): PointF = PointF(0f, 0f)

    override fun closePath() = Unit

    override fun endPath() = Unit

    override fun strokePath() = Unit

    override fun fillPath(windingRule: Path.FillType) = Unit

    override fun fillAndStrokePath(windingRule: Path.FillType) = Unit

    override fun shadingFill(shadingName: COSName) = Unit
}
