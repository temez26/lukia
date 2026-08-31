package io.lukia.features.library.domain

import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo

interface LibraryRepository {
    /**
     * Adds [document], replacing any entry already stored under the same
     * `document.location`. Location is the library's identity for a document,
     * so importing the same file twice refreshes it rather than duplicating it.
     */
    suspend fun addDocument(document: DocumentInfo)

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

    /**
     * The locations of every document in the library, and nothing else about
     * them. What an import needs to know which files it may leave alone, without
     * reading back the cover art of the whole library to find out.
     */
    suspend fun knownLocations(): Set<String>

    /**
     * Sets the favourite flag on the document stored at [location].
     *
     * A no-op for an unknown location: the caller is a UI event on a list that
     * may already have moved on, and there is nothing useful for it to do with
     * a failure.
     */
    suspend fun setFavorite(location: String, isFavorite: Boolean)

    /**
     * Drops the document at [location] from the library. Never touches the
     * underlying file. A no-op for an unknown location, for the same reason as
     * [setFavorite].
     */
    suspend fun removeDocument(location: String)
}
