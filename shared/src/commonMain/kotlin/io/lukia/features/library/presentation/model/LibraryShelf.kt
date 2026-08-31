package io.lukia.features.library.presentation.model

/**
 * The four states a book can be in with its reader, as the segments of the
 * control at the top of the library.
 *
 * Reading state earns the segmented control rather than format because it is the
 * division a reader actually browses by - what am I part way through, what have
 * I not started - while format is a property of the file, which belongs with the
 * other properties in the filter menu beside it.
 *
 * Deliberately a closed set: these are the segments of a control the reader can
 * see, and a segmented control with an open-ended number of options is not one.
 */
enum class LibraryShelf(val label: String) {
    All("All"),
    Reading("Reading"),
    Unread("Unread"),
    Finished("Finished"),
    ;

    fun matches(book: LibraryBook): Boolean = when (this) {
        All -> true
        Reading -> book.status == ReadingStatus.Reading
        Unread -> book.status == ReadingStatus.Unread
        Finished -> book.status == ReadingStatus.Finished
    }
}
