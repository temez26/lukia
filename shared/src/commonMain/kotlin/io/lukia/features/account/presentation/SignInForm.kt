package io.lukia.features.account.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import io.lukia.features.account.domain.LastSignIn
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.settings.presentation.screens.LabelledField
import io.lukia.resources.Res
import io.lukia.resources.account_create
import io.lukia.resources.account_create_admin
import io.lukia.resources.account_invite_code
import io.lukia.resources.account_invite_footnote
import io.lukia.resources.account_password
import io.lukia.resources.account_setup_footnote
import io.lukia.resources.account_sign_in
import io.lukia.resources.account_signing_in
import io.lukia.resources.account_use_password
import io.lukia.resources.account_username
import io.lukia.resources.settings_address
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Everything signing in needs, on one form: where the server is, who the reader
 * is, and their password - plus an invite code when the account is new. Filled
 * in from the last sign-in, so coming back asks only for the password.
 */
@Composable
internal fun SignInForm(form: AccountForm, busy: Boolean, last: LastSignIn?, actions: AccountActions) {
    // Saved across a rotation: none of these is a secret, and retyping them is
    // the kind of small cost that makes somebody give up on setting this up.
    var address by rememberSaveable { mutableStateOf(last?.address.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(last?.username.orEmpty()) }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    // Never saved: a saved state outlives the page, and a password should not.
    var password by remember { mutableStateOf("") }

    val inviteField = remember { FocusRequester() }
    val usernameField = remember { FocusRequester() }
    val passwordField = remember { FocusRequester() }

    val complete = address.isNotBlank() &&
        username.isNotBlank() &&
        password.isNotEmpty() &&
        (form != AccountForm.Invite || inviteCode.isNotBlank())
    val canSubmit = complete && !busy
    val submit = { if (canSubmit) actions.onSubmit(address, username, password, inviteCode) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        FormIntro(form)
        LeadingField(
            label = stringResource(Res.string.settings_address),
            value = address,
            onValueChange = { address = it },
            next = if (form == AccountForm.Invite) inviteField else usernameField,
            placeholder = ExampleAddress,
        )
        if (form == AccountForm.Invite) {
            LeadingField(
                label = stringResource(Res.string.account_invite_code),
                value = inviteCode,
                onValueChange = { inviteCode = it },
                next = usernameField,
                focusRequester = inviteField,
            )
        }
        LeadingField(
            label = stringResource(Res.string.account_username),
            value = username,
            onValueChange = { username = it },
            next = passwordField,
            focusRequester = usernameField,
            autofill = ContentType.Username,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            // A new account's password is one for the manager to suggest and
            // save, not to fill from an account it already has.
            autofill = if (form == AccountForm.SignIn) ContentType.Password else ContentType.NewPassword,
            onGo = submit,
            focusRequester = passwordField,
        )

        SignInActions(form = form, busy = busy, canSubmit = canSubmit, onSubmit = submit, actions = actions)
    }
}

/** What a form that is not plain signing in has to say first: whose admin is being made, or why a code is asked for. */
@Composable
private fun FormIntro(form: AccountForm) {
    val text = when (form) {
        is AccountForm.SetUp -> stringResource(Res.string.account_setup_footnote, form.serverName)
        // Only reached from Create account, on a server too old to have sign-up:
        // the reader asked for an account and has to be told why a code appeared.
        AccountForm.Invite -> stringResource(Res.string.account_invite_footnote)
        AccountForm.SignIn, AccountForm.SignUp -> return
    }

    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** A field with another after it, which the keyboard's action key moves on to. */
@Composable
private fun LeadingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    next: FocusRequester,
    focusRequester: FocusRequester? = null,
    placeholder: String = "",
    autofill: ContentType? = null,
) {
    LabelledField(label = label) {
        SettingsTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            autofill = autofill,
            imeAction = ImeAction.Next,
            onImeAction = { next.requestFocus() },
            focusRequester = focusRequester,
        )
    }
}

/** The last field of a form, whose action key submits it. */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    autofill: ContentType,
    onGo: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    LabelledField(label = stringResource(Res.string.account_password)) {
        SettingsTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "",
            secret = true,
            autofill = autofill,
            imeAction = ImeAction.Go,
            onImeAction = onGo,
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun SignInActions(
    form: AccountForm,
    busy: Boolean,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    actions: AccountActions,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SubmitButton(form = form, busy = busy, enabled = canSubmit, onClick = onSubmit)

        if (form !is AccountForm.SetUp) {
            val creating = form != AccountForm.SignIn
            LukiaButton(
                label = stringResource(if (creating) Res.string.account_use_password else Res.string.account_create),
                onClick = { actions.onCreateAccount(!creating) },
                variant = ButtonVariant.Ghost,
                enabled = !busy,
            )
        }
    }
}

/** Signs in, or creates the account or the admin - whichever the form is for - once it is filled in. */
@Composable
private fun SubmitButton(form: AccountForm, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    LukiaButton(
        label = stringResource(
            when {
                busy -> Res.string.account_signing_in
                form is AccountForm.SetUp -> Res.string.account_create_admin
                form == AccountForm.SignUp || form == AccountForm.Invite -> Res.string.account_create
                else -> Res.string.account_sign_in
            },
        ),
        onClick = onClick,
        enabled = enabled,
    )
}

/** What a server address looks like, as a hint in the empty field. An example, not words, so not translated. */
private const val ExampleAddress = "192.168.1.10:8080"
