package io.lukia.features.library.infrastructure

import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

class InMemoryLibraryRepository : LibraryRepository {
    private val documents = mutableListOf<DocumentInfo>()

    override suspend fun addDocument(document: DocumentInfo) {
        documents.add(document)
    }

    val allDocuments: List<DocumentInfo>
        get() = documents.toList()

    override suspend fun getDocuments(): List<DocumentInfo> {
        return documents.toList()
    }
}
