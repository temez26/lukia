package io.lukia.features.import.application.usecases

import io.lukia.features.import.domain.entities.ImportedDocument
import io.lukia.features.import.domain.repositories.DocumentImportRepository

class ImportDocumentUseCase(private val repository: DocumentImportRepository) {
    suspend operator fun invoke(): ImportedDocument {
        return repository.importDocument()
    }
}