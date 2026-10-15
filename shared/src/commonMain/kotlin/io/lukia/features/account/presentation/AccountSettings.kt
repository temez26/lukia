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
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.settings.presentation.screens.LabelledField
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.resources.Res
import io.lukia.resources.account_connect
import io.lukia.resources.account_connecting
import io.lukia.resources.account_create
import io.lukia.resources.account_footnote
import io.lukia.resources.account_invite_code
import io.lukia.resources.account_needs_sign_in
import io.lukia.resources.account_other_server
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
import io.lukia.resources.settings_address
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
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
        actions = AccountActions(
            onConnect = viewModel::connect,
            onSubmit = viewModel::submit,
            onUseInvite = viewModel::useInvite,
            onOtherServer = viewModel::otherServer,
            onSignInAgain = viewModel::signInAgain,
            onSignOut = viewModel::signOut,
        ),
        groupColor = groupColor,
    )
}

/** What the Account area can be asked to do. */
class AccountActions(
    val onConnect: (address: String) -> Unit = {},
    val onSubmit: (username: String, password: String, inviteCode: String) -> Unit = { _, _, _ -> },
    val onUseInvite: (Boolean) -> Unit = {},
    val onOtherServer: () -> Unit = {},
    val onSignInAgain: (password: String) -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
internal fun AccountSettingsContent(
    state: AccountUiState,
    actions: AccountActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.account_title),
        footnote = stringResource(Res.string.account_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            when (val status = state.status) {
                is AccountStatus.SignedIn -> SignedIn(status.account, actions)
                is AccountStatus.NeedsSignIn -> NeedsSignIn(status.account, state.busy, actions)
                AccountStatus.SignedOut -> when (val step = state.step) {
                    AccountStep.EnterAddress -> EnterAddress(state.busy, actions)
                    is AccountStep.Credentials -> EnterCredentials(step, state.busy, actions)
                }
            }

            state.problem?.let { ProblemText(it) }
        }
    }
}

@Composable
private fun SignedIn(account: ServerAccount, actions: AccountActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Text(
            text = stringResource(
                if (account.isAdmin) Res.string.account_signed_in_admin else Res.string.account_signed_in,
                account.serverName,
                account.username,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        LukiaButton(
            label = stringResource(Res.string.account_sign_out),
            onClick = actions.onSignOut,
            variant = ButtonVariant.Secondary,
        )
    }
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

@Composable
private fun EnterAddress(busy: Boolean, actions: AccountActions) {
    // Saved across a rotation: an address is no secret, and retyping one is the
    // kind of small cost that makes somebody give up on setting this up.
    var address by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        LabelledField(label = stringResource(Res.string.settings_address)) {
            SettingsTextField(value = address, onValueChange = { address = it }, placeholder = ExampleAddress)
        }
        LukiaButton(
            label = stringResource(if (busy) Res.string.account_connecting else Res.string.account_connect),
            onClick = { actions.onConnect(address) },
            enabled = !busy && address.isNotBlank(),
        )
    }
}

@Composable
private fun EnterCredentials(step: AccountStep.Credentials, busy: Boolean, actions: AccountActions) {
    var username by rememberSaveable(step.address) { mutableStateOf("") }
    var inviteCode by rememberSaveable(step.address) { mutableStateOf("") }
    // Never saved: a saved state outlives the page, and a password should not.
    var password by remember(step.address) { mutableStateOf("") }

    val creating = step.server.setupRequired || step.withInvite

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (step.server.setupRequired) {
            Text(
                text = stringResource(Res.string.account_setup_footnote, step.server.name),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (step.withInvite) {
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
                creating = creating,
                busy = busy,
                complete = username.isNotBlank() &&
                    password.isNotEmpty() &&
                    (!step.withInvite || inviteCode.isNotBlank()),
                onClick = { actions.onSubmit(username, password, inviteCode) },
            )
            if (!step.server.setupRequired) {
                LukiaButton(
                    label = stringResource(
                        if (step.withInvite) Res.string.account_use_password else Res.string.account_use_invite,
                    ),
                    onClick = { actions.onUseInvite(!step.withInvite) },
                    variant = ButtonVariant.Ghost,
                    enabled = !busy,
                )
            }
            LukiaButton(
                label = stringResource(Res.string.account_other_server),
                onClick = actions.onOtherServer,
                variant = ButtonVariant.Ghost,
                enabled = !busy,
            )
        }
    }
}

/** Signs in, or creates the account - whichever the form is for - once it is filled in. */
@Composable
private fun SubmitButton(creating: Boolean, busy: Boolean, complete: Boolean, onClick: () -> Unit) {
    LukiaButton(
        label = stringResource(
            when {
                busy -> Res.string.account_signing_in
                creating -> Res.string.account_create
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
