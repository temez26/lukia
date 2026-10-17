package io.lukia.features.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.ServerAdministration
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdministrationUiState(
    /** The admin this device is signed in as; null for anyone else, who is shown nothing here. */
    val you: String? = null,
    /** Whether the server takes new accounts; null until the server has said. */
    val signUpOpen: Boolean? = null,
    /** Everyone with an account, by name; null until the server has said. */
    val people: List<Person>? = null,
    /** Whose password was just reset, to say so until the next thing is asked. */
    val passwordResetFor: String? = null,
    /** While a reset or a removal is on its way, so the buttons cannot send a second. */
    val busy: Boolean = false,
    /** What went wrong loading the section or with the switch, shown under the switch until the next. */
    val problem: AccountProblem? = null,
    /** What went wrong with a reset or a removal, shown under People until the next. */
    val peopleProblem: AccountProblem? = null,
) {
    val isAdmin: Boolean get() = you != null
}

/**
 * The admin's section of Account: what the server allows, and who uses it.
 *
 * A switch changes at once and is put back if the server refuses, so pressing
 * it feels like any other setting rather than a form waiting on a network.
 */
class AdministrationViewModel(private val administration: ServerAdministration) : ViewModel() {

    private val _state = MutableStateFlow(AdministrationUiState())
    val state: StateFlow<AdministrationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            administration.signedInAdmin.collect { admin -> _state.update { it.copy(you = admin) } }
        }
    }

    /** Asks the server how it stands - each time the section is shown, since another device may have changed it. */
    fun load() {
        viewModelScope.launch {
            runCatchingApp { administration.signUpOpen() to administration.people() }
                .onSuccess { (open, people) ->
                    _state.update { it.copy(signUpOpen = open, people = people, problem = null) }
                }
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

    fun resetPassword(username: String, password: String) = act {
        administration.resetPassword(username, password)
        _state.update { it.copy(passwordResetFor = username) }
    }

    fun removeAccount(username: String) = act {
        administration.removeAccount(username)
        val people = administration.people()
        _state.update { it.copy(people = people) }
    }

    private fun act(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, peopleProblem = null, passwordResetFor = null) }

        viewModelScope.launch {
            runCatchingApp { action() }
                .onFailure { failure -> _state.update { it.copy(peopleProblem = problemOf(failure)) } }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun report(failure: Throwable) {
        _state.update { it.copy(problem = problemOf(failure)) }
    }

    private fun problemOf(failure: Throwable) = (failure as? AccountFailure)?.problem ?: AccountProblem.ServerFailed
}
