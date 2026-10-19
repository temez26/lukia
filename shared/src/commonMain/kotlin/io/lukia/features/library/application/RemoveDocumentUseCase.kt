package io.lukia.features.library.application

import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.reading.PdfLayoutRepository
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.domain.ServerShelf

/**
 * Takes a book off this device. The reader's own file is left alone - removing
 * a book here means "stop listing it", not "delete it".
 *
 * What the app kept for itself does go: the working copy, the copy of a book
 * handed to the app rather than fetched by it, where the reader had got to, and
 * how they chose to read it. All exist only to serve a shelved book.
 *
 * A book the sync server also has stays on the shelf, as one to download again.
 * Dropping its row would lose it for good: the server's catch-up only sends
 * what changed since the last one, so it never offers the book a second time.
 *
 * Two of the five stores are files on disk, so this can never be one atomic act.
 * The schema makes the part that matters survive interruption - position and
 * layout cascade off the book, see `ReadingPosition.sq`.
 *
 * The order does the rest: files first, the row that names them last. A row is
 * the only thing that remembers where a book's copies were filed - both are
 * named after a hash of the location - so dying between dropping the row and
 * deleting the copies would strand books nothing could name again. The other way
 * round costs nothing: the next open makes the copy again.
 */
class RemoveDocumentUseCase(
    private val repository: LibraryRepository,
    private val workspace: DocumentWorkspace,
    private val incoming: IncomingDocumentStore,
    private val progressRepository: ReadingProgressRepository,
    private val layoutRepository: PdfLayoutRepository,
    /** Null where there is never a server, as in tests of the shelf alone. */
    private val serverShelf: ServerShelf? = null,
    private val files: BookFileStore? = null,
) {
    suspend operator fun invoke(location: String) {
        val serverBook = serverShelf?.serverBookAt(location)

        workspace.discard(location)
        incoming.discard(location)
        layoutRepository.forget(location)

        if (serverShelf == null || files == null || serverBook == null) {
            progressRepository.forget(location)
            repository.removeDocument(location)
            return
        }

        val (contentHash, extension) = serverBook
        val downloadLocation = files.locationFor(contentHash, extension)

        if (downloadLocation == location) {
            // Still the same book on the same row, so where its reader got to
            // and whether it is a favourite stay with it.
            serverShelf.markNotDownloaded(location)
            return
        }

        // A copy imported from elsewhere: its row names the reader's file, so it
        // goes, and the server's book takes its place where a download lands -
        // unless another copy of the same bytes is still on the shelf.
        val document = repository.getDocument(location)
        progressRepository.forget(location)
        repository.removeDocument(location)

        if (document != null && serverShelf.locationOf(contentHash) == null) {
            serverShelf.addServerBook(
                document.copy(
                    document = document.document.copy(location = downloadLocation),
                    exists = false,
                    onServer = true,
                ),
                contentHash,
            )
        }
    }
}
