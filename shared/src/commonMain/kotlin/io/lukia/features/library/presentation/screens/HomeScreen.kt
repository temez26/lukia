package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.UiState
import io.lukia.features.import.presentation.rememberPlatformFilePicker
import io.lukia.features.import.presentation.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.HomeRow
import io.lukia.features.library.presentation.ImportProgress
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.HomeShelfRow
import io.lukia.features.library.presentation.homeRows
import io.lukia.shared.presentation.components.GearGlyph
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.components.rememberLaunchReveal
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.koin.compose.viewmodel.koinViewModel

/** Clears the last row of the tab bar the screen scrolls up to. */
private val HomeBottomPadding: Dp = 24.dp

/**
 * Where the mobile app opens: what you were reading, what has just arrived, and
 * what you marked worth keeping.
 *
 * It is a way back in rather than a place to work. Everything that acts on a
 * book - its details, its menu, selecting several at once - lives on the library
 * tab, and a press here does the one thing this screen is for, which is to open
 * the book.
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
    onSettings: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val library by viewModel.library.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()

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
        importProgress = importProgress,
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
    /**
     * How far through a multi-file import the library is, when there is more
     * than one file to count. Defaulted to nothing so this screen still composes
     * without a ViewModel behind it, which is how it is tested and previewed.
     */
    importProgress: ImportProgress? = null,
    onSettings: () -> Unit = {},
) {
    // The import menu is opened from the empty state's card, and nothing else on
    // this screen opens a menu at all.
    var importMenuOpen by remember { mutableStateOf(false) }

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
                    onOpenBook = onOpenBook,
                    onSeeAll = onSeeAll,
                    onSettings = onSettings,
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
                    importMenuExpanded = importMenuOpen,
                    onImportMenuRequest = { importMenuOpen = true },
                    onMenuDismiss = { importMenuOpen = false },
                    onImport = onImport,
                    onImportFolder = onImportFolder,
                    reveal = rememberLaunchReveal(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HomeRows(
    rows: List<HomeRow>,
    onOpenBook: (LibraryBook) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = HomeBottomPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
    ) {
        item(key = "header", contentType = "header") {
            HomeHeader(
                onSettings = onSettings,
                modifier = Modifier.padding(horizontal = dimens.screenPadding),
            )
        }

        items(
            items = rows,
            key = { row -> row.shelf.name },
            contentType = { "shelf" },
        ) { row ->
            HomeShelfRow(
                row = row,
                onOpenBook = onOpenBook,
                onSeeAll = onSeeAll,
            )
        }
    }
}

/**
 * The screen's name, and the way into the settings.
 *
 * This screen does carry a title where the library deliberately does not. The
 * library is one thing and says so by showing it; home is three samples of three
 * different things, and without a title the first row's own heading would read
 * as the name of the whole screen.
 *
 * The gear is here because home is where a reader lands. Reaching the settings
 * only through the library tab's menu would put the app's own preferences behind
 * a screen about books.
 */
@Composable
private fun HomeHeader(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        LukiaIconButton(
            label = "Settings",
            onClick = onSettings,
        ) { tint, glyph ->
            GearGlyph(tint = tint, modifier = glyph)
        }
    }
}
