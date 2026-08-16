package io.lukia.features.library.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.import.presentation.rememberPlatformFilePicker
import io.lukia.features.import.presentation.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.LibraryFilter
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.availableFilters
import io.lukia.features.library.presentation.components.BookCard
import io.lukia.features.library.presentation.components.ImportBookCard
import io.lukia.features.library.presentation.components.LibraryHeader
import io.lukia.features.library.presentation.components.LibraryNavBar
import io.lukia.features.library.presentation.components.libraryNavBarHeight
import io.lukia.features.library.presentation.matching
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.components.rememberLaunchReveal
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.stateSwapIn
import io.lukia.shared.presentation.theme.softFadeOut
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * How far the grid scrolls before the compact bar has fully separated itself from
 * the content passing under it. Roughly the height of the filter control, so the
 * bar has arrived by the time the header is behind it.
 */
private val HeaderCollapseDistance: Dp = 56.dp

/** Clears the last row of the bottom edge. */
private val GridBottomPadding: Dp = 32.dp

/** The lone import card on an empty screen, at about a phone grid cell's width. */
private val EmptyStateCoverWidth: Dp = 104.dp

/** The first two reveal slots belong to the filter control and the count under it. */
private const val GridRevealOffset = 2

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel(),
    onDocumentClick: (DocumentInfo) -> Unit = {},
    onDocumentDetails: (DocumentInfo) -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()
    val snackbarHostState = remember { SnackbarHostState() }

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

    LibraryContent(
        uiState = uiState,
        onImport = {
            filePicker.launch { pickedDocument ->
                pickedDocument?.let { viewModel.importDocument(it) }
            }
        },
        onImportFolder = {
            folderPicker.launch { pickedFolder ->
                pickedFolder?.let { viewModel.importFolder(it) }
            }
        },
        onDocumentClick = onDocumentClick,
        onDocumentDetails = onDocumentDetails,
        onSettings = onSettings,
        onToggleFavorite = viewModel::toggleFavorite,
        onDelete = viewModel::deleteDocument,
        onRetry = viewModel::refresh,
        isImporting = isImporting,
        snackbarHostState = snackbarHostState
    )
}

/**
 * Stateless half of the screen, so the layout can be composed in a test or a
 * preview without a ViewModel or a dependency graph behind it. Every screen in
 * the app splits this way.
 */
@Composable
fun LibraryContent(
    uiState: UiState<List<DocumentInfo>>,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onDocumentClick: (DocumentInfo) -> Unit,
    onDocumentDetails: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    onRetry: () -> Unit,
    isImporting: Boolean,
    onSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val dimens = LocalLukiaDimens.current
    val reveal = rememberLaunchReveal()
    val gridState = rememberLazyGridState()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.All) }

    // One value rather than a flag per menu: the screen has three of them and at
    // most one may be open, and a single slot makes that structural instead of
    // something every opener has to remember to enforce. Held here rather than in
    // the grid so the dimming scrim behind any of them sits in one layer.
    var openMenu by remember { mutableStateOf<OpenMenu?>(null) }

    // Narrowed here rather than inside the grid, because the bar needs the count
    // too - its search field says how many books it is searching - and working it
    // out twice from two places is how the two come to disagree.
    val documents = uiState.dataOrNull.orEmpty()
    val filters = remember(documents) { documents.availableFilters() }

    // Favouriting the last favourite, or removing the last EPUB, can take the
    // segment the reader is standing on out from under them. Falling back here
    // rather than leaving them on a filter that no longer exists is what stops
    // the grid emptying for a reason nothing on screen explains.
    val activeFilter = if (selectedFilter in filters) selectedFilter else LibraryFilter.All

    val matches = remember(documents, activeFilter, query) {
        documents.matching(activeFilter, query)
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
                    // The library it hands back is the same list already narrowed
                    // above, so this only decides which of its shapes to draw.
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
                                message = "Importing books…",
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
                                matches = matches,
                                gridState = gridState,
                                query = query,
                                filters = filters,
                                activeFilter = activeFilter,
                                onFilterChange = { selectedFilter = it },
                                isImporting = isImporting,
                                reveal = reveal,
                                onDocumentClick = onDocumentClick,
                                onDocumentDetails = onDocumentDetails,
                                openMenu = openMenu,
                                onOpenMenuChange = { openMenu = it },
                                onToggleFavorite = onToggleFavorite,
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
                query = query,
                onQueryChange = { query = it },
                searchableCount = matches.size,
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
        }
    }
}

