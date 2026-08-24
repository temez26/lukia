package io.lukia.features.library.application

import io.lukia.core.domain.DocumentWorkspace
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.domain.PdfLayoutRepository
import io.lukia.features.reader.domain.ReadingProgressRepository

/**
 * Drops a document from the library index. The file on disk is left alone -
 * removing a book here means "stop listing it", not "delete it".
 *
 * What the app kept for itself does go: the working copy, where the reader had
 * got to, and how they chose to read it. All three exist only to serve a book
 * that is in the library, and anything kept for a book nobody can see is state
 * with no way to reach it.
 */
class RemoveDocumentUseCase(
    private val repository: LibraryRepository,
    private val workspace: DocumentWorkspace,
    private val progressRepository: ReadingProgressRepository,
    private val layoutRepository: PdfLayoutRepository,
) {
    suspend operator fun invoke(location: String) {
        repository.removeDocument(location)
        workspace.discard(location)
        progressRepository.forget(location)
        layoutRepository.forget(location)
    }
}
