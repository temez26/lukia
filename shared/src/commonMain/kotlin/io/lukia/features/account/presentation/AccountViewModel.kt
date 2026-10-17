package io.lukia.features.account.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.LastSignIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the sign-in form is for. Only meaningful while signed out. */
sealed interface AccountForm {

    /** An account that exists. */
    data object SignIn : AccountForm

    /** A new account, on a server whose admin lets anyone create one. */
    data object SignUp : AccountForm

    /** A new account, with a code the admin handed out - for a server older than [SignUp]. */
    data object Invite : AccountForm

    /** The first account on a server nobody has set up yet, named [serverName]; it becomes the admin. */
    data class SetUp(val serverName: String) : AccountForm
}

data class AccountUiState(
    val status: AccountStatus = AccountStatus.SignedOut,
    val form: AccountForm = AccountForm.SignIn,
    /** A request is on its way; the controls that would send another are disabled. */
    val busy: Boolean = false,
    /** What went wrong with the last thing asked, shown under the form until the next. */
    val problem: AccountProblem? = null,
)

/**
 * The Account area of Settings: signing in, creating an account, and signing
 * out. The sign-in itself is [AccountSession]'s; this holds what the form is for
 * and what to tell the reader.
 *
 * One form rather than steps: the address, the username and the password are
 * all asked for at once, and the server is only asked about itself when the
 * form is sent - which is when a server with no accounts yet turns the form
 * into the one that creates its admin.
 *
 * Text fields are held by the screen rather than here, so a password never sits
 * in a view model that outlives the page.
 */
class AccountViewModel(private val session: AccountSession) : ViewModel() {

    private val form = MutableStateFlow<AccountForm>(AccountForm.SignIn)
    private val busy = MutableStateFlow(false)
    private val problem = MutableStateFlow<AccountProblem?>(null)

    val state: StateFlow<AccountUiState> =
        combine(session.status, form, busy, problem, ::AccountUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState(session.status.value))

    /** The server and username to fill the form with, from the last sign-in on this device. */
    val lastSignIn: LastSignIn? = session.lastSignIn()

    /** Switches between signing in and creating an account. */
    fun createAccount(creating: Boolean) {
        form.value = if (creating) AccountForm.SignUp else AccountForm.SignIn
        problem.value = null
    }

    /**
     * Sends the form: signs in, creates the account, or creates the admin -
     * whichever it is for. A server found to have no accounts yet is not set up
     * unasked: the form says so and turns into the one that does it.
     *
     * Creating an account depends on the server, which is only asked now: one
     * whose admin has new accounts closed is said to be closed before anything
     * is sent, and one too old to have sign-up turns the form into its invite
     * form, since an invite code is the only way such a server takes anybody.
     */
    fun submit(address: String, username: String, password: String, inviteCode: String = "") = perform {
        val (checked, server) = session.check(address)
        val current = form.value

        when {
            server.setupRequired && current !is AccountForm.SetUp -> {
                form.value = AccountForm.SetUp(server.name)
                return@perform
            }
            server.setupRequired -> session.setUp(checked, username, password)
            current == AccountForm.SignUp && server.signUpOpen == null -> {
                form.value = AccountForm.Invite
                return@perform
            }
            current == AccountForm.SignUp && server.signUpOpen == false -> {
                problem.value = AccountProblem.SignUpClosed
                return@perform
            }
            current == AccountForm.SignUp -> session.signUp(checked, username, password)
            current == AccountForm.Invite -> session.redeem(checked, inviteCode, username, password)
            else -> session.signIn(checked, username, password)
        }
        form.value = AccountForm.SignIn
    }

    /** For a sign-in the server stopped accepting: the same account, its password again. */
    fun signInAgain(password: String) = perform {
        val account = (session.status.value as? AccountStatus.NeedsSignIn)?.account ?: return@perform
        session.signIn(account.address, account.username, password)
    }

    fun signOut() = perform {
        session.signOut()
        form.value = AccountForm.SignIn
    }

    fun deleteAccount() = perform {
        session.deleteAccount()
        form.value = AccountForm.SignIn
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
