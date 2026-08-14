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
}
