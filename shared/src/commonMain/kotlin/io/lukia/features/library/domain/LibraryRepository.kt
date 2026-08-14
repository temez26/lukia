package io.lukia.features.library.domain

import io.lukia.core.domain.DocumentInfo

interface LibraryRepository {
    suspend fun addDocument(document: DocumentInfo)
    suspend fun getDocuments(): List<DocumentInfo>
    suspend fun getDocument(location: String): DocumentInfo?
}
