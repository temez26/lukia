package io.lukia.features.sync.application

import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.library.application.HashLibraryUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the device in step with the account: catches up whenever it is signed
 * in, sends a little after anything changes here, and lets go of the server
 * when it signs out.
 *
 * Runs for as long as the app does. A round that fails - offline, server down -
 * is left for the next one, which the next change or the app coming back to
 * the front starts: nothing here is in the way of reading, and the account page
 * is where a reader learns the sign-in itself stopped working.
 */
class SyncCoordinator(
    private val session: AccountSession,
    private val hashLibrary: HashLibraryUseCase,
    private val pullLibrary: PullServerLibraryUseCase,
    private val syncBookState: SyncBookStateUseCase,
    private val forgetServer: ForgetServerUseCase,
    /** How many changes are waiting to be sent, as it changes - see `BookStateStore.waiting`. */
    private val waiting: Flow<Long>,
) {
    /** One round at a time: two would send the same changes twice and race over the cursors. */
    private val round = Mutex()

    @OptIn(FlowPreview::class)
    suspend fun run() = coroutineScope {
        launch {
            session.status.collectLatest { status ->
                when (status) {
                    is AccountStatus.SignedIn -> syncNow()
                    AccountStatus.SignedOut -> forgetServer()
                    is AccountStatus.NeedsSignIn -> Unit
                }
            }
        }

        // Settled for a moment rather than on every write, so a reader turning
        // pages sends one position when they pause rather than one per page.
        // The first value is the count at launch, which the round above covers.
        launch {
            waiting.drop(1).debounce(SendAfter).collect { syncNow() }
        }
    }

    /**
     * One round. Books are hashed first, so a book already on this device is
     * recognised as the server's; the library comes next, so a book's state has
     * a book on the shelf to land on.
     */
    suspend fun syncNow() = round.withLock {
        if (session.status.value !is AccountStatus.SignedIn) return@withLock
        runCatchingApp {
            hashLibrary()
            pullLibrary()
            syncBookState()
        }
    }

    private companion object {
        val SendAfter = 5.seconds
    }
}
