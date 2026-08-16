package io.lukia.features.bookdetail.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.features.bookdetail.presentation.screens.BookDetailBody
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.BookActionsMenu
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.GlassChromeButton
import io.lukia.shared.presentation.components.OverlayPanel
import io.lukia.shared.presentation.components.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens

/**
 * Smaller than the cover on the detail screen. A panel is a glance, and a cover
 * at screen size fills it on its own - the Read button, which is the reason the
 * panel was opened, ends up below the fold in anything but a very tall window.
 */
private val PanelCoverWidth: Dp = 148.dp

/**
 * A book's details, over the library rather than instead of it.
 *
 * Draws nothing at all when [document] is null, so the caller can simply hand it
 * whatever book is currently being looked at. That also means a book deleted from
 * inside the panel takes the panel with it: the library no longer holds it, so
 * the caller's lookup comes back null on the next frame.
 */
@Composable
fun BookDetailOverlay(
    document: DocumentInfo?,
    onDismiss: () -> Unit,
    onRead: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    modifier: Modifier = Modifier
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
            trailing = {
                GlassChromeButton(
                    label = "Book actions",
                    onClick = { menuExpanded = true }
                ) {
                    EllipsisGlyph(
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                BookActionsMenu(
                    expanded = menuExpanded,
                    isFavorite = shown.isFavorite,
                    onToggleFavorite = { onToggleFavorite(shown) },
                    onDelete = { onDelete(shown) },
                    onDismissRequest = { menuExpanded = false }
                )
            }
        ) {
            BookDetailBody(
                document = shown,
                onRead = { onRead(shown) },
                onLongPress = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                topClearance = OverlayPanelTopClearance,
                bottomClearance = dimens.sectionSpacing,
                coverWidth = PanelCoverWidth,
                // A step away from the panel it sits on, rather than from the
                // window background the screen version sits on.
                infoCardColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // The actions menu is a non-focusable popup, so the press that closes it
        // has to be caught here - above the panel, or a press on the panel would
        // reach the panel instead of dismissing the menu.
        ActionMenuScrim(
            visible = menuExpanded,
            onDismiss = { menuExpanded = false }
        )
    }
}
