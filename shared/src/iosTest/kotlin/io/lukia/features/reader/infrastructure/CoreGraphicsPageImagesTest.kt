package io.lukia.features.reader.infrastructure

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGContextBeginTransparencyLayer
import platform.CoreGraphics.CGContextEndTransparencyLayer
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The picture walk, against PDFs written by CoreGraphics itself.
 *
 * Generating the file rather than checking one in keeps what is being asserted
 * visible: the rectangle the picture was drawn in is written a few lines above
 * the rectangle the reader is expected to report, and the two are the same
 * numbers because the reader's space and UIKit's are both measured downwards
 * from the top left of the page.
 */
@OptIn(ExperimentalForeignApi::class)
class CoreGraphicsPageImagesTest {

    @Test
    fun findsAPictureWhereItWasDrawn() = runTest {
        withTemporaryPdf(pdfDrawing(CGRectMake(50.0, 100.0, 100.0, 80.0))) { path ->
            val images = openPdfPages(path).use { it.pageImages(0) }

            assertEquals(1, images.size)

            val bounds = images.single().bounds

            assertEquals(50f, bounds.left, EDGE_TOLERANCE)
            assertEquals(100f, bounds.top, EDGE_TOLERANCE)
            assertEquals(150f, bounds.right, EDGE_TOLERANCE)
            assertEquals(180f, bounds.bottom, EDGE_TOLERANCE)
        }
    }

    @Test
    fun readsThePictureAsAnEncodedImage() = runTest {
        withTemporaryPdf(pdfDrawing(CGRectMake(50.0, 100.0, 100.0, 80.0))) { path ->
            val bytes = openPdfPages(path).use { it.pageImages(0) }.single().bytes

            assertTrue(bytes.size > 1, "the picture came back with no bytes")
            assertTrue(bytes.isJpeg() || bytes.isPng(), "the picture is in no format a reader decodes")
        }
    }

    @Test
    fun findsAPictureInsideAFormThePageDrawsAsAUnit() = runTest {
        val grouped = pdfDrawing(CGRectMake(50.0, 100.0, 100.0, 80.0), asGroup = true)

        withTemporaryPdf(grouped) { path ->
            val images = openPdfPages(path).use { it.pageImages(0) }

            assertEquals(1, images.size)

            val bounds = images.single().bounds

            assertEquals(50f, bounds.left, EDGE_TOLERANCE)
            assertEquals(100f, bounds.top, EDGE_TOLERANCE)
            assertEquals(150f, bounds.right, EDGE_TOLERANCE)
            assertEquals(180f, bounds.bottom, EDGE_TOLERANCE)
        }
    }

    @Test
    fun keepsWhatAPictureIsSeeThroughIn() = runTest {
        val transparent = pdfDrawing(CGRectMake(50.0, 100.0, 100.0, 80.0), opaque = false)

        withTemporaryPdf(transparent) { path ->
            val bytes = openPdfPages(path).use { it.pageImages(0) }.single().bytes

            assertTrue(bytes.isPng(), "a picture with transparency was written out as JPEG")
        }
    }

    @Test
    fun leavesMarksTooSmallToBeIllustrations() = runTest {
        withTemporaryPdf(pdfDrawing(CGRectMake(50.0, 100.0, 10.0, 10.0))) { path ->
            val images = openPdfPages(path).use { it.pageImages(0) }

            assertTrue(images.isEmpty(), "a ten point mark was carried into the text")
        }
    }

    @Test
    fun aPageThatDrawsNothingHasNoPictures() = runTest {
        withTemporaryPdf(pdfDrawing(null)) { path ->
            val images = openPdfPages(path).use { it.pageImages(0) }

            assertTrue(images.isEmpty())
        }
    }

    /**
     * A page with the picture drawn on it, or with nothing drawn on it at all.
     *
     * [asGroup] puts the drawing inside a transparency layer, which is how
     * CoreGraphics is asked to write a form: a piece of a page kept as its own
     * little program with its own resources, which is where a word processor
     * puts a figure and where the walk has to follow it.
     */
    private fun pdfDrawing(
        picture: CValue<CGRect>?,
        opaque: Boolean = true,
        asGroup: Boolean = false,
    ): NSData {
        val renderer = UIGraphicsPDFRenderer(
            bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT),
        )

        return renderer.PDFDataWithActions { context ->
            context?.beginPage()

            val layer = if (asGroup) context?.CGContext else null

            layer?.let { CGContextBeginTransparencyLayer(it, null) }
            picture?.let { solidImage(opaque).drawInRect(it) }
            layer?.let { CGContextEndTransparencyLayer(it) }
        }
    }

    /**
     * A red square, filling its whole image or only the left half of it. The
     * half-filled one leaves the rest see-through, which is what makes
     * CoreGraphics store it with a soft mask beside it.
     */
    private fun solidImage(opaque: Boolean): UIImage {
        val filled = if (opaque) PICTURE_PIXELS else PICTURE_PIXELS / 2

        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(PICTURE_PIXELS, PICTURE_PIXELS),
            opaque,
            1.0,
        )
        UIColor.redColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, filled, PICTURE_PIXELS))
        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return checkNotNull(image)
    }

    private inline fun withTemporaryPdf(data: NSData, block: (String) -> Unit) {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString() + ".pdf"

        data.writeToFile(path, atomically = true)

        try {
            block(path)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }

    private fun ByteArray.isJpeg(): Boolean =
        size > 1 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()

    private fun ByteArray.isPng(): Boolean =
        size > 1 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()

    private companion object {
        const val PAGE_WIDTH = 612.0
        const val PAGE_HEIGHT = 792.0
        const val PICTURE_PIXELS = 64.0

        /** A point either way: a placement is not expected to be exact to the bit. */
        const val EDGE_TOLERANCE = 1f
    }
}
