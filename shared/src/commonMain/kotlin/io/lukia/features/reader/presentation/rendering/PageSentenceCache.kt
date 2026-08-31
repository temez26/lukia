package io.lukia.features.reader.presentation.rendering

import io.lukia.features.reader.domain.page.PageSentence
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How many pages' worth of parsed sentences are kept.
 *
 * Counted in pages rather than in bytes, unlike the drawn ones: what this holds
 * is text and rectangles, whose size follows how much is written on a page
 * rather than how large the page is drawn. Comfortably more than fits on any
 * screen, so a reader moving back and forth over a spread never waits for the
 * same page to be parsed twice.
 */
private const val MAX_CACHED_PAGES = 24

/**
 * The sentences of the pages most recently looked at.
 *
 * Reading a page's text means parsing it out of the file, which is far too much
 * to do again every time somebody taps the same page twice - and a reader
 * working through a page taps it several times.
 *
 * Least-recently-used, like the drawn pages, and for the same reason rather than
 * the same weight: one page of sentences is small, but it is a rectangle per
 * line of every sentence on it, and a long document read end to end is hundreds
 * of pages of them held for a reader who is no longer anywhere near any of it.
 */
internal class PageSentenceCache {

    private val pages = LinkedHashMap<Int, List<PageSentence>>()
    private val lock = Mutex()

    /**
     * What is written on page [index], parsing it with [parse] only if it has
     * not been read already.
     */
    suspend fun sentencesOn(index: Int, parse: suspend () -> List<PageSentence>): List<PageSentence> {
        remembered(index)?.let { return it }

        val sentences = parse()

        return lock.withLock {
            pages.getOrPut(index) { sentences }.also {
                while (pages.size > MAX_CACHED_PAGES) {
                    pages.remove(pages.keys.first())
                }
            }
        }
    }

    fun clear() {
        pages.clear()
    }

    /**
     * Removed and re-inserted so that reading a page moves it to the end, which
     * is what makes eviction least-recently-used rather than oldest-first.
     */
    private suspend fun remembered(index: Int): List<PageSentence>? =
        lock.withLock {
            pages.remove(index)?.also { pages[index] = it }
        }
}
