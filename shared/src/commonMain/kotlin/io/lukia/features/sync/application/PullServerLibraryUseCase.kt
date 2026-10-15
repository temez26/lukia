package io.lukia.features.sync.application

import io.lukia.api.BookResponse
import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.features.account.application.AccountSession
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SyncCursors

/**
 * Brings the shelf level with the server's shared library: books added there
 * appear here, ready to download; books removed there go from here if they were
 * never downloaded; and a book already on this device is marked as on the
 * server too, by the hash of its bytes.
 *
 * Only what changed since the last catch-up is asked for, a page at a time, and
 * how far it got is saved after every page - a catch-up cut short resumes
 * rather than starting over.
 */
class PullServerLibraryUseCase(
    private val session: AccountSession,
    private val api: ServerLibraryApi,
    private val shelf: ServerShelf,
    private val files: BookFileStore,
    private val cursors: SyncCursors,
    private val changes: ShelfChanges,
) {
    suspend operator fun invoke() {
        var since = cursors.books()
        var changed = false

        do {
            val page = session.authorized { address, token -> api.changes(address, token, since) }
            page.items.forEach { changed = apply(it) || changed }
            since = page.cursor
            cursors.setBooks(since)
        } while (page.hasMore)

        if (changed) changes.announce()
    }

    /** Whether the shelf changed. */
    private suspend fun apply(book: BookResponse): Boolean {
        if (book.deleted) {
            shelf.removeUndownloaded(book.id)
            shelf.markOnServer(book.id, onServer = false)
            return true
        }

        if (shelf.locationOf(book.id) != null) {
            shelf.markOnServer(book.id, onServer = true)
            return true
        }

        val type = DocumentType.entries.firstOrNull { it.name == book.type } ?: return false
        if (type == DocumentType.UNKNOWN) return false

        val extension = book.fileName.substringAfterLast('.', "")
        shelf.addServerBook(
            DocumentInfo(
                document = PickedDocument(book.fileName, extension, files.locationFor(book.id, extension)),
                type = type,
                exists = false,
                sizeBytes = book.sizeBytes,
                coverImage = coverOf(book),
                metadata = book.metadata(),
                onServer = true,
            ),
            contentHash = book.id,
        )
        return true
    }

    /** A cover that fails to arrive is a book without one, not a catch-up that stops. */
    private suspend fun coverOf(book: BookResponse): CoverImage? = if (book.hasCover) {
        runCatching { session.authorized { address, token -> api.cover(address, token, book.id) } }
            .getOrNull()
            ?.let(::CoverImage)
    } else {
        null
    }

    private fun BookResponse.metadata() = BookMetadata(
        title = title,
        authors = authors,
        publisher = publisher,
        published = published,
        language = language,
        description = description,
        subjects = subjects,
        identifier = identifier,
    ).takeUnless { it.isEmpty }
}
