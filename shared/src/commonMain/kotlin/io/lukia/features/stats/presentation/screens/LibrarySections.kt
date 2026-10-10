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
import io.lukia.features.stats.domain.ReadingActivity
import io.lukia.features.stats.domain.ReadingStats
import io.lukia.features.stats.presentation.components.RecentReadingList
import io.lukia.features.stats.presentation.components.StatTileGrid
import io.lukia.features.stats.presentation.components.tiles
import io.lukia.resources.Res
import io.lukia.resources.stats_nothing_to_count
import io.lukia.resources.stats_recently_read
import io.lukia.resources.stats_share_read
import io.lukia.resources.stats_your_library
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/** The overall bar, drawn heavier than the hairline a row carries. */
private val LibraryBarHeight: Dp = 8.dp

/**
 * What is on the shelf, and how far through it the reading has got: the library
 * as a whole first, then the six counts behind it.
 *
 * The bar comes first because it is the only one of them that answers "how am I
 * doing" rather than "how much is there", and the counts read as its parts.
 */
@Composable
internal fun LibrarySection(
    stats: ReadingStats,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    GroupSection(
        title = stringResource(Res.string.stats_your_library),
        color = groupColor,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            if (stats.isEmpty) {
                Text(
                    text = stringResource(Res.string.stats_nothing_to_count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                return@Column
            }

            stats.libraryFraction?.let { fraction ->
                LibraryProgress(fraction = fraction)
            }

            // Three across where there is room for the labels and two where
            // there is not, so "Not started" is never cut to "Not st…".
            StatTileGrid(tiles = stats.tiles(), columns = if (isWideWindow) 3 else 2)
        }
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
            text = stringResource(
                Res.string.stats_share_read,
                (fraction * 100).toInt().coerceIn(0, 100),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.md),
        )
    }
}

/**
 * What the reader has had open lately.
 *
 * Left out for a reader with nothing read yet, for the same reason the home
 * screen drops an empty row: a card headed "Recently read" with nothing under it
 * says less than no card at all.
 */
@Composable
internal fun ActivitySection(
    activity: List<ReadingActivity>,
    onOpenBook: (String) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    if (activity.isEmpty()) return

    GroupSection(
        title = stringResource(Res.string.stats_recently_read),
        color = groupColor,
        // The rows carry their own vertical padding, so the card asks for none.
        padding = 0.dp,
        modifier = modifier,
    ) {
        RecentReadingList(activity = activity, onOpenBook = onOpenBook)
    }
}
