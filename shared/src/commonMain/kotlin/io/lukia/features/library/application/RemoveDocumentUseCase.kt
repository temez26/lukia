package io.lukia.features.library.application

import io.lukia.core.domain.reading.PdfLayoutRepository
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.features.library.domain.LibraryRepository

/**
 * Drops a document from the library index. The file on disk is left alone -
 * removing a book here means "stop listing it", not "delete it".
 *
 * What the app kept for itself does go: the working copy, where the reader had
 * got to, and how they chose to read it. All three exist only to serve a book
 * that is in the library, and anything kept for a book nobody can see is state
 * with no way to reach it.
 *
 * Four writes to four stores, and only the first three of them can be made one
 * thing: the working copy is a file on disk, so nothing that removes a book can
 * ever be a single atomic act. What the schema does instead is make the part
 * that matters survive being interrupted - the position and the layout are keyed
 * to the book and cascade when it goes, see `ReadingPosition.sq` - so a process
 * that dies part way through this leaves no state a reader could never reach.
 *
 * They are still asked for by name here. The cascade is a property of one store;
 * this is the operation, and reading it should say what removing a book means
 * without knowing which of the four happens to enforce it.
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
