package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.features.library.presentation.components.selection.CardSelection
import io.lukia.features.library.presentation.components.selection.SelectionMark
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.resources.Res
import io.lukia.resources.book_format_and_size
import io.lukia.resources.book_progress_percent
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.controls.pressFeedback
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Tall enough to read a jacket by, short enough to fit three times the grid's
 * books - and the size of every slot in a library laid out as a list, including
 * the one at the end that imports rather than opens. `ImportBookRow` carried its
 * own copy of both numbers, so a row and the slot under it agreed only by
 * coincidence.
 */
internal val RowCoverHeight: Dp = 64.dp

internal val RowCoverWidth: Dp = 44.dp

private val SelectionMarkSize: Dp = 22.dp

private val ProgressBarWidth: Dp = 56.dp

private val ProgressBarHeight: Dp = 3.dp

/**
 * One book as a row: a thumbnail, what it is called, who wrote it, and how far
 * through it its reader is.
 *
 * The list is for the library that has outgrown its covers. A grid is how a
 * shelf is browsed - a jacket is recognised before a title is read - but past a
 * few hundred books browsing gives way to searching, and a row fits the author,
 * format and position a cover has no room for.
 *
 * Deliberately not jiggling during selection, unlike the cards: the rocking says
 * "loose objects you can pick up", which is true of a grid of covers and not of
 * a list. A list asks with a column of circles instead, and so does this.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookRow(
    book: LibraryBook,
    onClick: () -> Unit,
    onMenuRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What a long press is announced as. The shelf offers a book's actions;
     * a list of search results offers what the book is, and a screen reader
     * should say which of the two this row is.
     */
    longClickLabel: String = "Book actions",
    /** How this row takes part in selection, or null when nothing is being selected. */
    selection: CardSelection? = null,
    /** The book on its way down from the server, or null when it is not. */
    transfer: BookTransfer? = null,
) {
    val document = book.document
    val title = document.displayTitle()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .pressFeedback(
                interactionSource = interactionSource,
                shape = shape,
            )
            .menuGesture(
                interactionSource = interactionSource,
                onClick = selection?.onToggle ?: onClick,
                onMenu = selection?.onToggle ?: onMenuRequest,
                menuLabel = longClickLabel,
            )
            .padding(Space.sm),
    ) {
        if (selection != null) {
            SelectionMark(
                selected = selection.selected,
                size = SelectionMarkSize,
                modifier = Modifier.padding(end = Space.sm),
            )
        }

        Box {
            BookCover(
                cover = book.cover,
                title = title,
                monogramFontSize = 14.sp,
                modifier = Modifier.size(RowCoverWidth, RowCoverHeight),
            )

            FavoriteBadge(
                visible = document.isFavorite,
                size = 16.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(Space.xxs),
            )
        }

        RowDetails(
            document = document,
            title = title,
            progress = book.progress,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
            transfer = transfer,
        )
    }
}

/**
 * What the row says about the book, which is everything a cover cannot: who
 * wrote it, what kind of file it is, and where its reader got to.
 */
@Composable
private fun RowDetails(
    document: DocumentInfo,
    title: String,
    progress: ReadingProgress?,
    modifier: Modifier = Modifier,
    transfer: BookTransfer? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        document.metadata?.authorLine()?.let { authors ->
            Text(
                text = authors,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    Res.string.book_format_and_size,
                    document.displayType(),
                    document.formattedSize(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // Yields to the percentage after it, which is the shorter of the
                // two and the one a reader is actually scanning down the row for.
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (document.alsoOnServer) OnServerMark(Modifier.padding(start = Space.xs))

            // Only for a book somebody has started. A bar at nothing on every
            // unread row would turn a shelf into a progress report.
            // Read out before the check rather than inside a `let`: the format
            // wants the whole percentage and cannot be handed a null, and the
            // two are non-null together - see `ReadingProgress.percent`.
            val fraction = progress?.fraction
            val percent = progress?.percent

            if (fraction != null && percent != null) {
                Text(
                    text = stringResource(Res.string.book_progress_percent, percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                // Draws its own track, so this only says how long and how tall.
                ReadingProgressBar(
                    fraction = fraction,
                    modifier = Modifier
                        .padding(start = Space.sm)
                        .width(ProgressBarWidth)
                        .height(ProgressBarHeight),
                )
            }
        }

        if (!document.exists) {
            BookStatusBadge(document, transfer = transfer)
        }
    }
}
