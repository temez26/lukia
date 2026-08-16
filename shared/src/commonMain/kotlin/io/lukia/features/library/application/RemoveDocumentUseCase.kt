package io.lukia.features.library.application

import io.lukia.core.domain.DocumentWorkspace
import io.lukia.features.library.domain.LibraryRepository

/**
 * Drops a document from the library index. The file on disk is left alone -
 * removing a book here means "stop listing it", not "delete it".
 *
 * The app's own working copy of it does go, though: it exists only to serve a
 * book that is in the library, and nothing would ever come back for it.
 */
class RemoveDocumentUseCase(
    private val repository: LibraryRepository,
    private val workspace: DocumentWorkspace
) {
    suspend operator fun invoke(location: String) {
        repository.removeDocument(location)
        workspace.discard(location)
    }
}
