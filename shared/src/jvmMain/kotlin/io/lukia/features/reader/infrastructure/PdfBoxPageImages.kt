package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.infrastructure.pdf.PdfRect
import io.lukia.features.reader.infrastructure.pdf.isWorthShowing
import io.lukia.features.reader.infrastructure.pdf.pdfImageBounds
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import java.awt.geom.Point2D

/**
 * The pictures on one page of a PDF, in the places they were drawn.
 *
 * A PDF has no list of the pictures on a page - it has a program that draws
 * them, and where each lands depends on the transformations in force when the
 * instruction is reached. So the only way to know what a picture is and where it
 * went is to walk the page as a renderer would and stop at each image.
 *
 * That is what [PDFGraphicsStreamEngine] is: a renderer with nothing behind it.
 * Every method below except [ImageCollector.drawImage] is a painting instruction
 * deliberately ignored, and the engine still has to be told - it is the running
 * of the whole program that puts each image where it belongs.
 *
 * [figures] belongs to the document rather than to the page, and is what keeps
 * a mark printed on every leaf from being decoded on every leaf - see
 * [PdfFigureBytes].
 */
internal fun pdfBoxPageImages(page: PDPage, figures: PdfFigureBytes): List<PageImage> =
    runCatching {
        ImageCollector(page, figures).also { it.processPage(page) }.images
    }.getOrDefault(emptyList())

private class ImageCollector(
    private val page: PDPage,
    private val figures: PdfFigureBytes,
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

        if (!bounds.isWorthShowing()) return

        figures.of(pdImage)?.let { images += PageImage(it, bounds) }
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
