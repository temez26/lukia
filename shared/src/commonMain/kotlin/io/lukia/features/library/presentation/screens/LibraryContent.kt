package io.lukia.features.library.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.ReadingProgress
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.presentation.BookDetailOverlay
import io.lukia.features.library.presentation.ImportProgress
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryFacets
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibraryShelfUi
import io.lukia.features.library.presentation.OpenMenu
import io.lukia.features.library.presentation.components.LibraryNavBar
import io.lukia.features.library.presentation.components.libraryNavBarHeight
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.components.behindOverlay
import io.lukia.shared.presentation.components.rememberLaunchReveal
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.isWideWindow
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn

/**
 * How far the grid scrolls before the compact bar has fully separated itself from
 * the content passing under it. Roughly the height of the filter control, so the
 * bar has arrived by the time the header is behind it.
 */
private val HeaderCollapseDistance: Dp = 56.dp

/**
 * Stateless half of the screen, so the layout can be composed in a test or a
 * preview without a ViewModel or a dependency graph behind it. Every screen in
 * the app splits this way.
 *
 * [books] arrives already narrowed and ordered. Deciding what the grid shows is
 * the ViewModel's work, done off the main thread - a library of several hundred
 * books searched on every keystroke is not something to do on the thread that
 * draws them - so this screen only ever draws what it is handed.
 */
