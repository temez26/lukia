package io.lukia.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.application.SyncStatus
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.SessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the sync card shows: how the last round went, and what is still waiting to go up. */
data class SyncStatusUi(
    val status: SyncStatus = SyncStatus(),
    /** Positions, layouts, favourites and sittings this device has not sent yet. */
    val pending: Long = 0,
    /** The server's name, while signed in to one; null hides the card. */
    val serverName: String? = null,
)

class SyncStatusViewModel(
    private val coordinator: SyncCoordinator,
    session: AccountSession,
    bookStates: BookStateStore,
    sessions: SessionStore,
) : ViewModel() {

    val state: StateFlow<SyncStatusUi> =
        combine(coordinator.status, bookStates.waiting, sessions.waiting, session.status) { status, a, b, account ->
            SyncStatusUi(
                status = status,
                pending = a + b,
                serverName = (account as? AccountStatus.SignedIn)?.account?.serverName,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopAfterMillis), SyncStatusUi())

    fun syncNow() {
        viewModelScope.launch { coordinator.syncNow() }
    }

    private companion object {
        const val StopAfterMillis = 5_000L
    }
}
