package io.lukia.features.reader.infrastructure

import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.PageImage
import io.lukia.features.reader.domain.PageSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFOutline
import platform.PDFKit.kPDFDisplayBoxCropBox
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIScreen

internal actual fun openPdfPages(path: String): DocumentPages = IOSDocumentPages(path)

/** Guards against an outline that is malformed into a cycle or absurdly deep. */
private const val MAX_OUTLINE_DEPTH = 8
private const val MAX_OUTLINE_ENTRIES = 5000

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
            val bounds = page.boundsForBox(kPDFDisplayBoxCropBox)
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

    override val outline: List<OutlineEntry> by lazy {
        val root = document.outlineRoot ?: return@lazy emptyList()

        buildList { appendOutline(root, level = 0) }
    }

    private fun MutableList<OutlineEntry>.appendOutline(node: PDFOutline, level: Int) {
        if (level > MAX_OUTLINE_DEPTH || size >= MAX_OUTLINE_ENTRIES) return

        for (childIndex in 0uL until node.numberOfChildren) {
            if (size >= MAX_OUTLINE_ENTRIES) return

            val child = node.childAtIndex(childIndex) ?: continue
            val title = child.label?.trim()
            val page = child.destination?.page
            val pageIndex = page?.let { document.indexForPage(it).toInt() }

            if (!title.isNullOrEmpty() && pageIndex != null && pageIndex >= 0) {
                add(OutlineEntry(title = title, pageIndex = pageIndex, level = level))
            }

            // Descended into even when the entry itself led nowhere: a heading
            // with no destination of its own still has real sections under it.
            appendOutline(child, level + 1)
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
                        forBox = kPDFDisplayBoxCropBox,
                    )

                    UIImagePNGRepresentation(thumbnail)
                        ?.toByteArray()
                        ?.decodeToImageBitmap()
                }.getOrNull()
            }
        }

    /**
     * Read by running the page's own drawing program rather than by asking
     * PDFKit, which has no notion of what is on a page - see
     * [coreGraphicsPageImages]. Serialised with everything else because it
     * reaches the same document through the page PDFKit is holding.
     */
    override suspend fun pageImages(index: Int): List<PageImage> =
        withContext(Dispatchers.Default) {
            renderLock.withLock {
                runCatching {
                    document.pageAtIndex(index.toULong())?.pageRef?.let { coreGraphicsPageImages(it) }
                }.getOrNull().orEmpty()
            }
        }

    override suspend fun pageText(index: Int): List<PageGlyph>? =
        withContext(Dispatchers.Default) {
            renderLock.withLock {
                runCatching { document.pageAtIndex(index.toULong())?.pageGlyphs() }.getOrNull()
            }
        }

    /**
     * Nothing to release: PDFKit owns the file handle and lets it go with the
     * document. Present because the contract is closeable for the platforms
     * that do hold one.
     */
    override fun close() = Unit
}
