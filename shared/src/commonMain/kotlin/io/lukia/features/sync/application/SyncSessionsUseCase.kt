package io.lukia.features.sync.application

import io.lukia.features.account.application.AccountSession
import io.lukia.features.sync.infrastructure.HistoryChoice
import io.lukia.features.sync.infrastructure.SessionApi
import io.lukia.features.sync.infrastructure.SessionStore
import io.lukia.features.sync.infrastructure.SyncCursors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Brings the account's reading sessions level with this device's, both ways,
 * so the statistics on every device count all of them. See
 * docs/decisions/0003-offline-first-sync.md.
 *
 * Reading this device recorded before it was ever signed in is the reader's to
 * give: until they have said whether it goes to the account, none of it is
 * sent - see [HistoryChoice] and [historyQuestion]. Reading done since goes
 * either way.
 */
class SyncSessionsUseCase(
    private val session: AccountSession,
    private val api: SessionApi,
    private val store: SessionStore,
    private val choice: HistoryChoice,
    private val cursors: SyncCursors,
) {
    private val _historyQuestion = MutableStateFlow(false)

    /** True while the reader has history on this device and has not said whether it goes to the account. */
    val historyQuestion: StateFlow<Boolean> = _historyQuestion.asStateFlow()

    /**
     * Marks where this device's history ends: everything recorded until now is
     * what the question is about, and anything after belongs to the account.
     * Called the moment the device is signed in, which needs no network, so a
     * first round that fails offline cannot let later reading count as earlier.
     */
    suspend fun signedIn() {
        if (!choice.decided && choice.askedUpTo == 0L) choice.askedUpTo = store.latestId()
    }

    suspend operator fun invoke() {
        send(afterId = if (decided()) 0 else choice.askedUpTo)
        receive()
    }

    /** The reader's answer: this device's earlier reading goes to the account, or stays here. */
    suspend fun answer(addToAccount: Boolean) {
        if (!addToAccount) store.keepOnDevice(upToId = choice.askedUpTo)
        choice.decided = true
        _historyQuestion.value = false
    }

    private suspend fun decided(): Boolean {
        if (choice.decided) return true

        if (!store.hasUnsentHistory(upToId = choice.askedUpTo)) {
            // Nothing from before to ask about.
            choice.decided = true
            return true
        }

        _historyQuestion.value = true
        return false
    }

    private suspend fun send(afterId: Long) {
        store.unsent(afterId).forEach { unsent ->
            session.authorized { address, token -> api.send(address, token, unsent.id, unsent.request) }
            store.markSent(unsent.id, unsent.request.durationMs)
        }
    }

    private suspend fun receive() {
        var since = cursors.sessions()
        do {
            val page = session.authorized { address, token -> api.changes(address, token, since) }
            page.items.forEach { store.receive(it) }
            since = page.cursor
            cursors.setSessions(since)
        } while (page.hasMore)
    }
}
