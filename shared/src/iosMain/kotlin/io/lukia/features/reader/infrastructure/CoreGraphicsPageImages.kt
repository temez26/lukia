package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.PageImage
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.infrastructure.pdf.PdfRect
import io.lukia.features.reader.infrastructure.pdf.isWorthShowing
import io.lukia.features.reader.infrastructure.pdf.pdfImageBounds
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreGraphics.CGPDFContentStreamCreateWithPage
import platform.CoreGraphics.CGPDFContentStreamCreateWithStream
import platform.CoreGraphics.CGPDFContentStreamGetResource
import platform.CoreGraphics.CGPDFContentStreamRef
import platform.CoreGraphics.CGPDFContentStreamRelease
import platform.CoreGraphics.CGPDFDictionaryRef
import platform.CoreGraphics.CGPDFOperatorTableCreate
import platform.CoreGraphics.CGPDFOperatorTableRef
import platform.CoreGraphics.CGPDFOperatorTableRelease
import platform.CoreGraphics.CGPDFOperatorTableSetCallback
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGPDFPageGetRotationAngle
import platform.CoreGraphics.CGPDFPageRef
import platform.CoreGraphics.CGPDFScannerCreate
import platform.CoreGraphics.CGPDFScannerGetContentStream
import platform.CoreGraphics.CGPDFScannerPopName
import platform.CoreGraphics.CGPDFScannerRef
import platform.CoreGraphics.CGPDFScannerRelease
import platform.CoreGraphics.CGPDFScannerScan
import platform.CoreGraphics.CGPDFStreamGetDictionary
import platform.CoreGraphics.CGPDFStreamRef
import platform.CoreGraphics.kCGPDFCropBox

/**
 * The pictures on one page of a PDF, in the places they were drawn.
 *
 * A PDF has no list of the pictures on a page - it has a program that draws
 * them, and where each one lands depends on the transformations in force when
 * the drawing instruction is reached. So the only way to know both what a
 * picture is and where it went is to run the page as a renderer would and stop
 * at each image.
 *
 * PDFKit, which draws the pages this reader shows, has no way to ask that
 * question: it renders a page or hands over its text, and what is on the page
 * is not part of its vocabulary. So this drops to CoreGraphics, which will run
 * a page's program and call back on the operators it is asked about - four of
 * them here, the two that save and restore the transformation, the one that
 * changes it, and the one that draws something the page named.
 *
 * Not found: an image written into the page's program rather than named by it,
 * the inline image. CoreGraphics does not offer its data to a scanner at all,
 * and the format exists for the small marks a page draws by hand - a rule, a
 * bullet - which are already below the size worth carrying into the text.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun coreGraphicsPageImages(page: CGPDFPageRef): List<PageImage> {
    val operators = imageOperators() ?: return emptyList()
    val content = CGPDFContentStreamCreateWithPage(page)

    if (content == null) {
        CGPDFOperatorTableRelease(operators)
        return emptyList()
    }

    val walk = PageWalk(page, operators)
    val handle = StableRef.create(walk)
    walk.info = handle.asCPointer()

    try {
        walk.scan(content)
    } finally {
        handle.dispose()
        CGPDFContentStreamRelease(content)
        CGPDFOperatorTableRelease(operators)
    }

    return walk.images
}

/**
 * A page being run, and the transformation in force at this point in it.
 *
 * CoreGraphics keeps none of this: a scanner reports operators and their
 * operands and leaves the graphics state to whoever asked. What a picture needs
 * is exactly the part of that state the scanner does not keep, so the four
 * operators that move it are followed here and the rest of the page's drawing
 * goes past unread.
 */
@OptIn(ExperimentalForeignApi::class)
private class PageWalk(page: CGPDFPageRef, val operators: CGPDFOperatorTableRef) {

    val images = mutableListOf<PageImage>()

    /** This object, as the pointer CoreGraphics hands back to each callback. */
    var info: COpaquePointer? = null

    private val box: PdfRect = CGPDFPageGetBoxRect(page, kCGPDFCropBox).useContents {
        PdfRect(
            x = origin.x.toFloat(),
            y = origin.y.toFloat(),
            width = size.width.toFloat(),
            height = size.height.toFloat(),
        )
    }

    private val rotation: Int = CGPDFPageGetRotationAngle(page)

    private var transform = PdfMatrix.IDENTITY
    private val saved = ArrayDeque<PdfMatrix>()
    private var depth = 0

    fun scan(content: CGPDFContentStreamRef) {
        val scanner = CGPDFScannerCreate(content, operators, info) ?: return

        CGPDFScannerScan(scanner)
        CGPDFScannerRelease(scanner)
    }

    fun save() {
        saved.addLast(transform)
    }

