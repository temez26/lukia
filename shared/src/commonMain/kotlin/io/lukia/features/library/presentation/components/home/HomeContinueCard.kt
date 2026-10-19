package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.core.presentation.languageName
import io.lukia.core.presentation.publishedYear
import io.lukia.features.library.presentation.components.book.CoverTransferOverlay
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_format_and_size
import io.lukia.resources.book_progress_page_of
import io.lukia.resources.book_read_continue
import io.lukia.resources.book_read_title
import io.lukia.resources.home_pages_left
import io.lukia.resources.home_resume
import io.lukia.resources.menu_book_details
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The cover's width against a shelf cell: the biggest on the screen, with room left for the text. */
private const val CoverScale = 1.2f

private val CoverMonogramSize = 26.sp

/** Thinner than the bar welded to a cover: here it sits on a card, not over artwork. */
private val ProgressBarHeight: Dp = 4.dp

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * Carrying on is the commonest reason to open the app, so it gets the screen's
 * one accent-filled control, its biggest cover, and room for what the book is,
 * where it is kept, how far through it its reader is and how it begins.
 * The whole of it opens the book as well, so a press anywhere on it does what the
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

    // Straight on the page rather than in a card, as reading apps set the book
    // in hand: the biggest cover on the screen is emphasis enough, and a box
    // around it only set it apart from the background it sits on.
    Row(
        verticalAlignment = Alignment.Top,
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
            onShowDetails = { actions.onShowDetails(book) },
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        )
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

@Composable
private fun Details(
    book: LibraryBook,
    title: String,
    onResume: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = book.document
    val metadata = document.metadata

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.book_read_continue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xs),
        )

        metadata?.authorLine()?.let { authors ->
            Text(
                text = authors,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }

        AboutLine(book, Modifier.padding(top = Space.sm))

        book.progress?.let { progress -> Progress(progress, Modifier.padding(top = Space.md)) }

        metadata?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = DescriptionLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.md),
            )
        }

        Actions(onResume = onResume, onShowDetails = onShowDetails, modifier = Modifier.padding(top = Space.md))
    }
}

/** Carrying on, which leads, and the book's own page beside it. */
@Composable
private fun Actions(onResume: () -> Unit, onShowDetails: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier,
    ) {
        LukiaButton(
            label = stringResource(Res.string.home_resume),
            onClick = onResume,
            size = ButtonSize.Compact,
        )

        LukiaButton(
            label = stringResource(Res.string.menu_book_details),
            onClick = onShowDetails,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Compact,
            icon = { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) },
        )
    }
}

/** How much of the blurb is shown: enough to recall the book by, not to read it here. */
private const val DescriptionLines = 3

/** Separates the facts in [aboutLine], as Home's summary sets its own. */
private const val AboutSeparator = " · "

/** What kind of file the book is and how big, then its language and year where it says them. */
@Composable
private fun AboutLine(book: LibraryBook, modifier: Modifier = Modifier) {
    val document = book.document
    val metadata = document.metadata

    Text(
        text = listOfNotNull(
            stringResource(Res.string.book_format_and_size, document.displayType(), document.formattedSize()),
            metadata?.languageName(),
            metadata?.publishedYear(),
        ).joinToString(AboutSeparator),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * How far through: the bar and its percentage, then the page and what is left.
 * A book whose length is unknown has no fraction to draw, and says nothing.
 */
@Composable
private fun Progress(progress: ReadingProgress, modifier: Modifier = Modifier) {
    val fraction = progress.fraction ?: return

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

            progress.percent?.let { percent ->
                Text(
                    text = stringResource(Res.string.reader_percent, percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.sm),
                )
            }
        }

        progress.pageNumber?.let { page ->
            val left = progress.pageCount - page

            Text(
                text = listOfNotNull(
                    stringResource(Res.string.book_progress_page_of, page, progress.pageCount),
                    pluralStringResource(Res.plurals.home_pages_left, left, left).takeIf { left > 0 },
                ).joinToString(AboutSeparator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}
