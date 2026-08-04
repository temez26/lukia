package io.lukia.features.import.infrastructure

interface DocumentAnalyzer {

    suspend fun analyze(
        document: StoredDocument
    ): DocumentMetadata
}