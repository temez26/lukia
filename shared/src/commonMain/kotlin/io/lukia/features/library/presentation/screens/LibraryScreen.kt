package io.lukia.features.library.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.bookdetail.presentation.BookDetailOverlay
import io.lukia.features.import.presentation.rememberPlatformFilePicker
import io.lukia.features.import.presentation.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.ImportProgress
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryFacets
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibraryShelf
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.OpenMenu
import io.lukia.features.library.presentation.components.BookCard
import io.lukia.features.library.presentation.components.ImportBookCard
import io.lukia.features.library.presentation.components.LibraryHeader
import io.lukia.features.library.presentation.components.LibraryNavBar
import io.lukia.features.library.presentation.components.libraryNavBarHeight
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.settings.presentation.SettingsOverlay
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.EmptyState
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.components.rememberLaunchReveal
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import io.lukia.shared.presentation.theme.stateSwapIn
import io.lukia.shared.presentation.theme.softFadeOut
import org.koin.compose.viewmodel.koinViewModel

/**
 * How far the grid scrolls before the compact bar has fully separated itself from
 * the content passing under it. Roughly the height of the filter control, so the
 * bar has arrived by the time the header is behind it.
 */
private val HeaderCollapseDistance: Dp = 56.dp

/** Clears the last row of the bottom edge. */
private val GridBottomPadding: Dp = 24.dp

/** The lone import card on an empty screen, at about a grid cell's width. */
private val EmptyStateCoverWidth: Dp = 116.dp

/**
 * The first reveal slots belong to the header: the shelves, the filter row, and
 * the importing notice under them.
 */
private const val GridRevealOffset = 3

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel(),
    onDocumentClick: (DocumentInfo) -> Unit = {},
    onDocumentDetails: (DocumentInfo) -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val books by viewModel.books.collectAsState()
    val facets by viewModel.facets.collectAsState()
    val query by viewModel.query.collectAsState()
    val message by viewModel.message.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()
    val snackbarHostState = remember { SnackbarHostState() }

    // Settings open in place for the same reason a book's details do: a window
    // with room for both should not throw the library away to change a theme.
    // Held here rather than in the stateless half below, which has no business
    // knowing about a dependency graph.
    val settingsOpenInPlace = isWideWindow
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // Fires again each time the grid is re-entered from the detail screen, which
    // may have favourited or removed a book behind its back.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryContent(
            uiState = uiState,
            books = books,
            facets = facets,
            query = query,
            onQueryChange = viewModel::updateQuery,
            onImport = {
                filePicker.launch(viewModel::importDocuments)
            },
            onImportFolder = {
                folderPicker.launch { pickedFolder ->
                    pickedFolder?.let { viewModel.importFolder(it) }
                }
            },
            onDocumentClick = onDocumentClick,
            onDocumentDetails = onDocumentDetails,
            onSettings = {
                if (settingsOpenInPlace) settingsOpen = true else onSettings()
            },
            onToggleFavorite = viewModel::toggleFavorite,
            onRefresh = viewModel::refreshDocument,
            onDelete = viewModel::deleteDocument,
            onRetry = viewModel::refresh,
            isImporting = isImporting,
            importProgress = importProgress,
            snackbarHostState = snackbarHostState,
            readingProgress = readingProgress
        )

        // Over the library and its own overlays both, since it is opened from the
        // bar that floats above them.
        SettingsOverlay(
            visible = settingsOpen,
            onDismiss = { settingsOpen = false }
        )
    }
}

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
    readingProgress: Map<String, ReadingProgress> = emptyMap()
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
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
                        .fillMaxSize()
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
                        label = "libraryContent"
                    ) { state ->
                        when (state) {
                            LibraryContentState.Importing -> LoadingState(
                                message = importingMessage(importProgress),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = libraryNavBarHeight())
                            )

                            LibraryContentState.Empty -> LibraryEmptyState(
                                importMenuExpanded = openMenu == OpenMenu.Import,
                                onImportMenuRequest = { openMenu = OpenMenu.Import },
                                onMenuDismiss = { openMenu = null },
                                onImport = onImport,
                                onImportFolder = onImportFolder,
                                reveal = reveal,
                                modifier = Modifier.fillMaxSize()
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
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Above the grid it floats over, below the menus and their scrims.
            LibraryNavBar(
                collapsedProgress = collapsedProgress,
                query = query.search,
                onQueryChange = { onQueryChange(query.copy(search = it)) },
                searchableCount = books.size,
                menuExpanded = openMenu == OpenMenu.Bar,
                onMenuExpandedChange = { openMenu = if (it) OpenMenu.Bar else null },
                onImport = onImport,
                onImportFolder = onImportFolder,
                onSettings = onSettings,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Every menu on this screen is a non-focusable popup drawn above this
            // layer, so the press that dismisses one has to be caught here.
            ActionMenuScrim(
                visible = openMenu != null,
                onDismiss = { openMenu = null }
            )

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
                onDelete = onDelete
            )
        }
    }
}