/**
 * Which of the screen's menus is open, if any.
 *
 * A closed set rather than a flag each: these are mutually exclusive, and one
 * slot that can only hold one of them cannot get into the state where two are
 * open at once.
 */
private sealed interface OpenMenu {

    /** The actions menu in the bar. */
    data object Bar : OpenMenu

    /** The import card's "where from" menu. */
    data object Import : OpenMenu

    /** One book's actions, by the location that identifies it. */
    data class Book(val location: String) : OpenMenu
}

/** What a successfully read library is showing. */
private enum class LibraryContentState {
    Importing,
    Empty,
    Content
}

@Composable
private fun LibraryGrid(
    matches: List<DocumentInfo>,
    gridState: LazyGridState,
    query: String,
    filters: List<LibraryFilter>,
    activeFilter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    isImporting: Boolean,
    reveal: LaunchReveal,
    onDocumentClick: (DocumentInfo) -> Unit,
    onDocumentDetails: (DocumentInfo) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
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
            top = libraryNavBarHeight() + 8.dp,
            bottom = GridBottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            LibraryHeader(
                filters = filters,
                selectedFilter = activeFilter,
                onFilterChange = onFilterChange,
                isImporting = isImporting,
                reveal = reveal
            )
        }

        if (matches.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "noMatches") {
                NoMatchesNotice(query = query, filter = activeFilter)
            }
        }

        itemsIndexed(
            items = matches,
            // Location identifies a document in the library, so keying on it lets
            // Compose track a card across reloads instead of rebuilding the whole
            // grid whenever an import appends to the list.
            key = { _, document -> document.document.location }
        ) { index, document ->
            val location = document.document.location

            BookCard(
                document = document,
                menuExpanded = openMenu == OpenMenu.Book(location),
                onClick = { onDocumentClick(document) },
                onMenuRequest = { onOpenMenuChange(OpenMenu.Book(location)) },
                onMenuDismiss = { onOpenMenuChange(null) },
                onToggleFavorite = { onToggleFavorite(document) },
                onDelete = { onDelete(document) },
                onShowDetails = { onDocumentDetails(document) },
                reveal = reveal,
                revealIndex = index + GridRevealOffset,
                modifier = Modifier.animateItem()
            )
        }

        // Last, so it reads as the end of the library rather than as the first
        // thing in it.
        //
        // Only under All. A slot offering to add a book to a filtered view
        // promises something it cannot keep: import a PDF while the grid is
        // showing favourites, and the book lands in the library but not on the
        // screen that just took it, which reads as having swallowed it. Left out
        // of a search that found nothing for the same reason - the notice above
        // is the whole answer, and a lone slot beneath it would look like a result.
        if (activeFilter == LibraryFilter.All && matches.isNotEmpty()) {
            item(key = "import") {
                ImportBookCard(
                    menuExpanded = openMenu == OpenMenu.Import,
                    onMenuRequest = { onOpenMenuChange(OpenMenu.Import) },
                    onMenuDismiss = { onOpenMenuChange(null) },
                    onImport = onImport,
                    onImportFolder = onImportFolder,
                    reveal = reveal,
                    revealIndex = matches.size + GridRevealOffset,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/**
 * The dead end at the bottom of a filter and a search that between them match
 * nothing, worded for whichever of the two is responsible.
 */
@Composable
private fun NoMatchesNotice(query: String, filter: LibraryFilter) {
    val trimmed = query.trim()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        Text(
            text = "No Results",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = when {
                trimmed.isEmpty() -> "Nothing in your library is ${filter.label.lowercase()}."
                filter == LibraryFilter.All -> "No books match “$trimmed”."
                else -> "No ${filter.label.lowercase()} books match “$trimmed”."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
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

            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 28.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // With no explicit button here, the copy has to carry the affordance
            // for the tappable cover above.
            Text(
                text = "Tap the cover to add a PDF or EPUB.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
