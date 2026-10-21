package io.lukia.features.library.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.presentation.components.toolbar.LibraryNavBar
import io.lukia.features.library.presentation.components.toolbar.libraryNavBarHeight
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.LibraryFacets
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.model.OpenMenu
import io.lukia.features.library.presentation.sessions.BookTransferUi
import io.lukia.features.library.presentation.sessions.LibraryImportUi
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.resources.Res
import io.lukia.resources.action_try_again
import io.lukia.shared.presentation.components.launch.rememberLaunchReveal
import io.lukia.shared.presentation.components.menu.ActionMenuScrim
import io.lukia.shared.presentation.components.state.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn
import org.jetbrains.compose.resources.stringResource

/**
 * How far the grid scrolls before the compact bar has fully separated itself from
 * the content passing under it. Roughly the height of the filter control, so the
 * bar has arrived by the time the header is behind it.
 */
private val HeaderCollapseDistance: Dp = 56.dp

/**
 * [books] arrives already narrowed and ordered. Deciding what the grid shows is
 * the ViewModel's work, done off the main thread - several hundred books
 * searched on every keystroke is not for the thread that draws them - so this
 * screen only draws what it is handed.
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
    /**
     * Whether an import is running, how far through it is, and the way to stop
     * it. Defaulted to nothing running so this screen still composes without a
     * ViewModel behind it, which is how it is tested and previewed.
     */
    importing: LibraryImportUi = LibraryImportUi.None,
    onSettings: () -> Unit = {},
    /** Puts a started book back to unread, from its menu. */
    onMarkUnread: ((DocumentInfo) -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /** How the shelf is laid out, what is picked off it, and what can be done to that. */
    shelf: LibraryShelfUi = LibraryShelfUi.None,
    /** Books moving to or from the sync server, for the panel's row. */
    transfers: BookTransferUi = BookTransferUi.None,
    /** False where searching has a screen of its own - see [LibraryNavBar]. */
    showSearch: Boolean = true,
    /** The way to the reader's own page, supplied by the shell - see `MobileShell`. */
    statsButton: @Composable () -> Unit = {},
    /** Where Find puts the keyboard - see `AppCommand.FindInLibrary`. */
    searchFocus: FocusRequester? = null,
    /** Where a window switches between Home and the library - see `DesktopSectionTabs`. */
    sections: @Composable () -> Unit = {},
) {
    val dimens = LocalLukiaDimens.current
    val reveal = rememberLaunchReveal()
    val gridState = rememberLazyGridState()

    // One value rather than a flag per menu: the screen has one on its bar, one
    // on every book and one on every filter chip, and at most one may be open.
    // Held here so the dimming scrim behind any of them sits in one layer.
    var openMenu by remember { mutableStateOf<OpenMenu?>(null) }

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
        // safeDrawing rather than the Scaffold's own default of systemBars,
        // which leaves out the display cutout: in landscape on a notched phone
        // that is the difference between the shelf starting clear of the camera
        // and starting under it. The screens that are pushed rather than tabbed
        // to already spell it this way.
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        actionLabel = stringResource(Res.string.action_try_again),
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
                            importing.isImporting -> LibraryContentState.Importing
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
                                LibraryContentState.Importing -> ImportingState(
                                    importing = importing,
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
                                    importing = importing,
                                    reveal = reveal,
                                    onDocumentClick = onDocumentClick,
                                    onDocumentDetails = onDocumentDetails,
                                    openMenu = openMenu,
                                    onOpenMenuChange = { openMenu = it },
                                    onToggleFavorite = onToggleFavorite,
                                    onRefresh = onRefresh,
                                    onDelete = onDelete,
                                    onMarkUnread = onMarkUnread,
                                    onImport = onImport,
                                    onImportFolder = onImportFolder,
                                    modifier = Modifier.fillMaxSize(),
                                    layout = shelf.layout,
                                    onLayoutChange = shelf.onLayoutChange,
                                    selection = shelf.selection,
                                    onStartSelecting = shelf.onStart,
                                    onToggle = shelf.onToggle,
                                    transfers = transfers,
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
                        searchFocus = searchFocus,
                        sections = sections,
                    )
                }

                // Every menu on this screen is a non-focusable popup drawn above this
                // layer, so the press that dismisses one has to be caught here.
                ActionMenuScrim(
                    visible = openMenu != null,
                    onDismiss = { openMenu = null },
                )
            }
        }
    }
}