@Composable
fun LibraryContent(
    uiState: UiState<List<DocumentInfo>>,
    books: List<LibraryBook>,
    facets: LibraryFacets,
    query: LibraryQuery,
    onQueryChange: (LibraryQuery) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onDocumentClick: (DocumentInfo) -> Unit,
    onDocumentDetails: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onRefresh: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    onRetry: () -> Unit,
    isImporting: Boolean,
    /**
     * How far through a multi-file import the library is, when there is more
     * than one file to count. Defaulted to nothing so this screen still composes
     * without a ViewModel behind it, which is how it is tested and previewed.
     */
    importProgress: ImportProgress? = null,
    onSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /**
     * How far through each book its reader is, keyed by location. Only the book
     * whose details are open is looked up here; the grid draws its bars from
     * [books], which already carries each book's position.
     */
    readingProgress: Map<String, ReadingProgress> = emptyMap(),
    /** How the shelf is laid out, what is picked off it, and what can be done to that. */
    shelf: LibraryShelfUi = LibraryShelfUi.None,
    /** False where searching has a screen of its own - see [LibraryNavBar]. */
    showSearch: Boolean = true,
    /** The way to the reader's own page, supplied by the shell - see `MobileShell`. */
    statsButton: @Composable () -> Unit = {},
) {
    val dimens = LocalLukiaDimens.current
    val reveal = rememberLaunchReveal()
    val gridState = rememberLazyGridState()

    // A phone leaves the library to show a book's details, because it has room
    // for one thing at a time. Anything wider keeps the library on screen and
    // lays the details over it, so browsing is not unwound to read a file size.
    val detailsOpenInPlace = isWideWindow

    // The book being looked at, held by location rather than by value: favouriting
    // it from inside the panel replaces the instance in the library, and a panel
    // holding the old one would go on showing an empty heart. Looking it up again
    // each pass also closes the panel when the book it was showing is deleted.
    var detailLocation by rememberSaveable { mutableStateOf<String?>(null) }

    // One value rather than a flag per menu: the screen has one on its bar, one
    // on every book and one on every filter chip, and at most one may be open.
    // Held here so the dimming scrim behind any of them sits in one layer.
    var openMenu by remember { mutableStateOf<OpenMenu?>(null) }

    val documents = uiState.dataOrNull.orEmpty()

    // From the whole library rather than from what is on screen: a filter that
    // no longer matches the book being looked at is not a reason to shut the
    // panel showing it.
    val detailDocument = detailLocation?.let { location ->
        documents.firstOrNull { it.document.location == location }
    }

    val collapseDistancePx = with(LocalDensity.current) { HeaderCollapseDistance.toPx() }
    // A lambda, not a value: read this way the scroll position reaches only the
    // draw and layer blocks that use it, so scrolling redraws the bar without
    // recomposing it or anything around it.
    val collapsedProgress: () -> Float = {
        if (gridState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (gridState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Everything the detail panel is opened over - the shelf, the bar
            // floating on it, and the scrim for the card menus - out of reach
            // while it is, see [behindOverlay].
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .behindOverlay(detailLocation != null),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // Reading the library is the outer state; everything inside is
                    // what a successfully read library can look like.
                    //
                    // The width cap lives on the container, not the children: put it
                    // on the children and AnimatedContent's own sizing wins, which
                    // left-aligns the grid instead of centring it.
                    UiStateContent(
                        state = uiState,
                        actionLabel = "Try again",
                        onAction = onRetry,
                        modifier = Modifier
                            // Capped first, then filled, so wide desktop windows keep
                            // a readable measure instead of stretching edge to edge.
                            .widthIn(max = dimens.maxGridWidth)
                            .fillMaxSize(),
                        // The library it hands back is only used to tell an empty
                        // shelf from a full one; the grid draws [books].
                    ) { loaded ->
                        // An import that lands books keeps the grid up and reports
                        // itself in the header instead; the importing state below is
                        // only for when there is nothing else to show yet.
                        val contentState = when {
                            loaded.isNotEmpty() -> LibraryContentState.Content
                            isImporting -> LibraryContentState.Importing
                            else -> LibraryContentState.Empty
                        }

                        AnimatedContent(
                            targetState = contentState,
                            transitionSpec = {
                                // `using null` disables the default SizeTransform,
                                // which would otherwise animate the container bounds
                                // and slide the content sideways mid-transition.
                                (stateSwapIn() togetherWith softFadeOut()) using null
                            },
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                            label = "libraryContent",
                        ) { state ->
                            when (state) {
                                LibraryContentState.Importing -> LoadingState(
                                    message = importingMessage(importProgress),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = libraryNavBarHeight()),
                                )

                                LibraryContentState.Empty -> LibraryEmptyState(
                                    importMenuExpanded = openMenu == OpenMenu.Import,
                                    onImportMenuRequest = { openMenu = OpenMenu.Import },
                                    onMenuDismiss = { openMenu = null },
                                    onImport = onImport,
                                    onImportFolder = onImportFolder,
                                    reveal = reveal,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                LibraryContentState.Content -> LibraryGrid(
                                    books = books,
                                    gridState = gridState,
                                    query = query,
                                    facets = facets,
                                    onQueryChange = onQueryChange,
                                    isImporting = isImporting,
                                    importProgress = importProgress,
                                    reveal = reveal,
                                    onDocumentClick = onDocumentClick,
                                    onDocumentDetails = { document ->
                                        if (detailsOpenInPlace) {
                                            detailLocation = document.document.location
                                        } else {
                                            onDocumentDetails(document)
                                        }
                                    },
                                    openMenu = openMenu,
                                    onOpenMenuChange = { openMenu = it },
                                    onToggleFavorite = onToggleFavorite,
                                    onRefresh = onRefresh,
                                    onDelete = onDelete,
                                    onImport = onImport,
                                    onImportFolder = onImportFolder,
                                    modifier = Modifier.fillMaxSize(),
                                    layout = shelf.layout,
                                    onLayoutChange = shelf.onLayoutChange,
                                    selection = shelf.selection,
                                    onStartSelecting = shelf.onStart,
                                    onToggle = shelf.onToggle,
                                )
                            }
                        }
                    }
                }

                // Above the grid it floats over, below the menus and their scrims.
                //
                // One or the other, never both: searching and selecting are answers
                // to different questions, and a shelf is only ever being asked one
                // of them - see LibrarySelectionBar.
                if (shelf.active) {
                    LibrarySelectionSurface(ui = shelf, books = books)
                } else {
                    LibraryNavBar(
                        collapsedProgress = collapsedProgress,
                        query = query.search,
                        onQueryChange = { onQueryChange(query.copy(search = it)) },
                        searchableCount = books.size,
                        menuExpanded = openMenu == OpenMenu.Bar,
                        onMenuExpandedChange = { openMenu = if (it) OpenMenu.Bar else null },
                        onImport = onImport,
                        onImportFolder = onImportFolder,
                        shelf = shelf,
                        onSettings = onSettings,
                        modifier = Modifier.align(Alignment.TopCenter),
                        showSearch = showSearch,
                        statsButton = statsButton,
                    )
                }

                // Every menu on this screen is a non-focusable popup drawn above this
                // layer, so the press that dismisses one has to be caught here.
                ActionMenuScrim(
                    visible = openMenu != null,
                    onDismiss = { openMenu = null },
                )
            }

            // Last, so it covers the library and its chrome both. Draws nothing
            // at all until a book is being looked at.
            BookDetailOverlay(
                document = detailDocument,
                progress = detailLocation?.let(readingProgress::get),
                onDismiss = { detailLocation = null },
                onRead = { document ->
                    detailLocation = null
                    onDocumentClick(document)
                },
                onToggleFavorite = onToggleFavorite,
                onRefresh = onRefresh,
                onDelete = onDelete,
            )
        }
    }
}
