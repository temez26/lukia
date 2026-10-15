package io.lukia.features.library.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.screens.BookDetailBody
import io.lukia.features.library.presentation.screens.BookTransferActions
import io.lukia.features.library.presentation.sessions.BookTransferUi
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.EllipsisGlyph
import io.lukia.shared.presentation.components.menu.ActionMenuScrim
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.surface.OverlayPanel
import io.lukia.shared.presentation.components.surface.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Smaller than the cover on the detail screen. A panel is a glance, and a cover
 * at screen size fills it on its own - everything worth reading about the book
 * ends up below the fold in anything but a very tall window.
 */
private val PanelCoverWidth: Dp = 136.dp

/**
 * A book's details, over the library rather than instead of it.
 *
 * Draws nothing when [document] is null, so the caller hands it whatever book is
 * being looked at - which also means a book deleted from inside the panel takes
 * the panel with it, the lookup coming back null on the next frame.
 *
 * The book comes from the library rather than being loaded again, which keeps
 * favouriting it inside the panel visible on the card behind. [progress] arrives
 * separately, being kept in a store the library never reads.
 */
@Composable
fun BookDetailOverlay(
    document: DocumentInfo?,
    progress: ReadingProgress?,
    onDismiss: () -> Unit,
    onRead: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onRefresh: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    modifier: Modifier = Modifier,
    transfers: BookTransferUi = BookTransferUi.None,
) {
    val dimens = LocalLukiaDimens.current

    // Held through the closing animation so the panel fades out with a book still
    // in it rather than emptying first.
    var lastShown by remember { mutableStateOf<DocumentInfo?>(null) }
    if (document != null) lastShown = document

    val shown = document ?: lastShown ?: return

    var menuExpanded by remember(shown.document.location) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        OverlayPanel(
            visible = document != null,
            // With the menu open this closes the menu instead: dismissing the
            // panel out from under an open menu would take two decisions at once.
            onDismiss = { if (menuExpanded) menuExpanded = false else onDismiss() },
            leading = {
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
                    onDismissRequest = { menuExpanded = false },
                )
            },
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
            )
        }

        // The actions menu is a non-focusable popup, so the press that closes it
        // has to be caught here - above the panel, or a press on the panel would
        // reach the panel instead of dismissing the menu.
        ActionMenuScrim(
            visible = menuExpanded,
            onDismiss = { menuExpanded = false },
        )
    }
}
