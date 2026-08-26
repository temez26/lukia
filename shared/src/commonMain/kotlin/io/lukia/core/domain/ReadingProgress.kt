package io.lukia.core.domain

/**
 * How far into a book someone got.
 *
 * [pageCount] travels with [pageIndex] rather than being looked up from the
 * document, so that a library can say how far through a book its reader is
 * without opening the file to find out how long it is.
 *
 * For a fixed-layout document the two are pages the file itself has. For a
 * reflowable one they are pages the reader set, in the window and at the type
 * size it was last read at - so the pair means something together and neither
 * half means much alone. That is why they are stored and replaced together, and
 * why every reading of them here is a ratio rather than a count.
 */
data class ReadingProgress(
    val pageIndex: Int,
    val pageCount: Int,
    /**
     * Which of two books was read more recently, as a count of writes rather
     * than a time - see `ReadingPosition.sq` for why it is not a clock.
     *
     * Stamped by the store, so whatever a caller puts here on the way in is
     * ignored; it is only ever worth reading. Zero for a position stored before
     * the app began recording this at all.
     */
    val readAt: Long = 0L,
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

    /**
     * The page the reader is on as they would be told it, counting from one.
     *
     * Clamped to the book for the same reason [fraction] is: a document can be
     * replaced by a shorter one under the same name, and a reflowable book set
     * larger since it was last read is shorter than the position stored against
     * it - neither is a reason to say "page 400 of 380".
     */
    val pageNumber: Int?
        get() = if (pageCount <= 0) null else (pageIndex + 1).coerceIn(1, pageCount)

    /**
     * True once the reader has reached the last page.
     *
     * Here rather than on either screen that asks, so that a book called
     * finished on its detail screen is the same book the library files under
     * Finished.
     */
    val isFinished: Boolean
        get() = percent == 100
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

    /** Stamps [ReadingProgress.readAt], so the saved position is the newest there is. */
    suspend fun save(location: String, progress: ReadingProgress)

    /**
     * Forgets where a book was left off, for one being removed from the
     * library. A position nobody can reach a book by is state with no way out.
     */
    suspend fun forget(location: String)
}
