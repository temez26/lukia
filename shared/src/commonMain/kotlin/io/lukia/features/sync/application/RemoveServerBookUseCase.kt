package io.lukia.features.sync.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.document.ServerBookRemover
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Takes a server book out of the shared library. See [ServerBookRemover].
 *
 * This shelf is brought level here rather than left to the next catch-up, the
 * same way that catch-up would: the row goes if it was never downloaded, and a
 * downloaded copy stays, no longer marked as on the server.
 */
class RemoveServerBookUseCase(
    private val session: AccountSession,
    private val api: ServerLibraryApi,
    private val shelf: ServerShelf,
    private val changes: ShelfChanges,
) : ServerBookRemover {

    override val available: Flow<Boolean> =
        session.status.map { (it as? AccountStatus.SignedIn)?.account?.isAdmin == true }

    override suspend fun remove(location: String) {
        val (contentHash, _) = shelf.serverBookAt(location) ?: throw AppErrorException(AppError.DocumentNotFound)

        session.authorized { address, token -> api.remove(address, token, contentHash) }

        shelf.removeUndownloaded(contentHash)
        shelf.markOnServer(contentHash, onServer = false)
        changes.announce()
    }
}
