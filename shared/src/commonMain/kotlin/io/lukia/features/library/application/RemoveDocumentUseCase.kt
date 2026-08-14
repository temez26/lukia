package io.lukia.features.library.application

import io.lukia.features.library.domain.LibraryRepository

/**
 * Drops a document from the library index. The file on disk is left alone -
 * removing a book here means "stop listing it", not "delete it".
 */
class RemoveDocumentUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(location: String) {
        repository.removeDocument(location)
    }
}
