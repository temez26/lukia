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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import io.lukia.resources.home_resume
import io.lukia.resources.menu_book_details
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.BOOK_COVER_ASPECT_RATIO
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The cover's width against a shelf cell: the biggest on the screen, with room left for the text. */
private const val CoverScale = 1.3f

private val CoverMonogramSize = 30.sp

/** A long title gets this many lines before it is cut short. */
private const val TitleLines = 3

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * Carrying on is the commonest reason to open the app, so it gets the screen's
 * one accent-filled control and its biggest cover. The whole of it opens the
 * book as well, so a press anywhere on it does what the button says; a long
 * press offers the same menu as every other cover.
 *
 * Everything beside the cover has a fixed place: what the book is along the
 * cover's top edge, how far through and the buttons along its bottom, and the
 * facts about the file in a row under both. Hung loosely off the top instead,
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

            Details(
                book = book,
                title = title,
                onResume = { actions.onOpen(book) },
                onShowDetails = { actions.onShowDetails(book) },
                modifier = Modifier
                    .weight(1f)
                    // At least as tall as the cover, so the bottom slot meets its
                    // bottom edge; taller only when a long title or large text needs it.
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
    Box(modifier = modifier) {
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

/** What the book is at the top, how far through and the buttons at the bottom, and space between. */
@Composable
private fun Details(
    book: LibraryBook,
    title: String,
    onResume: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Two groups pushed apart rather than a weighted gap between them: a weight
    // stretches to whatever height the parent allows, where this only ever has
    // to reach the cover's bottom edge.
    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = modifier) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = TitleLines,
                overflow = TextOverflow.Ellipsis,
            )

            book.document.metadata?.authorLine()?.let { authors ->
                Text(
                    text = authors,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }

        Column(modifier = Modifier.padding(top = Space.md)) {
            book.progress?.let { progress -> ContinueProgress(progress, Modifier.padding(bottom = Space.md)) }

            Actions(onResume = onResume, onShowDetails = onShowDetails)
        }
    }
}

/**
 * Carrying on, which leads, and the way to the book's own page beside it as an
 * icon: the column beside a phone's cover is too narrow for two labelled
 * buttons, and the one that matters keeps its word. The icon is still named
 * for screen readers.
 */
@Composable
private fun Actions(onResume: () -> Unit, onShowDetails: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        LukiaButton(
            label = stringResource(Res.string.home_resume),
            onClick = onResume,
            size = ButtonSize.Compact,
        )

        LukiaIconButton(
            label = stringResource(Res.string.menu_book_details),
            onClick = onShowDetails,
        ) { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) }
    }
}
