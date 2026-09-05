package io.lukia.features.library.infrastructure.importing.cover

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

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
 */
internal actual fun renderPdfCover(pdfBytes: ByteArray): ByteArray? {
    val tempFile = File.createTempFile("lukia-cover-", ".pdf")

    return try {
        tempFile.writeBytes(pdfBytes)

        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    null
                } else {
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

                        // PdfRenderer draws the page's ink and nothing else, so
                        // whatever it does not cover keeps the bitmap's own
                        // transparent background - which comes out black the
                        // moment the cover is stored in a format without an
                        // alpha channel. The paper has to be painted first.
                        bitmap.eraseColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        ByteArrayOutputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            output.toByteArray()
                        }
                    }
                }
            }
        }
    } catch (ignored: IOException) {
        null
    } catch (ignored: SecurityException) {
        null
    } finally {
        tempFile.delete()
    }
}
