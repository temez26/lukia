package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.infrastructure.pdf.PdfRect
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGPDFDictionaryRef
import platform.CoreGraphics.CGPDFScannerPopNumber
import platform.CoreGraphics.CGPDFScannerRef

/**
 * A PDF's transformations, and the two places the picture walk reads one from:
 * the page's own program, where a transformation arrives as six numbers on a
 * stack, and a form's dictionary, where it is six numbers in an array.
 */

private const val MATRIX_VALUES = 6

/**
 * A PDF's transformation, as the six numbers it writes one in.
 *
 * Multiplication is the order PDF concatenates in: the new transformation acts
 * first and the one already in force acts on its result, which is why a picture
 * inside a form inside a page ends up where all three put it.
 */
internal class PdfMatrix(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
) {
    operator fun times(outer: PdfMatrix) = PdfMatrix(
        a = a * outer.a + b * outer.c,
        b = a * outer.b + b * outer.d,
        c = c * outer.a + d * outer.c,
        d = c * outer.b + d * outer.d,
        e = e * outer.a + f * outer.c + outer.e,
        f = e * outer.b + f * outer.d + outer.f,
    )

    /**
     * The rectangle this transformation puts the unit square in, measured from
     * the page box's own corner.
     *
     * A PDF draws every image by transforming the square from (0,0) to (1,1),
     * so the four corners are carried through and the box around them taken -
     * which is what a rotated or mirrored placement needs, and what a plain one
     * comes out of unchanged.
     */
    fun unitSquare(offsetX: Float, offsetY: Float): PdfRect {
        val xs = doubleArrayOf(e, a + e, c + e, a + c + e)
        val ys = doubleArrayOf(f, b + f, d + f, b + d + f)

        val left = xs.min()
        val right = xs.max()
        val bottom = ys.min()
        val top = ys.max()

        return PdfRect(
            x = (left - offsetX).toFloat(),
            y = (bottom - offsetY).toFloat(),
            width = (right - left).toFloat(),
            height = (top - bottom).toFloat(),
        )
    }

    companion object {
        val IDENTITY = PdfMatrix(a = 1.0, b = 0.0, c = 0.0, d = 1.0, e = 0.0, f = 0.0)
    }
}

/**
 * The six numbers of a `cm`, taken off the scanner's stack.
 *
 * Popped last first, because a stack hands back what was pushed most recently
 * and the file wrote them in order.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFScannerRef.popMatrix(): PdfMatrix? = memScoped {
    val number = alloc<DoubleVar>()
    val values = DoubleArray(MATRIX_VALUES)

    for (position in MATRIX_VALUES - 1 downTo 0) {
        if (!CGPDFScannerPopNumber(this@popMatrix, number.ptr)) return null
        values[position] = number.value
    }

    PdfMatrix(
        a = values[0],
        b = values[1],
        c = values[2],
        d = values[3],
        e = values[4],
        f = values[5],
    )
}

/** A form's own transformation, which places its contents inside the page. */
@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.formMatrix(): PdfMatrix? {
    val values = pdfArray("Matrix")?.pdfNumbers(MATRIX_VALUES) ?: return null

    return PdfMatrix(
        a = values[0],
        b = values[1],
        c = values[2],
        d = values[3],
        e = values[4],
        f = values[5],
    )
}
