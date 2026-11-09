package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.core.presentation.bookTitle
import io.lukia.features.stats.domain.BookTime
import io.lukia.features.stats.domain.FinishedBook
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.book_open_title
import io.lukia.resources.stats_finished_on
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.rememberBookCover
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

private val RowCoverHeight: Dp = 56.dp

private val RowCoverWidth: Dp = 38.dp

/** This year's books by the time spent in them, most first. */
@Composable
internal fun MostReadList(
    books: List<BookTime>,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookList(items = books, modifier = modifier) { book ->
        BookRow(
            location = book.location,
            document = book.document,
            // A book gone from the shelf is named by the title its sittings kept,
            // and failing that by its file.
            title = book.document?.bookTitle() ?: book.title ?: book.location.substringAfterLast('/'),
            detail = readingTime(book.durationMs),
            onClick = book.document?.let { { onOpenBook(book.location) } },
        )
    }
}

/** This year's finished books, most recent first, each with the day it was finished. */
@Composable
internal fun FinishedList(
    books: List<FinishedBook>,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookList(items = books, modifier = modifier) { book ->
        val location = book.document.document.location

        BookRow(
            location = location,
            document = book.document,
            title = book.document.bookTitle(),
            detail = stringResource(
                Res.string.stats_finished_on,
                book.finishedOn.day,
                monthName(book.finishedOn.month, MonthForm.Short),
            ),
            onClick = { onOpenBook(location) },
        )
    }
}

@Composable
private fun <T> BookList(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                // Inset past the cover, so the rules line up under the titles
                // rather than cutting across the artwork.
                LukiaDivider(modifier = Modifier.padding(start = RowCoverWidth + Space.lg))
            }

            row(item)
        }
    }
}

/**
 * One book: its cover, its title, and what this list says about it.
 *
 * [onClick] is null for a book no longer on the shelf, which has nothing to open.
 */
@Composable
private fun BookRow(
    location: String,
    document: DocumentInfo?,
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.pressable(
                        interactionSource = interactionSource,
                        shape = shape,
                        onClickLabel = stringResource(Res.string.book_open_title, title),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = Space.md),
    ) {
        BookCover(
            cover = rememberBookCover(location, document?.coverImage?.bytes),
            title = title,
            monogramFontSize = 13.sp,
            modifier = Modifier.size(RowCoverWidth, RowCoverHeight),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(Space.xs),
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
