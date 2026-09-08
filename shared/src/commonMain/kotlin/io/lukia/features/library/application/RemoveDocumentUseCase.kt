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
 * that was handed to the app rather than fetched by it, where the reader had got
 * to, and how they chose to read it. All of them exist only to serve a book that
 * is in the library, and anything kept for a book nobody can see is state with
 * no way to reach it.
 *
 * Five writes to five stores, and only the ones in the database can be made one
 * thing: two of these are files on disk, so nothing that removes a book can ever
 * be a single atomic act. What the schema does instead is make the part that
 * matters survive being interrupted - the position and the layout are keyed to
 * the book and cascade when it goes, see `ReadingPosition.sq` - so a process that
 * dies part way through this leaves no state a reader could never reach.
 *
 * The order is what does the rest, and it is deliberate: the files go first and
 * the row that names them goes last. A row is the only thing that remembers where
 * a book's copies were filed - both are named after a hash of the location - so a
 * process that died between dropping the row and deleting the copies would leave
 * whole books on the device that nothing could ever name again. The other way
 * round the interruption costs nothing: a row whose working copy has gone is
 * repaired by the next open, which simply makes the copy again.
 *
 * They are still asked for by name here. The cascade is a property of one store;
 * this is the operation, and reading it should say what removing a book means
 * without knowing which of the five happens to enforce it.
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
