package io.lukia.features.import.domain

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.PickedDocument
import io.lukia.features.library.domain.LibraryRepository

class ImportDocumentUseCase(
    private val documentAnalyzer: DocumentAnalyzer,
    private val libraryRepository: LibraryRepository
) {
    suspend fun execute(pickedDocument: PickedDocument): DocumentInfo {
        val documentInfo = documentAnalyzer.analyze(pickedDocument)
        libraryRepository.addDocument(documentInfo)
        return documentInfo
    }
}
