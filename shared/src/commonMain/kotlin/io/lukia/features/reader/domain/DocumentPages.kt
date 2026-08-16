package io.lukia.features.reader.domain

import androidx.compose.ui.graphics.ImageBitmap

/**
 * An open fixed-layout document, page by page.
 *
 * A PDF carries its own layout, so the only way to show it as its author laid it
 * out is to let the platform draw each page. Nothing here re-flows or
 * re-typesets; the reader is only deciding how wide to draw.
 *
 * Implementations hold an open handle on the file and are [AutoCloseable] for
 * that reason - whoever opens one owns closing it.
 *
 * Rendering yields an [ImageBitmap] rather than encoded bytes on purpose. A
 * rendered page *is* a raster image, and routing it through PNG for the sake of
 * keeping a Compose type out of this layer would put an encode and a decode on
 * the path of every page the reader scrolls past.
 */
interface DocumentPages : AutoCloseable {

    /**
     * Every page's shape, in order, read when the document is opened.
     *
     * Read up front rather than per page because the reader needs all of them
     * at once: a scrolling list can only size its scrollbar, and only keep the
     * position under the reader's thumb steady, if it knows how tall the pages
     * it has not drawn yet are. Asking the renderer for them one at a time would
     * put that work on the main thread mid-scroll.
     */
    val pageSizes: List<PageSize>

    val pageCount: Int
        get() = pageSizes.size

    /**
     * The document's own chapter list, flattened and in reading order, read when
     * the document is opened.
     *
     * Three states, and the difference matters to what the reader is told:
     * a list of entries, empty when the document simply has no table of
     * contents, and null when this platform cannot read one at all. Reporting
     * "no chapters" for a book full of them would send someone looking for a bug
     * in their file.
     */
    val outline: List<OutlineEntry>?

    /**
     * Draws page [index] to fit [targetWidthPx], or returns null if that page
     * cannot be drawn - one damaged page should cost the reader that page, not
     * the whole book.
     */
    suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap?
}
