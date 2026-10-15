package io.lukia.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.application.SyncSessionsUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The once-per-device question of whether earlier reading goes to the account. */
class HistoryViewModel(
    private val sessions: SyncSessionsUseCase,
    private val coordinator: SyncCoordinator,
) : ViewModel() {

    val asking: StateFlow<Boolean> = sessions.historyQuestion

    /** Records the answer and sends at once, rather than at the next change, what it said may go. */
    fun answer(addToAccount: Boolean) {
        viewModelScope.launch {
            sessions.answer(addToAccount)
            coordinator.syncNow()
        }
    }
}
