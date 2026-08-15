package io.lukia.features.reader.infrastructure

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.PageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

internal actual fun openPdfPages(path: String): DocumentPages = PdfBoxDocumentPages(path)

private class PdfBoxDocumentPages(path: String) : DocumentPages {

    private val document: PDDocument = Loader.loadPDF(File(path))
    private val renderer = PDFRenderer(document)

    /**
     * PDFBox shares mutable state across a document's pages, so two pages
     * rendering at once corrupt each other's output. The reader asks for
     * several pages at a time as it scrolls, so this is the common case rather
     * than an edge one.
     */
    private val renderLock = Mutex()

    override val pageSizes: List<PageSize> = document.pages.map { page ->
        val box = page.cropBox

        // A quarter-turn page reports its box unrotated, but renders rotated.
        if (page.rotation % 180 == 0) {
            PageSize(width = box.width, height = box.height)
        } else {
            PageSize(width = box.height, height = box.width)
        }
    }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            renderLock.withLock {
                runCatching {
                    val scale = targetWidthPx / pageSizes[index].width
                    renderer.renderImage(index, scale, ImageType.RGB).toComposeImageBitmap()
                }.getOrNull()
            }
        }

    override fun close() {
        document.close()
    }
}
