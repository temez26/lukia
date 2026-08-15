package io.lukia.features.reader.infrastructure

import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.PageSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.PDFKit.PDFDisplayBox
import platform.PDFKit.PDFDocument
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIScreen
import platform.posix.memcpy

internal actual fun openPdfPages(path: String): DocumentPages = IOSDocumentPages(path)

/**
 * PDFKit rather than raw CoreGraphics: it renders a page to a requested size in
 * one call, which is the whole of what the reader needs, and keeps this file
 * free of manual bitmap contexts.
 */
@OptIn(ExperimentalForeignApi::class)
private class IOSDocumentPages(path: String) : DocumentPages {

    private val document: PDFDocument = PDFDocument(uRL = NSURL.fileURLWithPath(path))

    /** PDFKit does not promise a document is safe to use from two threads. */
    private val renderLock = Mutex()

    override val pageSizes: List<PageSize> = List(document.pageCount.toInt()) { index ->
        val page = document.pageAtIndex(index.toULong())

        if (page == null) {
            PageSize(width = 0f, height = 0f)
        } else {
            val bounds = page.boundsForBox(PDFDisplayBox.PDFDisplayBoxCropBox)
            val width = bounds.useContents { size.width }.toFloat()
            val height = bounds.useContents { size.height }.toFloat()

            // A quarter-turn page reports its box unrotated, but renders rotated.
            if (page.rotation % 180 == 0L) {
                PageSize(width = width, height = height)
            } else {
                PageSize(width = height, height = width)
            }
        }
    }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            renderLock.withLock {
                runCatching {
                    val page = document.pageAtIndex(index.toULong()) ?: return@runCatching null
                    val size = pageSizes[index]

                    // PDFKit draws in points at the screen's scale, so asking for
                    // a size in pixels would come back two or three times larger
                    // than the reader asked for.
                    val scale = UIScreen.mainScreen.scale
                    val widthPoints = targetWidthPx / scale
                    val heightPoints = widthPoints * size.height / size.width

                    val thumbnail = page.thumbnailOfSize(
                        CGSizeMake(widthPoints, heightPoints),
                        forBox = PDFDisplayBox.PDFDisplayBoxCropBox
                    )

                    UIImagePNGRepresentation(thumbnail)
                        ?.toByteArray()
                        ?.decodeToImageBitmap()
                }.getOrNull()
            }
        }

    /**
     * Nothing to release: PDFKit owns the file handle and lets it go with the
     * document. Present because the contract is closeable for the platforms
     * that do hold one.
     */
    override fun close() = Unit

    private fun NSData.toByteArray(): ByteArray {
        val result = ByteArray(length.toInt())

        if (result.isNotEmpty()) {
            result.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }

        return result
    }
}
