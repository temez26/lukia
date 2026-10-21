package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.displayTitle
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.home_see_all
import io.lukia.resources.home_see_all_shelf
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * One row of the home screen: its title, which leads through to all of it, and
 * the books themselves scrolling sideways, straight on the page rather than on a
 * card - the covers are the row's surface, and a card around each row stacked
 * one box on another down the screen.
 *
 * The books just opened are the exception, drawn as a short list - see
 * [HomeShelfList].
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
    onOpenShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier,
    /** Downloads under way, by book location - see `BookTransfers`. */
    transfers: Map<String, BookTransfer> = emptyMap(),
) {
    if (row.shelf.isListed) {
        HomeShelfList(row, actions, openMenu, onOpenMenuChange, onOpenShelf, modifier, transfers)
        return
    }

    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        ShelfHeader(
            title = row.shelf.displayTitle(),
            onOpen = { onOpenShelf(row.shelf) },
            modifier = Modifier.padding(horizontal = dimens.screenPadding),
        )

        LazyRow(
            // The row runs to the screen's edges while its covers start at
            // the page's own inset, so a book scrolled to the end of it
            // passes under the edge rather than stopping short of one.
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
                    actions = actions,
                    menuExpanded = openMenu == HomeMenu.Book(book.location),
                    onMenuExpandedChange = { expanded ->
                        onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                    },
                    // Off the measurement the library grid sizes its cells
                    // from, so both screens agree how big a book is.
                    modifier = Modifier.width(dimens.bookCellMinWidth),
                    transfer = transfers[book.location],
                )
            }
        }
    }
}

/**
 * A shelf's name, as the way to the whole of what it is a sample of: the title,
 * and "See all" in the accent at the far end, the whole line one press.
 */
@Composable
internal fun ShelfHeader(
    title: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .pressable(
                interactionSource = interactionSource,
                shape = shape,
                onClickLabel = stringResource(Res.string.home_see_all_shelf, title),
                onClick = onOpen,
            )
            .padding(vertical = Space.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = stringResource(Res.string.home_see_all),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
