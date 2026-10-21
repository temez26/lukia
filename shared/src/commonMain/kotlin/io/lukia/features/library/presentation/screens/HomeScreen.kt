package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.UiState
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.home.AddBooksSheet
import io.lukia.features.library.presentation.components.home.HomeGlance
import io.lukia.features.library.presentation.model.HomeLayout
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.homeLayout
import io.lukia.features.library.presentation.pickers.rememberPlatformFilePicker
import io.lukia.features.library.presentation.pickers.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.features.library.presentation.sessions.LibraryImportUi
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.features.library.presentation.sessions.openBook
import io.lukia.features.library.presentation.sessions.rememberImportUi
import io.lukia.resources.Res
import io.lukia.resources.action_try_again
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.components.launch.rememberLaunchReveal
import io.lukia.shared.presentation.components.menu.ActionMenuScrim
import io.lukia.shared.presentation.components.state.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the mobile app opens: the book being read, then what else is on the go,
 * what has just arrived and what has not been started.
 *
 * A way back in rather than a place to work: a press opens the book, and a long
 * press offers the same menu the shelf hangs off its covers - one learned
 * gesture answered the same way in both places. Picking several books at once
 * stays the shelf's; see [LibraryShelfUi.selectingOn].
 *
 * It draws from the shared library - see `MobileShell` - so the rows are already
 * loaded when the tab is first looked at, and favouriting a book on the library
 * tab shows its mark here without either screen telling the other.
 */
@Composable
internal fun HomeScreen(
    onOpenBook: (DocumentInfo) -> Unit,
    onShowDetails: (DocumentInfo) -> Unit,
    onOpenShelf: (HomeShelf) -> Unit,
    onOpenLibrary: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** A glance at the week's reading that opens the reader's own page, supplied by the shell - see `MobileShell`. */
    stats: @Composable () -> Unit = {},
    /** Syncs with the server and opens where that is reported - see `LibraryShelfUi`. */
    onSyncWithServer: () -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val library by viewModel.library.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()

    // The selection bar and the row of actions that come with it live on the
    // library tab, so the menu rows that start one send the reader there first.
    val shelf = rememberShelfUi(viewModel, onSyncWithServer)
    val homeShelf = remember(shelf, onOpenLibrary) { shelf.selectingOn(onOpenLibrary) }

    // Fires again each time the tab is re-entered from a book, which may have
    // moved how far through it its reader is and so which row it belongs on.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Over the whole library rather than over what the library tab is narrowed
    // to: these rows are about the books themselves, and a filter somebody left
    // on another tab has nothing to say about where they stopped reading.
    val home = remember(library) { library.homeLayout() }

    val removal = rememberServerRemoval(viewModel)
    val actions = rememberHomeBookActions(viewModel, onOpenBook, onShowDetails, homeShelf.onStart, removal)

    HomeContent(
        uiState = uiState,
        home = home,
        importing = rememberImportUi(viewModel),
        transfers = rememberTransferUi(viewModel).states,
        actions = actions,
        onOpenShelf = onOpenShelf,
        onImport = { filePicker.launch(viewModel::importDocuments) },
        onImportFolder = {
            folderPicker.launch { pickedFolder ->
                pickedFolder?.let { viewModel.importFolder(it) }
            }
        },
        onRetry = viewModel::refresh,
        onSettings = onSettings,
        stats = stats,
        modifier = modifier,
    )

    RemoveFromServerDialog(removal, onRemove = viewModel::removeFromServer)
}

/**
 * What a press or a long press on one of Home's covers does - on Home itself
 * and on the screen a row's title opens, which offer the same menu.
 */
@Composable
internal fun rememberHomeBookActions(
    viewModel: LibraryViewModel,
    onOpenBook: (DocumentInfo) -> Unit,
    onShowDetails: (DocumentInfo) -> Unit,
    onSelectBooks: () -> Unit,
    removal: ServerRemoval? = null,
): HomeBookActions = remember(viewModel, onOpenBook, onShowDetails, onSelectBooks, removal) {
    HomeBookActions(
        onOpen = { book -> viewModel.openBook(book.document, onOpenBook) },
        onShowDetails = { book -> onShowDetails(book.document) },
        onToggleFavorite = { book -> viewModel.toggleFavorite(book.document) },
        onRefresh = { book -> viewModel.refreshDocument(book.document) },
        onDelete = { book -> viewModel.deleteDocument(book.document) },
        onSelectBooks = onSelectBooks,
        removeFromServer = { document -> removal?.rowFor(document) },
        onMarkUnread = { book -> viewModel.markUnread(book.document) },
    )
}

/**
 * [home] arrives already grouped and ordered - see `homeLayout` - so this screen
 * only ever draws what it is handed.
 */
