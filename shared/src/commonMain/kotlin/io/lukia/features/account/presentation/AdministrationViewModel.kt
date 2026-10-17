package io.lukia.features.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.ServerAdministration
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdministrationUiState(
    /** Whether this device is signed in as the admin; nothing here is shown otherwise. */
    val isAdmin: Boolean = false,
    /** Whether the server takes new accounts; null until the server has said. */
    val signUpOpen: Boolean? = null,
    /** What went wrong with the last thing asked, shown under the section until the next. */
    val problem: AccountProblem? = null,
)

/**
 * The admin's section of Account: what the server allows, and later who uses it.
 *
 * A switch changes at once and is put back if the server refuses, so pressing
 * it feels like any other setting rather than a form waiting on a network.
 */
class AdministrationViewModel(private val administration: ServerAdministration) : ViewModel() {

    private val _state = MutableStateFlow(AdministrationUiState())
    val state: StateFlow<AdministrationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            administration.isAdmin.collect { admin -> _state.update { it.copy(isAdmin = admin) } }
        }
    }

    /** Asks the server how it stands - each time the section is shown, since another device may have changed it. */
    fun load() {
        viewModelScope.launch {
            runCatchingApp { administration.signUpOpen() }
                .onSuccess { open -> _state.update { it.copy(signUpOpen = open, problem = null) } }
                .onFailure(::report)
        }
    }

    fun setSignUpOpen(open: Boolean) {
        val before = _state.value.signUpOpen
        _state.update { it.copy(signUpOpen = open, problem = null) }

        viewModelScope.launch {
            runCatchingApp { administration.setSignUpOpen(open) }
                .onSuccess { stands -> _state.update { it.copy(signUpOpen = stands) } }
                .onFailure { failure ->
                    _state.update { it.copy(signUpOpen = before) }
                    report(failure)
                }
        }
    }

    private fun report(failure: Throwable) {
        _state.update { it.copy(problem = (failure as? AccountFailure)?.problem ?: AccountProblem.ServerFailed) }
    }
}
