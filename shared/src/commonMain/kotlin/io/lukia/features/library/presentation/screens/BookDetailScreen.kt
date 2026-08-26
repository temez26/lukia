package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.BookDetailViewModel
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.BookActionsMenu
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
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
        // Nothing left on this screen once the entry is gone, so leaving is part
        // of the action rather than something the user is left to do.
        onDelete = {
            viewModel.deleteDocument()
            onBack()
        },
    )
}

/**
 * Stateless half of the screen, so the layout can be composed in a test or a
 * preview without a ViewModel or a dependency graph behind it. Every screen in
 * the app splits this way.
 */
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
) {
    val dimens = LocalLukiaDimens.current
    var menuExpanded by remember { mutableStateOf(false) }
    // The actions chrome is only meaningful once there is a book behind it.
    val currentDocument = uiState.dataOrNull?.document

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // This screen has no Scaffold - it is deliberately full-bleed so
        // content passes under the floating back button - so window insets have
        // to be applied by hand. Without this the back control and the cover
        // sit underneath the status bar clock on phones.
        //
        // The background above stays outside the inset so colour still reaches
        // the screen edges. Resolves to zero on desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            UiStateContent(
                state = uiState,
                // A document that failed to load may simply have been removed,
                // but a read that blew up can be worth a second attempt - so the
                // way out of the failure is a retry rather than a way back.
                actionLabel = "Try again",
                onAction = onRetry,
                modifier = Modifier.fillMaxSize(),
            ) { details ->
                BookDetailBody(
                    details = details,
                    onRead = onRead,
                    onLongPress = { menuExpanded = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Tap-outside-to-dismiss for the actions menu, which is a
            // non-focusable Popup and so cannot catch the press itself. Above
            // the content but below both floating controls.
            ActionMenuScrim(
                visible = menuExpanded,
                onDismiss = { menuExpanded = false },
            )

            // Floats above the scrolling content: this is the piece that most
            // wants the glass treatment, since content passes underneath it.
            LukiaIconButton(
                label = "Go back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = Space.lg),
                variant = IconButtonVariant.Glass,
            ) { tint, glyph ->
                BackChevron(tint = tint, modifier = glyph)
            }

            // Mirrors the back control across the top of the screen. Always
            // visible rather than hover-revealed as on a card: there is one book
            // here and room to show its actions outright.
            if (currentDocument != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = dimens.screenPadding, top = Space.lg),
                ) {
                    LukiaIconButton(
                        label = "Book actions",
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

            // By hand rather than through a Scaffold: this screen is full-bleed
            // on purpose, and wrapping it in one to carry a notice would put the
            // content back inside the insets it deliberately passes under.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
