package io.lukia.features.account.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.account.domain.AccountProblem
import io.lukia.resources.Res
import io.lukia.resources.account_problem_already_set_up
import io.lukia.resources.account_problem_insecure
import io.lukia.resources.account_problem_invalid_address
import io.lukia.resources.account_problem_invalid_invite
import io.lukia.resources.account_problem_invalid_password
import io.lukia.resources.account_problem_invalid_username
import io.lukia.resources.account_problem_last_admin
import io.lukia.resources.account_problem_not_lukia
import io.lukia.resources.account_problem_rejected
import io.lukia.resources.account_problem_server_failed
import io.lukia.resources.account_problem_session_expired
import io.lukia.resources.account_problem_sign_up_closed
import io.lukia.resources.account_problem_too_many
import io.lukia.resources.account_problem_unreachable
import io.lukia.resources.account_problem_username_taken
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What each [AccountProblem] says to the reader - the one place it turns into words. */
@Composable
internal fun AccountProblem.displayMessage(): String = stringResource(message())

private fun AccountProblem.message(): StringResource = when (this) {
    AccountProblem.InvalidAddress -> Res.string.account_problem_invalid_address
    AccountProblem.ServerUnreachable -> Res.string.account_problem_unreachable
    AccountProblem.NotALukiaServer -> Res.string.account_problem_not_lukia
    AccountProblem.InsecureAddress -> Res.string.account_problem_insecure
    AccountProblem.SignInRejected -> Res.string.account_problem_rejected
    AccountProblem.TooManyAttempts -> Res.string.account_problem_too_many
    AccountProblem.InvalidUsername -> Res.string.account_problem_invalid_username
    AccountProblem.InvalidPassword -> Res.string.account_problem_invalid_password
    AccountProblem.UsernameTaken -> Res.string.account_problem_username_taken
    AccountProblem.InvalidInvite -> Res.string.account_problem_invalid_invite
    AccountProblem.SignUpClosed -> Res.string.account_problem_sign_up_closed
    AccountProblem.AlreadySetUp -> Res.string.account_problem_already_set_up
    AccountProblem.LastAdmin -> Res.string.account_problem_last_admin
    AccountProblem.SessionExpired -> Res.string.account_problem_session_expired
    AccountProblem.ServerFailed -> Res.string.account_problem_server_failed
}