@Composable
internal fun HomeContent(
    uiState: UiState<List<DocumentInfo>>,
    home: HomeLayout,
    actions: HomeBookActions,
    onOpenShelf: (HomeShelf) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * How far through a multi-file import the library is, when there is more
     * than one file to count. Defaulted to nothing so this screen still composes
     * without a ViewModel behind it, which is how it is tested and previewed.
     */
    importing: LibraryImportUi = LibraryImportUi.None,
    /** Downloads under way, by book location - see `BookTransfers`. */
    transfers: Map<String, BookTransfer> = emptyMap(),
    onSettings: () -> Unit = {},
    /** A glance at the week's reading, supplied by the shell - see `MobileShell`. */
    stats: @Composable () -> Unit = {},
) {
    // One value rather than a flag per menu, as everywhere else in this app: the
    // way to add books carries one and every cover on every row carries its
    // own, and at most one of them may be open.
    var menuOpen by remember { mutableStateOf<HomeMenu?>(null) }

    // This is where the mobile app opens, so this is the screen the launch
    // animation hands over to - see [LaunchSplash]. Read once here and handed
    // down, because the header and the rows are one stagger and not four.
    val reveal = rememberLaunchReveal()

    val header: @Composable () -> Unit = { HomeHeader(summary = homeSummary(home)) }

    val glance: @Composable (Modifier) -> Unit = { glanceModifier ->
        HomeGlance(
            onAddBooks = { menuOpen = HomeMenu.Import },
            onSettings = onSettings,
            modifier = glanceModifier,
            stats = stats,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = homeInsets(home),
    ) { paddingValues ->
        UiStateContent(
            state = uiState,
            actionLabel = stringResource(Res.string.action_try_again),
            onAction = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                !home.isEmpty -> HomeRows(
                    home = home,
                    actions = actions,
                    openMenu = menuOpen,
                    onOpenMenuChange = { menuOpen = it },
                    onOpenShelf = onOpenShelf,
                    reveal = reveal,
                    transfers = transfers,
                    modifier = Modifier.fillMaxSize(),
                    glance = glance,
                    header = header,
                )

                // The header stays with no books too, so the screen still says
                // where it is.
                else -> HeadedState(header = header, reveal = reveal) {
                    NoBooksYet(
                        importing = importing,
                        importMenuExpanded = menuOpen == HomeMenu.Import,
                        onImportMenuExpandedChange = { menuOpen = if (it) HomeMenu.Import else null },
                        onImport = onImport,
                        onImportFolder = onImportFolder,
                        reveal = reveal,
                    )
                }
            }
        }

        HomeOverlays(
            menuOpen = menuOpen,
            onClose = { menuOpen = null },
            emptyLibrary = home.isEmpty,
            onImport = onImport,
            onImportFolder = onImportFolder,
        )
    }
}

/** What is drawn over Home while one of its menus is open. */
@Composable
private fun HomeOverlays(
    menuOpen: HomeMenu?,
    onClose: () -> Unit,
    emptyLibrary: Boolean,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
) {
    // Every menu on this screen is a non-focusable popup drawn above this
    // layer, so the press that dismisses one has to be caught here.
    ActionMenuScrim(visible = menuOpen != null, onDismiss = onClose)

    // The empty screen's own card opens a menu of the same two choices.
    AddBooksSheet(
        expanded = menuOpen == HomeMenu.Import && !emptyLibrary,
        onDismissRequest = onClose,
        onImport = onImport,
        onImportFolder = onImportFolder,
    )
}

/** Home's header over a state that fills the rest of the screen, set as it is over the shelves. */
@Composable
private fun HeadedState(
    header: @Composable () -> Unit,
    reveal: LaunchReveal,
    content: @Composable () -> Unit,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(horizontal = dimens.screenPadding)
                .launchReveal(reveal, index = 0),
        ) {
            header()
        }

        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/**
 * Home before its first book: what is being read in, or the way to add one.
 *
 * A library still filling has nothing to sample yet, and a card offering to add
 * a fourth book while three are being read in is the wrong thing to be pressing.
 */
@Composable
private fun NoBooksYet(
    importing: LibraryImportUi,
    importMenuExpanded: Boolean,
    onImportMenuExpandedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    reveal: LaunchReveal,
) {
    if (importing.isImporting) {
        ImportingState(importing = importing, modifier = Modifier.fillMaxSize())
    } else {
        LibraryEmptyState(
            importMenuExpanded = importMenuExpanded,
            onImportMenuRequest = { onImportMenuExpandedChange(true) },
            onMenuDismiss = { onImportMenuExpandedChange(false) },
            onImport = onImport,
            onImportFolder = onImportFolder,
            reveal = reveal,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * What the screen keeps clear of. See `LibraryContent`: the cutout is not in the
 * Scaffold's default. With shelves to show, the top is left to them - their
 * first item, the book being read in its own colour, runs up under the status
 * bar and pads itself.
 */
@Composable
private fun homeInsets(home: HomeLayout): WindowInsets = if (home.isEmpty) {
    WindowInsets.safeDrawing
} else {
    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
}
