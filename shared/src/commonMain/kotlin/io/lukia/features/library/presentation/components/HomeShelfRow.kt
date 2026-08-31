package io.lukia.features.library.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.HomeBookActions
import io.lukia.features.library.presentation.HomeMenu
import io.lukia.features.library.presentation.HomeRow
import io.lukia.features.library.presentation.HomeShelf
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.shared.presentation.components.DisclosureChevron
import io.lukia.shared.presentation.components.hoverHighlight
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * How much larger a cover is drawn on the row a reader came back for than on
 * every other row.
 *
 * Where you left off is the reason the screen exists, so it is given the larger
 * card. The rows under it are a wall of jackets to scan, and a smaller cover
 * puts more of them within a flick.
 */
private const val ContinueCardScale = 1.27f

/** The chevron after "See All", at the size a label wears rather than a button. */
private val SeeAllChevronSize: Dp = 14.dp

/**
 * One row of the home screen: what it is called, a way through to all of it, and
 * the books themselves scrolling sideways.
 *
 * Sideways because a home screen is a summary. Three rows down the page, each
 * showing what will fit and no more, answers "what am I reading, what is new,
 * what did I keep" in one screen - where three vertical lists would answer the
 * first question and bury the other two.
 */
@Composable
internal fun HomeShelfRow(
    row: HomeRow,
    actions: HomeBookActions,
    openMenu: HomeMenu?,
    onOpenMenuChange: (HomeMenu?) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    // Off the same measurement the library grid sizes its cells from, rather
    // than off two constants of its own. Written as fixed dp, a home row stayed
    // at phone proportions on every window the app opens in: an iPad running
    // the mobile shell grew its shelf to 142dp cells and went on sampling them
    // at 104, so the two screens showing the same books disagreed about how big
    // a book is. Identical on a phone, which is where these numbers came from.
    val cardWidth = if (row.shelf == HomeShelf.Recent) {
        dimens.bookCellMinWidth * ContinueCardScale
    } else {
        dimens.bookCellMinWidth
    }

    Column(modifier = modifier.fillMaxWidth()) {
        RowHeader(
            title = row.shelf.title,
            onSeeAll = { onSeeAll(row.shelf.seeAll()) },
            modifier = Modifier.padding(horizontal = dimens.screenPadding),
        )

        LazyRow(
            // The row runs to the window's edges while its cards start at the
            // screen's own inset, so a book scrolled to the end of it passes off
            // the edge rather than stopping short of one.
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.lg),
        ) {
            items(
                items = row.books,
                key = { book -> book.location },
                contentType = { "homeBook" },
            ) { book ->
                HomeBookCard(
                    book = book,
                    width = cardWidth,
                    actions = actions,
                    menuExpanded = openMenu == HomeMenu.Book(row.shelf, book.location),
                    onMenuExpandedChange = { expanded ->
                        onOpenMenuChange(
                            HomeMenu.Book(row.shelf, book.location).takeIf { expanded },
                        )
                    },
                )
            }
        }
    }
}

/**
 * The row's name, and the way to the whole of what it is a sample of.
 *
 * "See All" lands on the library tab already narrowed to this row's subject -
 * see [HomeShelf.seeAll]. It is a way deeper into the same idea rather than a
 * different screen, which is why it is a quiet label beside the heading and not
 * a button.
 */
@Composable
private fun RowHeader(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .hoverHighlight(interactionSource, shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "See all $title",
                    onClick = onSeeAll,
                )
                .padding(horizontal = Space.xs, vertical = Space.xxs),
        ) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.size(Space.xxs))

            // The app's one disclosure mark, turned to point the way this
            // control goes rather than drawn a second time facing sideways.
            DisclosureChevron(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(SeeAllChevronSize)
                    .graphicsLayer { rotationZ = -90f },
            )
        }
    }
}
