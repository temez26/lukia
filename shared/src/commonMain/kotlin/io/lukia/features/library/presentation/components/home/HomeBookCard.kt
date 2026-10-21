package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.components.book.CoverTransferOverlay
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.menuHeader
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_read_title
import io.lukia.shared.presentation.components.controls.ReadingProgressOverlay
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.controls.scaleOnPress
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Big enough to read at a glance, small enough not to be the card's subject. */
private val CoverMonogramSize = 26.sp

/**
 * One book on a home row: its cover, its title, and who wrote it.
 *
 * Deliberately not [BookCard]. That card belongs to a shelf being worked on - a
 * selection mark, a hovering menu trigger, the jiggle of picking books up - and
 * none of it belongs on a screen whose whole job is a way back into a book.
 *
 * A long press is the exception, opening the same menu as on the shelf: a reader
 * who has learned that gesture does not expect it missing from the screen they
 * see most, and a home row is exactly where a book turns out to be finished
 * with, or a favourite, or gone.
 *
 * Sized by its caller through [modifier]: a home row gives it a fixed width, a
 * shelf screen's grid gives it a cell.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeBookCard(
    book: LibraryBook,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** How a download of this book is going, if one is - see [CoverTransferOverlay]. */
    transfer: BookTransfer? = null,
    /** False for a shelf of covers alone, small and many - see `HomeShelfGrid`. */
    showText: Boolean = true,
) {
    val document = book.document
    val title = document.bookTitle()
    val interactionSource = remember { MutableInteractionSource() }
    val coverShape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            .menuGesture(
                interactionSource = interactionSource,
                onClick = { actions.onOpen(book) },
                onMenu = { onMenuExpandedChange(true) },
                menuLabel = stringResource(Res.string.book_actions),
                clickLabel = stringResource(Res.string.book_read_title, title),
            )
            .scaleOnPress(interactionSource, pressedScale = 0.96f)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics(mergeDescendants = true) { contentDescription = title },
    ) {
        Cover(
            book = book,
            title = title,
            shape = coverShape,
            interactionSource = interactionSource,
            menuExpanded = menuExpanded,
            actions = actions,
            onMenuExpandedChange = onMenuExpandedChange,
            transfer = transfer,
        )

        if (!showText) return@Column

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            // Two lines whether the title needs them or not, so the author line
            // sits level across a row of books with titles of every length.
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.md),
        )

        document.metadata?.authorLine()?.let { authors ->
            Text(
                text = authors,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }
    }
}

/**
 * The jacket, with how far through the book its reader is laid over it and the
 * favourite mark in the corner - the same two overlays, in the same places, that
 * the library grid draws on its own covers.
 *
 * The menu hangs off the cover rather than off the card, so it opens under the
 * artwork a finger is holding rather than under the author line below it, which
 * is where the shelf's own cards put it.
 */
@Composable
private fun Cover(
    book: LibraryBook,
    title: String,
    shape: Shape,
    interactionSource: MutableInteractionSource,
    menuExpanded: Boolean,
    actions: HomeBookActions,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    transfer: BookTransfer? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        BookCover(
            cover = book.cover,
            title = title,
            shape = shape,
            monogramFontSize = CoverMonogramSize,
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(interactionSource, shape),
        )

        // Only for a book somebody has started: an empty bar on every cover
        // would turn a home screen into a progress report.
        book.progress?.fraction?.let { fraction ->
            ReadingProgressOverlay(
                fraction = fraction,
                percent = book.progress.percent,
                modifier = Modifier.matchParentSize().clip(shape),
            )
        }

        FavoriteBadge(
            visible = book.document.isFavorite,
            modifier = Modifier.padding(Space.sm),
        )

        CoverTransferOverlay(
            document = book.document,
            transfer = transfer,
            modifier = Modifier.matchParentSize().clip(shape),
        )

        BookActionsMenu(
            expanded = menuExpanded,
            isFavorite = book.document.isFavorite,
            onToggleFavorite = { actions.onToggleFavorite(book) },
            onRefresh = { actions.onRefresh(book) },
            onDelete = { actions.onDelete(book) },
            canRemoveFromDevice = !book.document.awaitsDownload,
            onDismissRequest = { onMenuExpandedChange(false) },
            onShowDetails = { actions.onShowDetails(book) },
            onSelectBooks = actions.onSelectBooks,
            onRemoveFromServer = actions.removeFromServer(book.document),
            onMarkUnread = book.progress?.let { { actions.onMarkUnread(book) } },
            header = book.menuHeader(),
        )
    }
}
