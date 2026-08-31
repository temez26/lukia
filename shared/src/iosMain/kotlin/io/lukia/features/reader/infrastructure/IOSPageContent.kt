package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.PageContent
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument

/**
 * Opens the PDF at [path] to be read rather than drawn - see [PageContent].
 *
 * A handle of its own, holding nothing the reader needs: no page measurements
 * and no table of contents. That is what makes it cheap enough to open several
 * of, which is what a conversion does.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun openPdfPageContent(path: String): PageContent =
    IOSPageContent(PDFDocument(uRL = NSURL.fileURLWithPath(path)), Mutex())

/**
 * What is on a PDF's pages on iOS: the words from PDFKit, the pictures from
 * CoreGraphics underneath it.
 *
 * Takes the document rather than opening one, because it is used both ways: the
 * open book reads through the same document it draws from, and a conversion
 * opens documents of its own to read several pages at once. [lock] is shared
 * with whatever else touches the document, since PDFKit does not promise one is
 * safe to use from two threads.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IOSPageContent(
    private val document: PDFDocument,
    private val lock: Mutex,
) : PageContent {

    /**
     * The pictures this document has already given up, kept for as long as it
     * is open - see [CoreGraphicsFigureBytes]. Held here because a handle is
     * what a repeated picture repeats within.
     */
    private val figures = CoreGraphicsFigureBytes()

    override suspend fun pageText(index: Int): List<PageGlyph>? =
        withContext(Dispatchers.Default) {
            lock.withLock {
                runCatching { document.pageAtIndex(index.toULong())?.pageGlyphs() }.getOrNull()
            }
        }

    /**
     * Read by running the page's own drawing program rather than by asking
     * PDFKit, which has no notion of what is on a page - see
     * [coreGraphicsPageImages].
     */
    override suspend fun pageImages(index: Int): List<PageImage> =
        withContext(Dispatchers.Default) {
            lock.withLock {
                runCatching {
                    document.pageAtIndex(index.toULong())
                        ?.pageRef
                        ?.let { coreGraphicsPageImages(it, figures) }
                }.getOrNull().orEmpty()
            }
        }

    /** Nothing to release: PDFKit owns the file handle and lets it go with the document. */
    override fun close() = Unit
}
