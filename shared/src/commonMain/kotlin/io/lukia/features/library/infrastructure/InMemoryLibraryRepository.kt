package io.lukia.features.library.infrastructure

import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

class InMemoryLibraryRepository : LibraryRepository {
    private val documents = mutableListOf<DocumentInfo>()

    // NOTE: importing the same file twice appends a second entry with an
    // identical `document.location`. Because location is the only
    // identifier we have, that also rules out using it as a Compose list
    // key. Worth deciding whether re-import should replace instead.
    override suspend fun addDocument(document: DocumentInfo) {
        documents.add(document)
    }

    override suspend fun getDocuments(): List<DocumentInfo> {
        return documents.toList()
    }

    override suspend fun getDocument(location: String): DocumentInfo? {
        return documents.firstOrNull { it.document.location == location }
    }
}
