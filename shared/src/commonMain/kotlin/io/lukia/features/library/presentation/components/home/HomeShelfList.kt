package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.components.book.CoverTransferOverlay
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.displayTitle
import io.lukia.features.library.presentation.model.menuHeader
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_read_title
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Whether the shelf is drawn as a [HomeShelfList] rather than a row of covers. */
internal val HomeShelf.isListed: Boolean get() = this == HomeShelf.RecentlyOpened

/** As many books as the list shows; the rest are behind its "See all". */
private const val ListLength = 3

private val ListCoverWidth: Dp = 44.dp
private val ListCoverMonogramSize = 14.sp
private val ProgressHeight: Dp = 3.dp

/**
 * A shelf as a short list rather than a row of covers: a small cover beside the
 * title, who wrote it and how far through it is. For the books just opened,
 * where how far each one got is the point and a row of covers has no room to
 * say it.
 */
@Composable
internal fun HomeShelfList(
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

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding)) {
        ShelfHeader(title = row.shelf.displayTitle(), onOpen = { onOpenShelf(row.shelf) })

        GroupCard(modifier = Modifier.padding(top = Space.sm)) {
            Column {
                row.books.take(ListLength).forEachIndexed { index, book ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = Space.lg),
                        )
                    }

                    HomeBookListItem(
                        book = book,
                        actions = actions,
                        menuExpanded = openMenu == HomeMenu.Book(book.location),
                        onMenuExpandedChange = { expanded ->
                            onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                        },
                        transfer = transfers[book.location],
                    )
                }
            }
        }
    }
}

/**
 * One book in a [HomeShelfList]. Pressed, it opens the book; held, it opens the
 * same menu as a cover on a row - see `HomeBookCard`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBookListItem(
    book: LibraryBook,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    transfer: BookTransfer?,
) {
    val title = book.document.bookTitle()
    val shape = MaterialTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier
            .fillMaxWidth()
            .menuGesture(
                interactionSource = remember { MutableInteractionSource() },
                onClick = { actions.onOpen(book) },
                onMenu = { onMenuExpandedChange(true) },
                menuLabel = stringResource(Res.string.book_actions),
                clickLabel = stringResource(Res.string.book_read_title, title),
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        Box(modifier = Modifier.width(ListCoverWidth)) {
            BookCover(
                cover = book.cover,
                title = title,
                shape = shape,
                monogramFontSize = ListCoverMonogramSize,
                modifier = Modifier.fillMaxWidth(),
            )

            CoverTransferOverlay(
                document = book.document,
                transfer = transfer,
                modifier = Modifier.matchParentSize().clip(shape),
            )

            BookActionsMenu(
                expanded = menuExpanded,
                isFavorite = book.document.isFavorite,
                onToggleFavorite = { actions.onToggleFavorite(book) },
                onRefresh = { actions.onRefresh(book) },
                onDelete = { actions.onDelete(book) },
                canRemoveFromDevice = !book.document.awaitsDownload,
                onDismissRequest = { onMenuExpandedChange(false) },
                onShowDetails = { actions.onShowDetails(book) },
                onSelectBooks = actions.onSelectBooks,
                onRemoveFromServer = actions.removeFromServer(book.document),
                onMarkUnread = book.progress?.let { { actions.onMarkUnread(book) } },
                header = book.menuHeader(),
            )
        }

        ListItemText(book = book, title = title, modifier = Modifier.weight(1f))

        book.progress?.percent?.let { percent ->
            Text(
                text = stringResource(Res.string.reader_percent, percent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The title, who wrote it, and - for a book begun - a thin bar of how far through. */
@Composable
private fun ListItemText(book: LibraryBook, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        book.document.metadata?.authorLine()?.let { authors ->
            Text(
                text = authors,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        book.progress?.fraction?.let { fraction ->
            Box(
                modifier = Modifier
                    .padding(top = Space.sm)
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
