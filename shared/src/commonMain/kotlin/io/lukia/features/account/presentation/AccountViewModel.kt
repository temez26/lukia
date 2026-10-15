package io.lukia.features.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where somebody signing in has got to. Only meaningful while signed out. */
sealed interface AccountStep {

    /** Typing the server's address. */
    data object EnterAddress : AccountStep

    /** A server has answered; now who they are. */
    data class Credentials(val address: String, val server: ServerInfo, val withInvite: Boolean) : AccountStep
}

data class AccountUiState(
    val status: AccountStatus = AccountStatus.SignedOut,
    val step: AccountStep = AccountStep.EnterAddress,
    /** A request is on its way; the controls that would send another are disabled. */
    val busy: Boolean = false,
    /** What went wrong with the last thing asked, shown under the form until the next. */
    val problem: AccountProblem? = null,
)

/**
 * The Account area of Settings: connecting to a server, signing in to it, and
 * signing out. The sign-in itself is [AccountSession]'s; this holds the form's
 * progress and what to tell the reader.
 *
 * Text fields are held by the screen rather than here, so a password never sits
 * in a view model that outlives the page.
 */
class AccountViewModel(private val session: AccountSession) : ViewModel() {

    private val step = MutableStateFlow<AccountStep>(AccountStep.EnterAddress)
    private val busy = MutableStateFlow(false)
    private val problem = MutableStateFlow<AccountProblem?>(null)

    val state: StateFlow<AccountUiState> =
        combine(session.status, step, busy, problem, ::AccountUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState(session.status.value))

    fun connect(typedAddress: String) = perform {
        val (address, server) = session.check(typedAddress)
        step.value = AccountStep.Credentials(address, server, withInvite = false)
    }

    /** Switches between signing in with a password and creating an account with an invite. */
    fun useInvite(withInvite: Boolean) {
        val current = step.value as? AccountStep.Credentials ?: return
        step.value = current.copy(withInvite = withInvite)
        problem.value = null
    }

    fun otherServer() {
        step.value = AccountStep.EnterAddress
        problem.value = null
    }

    /**
     * Signs in, sets the server up, or redeems an invite, whichever the step is
     * showing. [inviteCode] is only read for an invite.
     */
    fun submit(username: String, password: String, inviteCode: String = "") = perform {
        val current = step.value as? AccountStep.Credentials ?: return@perform
        when {
            current.server.setupRequired -> session.setUp(current.address, username, password)
            current.withInvite -> session.redeem(current.address, inviteCode, username, password)
            else -> session.signIn(current.address, username, password)
        }
        step.value = AccountStep.EnterAddress
    }

    /** For a sign-in the server stopped accepting: the same account, its password again. */
    fun signInAgain(password: String) = perform {
        val account = (session.status.value as? AccountStatus.NeedsSignIn)?.account ?: return@perform
        session.signIn(account.address, account.username, password)
    }

    fun signOut() = perform {
        session.signOut()
        step.value = AccountStep.EnterAddress
    }

    private fun perform(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        problem.value = null

        viewModelScope.launch {
            runCatchingApp { action() }.onFailure { failure ->
                problem.value = (failure as? AccountFailure)?.problem ?: AccountProblem.ServerFailed
            }
            busy.value = false
        }
    }
}
