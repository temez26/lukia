package io.lukia.features.profile.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.features.profile.domain.ProfileAccount
import io.lukia.features.profile.domain.UserProfile
import io.lukia.shared.presentation.theme.Space

/**
 * Who the page is about: the monogram, the name, and where the profile is kept.
 *
 * Centred, unlike every other header in the app, because there is nothing beside
 * it to line up with and because this is the one screen whose subject is a
 * person rather than a list.
 *
 * Where the profile is kept is said out loud rather than left to be assumed.
 * Somebody who has never been asked to sign in is entitled to wonder where their
 * reading has gone, and one quiet line answers it before the question is asked.
 */
@Composable
internal fun ProfileHeader(
    profile: UserProfile,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        ProfileAvatar(initials = profile.initials)

        Text(
            text = profile.username,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xl),
        )

        Text(
            text = profile.account.summaryLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * The one line under the name saying where this profile lives.
 *
 * Matched on rather than asked a boolean question, so that a profile carried by
 * a signed-in account cannot be added without the compiler stopping here and
 * asking what this line should then say.
 */
private fun ProfileAccount.summaryLine(): String = when (this) {
    ProfileAccount.OnThisDevice -> "Reading on this device"
}
