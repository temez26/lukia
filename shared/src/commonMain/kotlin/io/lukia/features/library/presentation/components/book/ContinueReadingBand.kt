package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.model.LibrarySelection
import io.lukia.features.library.presentation.model.LibrarySort
import io.lukia.features.library.presentation.model.ReadingStatus
import io.lukia.features.library.presentation.model.sortedFor
import io.lukia.getPlatform
import io.lukia.resources.Res
import io.lukia.resources.book_progress_page_of
import io.lukia.resources.book_read_continue
import io.lukia.resources.home_resume
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** How many books the band carries: the last few in progress, one row on any window wide enough to show it. */
private const val BandLength = 3

/** Small enough that the band stays one slim row, large enough to know the book by its cover. */
private val BandCoverWidth: Dp = 36.dp

/** The books the band offers: the last few being read, most recently opened first. */
internal fun List<LibraryBook>.continueReading(): List<LibraryBook> =
    filter { it.status == ReadingStatus.Reading }
        .sortedFor(LibrarySort.LastRead)
        .take(BandLength)

/**
 * What the band shows above [books], or nothing where it does not belong: on a
 * desktop only, which has no Home - phones and tablets lead with it on Home, see
 * `MobileShell` - and not while the shelf is narrowed or being picked from, when
 * the reader is after something else.
 */
@Composable
internal fun rememberContinueReading(
    books: List<LibraryBook>,
    query: LibraryQuery,
    selection: LibrarySelection,
): List<LibraryBook> {
    val reading = remember(books) { books.continueReading() }
    val shown = !getPlatform().isMobile && !query.isNarrowed && !selection.active
    return if (shown) reading else emptyList()
}

/**
 * The books being read, as one slim row above the shelf on a window: each one's
 * cover, title, place and progress, and a press that opens it where it was left.
 *
 * A window's answer to the phone's Home. It sits where the eye lands when the
 * library opens and scrolls away with the shelf once browsing starts, rather
 * than holding a page of its own.
 */
@Composable
internal fun ContinueReadingBand(
    books: List<LibraryBook>,
    onOpen: (DocumentInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth().padding(bottom = Space.sm)) {
        Text(
            text = stringResource(Res.string.book_read_continue),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = Space.sm)
                .semantics { heading() },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing)) {
            books.forEach { book ->
                ContinueEntry(book = book, onOpen = { onOpen(book.document) }, modifier = Modifier.weight(1f))
            }

            // Held to a third each however many there are, so one book does
            // not stretch into a banner across the window.
            repeat(BandLength - books.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ContinueEntry(book: LibraryBook, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val title = book.document.bookTitle()
    val progress = book.progress

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .pressable(
                interactionSource = interactionSource,
                shape = shape,
                onClickLabel = stringResource(Res.string.home_resume),
                onClick = onOpen,
            )
            .padding(Space.sm),
    ) {
        BookCover(
            cover = book.cover,
            title = title,
            shape = MaterialTheme.shapes.small,
            monogramFontSize = 14.sp,
            modifier = Modifier.width(BandCoverWidth),
        )

        Column(modifier = Modifier.padding(start = Space.md).weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (progress != null) {
                Text(
                    text = stringResource(Res.string.book_progress_page_of, progress.pageIndex + 1, progress.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                // On a track of its own: the bar's is drawn to sit over a cover,
                // and on this card it would vanish into the card itself.
                progress.fraction?.let { fraction ->
                    ReadingProgressBar(
                        fraction = fraction,
                        modifier = Modifier
                            .padding(top = Space.xs)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = LukiaAlpha.Tonal)),
                    )
                }
            }
        }
    }
}
