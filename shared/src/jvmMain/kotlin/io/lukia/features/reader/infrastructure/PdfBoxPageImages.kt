package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.PageImage
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.infrastructure.pdf.PdfRect
import io.lukia.features.reader.infrastructure.pdf.isWorthShowing
import io.lukia.features.reader.infrastructure.pdf.pdfImageBounds
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * The pictures on one page of a PDF, in the places they were drawn.
 *
 * A PDF has no list of the pictures on a page - it has a program that draws
 * them, and where each one lands depends on the transformations in force when
 * the drawing instruction is reached. So the only way to know both what a
 * picture is and where it went is to walk the page as a renderer would and stop
 * at each image.
 *
 * That is what [PDFGraphicsStreamEngine] is: a renderer with nothing behind it.
 * Every method below except [drawImage] is a painting instruction being
 * deliberately ignored - the lines, curves and fills of the page are exactly
 * what this is not interested in - and the engine still has to be told, because
 * it is the running of the whole program that puts each image where it belongs.
 */
internal fun pdfBoxPageImages(page: PDPage): List<PageImage> =
    runCatching {
        ImageCollector(page).also { it.processPage(page) }.images
    }.getOrDefault(emptyList())

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

private class ImageCollector(private val page: PDPage) : PDFGraphicsStreamEngine(page) {

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

        runCatching { pdImage.image?.let { images += PageImage(it.encoded(), bounds) } }
    }

    /**
     * The picture as bytes the reader can decode, shrunk to something a screen
     * can use.
     *
     * JPEG unless the image has transparency to keep, which is the trade a
     * browser makes with the same images: a photograph as PNG is several times
     * the size for a difference nobody can see, while a diagram with a
     * see-through background loses its background as JPEG.
     */
    private fun BufferedImage.encoded(): ByteArray {
        val shrunk = shrunkToFit()
        val transparent = colorModel.hasAlpha()
        val bytes = ByteArrayOutputStream()

        if (transparent) {
            ImageIO.write(shrunk, "png", bytes)
        } else {
            writeJpeg(shrunk, bytes)
        }

        return bytes.toByteArray()
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
        // JPEG cannot carry an alpha channel, and an image that has one - even
        // an unused one - is written as garbled colour rather than refused.
        val opaque = if (image.colorModel.hasAlpha()) image.withoutAlpha() else image

        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val parameters = writer.defaultWriteParam.apply {
            compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }

        ImageIO.createImageOutputStream(into).use { stream ->
            writer.output = stream
            writer.write(null, javax.imageio.IIOImage(opaque, null, null), parameters)
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

    // Everything the page paints that is not a picture. The engine runs the
    // whole content stream either way; these are where the rest of it goes.
    override fun appendRectangle(p0: Point2D, p1: Point2D, p2: Point2D, p3: Point2D) = Unit

    override fun clip(windingRule: Int) = Unit

    override fun moveTo(x: Float, y: Float) = Unit

    override fun lineTo(x: Float, y: Float) = Unit

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Unit

    override fun getCurrentPoint(): Point2D = Point2D.Float(0f, 0f)

    override fun closePath() = Unit

    override fun endPath() = Unit

    override fun strokePath() = Unit

    override fun fillPath(windingRule: Int) = Unit

    override fun fillAndStrokePath(windingRule: Int) = Unit

    override fun shadingFill(shadingName: COSName) = Unit
}
