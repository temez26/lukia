package io.lukia.features.library.domain

import io.lukia.core.domain.document.DocumentCatalogue
import io.lukia.core.domain.document.DocumentInfo

/**
 * The shelf, and what may be done to it.
 *
 * Reading it is [DocumentCatalogue], which the reader and the statistics screen
 * take instead of this - they look a book up and nothing more. What is added
 * here is everything that changes what is on the shelf, which is the library's
 * own business and stays in the library's own package.
 */
interface LibraryRepository : DocumentCatalogue {
    /**
     * Adds [document], replacing any entry already stored under the same
     * `document.location`. Location is the library's identity for a document,
     * so importing the same file twice refreshes it rather than duplicating it.
     */
    suspend fun addDocument(document: DocumentInfo)

    /**
     * Adds [batch] as one piece of work, with the same meaning per document as
     * [addDocument].
     *
     * Here because a folder import is thousands of books, and storing them one
     * at a time is thousands of separate commits - each one a round trip the
     * store has to make durable before the next may begin. A store that can do
     * better does; one that cannot is no worse off than before, which is what
     * the default below is.
     *
     * All or nothing is deliberately *not* promised. The caller hands these over
     * in chunks precisely so that stopping an import keeps what has already
     * arrived, and a store free to commit the batch in pieces is free to honour
     * that.
     */
    suspend fun addDocuments(batch: List<DocumentInfo>) {
        batch.forEach { addDocument(it) }
    }

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
