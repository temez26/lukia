package io.lukia.features.account.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.account.domain.Person
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
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
import io.lukia.resources.account_person_started
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val Separator = " · "

/** Everyone with an account on the server, each with totals of what they have read there. */
@Composable
internal fun PeopleSection(
    people: List<Person>,
    today: LocalDate,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.account_people_title),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            people.forEach { PersonRow(it, today) }
        }
    }
}

@Composable
private fun PersonRow(person: Person, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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
        Detail(
            listOf(
                pluralStringResource(Res.plurals.account_person_books_added, person.booksAdded, person.booksAdded),
                pluralStringResource(Res.plurals.account_person_started, person.booksStarted, person.booksStarted),
                pluralStringResource(Res.plurals.account_person_finished, person.booksFinished, person.booksFinished),
                pluralStringResource(Res.plurals.account_person_favourites, person.favourites, person.favourites),
            ).joinToString(Separator),
        )
        Detail(readingLine(person, today))
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
