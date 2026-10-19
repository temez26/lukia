package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.domain.LibraryLayout
import io.lukia.features.library.presentation.components.book.BookCard
import io.lukia.features.library.presentation.components.book.BookRow
import io.lukia.features.library.presentation.components.book.ImportBookCard
import io.lukia.features.library.presentation.components.book.ImportBookRow
import io.lukia.features.library.presentation.components.selection.CardSelection
import io.lukia.features.library.presentation.components.toolbar.LibraryHeader
import io.lukia.features.library.presentation.components.toolbar.libraryNavBarHeight
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.LibraryFacets
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.model.LibrarySelection
import io.lukia.features.library.presentation.model.LibraryShelf
import io.lukia.features.library.presentation.model.OpenMenu
import io.lukia.features.library.presentation.sessions.BookTransferUi
import io.lukia.features.library.presentation.sessions.LibraryImportUi
import io.lukia.resources.Res
import io.lukia.resources.library_no_matches_filtered
import io.lukia.resources.library_no_matches_filtered_term
import io.lukia.resources.library_no_matches_term
import io.lukia.resources.library_no_results_title
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.surface.EmptyState
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The first reveal slots belong to the header: the shelves, the filter row, and
 * the importing notice under them.
 */
private const val GridRevealOffset = 3

// The shelf itself: the books, the bar of filters above them, and what the
// screen says when a search matches none of them.

