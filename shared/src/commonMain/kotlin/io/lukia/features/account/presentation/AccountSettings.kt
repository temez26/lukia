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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.LastSignIn
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.settings.presentation.screens.LabelledField
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.resources.Res
import io.lukia.resources.account_create
import io.lukia.resources.account_create_admin
import io.lukia.resources.account_delete
import io.lukia.resources.account_delete_message
import io.lukia.resources.account_delete_title
import io.lukia.resources.account_footnote
import io.lukia.resources.account_invite_code
import io.lukia.resources.account_needs_sign_in
import io.lukia.resources.account_password
import io.lukia.resources.account_setup_footnote
import io.lukia.resources.account_sign_in
import io.lukia.resources.account_sign_out
import io.lukia.resources.account_signed_in
import io.lukia.resources.account_signed_in_admin
import io.lukia.resources.account_signing_in
import io.lukia.resources.account_title
import io.lukia.resources.account_use_invite
import io.lukia.resources.account_use_password
import io.lukia.resources.account_username
import io.lukia.resources.action_delete
import io.lukia.resources.settings_address
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
            onUseInvite = viewModel::useInvite,
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
    val onUseInvite: (Boolean) -> Unit = {},
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

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text(
            text = stringResource(Res.string.account_needs_sign_in, account.serverName),
            style = MaterialTheme.typography.bodyMedium,
        )
        LabelledField(label = stringResource(Res.string.account_password)) {
            SettingsTextField(value = password, onValueChange = { password = it }, placeholder = "", secret = true)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            LukiaButton(
                label = stringResource(if (busy) Res.string.account_signing_in else Res.string.account_sign_in),
                onClick = { actions.onSignInAgain(password) },
                enabled = !busy && password.isNotEmpty(),
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

/**
 * Everything signing in needs, on one form: where the server is, who the reader
 * is, and their password - plus an invite code when the account is new. Filled
 * in from the last sign-in, so coming back asks only for the password.
 */
@Composable
private fun SignInForm(form: AccountForm, busy: Boolean, last: LastSignIn?, actions: AccountActions) {
    // Saved across a rotation: none of these is a secret, and retyping them is
    // the kind of small cost that makes somebody give up on setting this up.
    var address by rememberSaveable { mutableStateOf(last?.address.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(last?.username.orEmpty()) }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    // Never saved: a saved state outlives the page, and a password should not.
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (form is AccountForm.SetUp) {
            Text(
                text = stringResource(Res.string.account_setup_footnote, form.serverName),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LabelledField(label = stringResource(Res.string.settings_address)) {
            SettingsTextField(value = address, onValueChange = { address = it }, placeholder = ExampleAddress)
        }
        if (form == AccountForm.Invite) {
            LabelledField(label = stringResource(Res.string.account_invite_code)) {
                SettingsTextField(value = inviteCode, onValueChange = { inviteCode = it }, placeholder = "")
            }
        }
        LabelledField(label = stringResource(Res.string.account_username)) {
            SettingsTextField(value = username, onValueChange = { username = it }, placeholder = "")
        }
        LabelledField(label = stringResource(Res.string.account_password)) {
            SettingsTextField(value = password, onValueChange = { password = it }, placeholder = "", secret = true)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            SubmitButton(
                form = form,
                busy = busy,
                complete = address.isNotBlank() &&
                    username.isNotBlank() &&
                    password.isNotEmpty() &&
                    (form != AccountForm.Invite || inviteCode.isNotBlank()),
                onClick = { actions.onSubmit(address, username, password, inviteCode) },
            )
            if (form !is AccountForm.SetUp) {
                LukiaButton(
                    label = stringResource(
                        if (form == AccountForm.Invite) {
                            Res.string.account_use_password
                        } else {
                            Res.string.account_use_invite
                        },
                    ),
                    onClick = { actions.onUseInvite(form != AccountForm.Invite) },
                    variant = ButtonVariant.Ghost,
                    enabled = !busy,
                )
            }
        }
    }
}

/** Signs in, or creates the account or the admin - whichever the form is for - once it is filled in. */
@Composable
private fun SubmitButton(form: AccountForm, busy: Boolean, complete: Boolean, onClick: () -> Unit) {
    LukiaButton(
        label = stringResource(
            when {
                busy -> Res.string.account_signing_in
                form is AccountForm.SetUp -> Res.string.account_create_admin
                form == AccountForm.Invite -> Res.string.account_create
                else -> Res.string.account_sign_in
            },
        ),
        onClick = onClick,
        enabled = !busy && complete,
    )
}

@Composable
private fun ProblemText(problem: AccountProblem) {
    Text(
        text = problem.displayMessage(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** What a server address looks like, as a hint in the empty field. An example, not words, so not translated. */
private const val ExampleAddress = "192.168.1.10:8080"
