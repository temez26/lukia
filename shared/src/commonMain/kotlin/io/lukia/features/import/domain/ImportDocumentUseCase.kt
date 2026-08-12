package io.lukia.features.import.domain

import io.lukia.features.library.domain.LibraryRepository

class ImportDocumentUseCase(
    private val documentAnalyzer: DocumentAnalyzer,
    private val libraryRepository: LibraryRepository
) {
    suspend fun execute(pickedDocument: PickedDocument): Result<DocumentInfo> {
        val documentInfo = documentAnalyzer.analyze(pickedDocument)

        if (documentInfo.type == DocumentType.UNKNOWN) {
            return Result.failure(Exception("Unsupported file type"))
        }

        libraryRepository.addDocument(documentInfo)
        return Result.success(documentInfo)
    }
}