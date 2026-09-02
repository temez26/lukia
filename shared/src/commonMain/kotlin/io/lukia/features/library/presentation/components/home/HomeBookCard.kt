package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.displayTitle
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.shared.presentation.components.controls.ReadingProgressOverlay
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.controls.scaleOnPress
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.Space

/** Big enough to read at a glance, small enough not to be the card's subject. */
private val CoverMonogramSize = 26.sp

/**
 * One book on a home row: its cover, its title, and who wrote it.
 *
 * Deliberately not [BookCard]. That card belongs to a grid a shelf is being
 * worked on - it carries a selection mark, a hovering menu trigger and the
 * jiggle that goes with picking books off a shelf - and none of that belongs on
 * a screen whose whole job is to be a way back into a book. A press opens the
 * book, and that is what the screen is for.
 *
 * A long press is the exception, and it is the same gesture that opens the same
 * menu on the shelf. A reader who has learned that holding a cover offers what
 * can be done with the book does not expect the gesture to be missing from the
 * screen they see most - and a home row is exactly where a book turns out to be
 * finished with, or a favourite, or gone.
 *
 * [width] is set by the row rather than by the card, because the rows are not
 * all the same: where a reader left off is worth more room than what happens to
 * have arrived recently.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeBookCard(
    book: LibraryBook,
    width: Dp,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = book.document
    val title = document.displayTitle()
    val interactionSource = remember { MutableInteractionSource() }
    val coverShape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            .width(width)
            .menuGesture(
                interactionSource = interactionSource,
                onClick = { actions.onOpen(book) },
                onMenu = { onMenuExpandedChange(true) },
                menuLabel = "Book actions",
                clickLabel = "Read",
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
        )

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
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

        BookActionsMenu(
            expanded = menuExpanded,
            isFavorite = book.document.isFavorite,
            onToggleFavorite = { actions.onToggleFavorite(book) },
            onRefresh = { actions.onRefresh(book) },
            onDelete = { actions.onDelete(book) },
            onDismissRequest = { onMenuExpandedChange(false) },
            onShowDetails = { actions.onShowDetails(book) },
            onSelectBooks = actions.onSelectBooks,
        )
    }
}
