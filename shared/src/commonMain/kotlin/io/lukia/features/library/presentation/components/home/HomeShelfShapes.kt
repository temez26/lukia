package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.displayTitle
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.home_and_more
import io.lukia.resources.home_see_all_shelf
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.cover.BOOK_COVER_ASPECT_RATIO
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How many covers the fan holds; the rest are counted beside it. */
private const val FanSize = 3

private val FanCoverWidth: Dp = 76.dp

/** How far each cover in the fan sits to the right of the one under it. */
private val FanStep: Dp = 30.dp

/** Each cover's turn, top cover first: the hand leans from left to right. */
private val FanTurns = listOf(-9f, -2f, 7f)

private val FanShadow: Dp = 6.dp
private val FanMonogramSize = 14.sp

/**
 * Favourites as a fanned hand of covers, the newest on top, with its title and
 * how many more there are beside it. A press anywhere opens the whole shelf: a
 * fan is a sample to recognise, not a row to pick from.
 */
@Composable
internal fun HomeShelfFan(
    row: HomeRow,
    onOpenShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current
    val title = row.shelf.displayTitle()
    val fan = row.books.take(FanSize)
    val more = row.total - 1

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding)) {
        ShelfHeader(title = title, count = row.total, onOpen = { onOpenShelf(row.shelf) })

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.lg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md)
                .pressable(
                    interactionSource = remember { MutableInteractionSource() },
                    shape = MaterialTheme.shapes.medium,
                    onClickLabel = stringResource(Res.string.home_see_all_shelf, title),
                    onClick = { onOpenShelf(row.shelf) },
                )
                // The header above says the same, and is the one read out.
                .clearAndSetSemantics {},
        ) {
            CoverFan(books = fan)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fan.first().document.bookTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (more > 0) {
                    Text(
                        text = pluralStringResource(Res.plurals.home_and_more, more, more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Up to [FanSize] covers leaning into one another, the first on top. */
@Composable
private fun CoverFan(books: List<LibraryBook>) {
    Box(
        modifier = Modifier
            .width(FanCoverWidth + FanStep * (FanSize - 1) + Space.sm)
            .height(FanCoverWidth / BOOK_COVER_ASPECT_RATIO + Space.lg)
            // Room for the bottom cover's tilt, which otherwise leans
            // past the page's margin.
            .padding(start = Space.sm, top = Space.sm, bottom = Space.sm),
    ) {
        // Drawn bottom first, so the newest lies on top of the hand.
        books.indices.reversed().forEach { index ->
            val book = books[index]
            BookCover(
                cover = book.cover,
                title = book.document.bookTitle(),
                shape = MaterialTheme.shapes.small,
                monogramFontSize = FanMonogramSize,
                modifier = Modifier
                    .offset(x = FanStep * index)
                    .width(FanCoverWidth)
                    .graphicsLayer { rotationZ = FanTurns[index] }
                    .shadow(FanShadow, MaterialTheme.shapes.small),
            )
        }
    }
}

private val GridCoverWidth: Dp = 68.dp

/**
 * Unread as two rows of small covers scrolling sideways together: a pile is
 * mostly worth seeing as a pile, and two rows show twice as much of it as one.
 * Covers alone - at this size a title under each is unreadable clutter - with
 * the same press and long press as any cover on Home.
 */
@Composable
internal fun HomeShelfGrid(
    row: HomeRow,
    actions: HomeBookActions,
    openMenu: HomeMenu?,
    onOpenMenuChange: (HomeMenu?) -> Unit,
    onOpenShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier,
    /** Downloads under way, by book location - see `BookTransfers`. */
    transfers: Map<String, BookTransfer> = emptyMap(),
) {
    val dimens = LocalLukiaDimens.current
    val coverHeight = GridCoverWidth / BOOK_COVER_ASPECT_RATIO

    Column(modifier = modifier.fillMaxWidth()) {
        ShelfHeader(
            title = row.shelf.displayTitle(),
            count = row.total,
            onOpen = { onOpenShelf(row.shelf) },
            modifier = Modifier.padding(horizontal = dimens.screenPadding),
        )

        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md)
                .height(coverHeight * 2 + Space.md),
        ) {
            items(items = row.books, key = { book -> book.location }, contentType = { "homeCover" }) { book ->
                HomeBookCard(
                    book = book,
                    actions = actions,
                    menuExpanded = openMenu == HomeMenu.Book(book.location),
                    onMenuExpandedChange = { expanded ->
                        onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                    },
                    modifier = Modifier.width(GridCoverWidth),
                    transfer = transfers[book.location],
                    showText = false,
                )
            }
        }
    }
}
