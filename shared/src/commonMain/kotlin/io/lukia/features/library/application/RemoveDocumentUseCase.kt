package io.lukia.features.library.application

import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.reading.PdfLayoutRepository
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.features.library.domain.LibraryRepository

/**
 * Drops a document from the library index. The reader's own file is left alone -
 * removing a book here means "stop listing it", not "delete it".
 *
 * What the app kept for itself does go: the working copy, the copy of a book
 * handed to the app rather than fetched by it, where the reader had got to, and
 * how they chose to read it. All exist only to serve a shelved book, and
 * anything kept for a book nobody can see is unreachable state.
 *
 * Two of the five stores are files on disk, so removing a book can never be one
 * atomic act. The schema makes the part that matters survive interruption - the
 * position and layout cascade off the book, see `ReadingPosition.sq`.
 *
 * The order does the rest: files first, the row that names them last. A row is
 * the only thing that remembers where a book's copies were filed - both are
 * named after a hash of the location - so dying between dropping the row and
 * deleting the copies would strand whole books nothing could name again. The
 * other way round costs nothing: a row whose working copy has gone is repaired
 * by the next open, which makes the copy again.
 *
 * All five are still asked for by name. The cascade is a property of one store;
 * this is the operation, and reading it should say what removing a book means
 * without knowing which store enforces what.
 */
class RemoveDocumentUseCase(
    private val repository: LibraryRepository,
    private val workspace: DocumentWorkspace,
    private val incoming: IncomingDocumentStore,
    private val progressRepository: ReadingProgressRepository,
    private val layoutRepository: PdfLayoutRepository,
) {
    suspend operator fun invoke(location: String) {
        workspace.discard(location)
        incoming.discard(location)
        progressRepository.forget(location)
        layoutRepository.forget(location)
        repository.removeDocument(location)
    }
}
