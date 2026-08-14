package io.lukia.features.library.application

import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

class GetLibraryDocumentsUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(): List<DocumentInfo> {
        return repository.getDocuments()
    }
}
