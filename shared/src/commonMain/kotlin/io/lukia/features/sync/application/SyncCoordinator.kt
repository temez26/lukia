package io.lukia.features.sync.application

import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the device in step with the account: catches up whenever it is signed
 * in, sends a little after anything changes here, and lets go of the server
 * when it signs out.
 *
 * Runs for as long as the app does. A round that fails - offline, server down -
 * is tried again on its own, a little later each time (see [retryAfter]), as
 * well as by the next change or the app coming back to the front. Nothing here
 * is in the way of reading, and the account page is where a reader learns the
 * sign-in itself stopped working.
 */
class SyncCoordinator(
    private val session: AccountSession,
    private val round: SyncRoundUseCase,
    private val syncSessions: SyncSessionsUseCase,
    private val forgetServer: ForgetServerUseCase,
    /**
     * Fires whenever what is waiting to be sent changes - a position, a
     * favourite, a sitting growing - and not for what was waiting at launch.
     * See `BookStateStore.waiting` and `SessionStore.waiting`.
     */
    private val waiting: Flow<Long>,
) {
    /** One round at a time: two would send the same changes twice and race over the cursors. */
    private val oneAtATime = Mutex()

    /** Rounds that have failed in a row; while above zero, another is on its way. */
    private val failedRounds = MutableStateFlow(0)

    @OptIn(FlowPreview::class)
    suspend fun run() = coroutineScope {
        launch {
            session.status.collectLatest { status ->
                when (status) {
                    is AccountStatus.SignedIn -> {
                        syncSessions.signedIn()
                        syncNow()
                    }
                    AccountStatus.SignedOut -> {
                        failedRounds.value = 0
                        forgetServer()
                    }
                    is AccountStatus.NeedsSignIn -> Unit
                }
            }
        }

        // Settled for a moment rather than on every write, so a reader turning
        // pages sends one position when they pause rather than one per page.
        launch {
            waiting.debounce(SendAfter).collect { syncNow() }
        }

        // Latest only: a round that succeeds in the meantime, or fails again,
        // replaces the wait that was running.
        launch {
            failedRounds.collectLatest { failures ->
                if (failures > 0) {
                    delay(retryAfter(failures))
                    syncNow()
                }
            }
        }
    }

    /** One round, now - see [SyncRoundUseCase]. Does nothing unless signed in. */
    suspend fun syncNow() = oneAtATime.withLock {
        if (session.status.value !is AccountStatus.SignedIn) return@withLock
        val succeeded = runCatchingApp { round() }.isSuccess
        failedRounds.value = if (succeeded) 0 else failedRounds.value + 1
    }

    private companion object {
        val SendAfter = 5.seconds
    }
}

/**
 * How long to wait before trying again after [failures] rounds in a row have
 * failed: half a minute, doubling, and never more than a quarter of an hour - soon
 * enough that a server back from a restart is noticed, rarely enough that one
 * that is gone for the night costs a phone nothing.
 */
internal fun retryAfter(failures: Int): Duration {
    val doublings = (failures - 1).coerceIn(0, MaxDoublings)
    return (FirstRetry * (1 shl doublings)).coerceAtMost(LongestWait)
}

private val FirstRetry = 30.seconds
private val LongestWait = 15.minutes

/** Enough doublings to pass [LongestWait]; more would only overflow. */
private const val MaxDoublings = 5
