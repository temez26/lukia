package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Composable
import io.lukia.features.library.application.BookDetails
import io.lukia.resources.Res
import io.lukia.resources.book_length_pages
import io.lukia.resources.book_progress_finished
import io.lukia.resources.book_progress_not_started
import io.lukia.resources.book_progress_page_of
import io.lukia.resources.book_read_again
import io.lukia.resources.book_read_continue
import io.lukia.resources.book_read_start
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How long the book is, once that is known.
 *
 * Known only for a book that has been opened: the length travels with the stored
 * reading position rather than being read from the file, so the library can say
 * how far through a book someone is without opening it. Showing nothing for an
 * unopened book is the honest answer - the alternative is opening every book in
 * the library to fill in a row.
 *
 * Pages either way, and the same pages the reader counts. A PDF's were set when
 * the file was made; a reflowable book's are set by the reader, so this is how
 * long the book was in the window it was last read in - which is why it changes
 * with the type size.
 */
@Composable
fun BookDetails.lengthLabel(): String? {
    val count = progress?.pageCount?.takeIf { it > 0 } ?: return null

    return pluralStringResource(Res.plurals.book_length_pages, count, count)
}

/**
 * Where the reader is in the book, in a sentence.
 *
 * Three states rather than a position in every case. A book nobody has opened
 * has no position to report and saying "page 1" would suggest it had been
 * started; a book that has been finished is worth saying so about outright.
 *
 * A book in progress is named by its page rather than by a percentage, because
 * the percentage is already on the cover beside this - see
 * `ReadingProgressOverlay` - and because the page is what the reader will be
 * shown the moment they open the book. Two screens saying the same thing about
 * the same book should say it the same way.
 */
@Composable
fun BookDetails.progressLabel(): String {
    val page = progress?.pageNumber

    return when {
        isFinished -> stringResource(Res.string.book_progress_finished)
        page != null -> stringResource(Res.string.book_progress_page_of, page, progress.pageCount)
        else -> stringResource(Res.string.book_progress_not_started)
    }
}

/** What pressing the cover would do, which depends on whether it has been started. */
@Composable
fun BookDetails.readActionLabel(): String = when {
    isInProgress -> stringResource(Res.string.book_read_continue)
    isFinished -> stringResource(Res.string.book_read_again)
    else -> stringResource(Res.string.book_read_start)
}
