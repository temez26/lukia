package io.lukia.features.library.presentation.model

/**
 * The four states a book can be in with its reader, as the segments of the
 * control at the top of the library.
 *
 * Reading state earns the segmented control rather than format, being the
 * division a reader browses by - what am I part way through, what have I not
 * started - where format is a property of the file and belongs in the filter
 * menu beside it.
 *
 * Deliberately a closed set: a segmented control with an open-ended number of
 * options is not one.
 */
enum class LibraryShelf {
    All,
    Reading,
    Unread,
    Finished,
    ;

    fun matches(book: LibraryBook): Boolean = when (this) {
        All -> true
        Reading -> book.status == ReadingStatus.Reading
        Unread -> book.status == ReadingStatus.Unread
        Finished -> book.status == ReadingStatus.Finished
    }
}
