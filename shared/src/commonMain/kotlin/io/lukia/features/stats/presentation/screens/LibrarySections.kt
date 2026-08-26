package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayMessage
import io.lukia.core.presentation.displayTitle
import io.lukia.features.stats.domain.ReadingStats
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.presentation.components.RecentReadingList
import io.lukia.features.stats.presentation.components.StatTileGrid
import io.lukia.features.stats.presentation.components.tiles
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
 * What is on the shelf, and how far through it the reading has got.
 *
 * The three states are handled here rather than through `UiStateContent`, which
 * fills the screen it is given: this is one section of a page that has other
 * sections, and a failure to count books must not take the whole page down with
 * it.
 */
@Composable
internal fun LibrarySection(
    summary: UiState<ReadingSummary>,
    onRetry: () -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    GroupSection(title = "Your library", color = groupColor, modifier = modifier) {
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

            is UiState.Success -> LibraryFigures(stats = summary.data.stats)
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
private fun LibraryFigures(
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