/**
 * What the screen says while it is filling an empty library.
 *
 * The count is only ever the one the ViewModel offers, so a single file reads as
 * plain "Importing books…" rather than as a progress bar that is finished before
 * it is seen.
 */
private fun importingMessage(progress: ImportProgress?): String =
    if (progress == null) {
        "Importing books…"
    } else {
        "Importing ${progress.completed} of ${progress.total}…"
    }

/** What a successfully read library is showing. */
private enum class LibraryContentState {
    Importing,
    Empty,
    Content
}

@Composable
private fun LibraryGrid(
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
    modifier: Modifier = Modifier
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
            bottom = GridBottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.gridRowSpacing)
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
                reveal = reveal
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
            contentType = { _, _ -> "book" }
        ) { index, book ->
            BookCard(
                document = book.document,
                progress = book.progress?.fraction,
                menuExpanded = openMenu == OpenMenu.Book(book.location),
                onClick = { onDocumentClick(book.document) },
                onMenuRequest = { onOpenMenuChange(OpenMenu.Book(book.location)) },
                onMenuDismiss = { onOpenMenuChange(null) },
                onToggleFavorite = { onToggleFavorite(book.document) },
                onRefresh = { onRefresh(book.document) },
                onDelete = { onDelete(book.document) },
                onShowDetails = { onDocumentDetails(book.document) },
                reveal = reveal,
                revealIndex = index + GridRevealOffset,
                modifier = Modifier.animateItem()
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
                    modifier = Modifier.animateItem()
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
            .padding(top = 40.dp)
    ) {
        EmptyState(
            title = "No Results",
            message = when {
                trimmed.isEmpty() -> "Nothing in your library matches every filter you have set."
                query.isRefined || query.shelf != LibraryShelf.All ->
                    "Nothing matching “$trimmed” is left once your filters are applied."
                else -> "No books match “$trimmed”."
            }
        )
    }
}

@Composable
private fun LibraryEmptyState(
    importMenuExpanded: Boolean,
    onImportMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    reveal: LaunchReveal,
    modifier: Modifier = Modifier
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenPadding)
    ) {
        // No heading of its own: there is nothing here to head, and "Your library
        // is empty" below is a better one than the word "Library" would be.
        EmptyState(
            title = "Your library is empty",
            // With no explicit button here, the copy has to carry the affordance
            // for the tappable cover above.
            message = "Choose the cover to add a PDF, EPUB, Word or text file.",
            modifier = Modifier
                .align(Alignment.Center)
                .launchReveal(reveal, index = 1)
        ) {
            // The same slot that ends a full library, standing alone here. One
            // card to learn, and it asks the same question in both places.
            ImportBookCard(
                menuExpanded = importMenuExpanded,
                onMenuRequest = onImportMenuRequest,
                onMenuDismiss = onMenuDismiss,
                onImport = onImport,
                onImportFolder = onImportFolder,
                reveal = reveal,
                revealIndex = 1,
                label = null,
                modifier = Modifier.width(EmptyStateCoverWidth)
            )
        }
    }
}
