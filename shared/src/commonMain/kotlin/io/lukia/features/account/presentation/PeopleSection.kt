package io.lukia.features.account.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import io.lukia.api.AccountRules
import io.lukia.features.account.domain.Person
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.settings.presentation.screens.LabelledField
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.account_new_password
import io.lukia.resources.account_people_title
import io.lukia.resources.account_person_admin
import io.lukia.resources.account_person_books_added
import io.lukia.resources.account_person_favourites
import io.lukia.resources.account_person_finished
import io.lukia.resources.account_person_last_read_days
import io.lukia.resources.account_person_last_read_today
import io.lukia.resources.account_person_last_read_yesterday
import io.lukia.resources.account_person_no_reading_time
import io.lukia.resources.account_person_read_for
import io.lukia.resources.account_person_remove
import io.lukia.resources.account_person_reset_password
import io.lukia.resources.account_person_started
import io.lukia.resources.account_remove_confirm
import io.lukia.resources.account_remove_message
import io.lukia.resources.account_remove_title
import io.lukia.resources.account_reset_done
import io.lukia.resources.account_reset_message
import io.lukia.resources.account_reset_title
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.surface.ConfirmDialog
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val Separator = " · "

/** What the admin can do to someone else's account from People. */
class PeopleActions(
    val onResetPassword: (username: String, password: String) -> Unit = { _, _ -> },
    val onRemove: (username: String) -> Unit = {},
)

/**
 * Everyone with an account on the server, each with totals of what they have
 * read there, and for everyone but the admin looking, a password reset and
 * removal - both asked about first.
 *
 * Your own row offers neither: removing yourself is Delete account above, and a
 * reset from here would sign this device out with a password nobody chose to
 * change.
 */
@Composable
internal fun PeopleSection(
    state: AdministrationUiState,
    people: List<Person>,
    today: LocalDate,
    actions: PeopleActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    var resetting by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }

    SettingsSection(
        title = stringResource(Res.string.account_people_title),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            people.forEach { person ->
                PersonRow(
                    person = person,
                    today = today,
                    canChange = person.username != state.you,
                    busy = state.busy,
                    onResetPassword = { resetting = person.username },
                    onRemove = { removing = person.username },
                )
            }

            // A reset is said, since the admin has a password to pass on.
            state.passwordResetFor?.let { username ->
                Text(
                    text = stringResource(Res.string.account_reset_done, username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            state.peopleProblem?.let { problem ->
                Text(
                    text = problem.displayMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    resetting?.let { username ->
        ResetPasswordDialog(username, onReset = actions.onResetPassword, onDismissRequest = { resetting = null })
    }
    removing?.let { username ->
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.account_remove_title, username),
            message = stringResource(Res.string.account_remove_message, username),
            confirmLabel = stringResource(Res.string.account_remove_confirm),
            onConfirm = { actions.onRemove(username) },
            onDismissRequest = { removing = null },
            destructive = true,
        )
    }
}

@Composable
private fun PersonRow(
    person: Person,
    today: LocalDate,
    canChange: Boolean,
    busy: Boolean,
    onResetPassword: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        PersonName(person)
        Detail(
            listOf(
                pluralStringResource(Res.plurals.account_person_books_added, person.booksAdded, person.booksAdded),
                pluralStringResource(Res.plurals.account_person_started, person.booksStarted, person.booksStarted),
                pluralStringResource(Res.plurals.account_person_finished, person.booksFinished, person.booksFinished),
                pluralStringResource(Res.plurals.account_person_favourites, person.favourites, person.favourites),
            ).joinToString(Separator),
        )
        Detail(readingLine(person, today))

        if (canChange) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(top = Space.xs),
            ) {
                LukiaButton(
                    label = stringResource(Res.string.account_person_reset_password),
                    onClick = onResetPassword,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Compact,
                    enabled = !busy,
                )
                LukiaButton(
                    label = stringResource(Res.string.account_person_remove),
                    onClick = onRemove,
                    variant = ButtonVariant.Destructive,
                    size = ButtonSize.Compact,
                    enabled = !busy,
                )
            }
        }
    }
}

@Composable
private fun PersonName(person: Person) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = person.username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            // Wraps rather than pushing the admin mark off the end of the row.
            modifier = Modifier.weight(1f, fill = false),
        )
        if (person.isAdmin) {
            Text(
                text = stringResource(Res.string.account_person_admin),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/** Asks for the new password, and lets it go only once the server would take it. */
@Composable
private fun ResetPasswordDialog(
    username: String,
    onReset: (username: String, password: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val acceptable = password.length in AccountRules.MinPasswordLength..AccountRules.MaxPasswordLength

    ConfirmDialog(
        visible = true,
        title = stringResource(Res.string.account_reset_title, username),
        message = stringResource(Res.string.account_reset_message, username),
        confirmLabel = stringResource(Res.string.account_person_reset_password),
        onConfirm = { onReset(username, password) },
        onDismissRequest = onDismissRequest,
        confirmEnabled = acceptable,
    ) {
        LabelledField(label = stringResource(Res.string.account_new_password)) {
            SettingsTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "",
                secret = true,
                autofill = ContentType.NewPassword,
                imeAction = ImeAction.Go,
                onImeAction = {
                    if (acceptable) {
                        onDismissRequest()
                        onReset(username, password)
                    }
                },
            )
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun readingLine(person: Person, today: LocalDate): String {
    val lastReadAt = person.lastReadAt ?: return stringResource(Res.string.account_person_no_reading_time)

    return stringResource(Res.string.account_person_read_for, readingTime(person.readingMs)) +
        Separator + lastRead(lastReadAt, today)
}

@Composable
private fun lastRead(at: Instant, today: LocalDate): String {
    val days = at.toLocalDateTime(TimeZone.currentSystemDefault()).date.daysUntil(today).coerceAtLeast(0)

    return when (days) {
        0 -> stringResource(Res.string.account_person_last_read_today)
        1 -> stringResource(Res.string.account_person_last_read_yesterday)
        else -> pluralStringResource(Res.plurals.account_person_last_read_days, days, days)
    }
}
