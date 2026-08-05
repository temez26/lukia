package io.lukia.features.import.domain

interface DocumentAnalyzer {
    suspend fun analyze(document: PickedDocument): DocumentInfo
}
