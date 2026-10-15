package io.lukia.features.sync.application

import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.library.application.HashLibraryUseCase
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.sync.infrastructure.SyncCursors
import kotlinx.coroutines.flow.collectLatest

/**
 * Keeps the device in step with the account: catches up whenever it is signed
 * in, and lets go of the server when it signs out.
 *
 * Runs for as long as the app does. A catch-up that fails - offline, server
 * down - is left for the next one: nothing here is in the way of reading, and
 * the account page is where a reader learns the sign-in itself stopped working.
 */
class SyncCoordinator(
    private val session: AccountSession,
    private val hashLibrary: HashLibraryUseCase,
    private val pullLibrary: PullServerLibraryUseCase,
    private val shelf: ServerShelf,
    private val cursors: SyncCursors,
    private val changes: ShelfChanges,
) {
    suspend fun run() {
        session.status.collectLatest { status ->
            when (status) {
                is AccountStatus.SignedIn -> syncNow()
                AccountStatus.SignedOut -> forgetServer()
                is AccountStatus.NeedsSignIn -> Unit
            }
        }
    }

    /**
     * One catch-up. Books are hashed first, so a book already on this device is
     * recognised as the server's rather than offered a second time.
     */
    suspend fun syncNow() {
        if (session.status.value !is AccountStatus.SignedIn) return
        runCatchingApp {
            hashLibrary()
            pullLibrary()
        }
    }

    /** Only once there was something to forget, so a device that never signed in does no work at launch. */
    private suspend fun forgetServer() {
        if (!cursors.anySet) return
        shelf.forgetServer()
        cursors.clear()
        changes.announce()
    }
}
