package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.home.HomeBookCard
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.displayTitle
import io.lukia.features.library.presentation.model.onShelf
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.home_shelf_empty_message
import io.lukia.resources.home_shelf_empty_title
import io.lukia.shared.presentation.components.controls.BackBar
import io.lukia.shared.presentation.components.menu.ActionMenuScrim
import io.lukia.shared.presentation.components.surface.EmptyState
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole of one home row, opened from its title.
 *
 * Its own screen rather than the library tab narrowed by filters: the row is a
 * question - what did I keep, what have I opened - and the answer is a page
 * about that, not a shelf whose filter bar has to be read to know why these
 * books are on it.
 *
 * Draws from the shared library, so favouriting or deleting a book here moves
 * it on or off this screen, and Home, at once.
 */
@Composable
internal fun HomeShelfScreen(
    shelf: HomeShelf,
    onBack: () -> Unit,
    onOpenBook: (DocumentInfo) -> Unit,
    onShowDetails: (DocumentInfo) -> Unit,
    /** Shows the library tab, where picking several books happens - see `LibraryShelfUi.selectingOn`. */
    onOpenLibrary: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val library by viewModel.library.collectAsState()
    val books = remember(library, shelf) { library.onShelf(shelf) }

    val selecting = rememberShelfUi(viewModel)
    val onSelectBooks = remember(selecting, onOpenLibrary) { selecting.selectingOn(onOpenLibrary).onStart }

    HomeShelfContent(
        title = shelf.displayTitle(),
        books = books,
        actions = rememberHomeBookActions(viewModel, onOpenBook, onShowDetails, onSelectBooks),
        transfers = rememberTransferUi(viewModel).states,
        onBack = onBack,
    )
}

/**
 * A bar with the way back and the row's name, held still while the covers
 * scroll under it: a long shelf is browsed far down, and its name and the way
 * out should not leave with the first screenful.
 */
@Composable
internal fun HomeShelfContent(
    title: String,
    books: List<LibraryBook>,
    actions: HomeBookActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Downloads under way, by book location - see `BookTransfers`. */
    transfers: Map<String, BookTransfer> = emptyMap(),
) {
    // The location of the book whose menu is open, if any: one slot, so at most
    // one menu can be.
    var openMenu by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                BackBar(onBack = onBack, title = title)

                ShelfGrid(
                    books = books,
                    transfers = transfers,
                    actions = actions,
                    openMenu = openMenu,
                    onOpenMenuChange = { openMenu = it },
                    modifier = Modifier.weight(1f),
                )
            }

            // The menus are non-focusable popups, so the press that dismisses
            // one has to be caught here - as on Home.
            ActionMenuScrim(visible = openMenu != null, onDismiss = { openMenu = null })
        }
    }
}

@Composable
private fun ShelfGrid(
    books: List<LibraryBook>,
    actions: HomeBookActions,
    openMenu: String?,
    onOpenMenuChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    transfers: Map<String, BookTransfer> = emptyMap(),
) {
    val dimens = LocalLukiaDimens.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.bookCellMinWidth),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = Space.md,
            bottom = Clearance.listBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.gridRowSpacing),
        modifier = modifier.fillMaxSize(),
    ) {
        // Reached by emptying the shelf while looking at it - the last
        // favourite un-favourited - since Home offers no title to an
        // empty row.
        if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty", contentType = "empty") {
                EmptyState(
                    title = stringResource(Res.string.home_shelf_empty_title),
                    message = stringResource(Res.string.home_shelf_empty_message),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.xl),
                )
            }
        }

        items(items = books, key = { book -> book.location }, contentType = { "book" }) { book ->
            HomeBookCard(
                book = book,
                actions = actions,
                menuExpanded = openMenu == book.location,
                onMenuExpandedChange = { expanded -> onOpenMenuChange(book.location.takeIf { expanded }) },
                modifier = Modifier.animateItem(),
                transfer = transfers[book.location],
            )
        }
    }
}
