package io.lukia.features.library.domain

import io.lukia.core.domain.DocumentInfo

interface LibraryRepository {
    /**
     * Adds [document], replacing any entry already stored under the same
     * `document.location`. Location is the library's identity for a document,
     * so importing the same file twice refreshes it rather than duplicating it.
     */
    suspend fun addDocument(document: DocumentInfo)
    suspend fun getDocuments(): List<DocumentInfo>
    suspend fun getDocument(location: String): DocumentInfo?

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
