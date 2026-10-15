package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.infrastructure.AppCommand
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.pickers.rememberPlatformFilePicker
import io.lukia.features.library.presentation.pickers.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.sessions.openBook
import io.lukia.features.library.presentation.sessions.rememberImportUi
import io.lukia.features.settings.presentation.SettingsOverlay
import io.lukia.features.settings.presentation.screens.AccountPage
import io.lukia.features.settings.presentation.screens.SettingsArea
import io.lukia.shared.presentation.components.surface.behindOverlay
import io.lukia.shared.presentation.theme.isWideWindow
import org.koin.compose.viewmodel.koinViewModel

/**
 * The library, with everything it can open laid over it.
 *
 * The stateful half: it resolves the ViewModel, holds what the screen is doing
 * rather than showing, and hands a finished picture to [LibraryContent] - which
 * lives next door precisely so it can be composed in a test with no dependency
 * graph behind it.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel(),
    onDocumentClick: (DocumentInfo) -> Unit = {},
    onDocumentDetails: (DocumentInfo) -> Unit = {},
    onSettings: () -> Unit = {},
    /**
     * Something asked of the app from outside its own screens - a desktop menu
     * item, or the keyboard shortcut standing in for one where there is no menu
     * bar. Carried out here because every one of them already has a control on
     * this screen; taken off the window by `DesktopShell`, which is what is
     * still there to take it while a book is being read.
     */
    command: AppCommand? = null,
    onClearCommand: () -> Unit = {},
    showSearch: Boolean = true,
    /** The way to the reader's own page, supplied by the shell - see `MobileShell`. */
    statsButton: @Composable () -> Unit = {},
    accountPage: AccountPage? = null,
    /** Starts a round with the sync server, if signed in to one - supplied by the shell. */
    onSyncWithServer: () -> Unit = {},
    /** Opens Settings on the Account page, where a window has no room to open it in place. */
    onAccountSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val books by viewModel.books.collectAsState()
    val facets by viewModel.facets.collectAsState()
    val query by viewModel.query.collectAsState()
    val message by viewModel.message.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val settings = rememberSettingsPanel(onSettings, onAccountSettings)
    val requests = rememberLibraryRequests(viewModel, settings, onSyncWithServer)

    LibraryEffects(
        message = message,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onClearMessage = viewModel::clearMessage,
        command = command,
        onCommand = { requested ->
            requests.carryOut(requested)
            onClearCommand()
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Everything the settings panel is opened over, quietened while it is -
        // see [behindOverlay]. Wrapped rather than passed a modifier because
        // what has to go out of reach is the whole screen under the panel, not
        // any one composable in it.
        Box(modifier = Modifier.fillMaxSize().behindOverlay(settings.open.value)) {
            LibraryContent(
                uiState = uiState,
                books = books,
                facets = facets,
                query = query,
                onQueryChange = viewModel::updateQuery,
                onImport = requests.addBooks,
                onImportFolder = requests.addFolder,
                onDocumentClick = { document -> viewModel.openBook(document, onDocumentClick) },
                onDocumentDetails = onDocumentDetails,
                onSettings = requests.openSettings,
                onToggleFavorite = viewModel::toggleFavorite,
                onRefresh = viewModel::refreshDocument,
                shelf = rememberShelfUi(viewModel, requests.syncWithServer),
                transfers = rememberTransferUi(viewModel),
                onDelete = viewModel::deleteDocument,
                onRetry = viewModel::refresh,
                importing = rememberImportUi(viewModel),
                snackbarHostState = snackbarHostState,
                readingProgress = readingProgress,
                showSearch = showSearch,
                statsButton = statsButton,
            )
        }

        // Over the library and its own overlays both, since it is opened from the
        // bar that floats above them.
        SettingsOverlay(
            visible = settings.open.value,
            onDismiss = { settings.open.value = false },
            account = accountPage,
            initialArea = settings.area.value,
        )
    }
}

/**
 * Settings, opened in place for the same reason a book's details are - a
 * window with room for both should not throw the library away to change a
 * theme - or as a screen of their own where there is no room. Held by the
 * screen rather than the stateless half, which has no business knowing about a
 * dependency graph.
 */
private class SettingsPanel(
    val open: MutableState<Boolean>,
    val area: MutableState<SettingsArea>,
    private val inPlace: Boolean,
    private val onSettings: () -> Unit,
    private val onAccountSettings: () -> Unit,
) {
    fun show(page: SettingsArea) {
        area.value = page
        when {
            inPlace -> open.value = true
            page == SettingsArea.Account -> onAccountSettings()
            else -> onSettings()
        }
    }
}

@Composable
private fun rememberSettingsPanel(onSettings: () -> Unit, onAccountSettings: () -> Unit) = SettingsPanel(
    open = rememberSaveable { mutableStateOf(false) },
    area = rememberSaveable { mutableStateOf(SettingsArea.Default) },
    inPlace = isWideWindow,
    onSettings = onSettings,
    onAccountSettings = onAccountSettings,
)

/**
 * The things the library can be asked for that are not about one book.
 *
 * Held together because each has two ways in - a control on the screen and an
 * item in the desktop's menu bar - and the whole point is that both reach the
 * same one. Written out separately at each call site, the menu's "Add books"
 * and the bar's would be two importers that merely looked alike.
 */
private class LibraryRequests(
    val addBooks: () -> Unit,
    val addFolder: () -> Unit,
    val openSettings: () -> Unit,
    /** Syncs, and shows how that went where it is reported - or where to sign in. */
    val syncWithServer: () -> Unit,
) {

    fun carryOut(command: AppCommand) = when (command) {
        AppCommand.AddBooks -> addBooks()
        AppCommand.AddFolder -> addFolder()
        AppCommand.OpenSettings -> openSettings()
    }
}

@Composable
private fun rememberLibraryRequests(
    viewModel: LibraryViewModel,
    settings: SettingsPanel,
    onSyncWithServer: () -> Unit,
): LibraryRequests {
    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()

    return LibraryRequests(
        addBooks = { filePicker.launch(viewModel::importDocuments) },
        addFolder = { folderPicker.launch { picked -> picked?.let(viewModel::importFolder) } },
        openSettings = { settings.show(SettingsArea.Default) },
        syncWithServer = {
            onSyncWithServer()
            settings.show(SettingsArea.Account)
        },
    )
}
