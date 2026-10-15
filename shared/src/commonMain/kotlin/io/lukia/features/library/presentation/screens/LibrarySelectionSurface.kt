package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.selection.LibrarySelectionActions
import io.lukia.features.library.presentation.components.selection.LibrarySelectionBar
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.sessions.BookTransferUi
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.features.library.presentation.sessions.bulkAction
import io.lukia.features.library.presentation.sessions.deleteSelected
import io.lukia.features.library.presentation.sessions.refreshSelected
import io.lukia.features.library.presentation.sessions.resetProgressOnSelected
import io.lukia.features.library.presentation.sessions.selectDuplicates
import io.lukia.features.library.presentation.sessions.selectMissing
import io.lukia.features.library.presentation.sessions.selection
import io.lukia.features.library.presentation.sessions.setFavoriteOnSelected
import io.lukia.features.library.presentation.sessions.startSelecting
import io.lukia.features.library.presentation.sessions.stopSelecting
import io.lukia.features.library.presentation.sessions.toggleAllSelected
import io.lukia.features.library.presentation.sessions.toggleSelected
import io.lukia.features.library.presentation.sessions.uploadSelected
import io.lukia.resources.Res
import io.lukia.resources.action_delete
import io.lukia.resources.library_delete_confirm_message
import io.lukia.resources.library_delete_confirm_title
import io.lukia.shared.presentation.components.surface.ConfirmDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The selection mode's state and the view model's answers to it, in one value.
 *
 * Remembered against what actually changes, so that a shelf being scrolled or a
 * snackbar appearing does not hand every card a fresh set of callbacks - which
 * is what would make each of them recompose.
 */
@Composable
internal fun rememberShelfUi(viewModel: LibraryViewModel, onSyncWithServer: () -> Unit = {}): LibraryShelfUi {
    val selection by viewModel.selection.collectAsState()
    val running by viewModel.bulkAction.collectAsState()
    val upkeep by viewModel.upkeep.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val canUpload by viewModel.canUpload.collectAsState()

    return remember(viewModel, selection, running, upkeep, layout, canUpload, onSyncWithServer) {
        LibraryShelfUi(
            selection = selection,
            running = running,
            upkeep = upkeep,
            onStart = viewModel::startSelecting,
            onStop = viewModel::stopSelecting,
            onToggle = viewModel::toggleSelected,
            onToggleAll = viewModel::toggleAllSelected,
            onRefresh = viewModel::refreshSelected,
            onFavorite = viewModel::setFavoriteOnSelected,
            onResetProgress = viewModel::resetProgressOnSelected,
            onDelete = viewModel::deleteSelected,
            canUpload = canUpload,
            onUpload = viewModel::uploadSelected,
            onSelectMissing = viewModel::selectMissing,
            onSelectDuplicates = viewModel::selectDuplicates,
            layout = layout,
            onLayoutChange = viewModel::setLayout,
            onSyncWithServer = onSyncWithServer,
        )
    }
}

/** The panel's books moving to or from the server, wired to the view model's transfers. */
@Composable
internal fun rememberTransferUi(viewModel: LibraryViewModel): BookTransferUi {
    val states by viewModel.transfers.states.collectAsState()
    val canUpload by viewModel.transfers.canUpload.collectAsState()

    return remember(viewModel, states, canUpload) {
        BookTransferUi(
            states = states,
            canUpload = canUpload,
            onStart = { viewModel.transfers.start(it) },
            onCancel = viewModel.transfers::cancel,
        )
    }
}

/**
 * The chrome of selection mode: what is picked, what can be done to it, and the
 * one question worth asking before it is.
 *
 * The bar goes where the search bar was and the actions go at the foot of the
 * screen, which is where a thumb is and where every phone puts them. Far enough
 * apart that neither is pressed by accident, and the count is never next to the
 * button that deletes what it counts.
 */
@Composable
internal fun BoxScope.LibrarySelectionSurface(
    ui: LibraryShelfUi,
    books: List<LibraryBook>,
) {
    // Asked here rather than in the view model: whether a question is on screen
    // is the screen's business, and only the answer is the model's.
    var confirming by remember { mutableStateOf(false) }

    // A mode nobody can be in once its books are gone - a shelf that empties
    // under a selection would leave a bar counting books that are not there.
    LaunchedEffect(books.isEmpty()) {
        if (books.isEmpty()) ui.onStop()
    }

    val selected = remember(books, ui.selection) {
        books.filter { it.location in ui.selection }
    }

    LibrarySelectionBar(
        selection = ui.selection,
        running = ui.running,
        allSelected = books.isNotEmpty() && selected.size == books.size,
        onToggleAll = ui.onToggleAll,
        onFinish = ui.onStop,
        modifier = Modifier.align(Alignment.TopCenter),
    )

    LibrarySelectionActions(
        enabled = !ui.selection.isEmpty && ui.running == null,
        // Filled where every one of them already is, so the button says what
        // pressing it would do rather than what most of them happen to be.
        anyFavorite = selected.isNotEmpty() && selected.all { it.document.isFavorite },
        onRefresh = ui.onRefresh,
        onToggleFavorite = ui.onFavorite,
        onResetProgress = ui.onResetProgress,
        onDelete = { confirming = true },
        onUpload = ui.onUpload.takeIf { ui.canUpload },
        modifier = Modifier.align(Alignment.BottomCenter),
    )

    ConfirmDialog(
        visible = confirming,
        title = pluralStringResource(
            Res.plurals.library_delete_confirm_title,
            ui.selection.count,
            ui.selection.count,
        ),
        message = stringResource(Res.string.library_delete_confirm_message),
        confirmLabel = stringResource(Res.string.action_delete),
        destructive = true,
        onConfirm = ui.onDelete,
        onDismissRequest = { confirming = false },
    )
}
