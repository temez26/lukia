package io.lukia.features.bookdetail.application

import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

class GetDocumentDetailsUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(location: String): DocumentInfo? {
        return repository.getDocument(location)
    }
}
