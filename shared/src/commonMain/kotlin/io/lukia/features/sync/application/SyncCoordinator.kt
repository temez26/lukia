package io.lukia.features.sync.application

import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.sync.infrastructure.ServerEvents
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
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
    /** The server's word that something changed - see [listenForChanges]. */
    private val events: ServerEvents,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** How often to check while the line to the server is down - see [keepUp]. */
    private val checkEvery: Duration = CheckEvery,
) {
    /** One round at a time: two would send the same changes twice and race over the cursors. */
    private val oneAtATime = Mutex()

    /**
     * How many rounds have started. A request that finds one started after it
     * was made is already answered - that round asked the server for
     * everything this one would - so a burst of requests during a round costs
     * one more round, not one each.
     */
    private val roundsStarted = MutableStateFlow(0L)

    /** Whether the line to the server is open, so that it, not a timer, says when to catch up. */
    private val lineOpen = MutableStateFlow(false)

    /** Rounds that have failed in a row; while above zero, another is on its way. */
    private val failedRounds = MutableStateFlow(0)

    private val _status = MutableStateFlow(SyncStatus())

    /** How the last round went and whether one is running - see [SyncStatus]. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    @OptIn(FlowPreview::class)
    suspend fun run() = coroutineScope {
        launch {
            // The status found at launch is not a sign-in: the app coming to the
            // front catches up then (see `App`), and a round for it here as well
            // was the same round twice, back to back.
            var atLaunch = true
            session.status.collectLatest { status ->
                val signingIn = !atLaunch
                atLaunch = false
                when (status) {
                    is AccountStatus.SignedIn -> {
                        syncSessions.signedIn()
                        if (signingIn) syncNow()
                    }
                    AccountStatus.SignedOut -> {
                        failedRounds.value = 0
                        _status.value = SyncStatus()
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

    /**
     * Keeps the device current for as long as this runs: by the line to the
     * server while it is open - see [listenForChanges] - and by checking every
     * [checkEvery] while it is not.
     *
     * Meant to run only while the app is on screen: nobody needs a shelf kept
     * current behind their back, and a held connection costs a phone battery.
     *
     * No round of its own to start with. The line catches up as it opens, which
     * leaves no moment between a round and the line in which a change could go
     * unheard; a line that has not opened within [OpenWithin] is taken to be
     * down, and the checks start.
     */
    suspend fun keepUp(): Unit = coroutineScope {
        launch { listenForChanges() }

        lineOpen.collectLatest { open ->
            if (open) return@collectLatest
            delay(OpenWithin)
            while (true) {
                syncNow()
                delay(checkEvery)
            }
        }
    }

    /**
     * Holds a line open to the server for as long as this is running and the
     * device is signed in. Catches up each time it opens - after a drop it may
     * have missed anything - and whenever the server says something changed: a
     * book added, or a page turned on another device. A line that drops is
     * opened again, a little later each time it keeps dropping.
     */
    private suspend fun listenForChanges() {
        session.status.collectLatest { status ->
            if (status !is AccountStatus.SignedIn) return@collectLatest
            var drops = 0
            while (true) {
                val outcome = runCatchingApp {
                    session.authorized { address, token ->
                        events.listen(
                            address,
                            token,
                            onOpen = {
                                lineOpen.value = true
                                syncNow()
                            },
                        ) { syncNow() }
                    }
                }
                lineOpen.value = false
                drops = if (outcome.isSuccess) 0 else drops + 1
                delay(reconnectAfter(drops))
            }
        }
    }

    /**
     * One round, now - see [SyncRoundUseCase] - unless one has started since this
     * was asked for. Does nothing unless signed in.
     */
    suspend fun syncNow() {
        val askedAfter = roundsStarted.value
        oneAtATime.withLock {
            if (roundsStarted.value > askedAfter) return
            runRound()
        }
    }

    /** The round itself; [oneAtATime] is held. */
    private suspend fun runRound() {
        if (session.status.value !is AccountStatus.SignedIn) return
        roundsStarted.value++
        _status.update { it.copy(running = true) }

        val failure = runCatchingApp { round() }.exceptionOrNull()
        failedRounds.value = if (failure == null) 0 else failedRounds.value + 1
        _status.update { previous ->
            if (failure == null) {
                SyncStatus(lastSyncedAt = nowMillis())
            } else {
                previous.copy(
                    running = false,
                    problem = (failure as? AccountFailure)?.problem ?: AccountProblem.ServerFailed,
                    retryAt = nowMillis() + retryAfter(failedRounds.value).inWholeMilliseconds,
                )
            }
        }
    }

    private companion object {
        val SendAfter = 5.seconds

        /** How long a line has to open before it is taken to be down. */
        val OpenWithin = 3.seconds

        /**
         * How often to check while the line is down - the server unreachable,
         * or a proxy in front of it that will not hold a line open.
         */
        val CheckEvery = 2.minutes
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

/**
 * How long to wait before opening the line to the server again: a moment after
 * it closed cleanly, then five seconds, doubling each time it keeps failing, up
 * to a minute.
 */
internal fun reconnectAfter(drops: Int): Duration = when (drops) {
    0 -> AfterClose
    else -> (FirstReconnect * (1 shl (drops - 1).coerceIn(0, MaxReconnectDoublings))).coerceAtMost(LongestReconnect)
}

private val AfterClose = 1.seconds
private val FirstReconnect = 5.seconds
private val LongestReconnect = 1.minutes

/** Enough doublings to pass [LongestReconnect]. */
private const val MaxReconnectDoublings = 4
