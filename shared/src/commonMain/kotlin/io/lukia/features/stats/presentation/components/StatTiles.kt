package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.core.infrastructure.formatCount
import io.lukia.features.stats.domain.ReadingStats
import io.lukia.resources.Res
import io.lukia.resources.stats_figure_and_label
import io.lukia.resources.stats_tile_books
import io.lukia.resources.stats_tile_favourites
import io.lukia.resources.stats_tile_finished
import io.lukia.resources.stats_tile_not_started
import io.lukia.resources.stats_tile_pages_read
import io.lukia.resources.stats_tile_reading
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One number worth showing, and what it counts. */
@Immutable
internal data class StatTile(val value: String, val label: String)

/**
 * The reader's figures, in the order they answer the question "how am I doing".
 *
 * Six rather than everything countable. What is on the go comes first because it
 * is the only one that is about today; the rest run down the same arc the home
 * screen's rows do - finished, waiting, kept - and the size of the library is
 * last, being the context for the others rather than an achievement.
 *
 * Pages are in there because they are the one figure that grows on an evening
 * when no book was started or finished, which is most evenings.
 *
 * The two labels that follow their own count are plural resources rather than a
 * one-or-many `if`. English has two forms and picks between them on a boolean;
 * Polish has three and Arabic six, and none of them is reachable that way.
 */
@Composable
internal fun ReadingStats.tiles(): List<StatTile> = listOf(
    StatTile(
        value = formatCount(booksReading),
        label = stringResource(Res.string.stats_tile_reading),
    ),
    StatTile(
        value = formatCount(booksFinished),
        label = stringResource(Res.string.stats_tile_finished),
    ),
    StatTile(
        // Grouped, unlike a page number. This is a quantity of pages rather than
        // a name for one, and a reader two years in has five figures of them.
        value = formatCount(pagesRead),
        label = pluralStringResource(Res.plurals.stats_tile_pages_read, pagesRead),
    ),
    StatTile(
        value = formatCount(booksUnread),
        label = stringResource(Res.string.stats_tile_not_started),
    ),
    StatTile(
        value = formatCount(favourites),
        label = stringResource(Res.string.stats_tile_favourites),
    ),
    StatTile(
        value = formatCount(booksInLibrary),
        label = pluralStringResource(Res.plurals.stats_tile_books, booksInLibrary),
    ),
)

/**
 * The figures laid out in a grid of [columns], padded so the last row lines up
 * with the ones above it.
 *
 * Laid out by hand rather than with a lazy grid: there are six of these, they
 * are known in advance, and a scrolling container inside a scrolling page is
 * both wasted machinery and a trap for the finger.
 */
@Composable
internal fun StatTileGrid(
    tiles: List<StatTile>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        tiles.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    StatFigure(
                        value = tile.value,
                        label = tile.label,
                        modifier = Modifier.weight(1f),
                    )
                }

                // A short last row keeps its cells the width of the ones above
                // rather than stretching to fill the gap, so the columns read as
                // columns.
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * One figure: the number, and the word for what it counts under it.
 *
 * The one shape every count on this screen is drawn in, whether it is a book, an
 * hour or a sitting - a page of statistics whose figures were each set by the
 * section that happened to own them is a page that reads as several pages.
 *
 * The two are announced as one phrase rather than as a number followed by a
 * label, which is what a screen reader would otherwise read them as while
 * running down a grid.
 */
@Composable
internal fun StatFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    // Read before the modifier chain: `clearAndSetSemantics` is not a
    // composable scope.
    val spoken = stringResource(Res.string.stats_figure_and_label, value, label)

    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            text = value,
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Two lines rather than one. These sit three to a row on a phone,
        // which is about 115dp a cell: "Pages read" fits and "Gelesene Seiten"
        // does not, and a label ellipsised to "Gelesene S…" has lost the word
        // that says what the figure above it counts.
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}
