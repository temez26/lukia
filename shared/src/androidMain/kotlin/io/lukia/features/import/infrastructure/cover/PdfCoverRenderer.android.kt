package io.lukia.features.import.infrastructure.cover

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

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
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        ByteArrayOutputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            output.toByteArray()
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        null
    } finally {
        tempFile.delete()
    }
}
