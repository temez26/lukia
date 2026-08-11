package io.lukia.features.library.domain

import io.lukia.features.import.domain.DocumentInfo

class GetLibraryDocumentsUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(): List<DocumentInfo> {
        return repository.getDocuments()
    }
}
