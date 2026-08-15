package io.lukia.features.reader.infrastructure

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.PageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal actual fun openPdfPages(path: String): DocumentPages = AndroidDocumentPages(path)

private class AndroidDocumentPages(path: String) : DocumentPages {

    private val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)

    private val renderer = PdfRenderer(descriptor)

    /**
     * [PdfRenderer] allows one open page at a time and throws on the second, so
     * the reader's concurrent page requests have to be serialised rather than
     * merely made safe.
     */
    private val renderLock = Mutex()

    override val pageSizes: List<PageSize> = List(renderer.pageCount) { index ->
        renderer.openPage(index).use { page ->
            PageSize(width = page.width.toFloat(), height = page.height.toFloat())
        }
    }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            renderLock.withLock {
                runCatching {
                    renderer.openPage(index).use { page ->
                        val height = targetWidthPx * page.height / page.width

                        val bitmap = Bitmap.createBitmap(
                            targetWidthPx,
                            height,
                            Bitmap.Config.ARGB_8888
                        )

                        // PdfRenderer draws only what the page paints, so a page
                        // with no background of its own would come out on
                        // transparent black without this.
                        bitmap.eraseColor(PAGE_BACKGROUND)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        bitmap.asImageBitmap()
                    }
                }.getOrNull()
            }
        }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    private companion object {
        const val PAGE_BACKGROUND = 0xFFFFFFFF.toInt()
    }
}
