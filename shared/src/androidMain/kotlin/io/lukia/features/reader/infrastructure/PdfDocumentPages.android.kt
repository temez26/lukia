package io.lukia.features.reader.infrastructure

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.infrastructure.pdf.PdfOutlineReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal actual fun openPdfPages(path: String): DocumentPages = AndroidDocumentPages(path)

private class AndroidDocumentPages(path: String) : DocumentPages {

    private val file = File(path)

    private val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

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

    /**
     * Read out of the file itself, because [PdfRenderer] exposes page count and
     * page bitmaps and nothing else - it has no outline, bookmark or destination
     * API at this app's minimum SDK.
     *
     * Lazy so that opening a book never pays for a chapter list until something
     * asks for one, and so the file is not scanned on the way to the first page.
     */
    override val outline: List<OutlineEntry>? by lazy {
        FilePdfByteSource(file).use { PdfOutlineReader(it).read() }
    }

    /**
     * Opened only if somebody asks what a page says, because it means opening
     * the file a second time with a library that can read text - which
     * [PdfRenderer] cannot.
     */
    private val text = lazy { AndroidPageText(file) }

    override suspend fun pageText(index: Int): List<PageGlyph>? =
        withContext(Dispatchers.Default) {
            // Serialised with drawing for the same reason drawing is serialised
            // with itself: this opens its own handle on the same file, and a
            // page being read while another is being drawn is contention over
            // one document either way.
            renderLock.withLock { text.value.glyphs(index) }
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
        // Only if it was ever opened: reading a book cover to cover without
        // translating anything never touches this.
        if (text.isInitialized()) runCatching { text.value.close() }
    }

    private companion object {
        const val PAGE_BACKGROUND = 0xFFFFFFFF.toInt()
    }
}
