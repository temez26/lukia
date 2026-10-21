package io.lukia.features.library.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.screens.BookDetailBody
import io.lukia.features.library.presentation.screens.BookTransferActions
import io.lukia.features.library.presentation.screens.RemoveFromServerDialog
import io.lukia.features.library.presentation.screens.rememberServerRemoval
import io.lukia.features.library.presentation.screens.rememberTransferUi
import io.lukia.features.library.presentation.sessions.BookTransferUi
import io.lukia.resources.Res
import io.lukia.resources.action_close
import io.lukia.resources.book_actions
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.cover.CoverArtSource
import io.lukia.shared.presentation.components.icons.EllipsisGlyph
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.menu.ActionMenuScrim
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.menu.DismissOnBack
import io.lukia.shared.presentation.components.surface.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** As wide as a column of facts reads well, and narrow enough to leave the shelf most of the window. */
private val PanelWidth: Dp = 360.dp

/**
 * Smaller than the cover on the detail screen. A panel is a glance, and a cover
 * at screen size fills it on its own - everything worth reading about the book
 * ends up below the fold in anything but a very tall window.
 */
private val PanelCoverWidth: Dp = 136.dp

/**
 * A book's details, from [viewModel] - see [BookDetailSidePanel], which this
 * feeds. [location] is the book being looked at, or null for none.
 *
 * Held by location rather than by value: favouriting the book from inside the
 * panel replaces the instance in the library, and a panel holding the old one
 * would go on showing an empty heart. Looking it up again each pass also closes
 * the panel when the book it was showing is deleted.
 */
@Composable
internal fun BookDetailSide(
    viewModel: LibraryViewModel,
    location: String?,
    onDismiss: () -> Unit,
    onRead: (DocumentInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val library by viewModel.library.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()
    val removal = rememberServerRemoval(viewModel)

    val document = location?.let { shown ->
        uiState.dataOrNull?.firstOrNull { it.document.location == shown }
    }

    BookDetailSidePanel(
        document = document,
        progress = location?.let(readingProgress::get),
        onDismiss = onDismiss,
        onRead = onRead,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefresh = viewModel::refreshDocument,
        onDelete = viewModel::deleteDocument,
        modifier = modifier,
        transfers = rememberTransferUi(viewModel, removal),
        onMarkUnread = viewModel::markUnread,
        cover = location?.let { shown -> library.firstOrNull { it.location == shown }?.cover },
    )

    RemoveFromServerDialog(removal, onRemove = viewModel::removeFromServer)
}

/**
 * A book's details, docked at the window's trailing edge beside the shelf
 * rather than over it.
 *
 * Beside, so the shelf stays in reach while the panel is open: another book can
 * be picked straight away, and the panel follows it rather than having to be
 * closed first. The shelf narrows to make room, and widens again on close.
 *
 * Draws nothing when [document] is null, so the caller hands it whatever book is
 * being looked at.
 */
@Composable
fun BookDetailSidePanel(
    document: DocumentInfo?,
    progress: ReadingProgress?,
    onDismiss: () -> Unit,
    onRead: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onRefresh: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    modifier: Modifier = Modifier,
    transfers: BookTransferUi = BookTransferUi.None,
    /** Puts a book back to unread; offered only for one that has been started. */
    onMarkUnread: ((DocumentInfo) -> Unit)? = null,
    /** The artwork, for a book off the shelf, which arrives without it - see `LibraryBook.cover`. */
    cover: CoverArtSource? = null,
) {
    // Held through the closing animation so the panel slides out with a book
    // still in it rather than emptying first.
    var lastShown by remember { mutableStateOf<DocumentInfo?>(null) }
    if (document != null) lastShown = document

    DismissOnBack(enabled = document != null, onDismiss = onDismiss)

    AnimatedVisibility(
        visible = document != null,
        enter = expandHorizontally(tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard)) +
            fadeIn(tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard)),
        exit = shrinkHorizontally(tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit)) +
            fadeOut(tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit)),
        modifier = modifier.fillMaxHeight(),
    ) {
        val shown = lastShown ?: return@AnimatedVisibility

        Row(modifier = Modifier.fillMaxHeight().semantics { isTraversalGroup = true }) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            PanelContent(
                shown = shown,
                progress = progress,
                onDismiss = onDismiss,
                onRead = onRead,
                onToggleFavorite = onToggleFavorite,
                onRefresh = onRefresh,
                onDelete = onDelete,
                transfers = transfers,
                onMarkUnread = onMarkUnread,
                cover = cover,
            )
        }
    }
}

@Composable
private fun PanelContent(
    shown: DocumentInfo,
    progress: ReadingProgress?,
    onDismiss: () -> Unit,
    onRead: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onRefresh: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    transfers: BookTransferUi,
    onMarkUnread: ((DocumentInfo) -> Unit)?,
    cover: CoverArtSource?,
) {
    val dimens = LocalLukiaDimens.current
    var menuExpanded by remember(shown.document.location) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(PanelWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        BookDetailBody(
            details = BookDetails(document = shown, progress = progress),
            onRead = { onRead(shown) },
            onLongPress = { menuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
            topClearance = OverlayPanelTopClearance,
            bottomClearance = dimens.sectionSpacing,
            coverWidth = PanelCoverWidth,
            // A step away from the panel it sits on, rather than from the
            // window background the screen version sits on.
            infoCardColor = MaterialTheme.colorScheme.surfaceVariant,
            transfer = transfers.stateOf(shown.document.location),
            transferActions = BookTransferActions(
                onStart = { transfers.onStart(shown) },
                onCancel = { transfers.onCancel(shown.document.location) },
            ),
            canUpload = transfers.canUpload,
            cover = cover,
        )

        // Where a touch window's menu is dismissed from - see [ActionMenuScrim].
        ActionMenuScrim(visible = menuExpanded, onDismiss = { menuExpanded = false })

        LukiaIconButton(
            label = stringResource(Res.string.action_close),
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = dimens.screenPadding, top = Space.lg),
            variant = IconButtonVariant.Glass,
        ) { tint, glyph ->
            PlusGlyph(tint = tint, modifier = glyph.graphicsLayer { rotationZ = 45f })
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = dimens.screenPadding, top = Space.lg),
        ) {
            LukiaIconButton(
                label = stringResource(Res.string.book_actions),
                onClick = { menuExpanded = true },
                variant = IconButtonVariant.Glass,
            ) { tint, glyph ->
                EllipsisGlyph(tint = tint, modifier = glyph)
            }

            BookActionsMenu(
                expanded = menuExpanded,
                isFavorite = shown.isFavorite,
                onToggleFavorite = { onToggleFavorite(shown) },
                onRefresh = { onRefresh(shown) },
                onDelete = { onDelete(shown) },
                canRemoveFromDevice = !shown.awaitsDownload,
                onDismissRequest = { menuExpanded = false },
                onRemoveFromServer = transfers.removeFromServer(shown),
                onMarkUnread = onMarkUnread?.takeIf { progress != null }?.let { mark -> { mark(shown) } },
            )
        }
    }
}
