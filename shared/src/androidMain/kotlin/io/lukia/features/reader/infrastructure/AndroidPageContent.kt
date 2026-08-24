package io.lukia.features.reader.infrastructure

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.PageImage
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.TextQuad
import io.lukia.features.reader.infrastructure.pdf.PdfRect
import io.lukia.features.reader.infrastructure.pdf.isWorthShowing
import io.lukia.features.reader.infrastructure.pdf.pdfImageBounds
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * What is on a page of a PDF on Android: the words, and the pictures.
 *
 * Android's own [android.graphics.pdf.PdfRenderer] draws pages and says nothing
 * about what is on them - it grew a text API only in API 35, which is far above
 * this app's minimum, and has never had one for images. So the file is opened a
 * second time, by a library that can read it, purely to answer "what does this
 * page say, what is drawn on it, and where".
 *
 * Opened lazily and only once: most books are never asked either question, and a
 * reader who asks one of them will ask it again. The handle is given back with
 * the document it belongs to.
 *
 * This is the same library the desktop uses, repackaged - which is why what
 * follows reads like its counterpart in `PdfDocumentPages.jvm.kt` and
 * `PdfBoxPageImages.kt` despite sharing no code with them. The two libraries
 * have different package names, different image types and different ways of
 * being opened; everything after the glyphs and the pictures come out is common
 * code.
 */
internal class AndroidPageContent(private val file: File) : AutoCloseable {

    private var document: PDDocument? = null
    private var opened = false

    fun glyphs(pageIndex: Int): List<PageGlyph>? {
        val document = document() ?: return null

        return runCatching {
            val stripper = GlyphStripper().apply {
                // PDFBox counts pages from one, and reads the range it is given
                // rather than the whole document.
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }

            stripper.getText(document)
            stripper.glyphs.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun images(pageIndex: Int): List<PageImage> {
        val document = document() ?: return emptyList()

        return runCatching {
            val page = document.getPage(pageIndex)

            ImageCollector(page).also { it.processPage(page) }.images
        }.getOrDefault(emptyList())
    }

    /**
     * A file that cannot be opened for reading is remembered as such, so a book
     * this library will not parse is not re-parsed on every tap.
     */
    private fun document(): PDDocument? {
        if (!opened) {
            opened = true
            document = runCatching { PDDocument.load(file) }.getOrNull()
        }

        return document
    }

    override fun close() {
        runCatching { document?.close() }
        document = null
    }
}

/**
 * Collects every positioned character on a page instead of writing the page out
 * as a string. See the desktop's own `GlyphStripper` for why the layer
 * underneath the text is the one wanted here.
 */
private class GlyphStripper : PDFTextStripper() {

    val glyphs = mutableListOf<PageGlyph>()

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        textPositions.forEach { position ->
            val character = position.unicode ?: return@forEach
            if (character.isEmpty()) return@forEach

            glyphs += PageGlyph(
                text = character,
                bounds = TextQuad(
                    left = position.xDirAdj,
                    top = position.yDirAdj - position.heightDir,
                    right = position.xDirAdj + position.widthDirAdj,
                    bottom = position.yDirAdj,
                ),
            )
        }
    }
}

/** See `PdfBoxPageImages.kt` on the desktop: the same walk, in this library. */
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
     * can use - see the desktop's own encoder for the trade between the two
     * formats.
     */
    private fun Bitmap.encoded(): ByteArray {
        val shrunk = shrunkToFit()
        val bytes = ByteArrayOutputStream()

        if (hasAlpha()) {
            shrunk.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)
        } else {
            shrunk.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
        }

        if (shrunk !== this) shrunk.recycle()

        return bytes.toByteArray()
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

    private companion object {
        /** See the desktop's own constant: no screen draws a plate larger. */
        const val MAX_FIGURE_PIXELS = 1600

        /** PNG is lossless, so the number is only how hard it tries to pack it. */
        const val PNG_QUALITY = 100
        const val JPEG_QUALITY = 82
    }
}
