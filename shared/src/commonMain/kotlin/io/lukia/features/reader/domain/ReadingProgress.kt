package io.lukia.features.reader.domain

/**
 * How far into a book someone got.
 *
 * [pageCount] travels with [pageIndex] rather than being looked up from the
 * document, so that a library can say how far through a book its reader is
 * without opening the file to find out how long it is.
 */
data class ReadingProgress(
    val pageIndex: Int,
    val pageCount: Int
) {
    /**
     * How far through, from 0 to 1, or null for a document with no length to
     * measure against.
     *
     * Counted from the page the reader is *on* rather than the pages behind
     * them: a book open at page one is a page into it, and a bar showing nothing
     * at all on a book the reader is looking at is a claim they can see is
     * wrong. A book on its last page is full.
     *
     * Lives here rather than on either of the two screens that draw it, so the
     * bar on a library card and the percentage on the detail screen can never
     * disagree about the same book.
     */
    val fraction: Float?
        get() {
            if (pageCount <= 0) return null

            val position = (pageIndex + 1).coerceAtMost(pageCount)
            return position.toFloat() / pageCount
        }

    /**
     * The same thing as a whole percentage, floored but never to zero - a book
     * one page into a long one is 1% read, not 0%.
     */
    val percent: Int?
        get() = fraction?.let { (it * 100).toInt().coerceIn(1, 100) }
}

/**
 * Where each book was left off.
 *
 * A book is addressed by the same location the library keys on, so a position
 * can be stored for a document the library has never been told about - the
 * reader can open a file the library only hears about afterwards.
 */
interface ReadingProgressRepository {

    /** Null when the book has never been opened, which is not the same as page one. */
    suspend fun progressFor(location: String): ReadingProgress?

    /**
     * Every position there is, keyed by the location it belongs to.
     *
     * For the library grid, which shows how far through each book its reader is
     * and would otherwise ask that once per cell. Books never opened are simply
     * absent rather than present with a zero.
     */
    suspend fun allProgress(): Map<String, ReadingProgress>

    suspend fun save(location: String, progress: ReadingProgress)

    /**
     * Forgets where a book was left off, for one being removed from the
     * library. A position nobody can reach a book by is state with no way out.
     */
    suspend fun forget(location: String)
}
