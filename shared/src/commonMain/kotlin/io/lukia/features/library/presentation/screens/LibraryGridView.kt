package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.presentation.ImportProgress
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryFacets
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibrarySelection
import io.lukia.features.library.presentation.LibraryShelf
import io.lukia.features.library.presentation.OpenMenu
import io.lukia.features.library.presentation.components.BookCard
import io.lukia.features.library.presentation.components.CardSelection
import io.lukia.features.library.presentation.components.ImportBookCard
import io.lukia.features.library.presentation.components.LibraryHeader
import io.lukia.features.library.presentation.components.libraryNavBarHeight
import io.lukia.shared.presentation.components.EmptyState
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/** Clears the last row of the bottom edge. */
private val GridBottomPadding: Dp = 24.dp

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
    isImporting: Boolean,
    importProgress: ImportProgress?,
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
    selection: LibrarySelection = LibrarySelection.None,
    onStartSelecting: () -> Unit = {},
    onToggle: (location: String) -> Unit = {},
) {
    val dimens = LocalLukiaDimens.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.bookCellMinWidth),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            // Content scrolls under the compact bar, so the header has to start
            // clear of where it sits at rest.
            top = libraryNavBarHeight() + Space.md,
            bottom = GridBottomPadding,
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
                isImporting = isImporting,
                importProgress = importProgress,
                reveal = reveal,
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
            BookCard(
                document = book.document,
                progress = book.progress,
                menuExpanded = openMenu == OpenMenu.Book(book.location),
                onClick = { onDocumentClick(book.document) },
                onMenuRequest = { onOpenMenuChange(OpenMenu.Book(book.location)) },
                onMenuDismiss = { onOpenMenuChange(null) },
                onToggleFavorite = { onToggleFavorite(book.document) },
                onRefresh = { onRefresh(book.document) },
                onDelete = { onDelete(book.document) },
                onShowDetails = { onDocumentDetails(book.document) },
                onSelectBooks = onStartSelecting,
                reveal = reveal,
                revealIndex = index + GridRevealOffset,
                modifier = Modifier.animateItem(),
                selection = if (selection.active) {
                    CardSelection(
                        selected = book.location in selection,
                        onToggle = { onToggle(book.location) },
                    )
                } else {
                    null
                },
            )
        }

        // Last, so it reads as the end of the library rather than as the first
        // thing in it.
        //
        // Only on an unnarrowed library. A slot offering to add a book to a
        // filtered view promises something it cannot keep: import a PDF while the
        // grid is showing favourites, and the book lands in the library but not
        // on the screen that just took it, which reads as having swallowed it.
        // Left out of a search that found nothing for the same reason - the
        // notice above is the whole answer, and a lone slot beneath it would look
        // like a result.
        if (!query.isNarrowed && books.isNotEmpty()) {
            item(key = "import", contentType = "import") {
                ImportBookCard(
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
            title = "No Results",
            message = when {
                trimmed.isEmpty() -> "Nothing in your library matches every filter you have set."
                query.isRefined || query.shelf != LibraryShelf.All ->
                    "Nothing matching “$trimmed” is left once your filters are applied."
                else -> "No books match “$trimmed”."
            },
        )
    }
}
