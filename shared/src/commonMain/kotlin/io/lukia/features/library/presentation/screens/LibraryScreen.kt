package io.lukia.features.library.presentation.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.infrastructure.AppCommand
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.pickers.rememberPlatformFilePicker
import io.lukia.features.library.presentation.pickers.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.sessions.openBook
import io.lukia.features.library.presentation.sessions.rememberImportUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The library, with everything it can open laid over it.
 *
 * The stateful half: it resolves the ViewModel, holds what the screen is doing
 * rather than showing, and hands a finished picture to [LibraryContent] - which
 * lives next door precisely so it can be composed in a test with no dependency
 * graph behind it.
 *
 * Settings are the shell's to open, in place or as a screen of their own - see
 * `DesktopShell` - so this screen only asks for them.
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
    /** Starts a round with the sync server, if signed in to one - supplied by the shell. */
    onSyncWithServer: () -> Unit = {},
    /** Opens Settings on the Account page, where syncing is reported. */
    onAccountSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val books by viewModel.books.collectAsState()
    val facets by viewModel.facets.collectAsState()
    val query by viewModel.query.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val requests = rememberLibraryRequests(viewModel, onSettings, onSyncWithServer, onAccountSettings)
    val removal = rememberServerRemoval(viewModel)

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
        onMarkUnread = viewModel::markUnread,
        shelf = rememberShelfUi(viewModel, requests.syncWithServer),
        transfers = rememberTransferUi(viewModel, removal),
        onDelete = viewModel::deleteDocument,
        onRetry = viewModel::refresh,
        importing = rememberImportUi(viewModel),
        snackbarHostState = snackbarHostState,
        showSearch = showSearch,
        statsButton = statsButton,
        searchFocus = requests.searchFocus,
    )

    RemoveFromServerDialog(removal, onRemove = viewModel::removeFromServer)
}

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
    /** The search field, which Find puts the keyboard in. */
    val searchFocus: FocusRequester,
    private val scope: CoroutineScope,
) {

    fun carryOut(command: AppCommand) = when (command) {
        AppCommand.AddBooks -> addBooks()
        AppCommand.AddFolder -> addFolder()
        AppCommand.OpenSettings -> openSettings()
        AppCommand.FindInLibrary -> findInLibrary()
    }

    /**
     * Tried for a few frames rather than once: Find from a book brings the
     * library back first, and its field is laid out a frame or two after the
     * command arrives. It is not on screen at all while books are being picked,
     * and a shortcut pressed then has nothing to land on.
     */
    private fun findInLibrary() {
        scope.launch {
            repeat(FindAttempts) {
                if (requestSearchFocus()) return@launch
                withFrameNanos { }
            }
        }
    }

    /** Whether focus landed, which a requester not yet attached to its field cannot say yes to. */
    private fun requestSearchFocus(): Boolean = try {
        searchFocus.requestFocus(FocusDirection.Enter)
    } catch (_: IllegalStateException) {
        false
    }

    private companion object {
        const val FindAttempts = 10
    }
}

@Composable
private fun rememberLibraryRequests(
    viewModel: LibraryViewModel,
    onSettings: () -> Unit,
    onSyncWithServer: () -> Unit,
    onAccountSettings: () -> Unit,
): LibraryRequests {
    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()

    return LibraryRequests(
        addBooks = { filePicker.launch(viewModel::importDocuments) },
        addFolder = { folderPicker.launch { picked -> picked?.let(viewModel::importFolder) } },
        openSettings = onSettings,
        syncWithServer = {
            onSyncWithServer()
            onAccountSettings()
        },
        searchFocus = remember { FocusRequester() },
        scope = rememberCoroutineScope(),
    )
}
