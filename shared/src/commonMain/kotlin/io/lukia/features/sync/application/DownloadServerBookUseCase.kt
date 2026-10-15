package io.lukia.features.sync.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.ServerBookDownloader
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.features.account.application.AccountSession
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Fetches a server book onto the device, so it opens like any other from then
 * on - offline included. See [ServerBookDownloader].
 *
 * The file only appears where the shelf expects it once every byte is there and
 * matches the book's hash; a download that fails or is cancelled leaves nothing
 * behind but the book still waiting on the shelf.
 */
class DownloadServerBookUseCase(
    private val session: AccountSession,
    private val api: ServerLibraryApi,
    private val shelf: ServerShelf,
    private val files: BookFileStore,
    private val changes: ShelfChanges,
) : ServerBookDownloader {

    override suspend fun download(location: String, onProgress: (Float) -> Unit) {
        val (contentHash, extension) = shelf.serverBookAt(location)
            ?: throw AppErrorException(AppError.DocumentNotFound)

        val writer = files.open(contentHash, extension)
        var committed = false
        try {
            // A refused token is found from the response's status, before a
            // byte of the body is written - so `authorized` asking twice never
            // writes the same bytes twice.
            session.authorized { address, token ->
                api.download(address, token, contentHash, writer::write, onProgress)
            }
            writer.commit()
            committed = true
        } finally {
            // Whatever stopped it, cancellation included - which is why this
            // cleanup must not itself be cancellable.
            if (!committed) withContext(NonCancellable) { writer.abort() }
        }

        shelf.markDownloaded(location)
        changes.announce()
    }
}
