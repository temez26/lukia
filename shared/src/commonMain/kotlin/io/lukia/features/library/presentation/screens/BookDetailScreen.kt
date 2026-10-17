package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.BookDetailViewModel
import io.lukia.features.library.presentation.model.displayText
import io.lukia.resources.Res
import io.lukia.resources.action_try_again
import io.lukia.resources.book_actions
import io.lukia.shared.presentation.components.controls.BackBar
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.EllipsisGlyph
import io.lukia.shared.presentation.components.menu.ActionMenuScrim
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.state.UiStateContent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BookDetailScreen(
    location: String,
    onBack: () -> Unit,
    onRead: () -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(parameters = { parametersOf(location) }),
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val transfer by viewModel.transfer.collectAsState()
    val canUpload by viewModel.canUpload.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Read in the composition, shown from the effect - a string resource needs
    // the one and the snackbar needs the other.
    val notice = message?.displayText()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BookDetailContent(
        uiState = uiState,
        onBack = onBack,
        onRead = onRead,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefresh = viewModel::refreshDocument,
        snackbarHostState = snackbarHostState,
        transfer = transfer,
        canUpload = canUpload,
        transferActions = remember(viewModel) {
            BookTransferActions(onStart = viewModel::startTransfer, onCancel = viewModel::cancelTransfer)
        },
        // Nothing left on this screen once the entry is gone, so leaving is part
        // of the action rather than something the user is left to do.
        onDelete = {
            viewModel.deleteDocument()
            onBack()
        },
    )
}

@Composable
fun BookDetailContent(
    uiState: UiState<BookDetails>,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    /**
     * Defaulted so this screen still composes without a ViewModel behind it,
     * which is how it is tested and previewed.
     */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /** How far fetching a server book onto the device has got - see [BookTransferRow]. */
    transfer: BookTransfer = BookTransfer.Idle,
    transferActions: BookTransferActions = BookTransferActions(),
    canUpload: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    // The actions chrome is only meaningful once there is a book behind it.
    val currentDocument = uiState.dataOrNull?.document

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // This screen has no Scaffold, so window insets have to be applied by
        // hand. Without this the back control and the cover sit underneath the
        // status bar clock on phones.
        //
        // The background above stays outside the inset so colour still reaches
        // the screen edges. Resolves to zero on desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                BackBar(onBack = onBack) {
                    // Only once there is a book for the actions to act on. One
                    // book here, with room to show its actions outright rather
                    // than revealing them on hover as a card does.
                    if (currentDocument != null) {
                        Box {
                            LukiaIconButton(
                                label = stringResource(Res.string.book_actions),
                                onClick = { menuExpanded = true },
                                variant = IconButtonVariant.Glass,
                            ) { tint, glyph ->
                                EllipsisGlyph(tint = tint, modifier = glyph)
                            }

                            BookActionsMenu(
                                expanded = menuExpanded,
                                isFavorite = currentDocument.isFavorite,
                                onToggleFavorite = onToggleFavorite,
                                onRefresh = onRefresh,
                                onDelete = onDelete,
                                onDismissRequest = { menuExpanded = false },
                            )
                        }
                    }
                }

                UiStateContent(
                    state = uiState,
                    // A document that failed to load may simply have been removed,
                    // but a read that blew up can be worth a second attempt - so the
                    // way out of the failure is a retry rather than a way back.
                    actionLabel = stringResource(Res.string.action_try_again),
                    onAction = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { details ->
                    BookDetailBody(
                        details = details,
                        onRead = onRead,
                        onLongPress = { menuExpanded = true },
                        transfer = transfer,
                        transferActions = transferActions,
                        canUpload = canUpload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Tap-outside-to-dismiss for the actions menu, which is a
            // non-focusable Popup and so cannot catch the press itself.
            ActionMenuScrim(
                visible = menuExpanded,
                onDismiss = { menuExpanded = false },
            )

            // By hand rather than through a Scaffold, which would apply the
            // insets this screen already applies itself a second time.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
