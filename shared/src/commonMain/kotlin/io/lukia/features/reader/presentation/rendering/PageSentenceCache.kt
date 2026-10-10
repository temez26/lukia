package io.lukia.features.reader.presentation.rendering

import io.lukia.features.reader.domain.page.PageSentence
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How much parsed page is kept, counted in the rectangles held rather than in
 * pages.
 *
 * A page is not a size. This holds a rectangle per line of every sentence and
 * another per word, and how many depends entirely on what is written: a novel's
 * page is a few hundred, a two-column paper with footnotes several thousand. A
 * budget in pages set for the novel is the wrong bound on the paper; set for the
 * paper it holds a whole chapter for a reader who has left it.
 *
 * Rectangles rather than bytes because a rectangle is the unit this can count -
 * four floats and an object header, near enough sixty bytes with its list slot.
 * So the budget below is around six megabytes of geometry, the same order as the
 * covers and a quarter of what the drawn pages are allowed.
 *
 * Kept generous in the ordinary case: a hundred thousand rectangles is still
 * fifty pages of a novel, so a reader moving back and forth over a spread never
 * waits for the same page to be parsed twice.
 */
private const val MAX_CACHED_QUADS = 100_000

/**
 * The last page read is never evicted, whatever its size.
 *
 * A page dense enough to be over the whole budget on its own is exactly the page
 * that must not be parsed again on the next tap - and it is the one certainly
 * about to be asked about, because it is the one being read. Same reasoning as
 * the drawn pages, which keep their newest entry for the same reason.
 */
private const val MIN_CACHED_PAGES = 1

/**
 * The sentences of the pages most recently looked at.
 *
 * Reading a page's text means parsing it out of the file, which is far too much
 * to do again every time somebody taps the same page twice - and a reader
 * working through a page taps it several times.
 *
 * Least-recently-used, like the drawn pages, and bounded by weight for the same
 * reason: one page of sentences is small, but it is a rectangle per line of
 * every sentence on it and another per word, and a long document read end to end
 * is hundreds of pages of them held for a reader who is no longer anywhere near
 * any of it.
 */
internal class PageSentenceCache {

    /**
     * Replaced rather than emptied when the book goes - see [clear] - so it is
     * read once into a local wherever it is used.
     */
    private var kept = KeptSentences()

    private val lock = Mutex()

    /**
     * What is written on page [index], parsing it with [parse] only if it has
     * not been read already.
     */
    suspend fun sentencesOn(index: Int, parse: suspend () -> List<PageSentence>): List<PageSentence> {
        remembered(index)?.let { return it }

        val sentences = parse()

        return lock.withLock {
            val held = kept

            held.pages.getOrPut(index) { sentences }.also {
                // Least-recently-used out first, until the budget is met - and
                // never the page just read, which is the one certainly about to
                // be asked about again.
                while (held.pages.size > MIN_CACHED_PAGES && held.quads() > MAX_CACHED_QUADS) {
                    held.pages.remove(held.pages.keys.first())
                }
            }
        }
    }

    /**
     * Forgets the pages read out of the book being put away.
     *
     * Swapped rather than emptied, and for the reason
     * [RenderedPageCache.clear] is: this is called from the thread that draws,
     * which cannot wait on the lock, while a page may be part way through being
     * parsed on another. That page finishes into the map it started with, and
     * that map is by then nobody's.
     */
    fun clear() {
        kept = KeptSentences()
    }

    /**
     * Removed and re-inserted so that reading a page moves it to the end, which
     * is what makes eviction least-recently-used rather than oldest-first.
     */
    private suspend fun remembered(index: Int): List<PageSentence>? =
        lock.withLock {
            val pages = kept.pages

            pages.remove(index)?.also { pages[index] = it }
        }
}

/** One book's parsed pages, and what they weigh. */
private class KeptSentences {

    val pages = LinkedHashMap<Int, List<PageSentence>>()

    /**
     * Counted rather than tracked as pages come and go.
     *
     * A page is stored once and read many times, and only the storing asks this
     * - so the walk happens on the rare path, while the common one stays a map
     * lookup. Two dozen pages of a few thousand rectangles is a sum over a few
     * hundred lists, which is nothing beside the parse that just produced them.
     */
    fun quads(): Long = pages.values.sumOf { page ->
        page.sumOf { sentence ->
            sentence.quads.size.toLong() + sentence.words.sumOf { it.quads.size.toLong() }
        }
    }
}
