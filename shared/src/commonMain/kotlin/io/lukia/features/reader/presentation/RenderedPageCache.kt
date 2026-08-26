package io.lukia.features.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
     * One gate per page being drawn, so that two callers asking for the same
     * page draw it once between them.
     *
     * The reader asks twice as a matter of course: each page draws the one after
     * it so that arriving there is not a wait, and that page then asks for
     * itself as it is composed. Both miss the cache - neither has finished, so
     * there is nothing in it to find - and without this the most expensive thing
     * the reader does happens twice for every page turned.
     *
     * A gate rather than a shared result: the second caller waits, and finds the
     * page in the cache the moment the first is done. If the first is cancelled
     * on the way - a page scrolled past - the second simply draws it itself,
     * which a shared result would have to be careful not to lose.
     */
    private val drawing = mutableMapOf<Int, PageGate>()

    /**
     * The page at [index] at [widthPx], drawing it with [render] only if it is
     * not already held and not already being drawn.
     */
    suspend fun page(index: Int, widthPx: Int, render: suspend () -> ImageBitmap?): ImageBitmap? {
        cached(index, widthPx)?.let { return it }

        val gate = enter(index)

        return try {
            gate.lock.withLock { drawn(index, widthPx, render) }
        } finally {
            // Uncancellable: a page scrolled away cancels this coroutine at its
            // next suspension, and the gate has to be given back all the same.
            withContext(NonCancellable) { leave(index, gate) }
        }
    }

    /** The page, drawn and kept - called with this page's gate held. */
    private suspend fun drawn(
        index: Int,
        widthPx: Int,
        render: suspend () -> ImageBitmap?,
    ): ImageBitmap? {
        // Asked again on the other side of the wait, because whoever this
        // caller queued behind will have stored it.
        cached(index, widthPx)?.let { return it }

        val rendered = render() ?: return null
        store(index, widthPx, rendered)

        return rendered
    }

    fun clear() {
        pages.clear()
        cachedWidthPx = 0
    }

    /** One page's gate, and how many callers are still holding it open. */
    private class PageGate {
        val lock = Mutex()
        var waiting = 0
    }

    private suspend fun enter(index: Int): PageGate = lock.withLock {
        drawing.getOrPut(index, ::PageGate).also { it.waiting++ }
    }

    /** Dropped once nobody is left waiting on it, so a long book keeps none. */
    private suspend fun leave(index: Int, gate: PageGate) = lock.withLock {
        gate.waiting--
        if (gate.waiting <= 0) drawing.remove(index)
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
