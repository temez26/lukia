package io.lukia.features.profile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayMessage
import io.lukia.core.presentation.displayTitle
import io.lukia.features.profile.domain.ProfileAccount
import io.lukia.features.profile.domain.ReadingStats
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.domain.Usernames
import io.lukia.features.profile.presentation.components.RecentReadingList
import io.lukia.features.profile.presentation.components.StatTileGrid
import io.lukia.features.profile.presentation.components.UsernameField
import io.lukia.features.profile.presentation.components.tiles
import io.lukia.shared.presentation.components.ErrorState
import io.lukia.shared.presentation.components.GroupSection
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.ReadingProgressBar
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow

/**
 * Room held for the figures while they are being counted, so the page does not
 * jump when they land. About the height the six tiles take on a phone.
 */
private val StatsPlaceholderHeight: Dp = 132.dp

/** The overall bar, drawn heavier than the hairline a row carries. */
private val LibraryBarHeight: Dp = 8.dp

/**
 * How much reading there has been, in figures.
 *
 * The three states are handled here rather than through `UiStateContent`,
 * which fills the screen it is given: this is one section of a page that is
 * already worth looking at without it - the reader's name is above it and does
 * not come from the library - so a failure to count books must not take the
 * whole profile down with it.
 */
@Composable
internal fun ReadingSection(
    summary: UiState<ReadingSummary>,
    onRetry: () -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    GroupSection(title = "Your reading", color = groupColor, modifier = modifier) {
        when (summary) {
            is UiState.Loading -> LoadingState(
                modifier = Modifier.fillMaxWidth().height(StatsPlaceholderHeight),
            )

            is UiState.Failure -> ErrorState(
                title = summary.error.displayTitle(),
                message = summary.error.displayMessage(),
                actionLabel = "Try again",
                onAction = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )

            is UiState.Success -> ReadingFigures(stats = summary.data.stats)
        }
    }
}

/**
 * The figures themselves: how far through the library as a whole, then the six
 * counts behind it.
 *
 * The bar comes first because it is the only one of them that answers "how am I
 * doing" rather than "how much is there", and the counts read as its parts.
 */
@Composable
private fun ReadingFigures(
    stats: ReadingStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        if (stats.isEmpty) {
            Text(
                text = "Nothing to count yet. Import a book and this fills in as you read.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            return@Column
        }

        stats.libraryFraction?.let { fraction ->
            LibraryProgress(fraction = fraction)
        }

        // Three across where there is room for the labels and two where there is
        // not, so "Not started" is never cut to "Not st…".
        StatTileGrid(tiles = stats.tiles(), columns = if (isWideWindow) 3 else 2)
    }
}

/** How far through everything the reader owns, as a bar and as a sentence. */
@Composable
private fun LibraryProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ReadingProgressBar(
            fraction = fraction,
            modifier = Modifier.fillMaxWidth().height(LibraryBarHeight),
        )

        Text(
            // Floored, and never rounded up to a hundred: a library with one
            // book left in it has not been finished.
            text = "${(fraction * 100).toInt().coerceIn(0, 100)}% of your library read",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.md),
        )
    }
}

/**
 * What the reader has had open lately.
 *
 * Left out entirely while the figures are still being read, rather than shown
 * as a second spinner: one wait on a page is a page loading, and two is a page
 * that looks broken. It is left out for a reader with nothing read yet for the
 * same reason the home screen drops an empty row.
 */
@Composable
internal fun ActivitySection(
    summary: UiState<ReadingSummary>,
    onOpenBook: (String) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val activity = (summary as? UiState.Success)?.data?.recent.orEmpty()

    if (activity.isEmpty()) return

    GroupSection(
        title = "Recently read",
        color = groupColor,
        // The rows carry their own vertical padding, so the card asks for none.
        padding = 0.dp,
        modifier = modifier,
    ) {
        RecentReadingList(activity = activity, onOpenBook = onOpenBook)
    }
}

/**
 * The name, editable in place.
 *
 * Held locally while it is being typed rather than read back from the store on
 * every keystroke: a half-typed name is not a name, and the store refuses one -
 * so a field showing what was stored would erase characters as they were
 * entered. Every usable value is written as it is reached, which is how the rest
 * of this app's settings behave.
 */
@Composable
internal fun NameSection(
    profile: UserProfile,
    onRename: (String) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    var typed by rememberSaveable(profile.username) { mutableStateOf(profile.username) }

    GroupSection(
        title = "Your name",
        color = groupColor,
        footnote = "Shown on this screen. Change it whenever you like.",
        modifier = modifier,
    ) {
        UsernameField(
            value = typed,
            onValueChange = {
                typed = it
                onRename(it)
            },
            problem = Usernames.problemWith(typed),
        )
    }
}

/**
 * Where the profile is kept, and what that means.
 *
 * It says what the app does today and makes no promise about tomorrow. Signing
 * in is a way of carrying a profile to a second device, which this app cannot do
 * yet - and a greyed-out button for it would be an advertisement rather than a
 * setting.
 */
@Composable
internal fun AccountSection(
    account: ProfileAccount,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    GroupSection(
        title = "Account",
        color = groupColor,
        footnote = "No account is needed to read. If signing in is ever added, " +
            "it will be a way to carry this profile to another device - not a way in.",
        modifier = modifier,
    ) {
        Text(
            text = account.detail(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun ProfileAccount.detail(): String = when (this) {
    ProfileAccount.OnThisDevice ->
        "Your profile, your library and every position in it are stored on this device."
}