    fun restore() {
        transform = saved.removeLastOrNull() ?: PdfMatrix.IDENTITY
    }

    fun concatenate(matrix: PdfMatrix) {
        transform = matrix * transform
    }

    fun drawObject(scanner: CGPDFScannerRef) {
        val name = scanner.popName() ?: return
        val content = CGPDFScannerGetContentStream(scanner) ?: return
        val stream = CGPDFContentStreamGetResource(content, "XObject", name)?.asStream() ?: return
        val dictionary = CGPDFStreamGetDictionary(stream) ?: return

        when (dictionary.pdfName("Subtype")) {
            "Image" -> takeImage(stream, dictionary, content)
            "Form" -> enterForm(stream, dictionary, content)
        }
    }

    private fun takeImage(
        stream: CGPDFStreamRef,
        dictionary: CGPDFDictionaryRef,
        content: CGPDFContentStreamRef,
    ) {
        // A stencil is a shape painted in the current colour - a mask, a logo
        // cut out of one ink. It carries no picture of its own to show.
        if (dictionary.pdfFlag("ImageMask") == true) return

        val bounds = pdfImageBounds(
            drawn = transform.unitSquare(offsetX = box.x, offsetY = box.y),
            // The box the file states, not the shape the page is shown at: the
            // turning is what `rotation` is for.
            page = PageSize(width = box.width, height = box.height),
            rotation = rotation,
        )

        // Asked before the image is decoded: a page can carry hundreds of
        // hairlines and gradient tiles, and decoding each one to find out it is
        // two points tall is most of what reading a page would cost.
        if (!bounds.isWorthShowing()) return

        coreGraphicsImageBytes(stream, dictionary, content)?.let {
            images += PageImage(bytes = it, bounds = bounds)
        }
    }

    /**
     * A form is a piece of a page drawn as a unit, and a figure placed by a
     * word processor is routinely one - so its own program is run here, under
     * the transformation that placed it, or the picture inside it would never
     * be reached.
     *
     * What the form's own `q` and `Q` left behind is dropped on the way out.
     * A malformed one can restore a state it never saved, and letting it take
     * the page's transformations with it would move everything drawn after it.
     */
    private fun enterForm(
        stream: CGPDFStreamRef,
        dictionary: CGPDFDictionaryRef,
        parent: CGPDFContentStreamRef,
    ) {
        if (depth >= MAX_FORM_DEPTH) return

        val nested = CGPDFContentStreamCreateWithStream(
            stream,
            dictionary.pdfDictionary("Resources"),
            parent,
        ) ?: return

        val outer = transform
        val mark = saved.size

        dictionary.formMatrix()?.let { transform = it * transform }
        depth++

        scan(nested)

        depth--
        transform = outer
        while (saved.size > mark) saved.removeLast()

        CGPDFContentStreamRelease(nested)
    }
}

/** How deep a page's forms may nest before the file is assumed malformed. */
private const val MAX_FORM_DEPTH = 8

/**
 * The four operators the picture walk listens for. Everything else a page draws
 * runs past without a callback, which is what makes this cheap on a page of
 * text.
 */
@OptIn(ExperimentalForeignApi::class)
private fun imageOperators(): CGPDFOperatorTableRef? {
    val table = CGPDFOperatorTableCreate() ?: return null

    CGPDFOperatorTableSetCallback(
        table,
        "q",
        staticCFunction<CGPDFScannerRef?, COpaquePointer?, Unit> { _, info -> info.walk()?.save() },
    )
    CGPDFOperatorTableSetCallback(
        table,
        "Q",
        staticCFunction<CGPDFScannerRef?, COpaquePointer?, Unit> { _, info ->
            info.walk()?.restore()
        },
    )
    CGPDFOperatorTableSetCallback(
        table,
        "cm",
        staticCFunction<CGPDFScannerRef?, COpaquePointer?, Unit> { scanner, info ->
            val walk = info.walk()
            val matrix = scanner?.popMatrix()

            if (walk != null && matrix != null) walk.concatenate(matrix)
        },
    )
    CGPDFOperatorTableSetCallback(
        table,
        "Do",
        staticCFunction<CGPDFScannerRef?, COpaquePointer?, Unit> { scanner, info ->
            if (scanner != null) info.walk()?.drawObject(scanner)
        },
    )

    return table
}

@OptIn(ExperimentalForeignApi::class)
private fun COpaquePointer?.walk(): PageWalk? = this?.asStableRef<PageWalk>()?.get()

@OptIn(ExperimentalForeignApi::class)
private fun CGPDFScannerRef.popName(): String? = memScoped {
    val name = alloc<CPointerVar<ByteVar>>()

    if (CGPDFScannerPopName(this@popName, name.ptr)) name.value?.toKString() else null
}
