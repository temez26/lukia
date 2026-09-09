package io.lukia.features.reader.infrastructure

import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.core.infrastructure.toByteArray
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

/**
 * The document is opened here rather than inside the pages, so that a file
 * PDFKit will not read comes back as null instead of as a document with no
 * pages in it - see the expect.
 */
internal actual fun openPdfPages(path: String): DocumentPages? {
    // Caught rather than tested for: PDFKit answers nil for a file it will not
    // read, and the initialiser is bound here as one that cannot, so nil
    // arrives as a throw rather than as a null to check.
    val document = runCatching { PDFDocument(uRL = NSURL.fileURLWithPath(path)) }.getOrNull()
        ?: return null

    // Nothing to give back by hand if this throws: a PDFKit document is
    // reference counted, which is why closing these pages is also nothing.
    return runCatching { IOSDocumentPages(document) }.getOrNull()
}

/** Guards against an outline that is malformed into a cycle or absurdly deep. */
private const val MAX_OUTLINE_DEPTH = 8
private const val MAX_OUTLINE_ENTRIES = 5000

/**
 * PDFKit rather than raw CoreGraphics: it renders a page to a requested size in
 * one call, which is the whole of what the reader needs, and keeps this file
 * free of manual bitmap contexts.
 */
@OptIn(ExperimentalForeignApi::class)
private class IOSDocumentPages(private val document: PDFDocument) : DocumentPages {

    /** PDFKit does not promise a document is safe to use from two threads. */
    private val renderLock = Mutex()

    /**
     * The reading half of this document, over the same handle and behind the
     * same lock - see [IOSPageContent].
     */
    private val content = IOSPageContent(document, renderLock)

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

    /**
     * Read as the document is opened, which is [DocumentPages]'s contract and
     * here the only way it can be safe: the walk reaches into the same
     * [PDFDocument] [renderPage] draws from, and PDFKit does not promise a
     * document is thread-safe. A plain `val` cannot take [renderLock], so its
     * only protection is finishing before anybody else has the document -
     * `PdfDocumentPages.jvm.kt` is in the same position for its own reason.
     *
     * Nobody waits longer for it: opening already runs off the main thread, and
     * both ways out of here read the outline anyway.
     */
    override val outline: List<OutlineEntry>? = readOutline()

    /**
     * Null only for an outline that could not be walked at all, which is the
     * "this platform cannot read one" of [DocumentPages.outline]. A file with
     * no table of contents is an empty list, as it was before.
     *
     * Both have to be answers rather than throws now that this runs while the
     * document is being opened: a malformed chapter list used to cost the
     * reader the chapter list, and must not start costing them the book.
     */
    private fun readOutline(): List<OutlineEntry>? {
        val root = document.outlineRoot ?: return emptyList()

        return runCatching { buildList { appendOutline(root, level = 0) } }.getOrNull()
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
        withContext(Dispatchers.IO) {
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

    override suspend fun pageImages(index: Int): List<PageImage> = content.pageImages(index)

    override suspend fun pageText(index: Int): List<PageGlyph>? = content.pageText(index)

    /**
     * Nothing to release: PDFKit owns the file handle and lets it go with the
     * document. Present because the contract is closeable for the platforms
     * that do hold one.
     */
    override fun close() = Unit
}
