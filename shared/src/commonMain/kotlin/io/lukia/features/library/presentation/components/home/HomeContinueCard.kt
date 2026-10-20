package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.components.book.CoverTransferOverlay
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_read_title
import io.lukia.resources.book_title_by_author
import io.lukia.resources.home_resume
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.BOOK_COVER_ASPECT_RATIO
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The cover's width against a shelf cell: the biggest on the screen, with room left for the text. */
private const val CoverScale = 1.3f

private val CoverMonogramSize = 30.sp

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * Carrying on is the commonest reason to open the app, so it gets the screen's
 * one accent-filled control and its biggest cover. The whole of it opens the
 * book as well, so a press anywhere on it does what the button says; a long
 * press offers the same menu as every other cover.
 *
 * The cover is the book here: no title or author beside it, which only said
 * again in small print what the artwork says at this size. Beside it, how far
 * through and the button to carry on; under both, the facts about the file.
 * The book's own page is in the long-press menu, as on every other cover. Hung loosely off the top instead,
 * a short title, a missing author or an unknown length moved the buttons, so
 * no two books looked alike. The cover's frame is the same for every book -
 * artwork of any shape is fitted inside it - so the frame is what everything
 * else lines up against.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeContinueCard(
    book: LibraryBook,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** How a download of this book is going, if one is - see [CoverTransferOverlay]. */
    transfer: BookTransfer? = null,
) {
    val title = book.document.bookTitle()
    val coverWidth = LocalLukiaDimens.current.bookCellMinWidth * CoverScale
    val interactionSource = remember { MutableInteractionSource() }

    // Straight on the page rather than in a card, as reading apps set the book
    // in hand: the biggest cover on the screen is emphasis enough.
    Column(
        modifier = modifier
            .menuGesture(
                interactionSource = interactionSource,
                onClick = { actions.onOpen(book) },
                onMenu = { onMenuExpandedChange(true) },
                menuLabel = stringResource(Res.string.book_actions),
                clickLabel = stringResource(Res.string.book_read_title, title),
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = Space.sm),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ContinueCover(
                book = book,
                title = title,
                shape = MaterialTheme.shapes.medium,
                actions = actions,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                transfer = transfer,
                modifier = Modifier.width(coverWidth),
            )

            Progress(
                book = book,
                onResume = { actions.onOpen(book) },
                modifier = Modifier
                    .weight(1f)
                    // As tall as the cover at least, so the progress sits centred
                    // against it; taller only when large text needs it.
                    .heightIn(min = coverWidth / BOOK_COVER_ASPECT_RATIO)
                    .padding(start = Space.lg),
            )
        }

        ContinueFacts(document = book.document, modifier = Modifier.padding(top = Space.lg))
    }
}

/** The cover, with what is drawn over it and the menu a long press opens under it. */
@Composable
private fun ContinueCover(
    book: LibraryBook,
    title: String,
    shape: Shape,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    transfer: BookTransfer?,
    modifier: Modifier = Modifier,
) {
    // The cover is all this card shows of what the book is, so it is what a
    // screen reader is told: the title, and who wrote it.
    val authors = book.document.metadata?.authorLine()
    val named = if (authors != null) stringResource(Res.string.book_title_by_author, title, authors) else title

    Box(modifier = modifier.semantics { contentDescription = named }) {
        BookCover(
            cover = book.cover,
            title = title,
            shape = shape,
            monogramFontSize = CoverMonogramSize,
            modifier = Modifier.fillMaxWidth(),
        )

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
        )
    }
}

/**
 * How far through, and the button to carry on, centred against the cover.
 *
 * Nothing here says what the book is: the cover does that, at the size this
 * card gives it, and a title and author beside it only said it again in
 * smaller print.
 */
@Composable
private fun Progress(
    book: LibraryBook,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.Center, modifier = modifier) {
        book.progress?.let { progress -> ContinueProgress(progress, Modifier.padding(bottom = Space.lg)) }

        LukiaButton(
            label = stringResource(Res.string.home_resume),
            onClick = onResume,
            size = ButtonSize.Compact,
        )
    }
}
