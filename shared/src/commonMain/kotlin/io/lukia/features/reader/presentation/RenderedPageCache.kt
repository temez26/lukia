package io.lukia.features.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How much drawn page is kept in memory, counted in pixels rather than pages.
 *
 * A page is four bytes per pixel, and a page's size varies enormously - a phone
 * page is a few megabytes where a zoomed desktop page is twenty. Counting pages
 * would mean choosing a number safe for the largest, which then holds far too
 * little on the smallest. This budget adapts: roughly a dozen pages on a phone,
 * three on a zoomed-in desktop, and about 64 MB either way.
 */
private const val MAX_CACHED_PIXELS = 16_000_000

/** Kept regardless of the budget, so the page being read is never evicted. */
private const val MIN_CACHED_PAGES = 3

/**
 * The pages drawn so far, so that scrolling back over a spread does not draw
 * them again.
 *
 * Least-recently-used, keyed by page index. Every entry is drawn at the same
 * width; when the window changes width the whole cache is stale at once, so it
 * is dropped rather than tracked per page.
 */
internal class RenderedPageCache {

    private val pages = LinkedHashMap<Int, ImageBitmap>()
    private val lock = Mutex()
    private var cachedWidthPx = 0

    /**
     * The page at [index] at [widthPx], drawing it with [render] only if it is
     * not already held.
     */
    suspend fun page(index: Int, widthPx: Int, render: suspend () -> ImageBitmap?): ImageBitmap? {
        cached(index, widthPx)?.let { return it }

        val rendered = render() ?: return null
        store(index, widthPx, rendered)

        return rendered
    }

    fun clear() {
        pages.clear()
        cachedWidthPx = 0
    }

    private suspend fun cached(index: Int, widthPx: Int): ImageBitmap? = lock.withLock {
        if (cachedWidthPx != widthPx) {
            pages.clear()
            cachedWidthPx = widthPx
            return@withLock null
        }

        // Removing and re-inserting moves the entry to the end of the map, which
        // is what makes eviction least-recently-used rather than oldest-first.
        pages.remove(index)?.also { pages[index] = it }
    }

    private suspend fun store(index: Int, widthPx: Int, page: ImageBitmap) = lock.withLock {
        // The window resized while this page was rendering, so it is already the
        // wrong size for the cache it would land in.
        if (cachedWidthPx != widthPx) return@withLock

        pages[index] = page

        // Least-recently-used out first, until the budget is met - but never
        // below the handful around the page being read.
        while (pages.size > MIN_CACHED_PAGES && cachedPixels() > MAX_CACHED_PIXELS) {
            pages.remove(pages.keys.first())
        }
    }

    private fun cachedPixels(): Long =
        pages.values.sumOf { it.width.toLong() * it.height.toLong() }
}
