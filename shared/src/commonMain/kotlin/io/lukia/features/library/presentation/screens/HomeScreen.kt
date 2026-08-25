package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.UiState
import io.lukia.features.import.presentation.rememberPlatformFilePicker
import io.lukia.features.import.presentation.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.HomeRow
import io.lukia.features.library.presentation.ImportProgress
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibraryShelfUi
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.homeRows
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.components.rememberLaunchReveal
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the mobile app opens: what you were reading, what has just arrived, what
 * is nearly done, and the rest of the library sampled a row at a time.
 *
 * It is a way back in rather than a place to work. A press on a cover does the
 * one thing this screen is for, which is to open the book; everything else about
 * a book - its details, its menu, selecting several at once - lives on the
 * library tab, and the actions menu in the corner is the library's own, so
 * nothing has to be learned twice.
 *
 * The library it draws from is the shared one - see `MobileShell` - so the rows
 * are already loaded by the time the tab is first looked at, and favouriting a
 * book on the library tab moves it onto the Favorites row here without either
 * screen having to tell the other.
 */
@Composable
internal fun HomeScreen(
    onOpenBook: (DocumentInfo) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    onOpenLibrary: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val library by viewModel.library.collectAsState()
    val facets by viewModel.facets.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()

    // The selection bar and the row of actions that come with it live on the
    // library tab, so the menu rows that start one send the reader there first.
    val shelf = rememberShelfUi(viewModel)
    val homeShelf = remember(shelf, onOpenLibrary) { shelf.selectingOn(onOpenLibrary) }

    // Fires again each time the tab is re-entered from a book, which may have
    // moved how far through it its reader is and so which row it belongs on.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Over the whole library rather than over what the library tab is narrowed
    // to: these rows are about the books themselves, and a filter somebody left
    // on another tab has nothing to say about where they stopped reading.
    val rows = remember(library) { library.homeRows() }

    HomeContent(
        uiState = uiState,
        rows = rows,
        isImporting = isImporting,
        onOpenBook = { book -> onOpenBook(book.document) },
        onSeeAll = onSeeAll,
        onImport = { filePicker.launch(viewModel::importDocuments) },
        onImportFolder = {
            folderPicker.launch { pickedFolder ->
                pickedFolder?.let { viewModel.importFolder(it) }
            }
        },
        onRetry = viewModel::refresh,
        categories = facets.categories,
        importProgress = importProgress,
        shelf = homeShelf,
        onProfile = onProfile,
        onSettings = onSettings,
    )
}

/**
 * Stateless half of the screen, so the layout can be composed in a test or a
 * preview without a ViewModel or a dependency graph behind it. Every screen in
 * the app splits this way.
 *
 * [rows] arrives already grouped and ordered - see `homeRows` - so this screen
 * only ever draws what it is handed.
 */
@Composable
internal fun HomeContent(
    uiState: UiState<List<DocumentInfo>>,
    rows: List<HomeRow>,
    isImporting: Boolean,
    onOpenBook: (LibraryBook) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** The subjects worth browsing by, or empty where the library has none. */
    categories: List<String> = emptyList(),
    /**
     * How far through a multi-file import the library is, when there is more
     * than one file to count. Defaulted to nothing so this screen still composes
     * without a ViewModel behind it, which is how it is tested and previewed.
     */
    importProgress: ImportProgress? = null,
    /** What the corner menu offers, which is what the library's own bar offers. */
    shelf: LibraryShelfUi = LibraryShelfUi.None,
    onProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    // One value rather than a flag per menu, as everywhere else in this app: the
    // header carries one and the empty state carries another, and at most one of
    // them may be open.
    var menuOpen by remember { mutableStateOf<HomeMenu?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        UiStateContent(
            state = uiState,
            actionLabel = "Try again",
            onAction = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                rows.isNotEmpty() -> HomeRows(
                    rows = rows,
                    categories = categories,
                    onOpenBook = onOpenBook,
                    onSeeAll = onSeeAll,
                    header = {
                        HomeHeader(
                            menuExpanded = menuOpen == HomeMenu.Bar,
                            onMenuExpandedChange = { menuOpen = if (it) HomeMenu.Bar else null },
                            onImport = onImport,
                            onImportFolder = onImportFolder,
                            shelf = shelf,
                            onProfile = onProfile,
                            onSettings = onSettings,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // A library still filling has nothing to sample yet, and a card
                // offering to add a fourth book while three are being read in is
                // the wrong thing to be pressing.
                isImporting -> LoadingState(
                    message = importingMessage(importProgress),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LibraryEmptyState(
                    importMenuExpanded = menuOpen == HomeMenu.Import,
                    onImportMenuRequest = { menuOpen = HomeMenu.Import },
                    onMenuDismiss = { menuOpen = null },
                    onImport = onImport,
                    onImportFolder = onImportFolder,
                    reveal = rememberLaunchReveal(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Every menu on this screen is a non-focusable popup drawn above this
        // layer, so the press that dismisses one has to be caught here.
        ActionMenuScrim(
            visible = menuOpen != null,
            onDismiss = { menuOpen = null },
        )
    }
}

/** The menus this screen can have open, of which at most one may be. */
private enum class HomeMenu {
    Bar,
    Import,
}
