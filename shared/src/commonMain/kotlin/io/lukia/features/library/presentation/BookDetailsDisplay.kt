package io.lukia.features.library.presentation

import io.lukia.features.library.application.BookDetails

/**
 * How long the book is, once that is known.
 *
 * Known only for a book that has been opened: the length travels with the stored
 * reading position rather than being read from the file, so the library can say
 * how far through a book someone is without opening it. A book nobody has opened
 * therefore has no length to show, and showing nothing is the honest answer -
 * the alternative is opening every book in the library to fill in a row.
 *
 * Pages either way, and the same pages the reader itself counts. A PDF's were
 * set when the file was made; a reflowable book's are set by the reader, so this
 * is how long the book was in the window it was last read in rather than a
 * property of the file - which is also why it changes when the type size does.
 */
fun BookDetails.lengthLabel(): String? {
    val count = progress?.pageCount?.takeIf { it > 0 } ?: return null

    return if (count == 1) "1 page" else "$count pages"
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
fun BookDetails.progressLabel(): String = when {
    isFinished -> "Finished"
    progress?.pageNumber != null -> "Page ${progress.pageNumber} of ${progress.pageCount}"
    else -> "Not started"
}

/** What pressing the cover would do, which depends on whether it has been started. */
fun BookDetails.readActionLabel(): String = when {
    isInProgress -> "Continue reading"
    isFinished -> "Read again"
    else -> "Start reading"
}
