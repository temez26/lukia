package io.lukia.features.sync.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.ServerBookUploader
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.infrastructure.ioDispatcher
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.library.infrastructure.BookFileReader
import io.lukia.features.sync.infrastructure.OutgoingBook
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Adds a book on this device to the server's shared library, where every
 * account sees it. See [ServerBookUploader].
 *
 * The server names the book by the hash of what it received, which is the same
 * hash this device would have worked out - so the book on the shelf learns it
 * here if it did not know it yet, is marked as on the server, and from then on
 * its place and favourite travel with the account like any other.
 */
class UploadServerBookUseCase(
    private val session: AccountSession,
    private val api: ServerLibraryApi,
    private val library: LibraryRepository,
    private val reader: BookFileReader,
    private val shelf: ServerShelf,
    private val changes: ShelfChanges,
) : ServerBookUploader {

    override val available: Flow<Boolean> = session.status.map { it is AccountStatus.SignedIn }

    override suspend fun upload(location: String, onProgress: (Float) -> Unit) {
        val book = library.getDocument(location)?.takeIf { it.exists }
            ?: throw AppErrorException(AppError.DocumentNotFound)
        val source = reader.open(book.document) ?: throw AppErrorException(AppError.DocumentNotFound)

        val filed = source.use {
            session.authorized { address, token ->
                val outgoing = OutgoingBook(book.document.nameWithExtension(), source.size) { offset, length ->
                    withContext(ioDispatcher()) { source.read(offset, length) }
                }
                api.upload(address, token, outgoing, onProgress)
            }
        }

        shelf.markUploaded(location, filed.id)
        changes.announce()
    }
}

/** The server reads a book's type from its name, so the name always carries the extension. */
private fun PickedDocument.nameWithExtension(): String =
    if (extension.isEmpty() || fileName.endsWith(".$extension", ignoreCase = true)) fileName else "$fileName.$extension"
