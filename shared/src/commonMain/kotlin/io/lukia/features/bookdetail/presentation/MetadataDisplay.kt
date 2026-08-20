package io.lukia.features.bookdetail.presentation

import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.languageName
import io.lukia.features.bookdetail.application.BookDetails

/**
 * What to call the book.
 *
 * The document's own title where it declares one, and the file name otherwise.
 * A book that names itself is naming itself better than whoever saved the file
 * did - "Moby Dick; or, The Whale" against "moby-dick-2.epub" - and the formats
 * that carry a title are the ones where the file name is least likely to be the
 * real one.
 */
fun DocumentInfo.bookTitle(): String =
    metadata?.title?.takeIf { it.isNotBlank() } ?: displayTitle()

/**
 * Everyone credited, as one line.
 *
 * Two authors are joined with "and" rather than a comma, which is how a credit
 * reads aloud and how a cover sets it. Beyond two, commas take over and the last
 * still gets its "and" - the alternative reads as a list of unrelated names.
 */
fun BookMetadata.authorLine(): String? = when (authors.size) {
    0 -> null
    1 -> authors[0]
    2 -> "${authors[0]} and ${authors[1]}"
    else -> authors.dropLast(1).joinToString(", ") + " and " + authors.last()
}

/**
 * The year a book was published, out of whatever the document wrote.
 *
 * The formats permit a full ISO timestamp, a bare year, and most things in
 * between; a reader wants the year from all of them. Anything that does not
 * start with a plausible year is passed through untouched rather than dropped -
 * a book that says "Spring 1851" is telling the truth in its own way.
 */
fun BookMetadata.publishedYear(): String? {
    val raw = published?.trim()?.takeIf { it.isNotBlank() } ?: return null

    return YearPrefix.find(raw)?.groupValues?.get(1) ?: raw
}

private val YearPrefix = Regex("""^(1\d{3}|20\d{2}|21\d{2})\b""")

/**
 * The language the document declares, as a language rather than as a tag.
 *
 * The table it reads from is shared with the library's language filter - see
 * [languageName] - so a book shown as Norwegian here is filed under Norwegian
 * there.
 */
fun BookMetadata.languageName(): String? = languageName(language)

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
