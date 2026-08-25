package io.lukia.features.profile.presentation.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.features.profile.domain.ReadingStats
import io.lukia.shared.presentation.theme.Space

/** One number worth showing, and what it counts. */
@Immutable
data class StatTile(val value: String, val label: String)

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
 */
fun ReadingStats.tiles(): List<StatTile> = listOf(
    StatTile(value = booksReading.toString(), label = "Reading"),
    StatTile(value = booksFinished.toString(), label = "Finished"),
    StatTile(value = pagesRead.toString(), label = pluralise(pagesRead, "Page read", "Pages read")),
    StatTile(value = booksUnread.toString(), label = "Not started"),
    StatTile(value = favourites.toString(), label = "Favourites"),
    StatTile(value = booksInLibrary.toString(), label = pluralise(booksInLibrary, "Book", "Books")),
)

private fun pluralise(count: Int, one: String, many: String): String =
    if (count == 1) one else many

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
                    StatTileCell(tile = tile, modifier = Modifier.weight(1f))
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
 * The two are announced as one phrase rather than as a number followed by a
 * label, which is what a screen reader would otherwise read them as while
 * running down the grid.
 */
@Composable
private fun StatTileCell(
    tile: StatTile,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${tile.value} ${tile.label}"
        },
    ) {
        Text(
            text = tile.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = tile.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}
