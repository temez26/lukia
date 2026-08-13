package io.lukia.features.bookdetail.domain

import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

class GetDocumentDetailsUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(location: String): DocumentInfo? {
        return repository.getDocument(location)
    }
}
