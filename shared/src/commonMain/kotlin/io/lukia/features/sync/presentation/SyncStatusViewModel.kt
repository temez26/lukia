package io.lukia.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.application.SyncStatus
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.SessionStore
import io.lukia.features.sync.infrastructure.UploadChoice
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Whether books imported here go to the server on their own - see `UploadChoice`. */
    val autoUpload: Boolean = true,
)

class SyncStatusViewModel(
    private val coordinator: SyncCoordinator,
    session: AccountSession,
    bookStates: BookStateStore,
    sessions: SessionStore,
    private val uploads: UploadChoice,
) : ViewModel() {

    private val autoUpload = MutableStateFlow(uploads.enabled)

    val state: StateFlow<SyncStatusUi> =
        combine(
            coordinator.status,
            combine(bookStates.waiting, sessions.waiting) { a, b -> a + b },
            session.status,
            autoUpload,
        ) { status, pending, account, upload ->
            SyncStatusUi(
                status = status,
                pending = pending,
                serverName = (account as? AccountStatus.SignedIn)?.account?.serverName,
                autoUpload = upload,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopAfterMillis), SyncStatusUi())

    fun setAutoUpload(enabled: Boolean) {
        uploads.enabled = enabled
        autoUpload.value = enabled
    }

    fun syncNow() {
        viewModelScope.launch { coordinator.syncNow() }
    }

    private companion object {
        const val StopAfterMillis = 5_000L
    }
}
