package io.lukia.core.domain.document

/**
 * What books this device has, for whoever only needs to look one up.
 *
 * The reader opens a book by location and the statistics screen counts what is
 * on the shelf; neither puts anything there or takes anything away. Kept apart
 * from the library's own `LibraryRepository`, which extends this with the four
 * operations that change what is on the shelf, so that asking what is on it does
 * not come with the ability to empty it.
 *
 * Here rather than in the library feature for the same reason
 * [io.lukia.core.domain.reading.ReadingProgressRepository] is here: three
 * features read it, it is written entirely in types this package already owns,
 * and a port two features share is not one feature's property. The implementation
 * stays where the storage is - see `features/library/infrastructure`.
 */
interface DocumentCatalogue {

    /**
     * Every book on the shelf, each of them without its artwork - see
     * [DocumentInfo.coverImage], which is null for every entry this returns.
     *
     * The artwork is left behind because of what it weighs: a cover is around a
     * hundred kilobytes, and a shelf of a few thousand books is a few hundred
     * megabytes of them, held for as long as the library is on screen. Whatever
     * draws one asks for it by name instead - see [coverFor] - which bounds what
     * is in memory by what the reader can see rather than by how much they own.
     */
    suspend fun getDocuments(): List<DocumentInfo>

    /** One book, artwork included. */
    suspend fun getDocument(location: String): DocumentInfo?

    /**
     * The artwork of the book at [location], or null for a book that has none -
     * which is most plain text, and any file whose cover could not be read.
     */
    suspend fun coverFor(location: String): CoverImage?
}
