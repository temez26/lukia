package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The cover's width against a shelf cell: a little larger than the covers in the rows under it. */
private const val CoverScale = 1.15f

private val CoverMonogramSize = 30.sp

/** The read button: as big as the ring across the cover from it. */
private val ReadButtonSize: Dp = 60.dp

/** The open book on the read button, against the button's width. */
private const val ReadGlyphShare = 0.45f

/**
 * The book being read, at the top of the home screen, with a way to carry on.
 *
 * The cover sits in the middle, whole and at its own proportions, with how far
 * through on its left and the button to carry on on its right - the way a player
 * sets what is playing between where it has got to and the button that goes on
 * - and the facts about the file along the bottom. No title or author is
 * printed: the cover says what the book is, and a screen reader is told both.
 *
 * The button is an open book rather than a play triangle, as reading apps draw
 * it: carrying on with a book is not something that runs while you watch.
 *
 * Anywhere on the card opens the book as the button does; a long press offers
 * the book's menu, the same as every other cover's, which is also where its own
 * page is.
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
    val authors = book.document.metadata?.authorLine()
    val named = if (authors != null) stringResource(Res.string.book_title_by_author, title, authors) else title
    val interactionSource = remember { MutableInteractionSource() }

    GroupCard(
        modifier = modifier
            .menuGesture(
                interactionSource = interactionSource,
                onClick = { actions.onOpen(book) },
                onMenu = { onMenuExpandedChange(true) },
                menuLabel = stringResource(Res.string.book_actions),
                clickLabel = stringResource(Res.string.book_read_title, title),
            )
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Equal room either side, so the cover stays in the middle even
                // where there is no progress to show on its left.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                    book.progress?.let { progress -> ContinueProgress(progress) }
                }

                ContinueCover(
                    book = book,
                    title = title,
                    named = named,
                    actions = actions,
                    menuExpanded = menuExpanded,
                    onMenuExpandedChange = onMenuExpandedChange,
                    transfer = transfer,
                )

                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                    ReadButton(onClick = { actions.onOpen(book) })
                }
            }

            ContinueFacts(document = book.document, modifier = Modifier.padding(top = Space.lg))
        }
    }
}

/** The cover, whole, with its badges and the menu a long press opens under it. */
@Composable
private fun ContinueCover(
    book: LibraryBook,
    title: String,
    named: String,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    transfer: BookTransfer?,
) {
    val shape = MaterialTheme.shapes.medium

    // The cover is all this card shows of what the book is, so it is what a
    // screen reader is told: the title, and who wrote it.
    Box(
        modifier = Modifier
            .width(LocalLukiaDimens.current.bookCellMinWidth * CoverScale)
            .semantics { contentDescription = named },
    ) {
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

/** Carrying on: a round accent button with an open book in it, and the word under it. */
@Composable
private fun ReadButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.home_resume)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .pressable(
                interactionSource = interactionSource,
                shape = MaterialTheme.shapes.medium,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(Space.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ReadButtonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            OpenBookGlyph(
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(ReadButtonSize * ReadGlyphShare),
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
