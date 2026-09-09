package io.lukia.features.reader.presentation.rendering

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
 * two on a zoomed-in desktop, and about 64 MB either way.
 *
 * The budget is the whole of it, with no floor of pages under it. There used to
 * be one, of three, so the page being read could not be evicted - but a count
 * cannot express that where a pixel budget is the point: three sheets of a PDF
 * zoomed in on a desktop are seventy megabytes, over this budget rather than
 * under, and on a phone that floor was the ceiling. What protects the page being
 * read is that the last one stored always survives, and that is the one just
 * asked for.
 */
private const val MAX_CACHED_PIXELS = 16_000_000

/**
 * The pages drawn so far, so that scrolling back over a spread does not draw
 * them again.
 *
 * Least-recently-used, keyed by page index. Every entry is drawn at the same
 * width; when the window changes width the whole cache is stale at once, so it
 * is dropped rather than tracked per page.
 */
internal class RenderedPageCache {

    /**
     * What is held, behind a reference that is replaced rather than emptied -
     * see [clear].
     *
     * Read once into a local wherever it is used, so that a swap part way
     * through leaves that use working on the map it started with rather than on
     * half of two.
     */
    private var kept = KeptPages()

    private val lock = Mutex()

    /**
     * One gate per page being drawn, so that two callers asking for the same
     * page draw it once between them.
     *
     * The reader asks twice as a matter of course: each page draws the one after
     * it so arriving is not a wait, and that page then asks for itself as it is
     * composed. Both miss the cache, since neither has finished, so without this
     * the most expensive thing the reader does happens twice per page turned.
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

    /**
     * Forgets every page drawn for the book that is being put away.
     *
     * The one thing here that does not run under [lock], because the two places
     * that call it cannot wait: a book being re-opened the other way, and a
     * reader leaving one, both settle on the thread that draws. So what it does
     * instead is swap the whole map for an empty one, which is a single write.
     * A page part way through being stored on another thread finishes into the
     * map it started with, which by then is nobody's - where emptying the map
     * under it was two threads writing to one map, and a crash on any platform
     * that says so.
     */
    fun clear() {
        kept = KeptPages()
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
        val held = kept

        if (held.widthPx != widthPx) {
            held.pages.clear()
            held.widthPx = widthPx
            return@withLock null
        }

        // Removing and re-inserting moves the entry to the end of the map, which
        // is what makes eviction least-recently-used rather than oldest-first.
        held.pages.remove(index)?.also { held.pages[index] = it }
    }

    private suspend fun store(index: Int, widthPx: Int, page: ImageBitmap) = lock.withLock {
        val held = kept

        // The window resized while this page was rendering, so it is already the
        // wrong size for the cache it would land in.
        if (held.widthPx != widthPx) return@withLock

        held.pages[index] = page

        // Least-recently-used out first, until the budget is met. The page just
        // asked for is the one thing certainly about to be drawn, so it is the
        // one that stays - counted in pixels rather than in pages, because a
        // floor of three pages is above the whole budget once each of them is a
        // zoomed-in sheet of twenty megabytes.
        while (held.pages.size > 1 && held.pixels() > MAX_CACHED_PIXELS) {
            held.pages.remove(held.pages.keys.first())
        }
    }
}

/** One book's drawn pages, and the width every one of them was drawn at. */
private class KeptPages {

    val pages = LinkedHashMap<Int, ImageBitmap>()
    var widthPx = 0

    fun pixels(): Long = pages.values.sumOf { it.width.toLong() * it.height.toLong() }
}