@Composable
internal fun LibraryGrid(
    books: List<LibraryBook>,
    gridState: LazyGridState,
    query: LibraryQuery,
    facets: LibraryFacets,
    onQueryChange: (LibraryQuery) -> Unit,
    importing: LibraryImportUi,
    reveal: LaunchReveal,
    onDocumentClick: (DocumentInfo) -> Unit,
    onDocumentDetails: (DocumentInfo) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onRefresh: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
    /** What is ticked, while the shelf is being selected from. */
    layout: LibraryLayout = LibraryLayout.Grid,
    onLayoutChange: (LibraryLayout) -> Unit = {},
    selection: LibrarySelection = LibrarySelection.None,
    onStartSelecting: () -> Unit = {},
    onToggle: (location: String) -> Unit = {},
    /** Puts a started book back to unread, from its menu. */
    onMarkUnread: ((DocumentInfo) -> Unit)? = null,
    /** Books downloading or uploading, for the progress their cards show. */
    transfers: BookTransferUi = BookTransferUi.None,
) {
    val dimens = LocalLukiaDimens.current

    LazyVerticalGrid(
        // One grid for both, differing only in how wide a cell is. A list is a
        // grid one column across, and building it as a lazy column of its own
        // would be a second copy of the header, the no-matches notice and the
        // import slot, kept in step by hand.
        columns = when (layout) {
            LibraryLayout.Grid -> GridCells.Adaptive(minSize = dimens.bookCellMinWidth)
            LibraryLayout.List -> GridCells.Fixed(1)
        },
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            // Content scrolls under the compact bar, so the header has to start
            // clear of where it sits at rest.
            top = libraryNavBarHeight() + Space.md,
            bottom = Clearance.listBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.gridRowSpacing),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header", contentType = "header") {
            LibraryHeader(
                query = query,
                facets = facets,
                onQueryChange = onQueryChange,
                openMenu = openMenu,
                onOpenMenuChange = onOpenMenuChange,
                importing = importing,
                reveal = reveal,
                layout = layout,
                onLayoutChange = onLayoutChange,
            )
        }

        // Only where the reader asked for something the library could not
        // answer. An unnarrowed library with nothing in it is not a dead end -
        // it is the empty state, which this grid is not showing at all.
        if (books.isEmpty() && query.isNarrowed) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "noMatches", contentType = "notice") {
                NoMatchesNotice(query)
            }
        }

        itemsIndexed(
            items = books,
            // Location identifies a document in the library, so keying on it lets
            // Compose track a card across reloads instead of rebuilding the whole
            // grid whenever an import appends to the list - and lets a card slide
            // to its new place when the order changes rather than being redrawn
            // somewhere else.
            key = { _, book -> book.location },
            // Every cell in the body of the grid is the same kind of thing, so
            // scrolling reuses the layout it already has rather than building a
            // new one for each row that comes into view.
            contentType = { _, _ -> "book" },
        ) { index, book ->
            ShelfItem(
                book = book,
                layout = layout,
                reveal = reveal,
                revealIndex = index + GridRevealOffset,
                menuExpanded = openMenu == OpenMenu.Book(book.location),
                selection = if (selection.active) {
                    CardSelection(
                        selected = book.location in selection,
                        onToggle = { onToggle(book.location) },
                    )
                } else {
                    null
                },
                onClick = { onDocumentClick(book.document) },
                transfer = transfers.states[book.location],
                onMenuExpandedChange = { expanded ->
                    onOpenMenuChange(OpenMenu.Book(book.location).takeIf { expanded })
                },
                onToggleFavorite = { onToggleFavorite(book.document) },
                onRefresh = { onRefresh(book.document) },
                onDelete = { onDelete(book.document) },
                onShowDetails = { onDocumentDetails(book.document) },
                onSelectBooks = onStartSelecting,
                onRemoveFromServer = transfers.removeFromServer(book.document),
                onMarkUnread = onMarkUnread?.takeIf { book.progress != null }?.let { mark -> { mark(book.document) } },
            )
        }

        // Last, so it reads as the end of the library rather than as the first
        // thing in it.
        //
        // Only on an unnarrowed library. A slot offering to add a book to a
        // filtered view promises what it cannot keep: import a PDF while the
        // grid shows favourites and the book lands in the library but not on the
        // screen that took it, which reads as having swallowed it. Left out of
        // an empty search for the same reason - the notice above is the whole
        // answer, and a lone slot beneath it would look like a result.
        if (!query.isNarrowed && books.isNotEmpty()) {
            item(key = "import", contentType = "import") {
                // Drawn as whichever the shelf is laid out as, for the same
                // reason a book is: a list's cell is the whole width of the
                // window, and a cover drawn into one is enormous.
                when (layout) {
                    LibraryLayout.Grid -> ImportBookCard(
                        menuExpanded = openMenu == OpenMenu.Import,
                        onMenuRequest = { onOpenMenuChange(OpenMenu.Import) },
                        onMenuDismiss = { onOpenMenuChange(null) },
                        onImport = onImport,
                        onImportFolder = onImportFolder,
                        reveal = reveal,
                        revealIndex = books.size + GridRevealOffset,
                        modifier = Modifier.animateItem(),
                    )

                    LibraryLayout.List -> ImportBookRow(
                        menuExpanded = openMenu == OpenMenu.Import,
                        onMenuRequest = { onOpenMenuChange(OpenMenu.Import) },
                        onMenuDismiss = { onOpenMenuChange(null) },
                        onImport = onImport,
                        onImportFolder = onImportFolder,
                        reveal = reveal,
                        revealIndex = books.size + GridRevealOffset,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * The dead end at the bottom of a query that matches nothing, worded for
 * whichever part of it is responsible.
 *
 * A word somebody just typed is the likeliest thing to be wrong, so it is quoted
 * back wherever there is one. The filters are named only in the general: listing
 * all five would be a paragraph, and the "Clear" chip above is already the
 * answer to every one of them.
 */
@Composable
private fun NoMatchesNotice(query: LibraryQuery) {
    val trimmed = query.search.trim()

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
    ) {
        EmptyState(
            title = stringResource(Res.string.library_no_results_title),
            message = when {
                trimmed.isEmpty() -> stringResource(Res.string.library_no_matches_filtered)
                query.isRefined || query.shelf != LibraryShelf.All ->
                    stringResource(Res.string.library_no_matches_filtered_term, trimmed)

                else -> stringResource(Res.string.library_no_matches_term, trimmed)
            },
        )
    }
}

/**
 * One book on the shelf, drawn as whichever the shelf is laid out as.
 *
 * The two are the same book with the same actions, and differ only in how much
 * room they take and how much they can therefore say - see [BookRow].
 */
@Composable
private fun LazyGridItemScope.ShelfItem(
    book: LibraryBook,
    layout: LibraryLayout,
    reveal: LaunchReveal,
    revealIndex: Int,
    menuExpanded: Boolean,
    selection: CardSelection?,
    onClick: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSelectBooks: () -> Unit,
    transfer: BookTransfer? = null,
    onRemoveFromServer: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
) {
    when (layout) {
        LibraryLayout.Grid -> BookCard(
            book = book,
            menuExpanded = menuExpanded,
            onClick = onClick,
            onMenuRequest = { onMenuExpandedChange(true) },
            onMenuDismiss = { onMenuExpandedChange(false) },
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            onDelete = onDelete,
            onShowDetails = onShowDetails,
            onSelectBooks = onSelectBooks,
            reveal = reveal,
            revealIndex = revealIndex,
            modifier = Modifier.animateItem(),
            selection = selection,
            transfer = transfer,
            onRemoveFromServer = onRemoveFromServer,
            onMarkUnread = onMarkUnread,
        )

        LibraryLayout.List -> Box {
            BookRow(
                book = book,
                onClick = onClick,
                onMenuRequest = { onMenuExpandedChange(true) },
                modifier = Modifier.animateItem(),
                selection = selection,
                transfer = transfer,
            )

            // Hung off the row itself so it opens where the finger was, the same
            // way a card's does.
            BookActionsMenu(
                expanded = menuExpanded,
                isFavorite = book.document.isFavorite,
                onToggleFavorite = onToggleFavorite,
                onRefresh = onRefresh,
                onDelete = onDelete,
                onDismissRequest = { onMenuExpandedChange(false) },
                canRemoveFromDevice = !book.document.awaitsDownload,
                onShowDetails = onShowDetails,
                onSelectBooks = onSelectBooks,
                onRemoveFromServer = onRemoveFromServer,
                onMarkUnread = onMarkUnread,
            )
        }
    }
}
