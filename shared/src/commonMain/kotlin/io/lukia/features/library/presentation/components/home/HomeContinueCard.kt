package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.lukia.resources.book_read_continue
import io.lukia.resources.book_read_title
import io.lukia.resources.home_resume
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The cover's width against a shelf cell: big enough to lead, small enough to leave room for the text. */
private const val CoverScale = 0.85f

private val CoverMonogramSize = 26.sp

/** Thinner than the bar welded to a cover: here it sits on a card, not over artwork. */
private val ProgressBarHeight: Dp = 4.dp

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * Carrying on is the commonest reason to open the app, so it gets the screen's
 * one accent-filled control and room for the title, author and how far through.
 * The whole card opens the book as well, so a press anywhere on it does what the
 * button says; a long press offers the same menu as every other cover.
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
    val document = book.document
    val title = document.bookTitle()
    val dimens = LocalLukiaDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val coverShape = MaterialTheme.shapes.medium

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Space.lg),
        ) {
            ContinueCover(
                book = book,
                title = title,
                shape = coverShape,
                actions = actions,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                transfer = transfer,
                modifier = Modifier.width(dimens.bookCellMinWidth * CoverScale),
            )

            Details(
                book = book,
                title = title,
                onResume = { actions.onOpen(book) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.lg),
            )
        }
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
            onDismissRequest = { onMenuExpandedChange(false) },
            onShowDetails = { actions.onShowDetails(book) },
            onSelectBooks = actions.onSelectBooks,
            onRemoveFromServer = actions.removeFromServer(book.document),
            onMarkUnread = book.progress?.let { { actions.onMarkUnread(book) } },
        )
    }
}

@Composable
private fun Details(
    book: LibraryBook,
    title: String,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.book_read_continue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xs),
        )

        book.document.metadata?.authorLine()?.let { authors ->
            Text(
                text = authors,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }

        // A book is current because it has been opened, but one whose length is
        // unknown has no fraction to draw.
        book.progress?.fraction?.let { fraction ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Space.md),
            ) {
                ReadingProgressBar(
                    fraction = fraction,
                    modifier = Modifier
                        .weight(1f)
                        .height(ProgressBarHeight)
                        .clip(MaterialTheme.shapes.extraSmall)
                        // The bar's own track is tuned to sit over artwork and
                        // vanishes on a card, leaving a sliver with no length.
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                book.progress.percent?.let { percent ->
                    Text(
                        text = stringResource(Res.string.reader_percent, percent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
            }
        }

        LukiaButton(
            label = stringResource(Res.string.home_resume),
            onClick = onResume,
            size = ButtonSize.Compact,
            modifier = Modifier.padding(top = Space.md),
        )
    }
}
