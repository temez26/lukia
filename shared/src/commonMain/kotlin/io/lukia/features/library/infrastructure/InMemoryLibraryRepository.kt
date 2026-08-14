package io.lukia.features.library.infrastructure

import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

class InMemoryLibraryRepository : LibraryRepository {
    // Keyed by location rather than held in a list: re-importing a file - or
    // rescanning a library folder - then refreshes the existing entry instead
    // of appending a duplicate. mutableMapOf keeps insertion order, so a
    // refreshed document also keeps its place in the library.
    private val documentsByLocation = mutableMapOf<String, DocumentInfo>()

    override suspend fun addDocument(document: DocumentInfo) {
        documentsByLocation[document.document.location] = document
    }

    override suspend fun getDocuments(): List<DocumentInfo> {
        return documentsByLocation.values.toList()
    }

    override suspend fun getDocument(location: String): DocumentInfo? {
        return documentsByLocation[location]
    }
}
