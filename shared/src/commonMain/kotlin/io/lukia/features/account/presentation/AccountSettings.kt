package io.lukia.features.account.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.LastSignIn
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.resources.Res
import io.lukia.resources.account_delete
import io.lukia.resources.account_delete_message
import io.lukia.resources.account_delete_title
import io.lukia.resources.account_footnote
import io.lukia.resources.account_needs_sign_in
import io.lukia.resources.account_sign_in
import io.lukia.resources.account_sign_out
import io.lukia.resources.account_signed_in
import io.lukia.resources.account_signed_in_admin
import io.lukia.resources.account_signing_in
import io.lukia.resources.account_title
import io.lukia.resources.action_delete
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.surface.ConfirmDialog
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Account area of Settings, wired to its view model. Handed to Settings as
 * a slot by the navigation - see `SettingsBody` - so the settings feature never
 * depends on this one.
 */
@Composable
fun AccountSettings(groupColor: Color, viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    AccountSettingsContent(
        state = state,
        lastSignIn = viewModel.lastSignIn,
        actions = AccountActions(
            onSubmit = viewModel::submit,
            onCreateAccount = viewModel::createAccount,
            onSignInAgain = viewModel::signInAgain,
            onSignOut = viewModel::signOut,
            onDeleteAccount = viewModel::deleteAccount,
        ),
        groupColor = groupColor,
    )
}

/** What the Account area can be asked to do. */
class AccountActions(
    val onSubmit: (address: String, username: String, password: String, inviteCode: String) -> Unit =
        { _, _, _, _ -> },
    /** True to the form that creates an account, false back to signing in. */
    val onCreateAccount: (Boolean) -> Unit = {},
    val onSignInAgain: (password: String) -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
)

@Composable
internal fun AccountSettingsContent(
    state: AccountUiState,
    actions: AccountActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
    /** What to fill the form with - the last sign-in on this device. */
    lastSignIn: LastSignIn? = null,
) {
    SettingsSection(
        title = stringResource(Res.string.account_title),
        footnote = stringResource(Res.string.account_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            when (val status = state.status) {
                is AccountStatus.SignedIn -> SignedIn(status.account, state.busy, actions)
                is AccountStatus.NeedsSignIn -> NeedsSignIn(status.account, state.busy, actions)
                AccountStatus.SignedOut -> SignInForm(state.form, state.busy, lastSignIn, actions)
            }

            state.problem?.let { ProblemText(it) }
        }
    }
}

@Composable
private fun SignedIn(account: ServerAccount, busy: Boolean, actions: AccountActions) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text(
            text = stringResource(
                if (account.isAdmin) Res.string.account_signed_in_admin else Res.string.account_signed_in,
                account.serverName,
                account.username,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            LukiaButton(
                label = stringResource(Res.string.account_sign_out),
                onClick = actions.onSignOut,
                variant = ButtonVariant.Secondary,
                enabled = !busy,
            )
            LukiaButton(
                label = stringResource(Res.string.account_delete),
                onClick = { confirmingDelete = true },
                variant = ButtonVariant.Destructive,
                enabled = !busy,
            )
        }
    }

    ConfirmDialog(
        visible = confirmingDelete,
        title = stringResource(Res.string.account_delete_title),
        message = stringResource(Res.string.account_delete_message, account.serverName),
        confirmLabel = stringResource(Res.string.action_delete),
        onConfirm = actions.onDeleteAccount,
        onDismissRequest = { confirmingDelete = false },
        destructive = true,
    )
}

@Composable
private fun NeedsSignIn(account: ServerAccount, busy: Boolean, actions: AccountActions) {
    var password by remember { mutableStateOf("") }
    val canSubmit = !busy && password.isNotEmpty()
    val submit = { if (canSubmit) actions.onSignInAgain(password) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text(
            text = stringResource(Res.string.account_needs_sign_in, account.serverName),
            style = MaterialTheme.typography.bodyMedium,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            autofill = ContentType.Password,
            onGo = submit,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            LukiaButton(
                label = stringResource(if (busy) Res.string.account_signing_in else Res.string.account_sign_in),
                onClick = submit,
                enabled = canSubmit,
            )
            LukiaButton(
                label = stringResource(Res.string.account_sign_out),
                onClick = actions.onSignOut,
                variant = ButtonVariant.Secondary,
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun ProblemText(problem: AccountProblem) {
    Text(
        text = problem.displayMessage(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
