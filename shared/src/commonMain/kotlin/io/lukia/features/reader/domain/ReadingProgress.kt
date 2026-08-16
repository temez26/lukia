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
)

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

    suspend fun save(location: String, progress: ReadingProgress)

    /**
     * Forgets where a book was left off, for one being removed from the
     * library. A position nobody can reach a book by is state with no way out.
     */
    suspend fun forget(location: String)
}
