package io.lukia.features.reader.infrastructure

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.PageImage
import io.lukia.features.reader.domain.PageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

/** Guards against an outline that is malformed into a cycle or absurdly deep. */
private const val MAX_OUTLINE_DEPTH = 8
private const val MAX_OUTLINE_ENTRIES = 5000

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

    /**
     * Destinations point at a page's dictionary, and the same page reached two
     * ways is not the same [PDPage] instance - so the lookup is built once over
     * the dictionaries rather than searching the page tree per entry, which on a
     * long book with a full table of contents is the difference between opening
     * instantly and visibly waiting.
     */
    private val pageIndexByDictionary: Map<COSDictionary, Int> =
        document.pages.withIndex().associate { (index, page) -> page.cosObject to index }

    /**
     * Read as the document is opened, which is the contract [DocumentPages]
     * states - and here it is also the only way it can be safe.
     *
     * The walk parses objects out of the same PDFBox document [renderPage]
     * draws from, and PDFBox shares mutable state across the two. Everything
     * else that touches the document once it is open takes [renderLock]; a
     * plain `val` cannot, so the only lock available to it is being finished
     * before anybody else has the document at all. Left until something asked,
     * this ran on a dispatcher of its own beside a page being drawn - one
     * document, two threads, and the corruption the lock exists to prevent.
     *
     * Nobody waits longer for it. Opening already runs off the main thread, and
     * the outline is read on both ways out of here anyway - the text path asks
     * for it, and the reader reads it beside the open book. What this walks is
     * structure the open has already parsed, not the file: the scan that would
     * be too much to do up front belongs to Android, whose renderer has no
     * outline of its own, and stays lazy there.
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
        val root = runCatching { document.documentCatalog.documentOutline }.getOrNull()
            ?: return emptyList()

        return runCatching { buildList { appendOutline(root, level = 0) } }.getOrNull()
    }

    private fun MutableList<OutlineEntry>.appendOutline(node: PDOutlineNode, level: Int) {
        if (level > MAX_OUTLINE_DEPTH || size >= MAX_OUTLINE_ENTRIES) return

        node.children().forEach { item ->
            if (size >= MAX_OUTLINE_ENTRIES) return

            val title = runCatching { item.title }.getOrNull()?.trim()
            val pageIndex = runCatching { item.findDestinationPage(document) }
                .getOrNull()
                ?.let { pageIndexByDictionary[it.cosObject] }

            if (!title.isNullOrEmpty() && pageIndex != null) {
                add(OutlineEntry(title = title, pageIndex = pageIndex, level = level))
            }

            // Descended into even when the entry itself led nowhere: a heading
            // with no destination of its own still has real sections under it.
            appendOutline(item, level + 1)
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

    /**
     * The reading half of this document, over the same handle and behind the
     * same lock - see [PdfBoxPageContent]. Not owned: closing the document is
     * this class's job, and doing it twice would be one close too many.
     */
    private val content = PdfBoxPageContent(document, renderLock, owned = false)

    override suspend fun pageText(index: Int): List<PageGlyph>? = content.pageText(index)

    override suspend fun pageImages(index: Int): List<PageImage> = content.pageImages(index)

    override fun close() {
        document.close()
    }
}
