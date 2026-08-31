package io.lukia.features.library.presentation.model

/**
 * The orders a library can be put in.
 *
 * [Added] is the shelf's own order - the order the books were imported in - and
 * is the default because it is the one a reader built themselves. Everything
 * else here is a view onto it, and none of them is stored: an ordering is how
 * someone is looking at their books this minute, not a property of the books.
 */
enum class LibrarySort(val label: String) {
    Added("Date added"),
    RecentlyAdded("Recently added"),
    Title("Title A–Z"),
    TitleReversed("Title Z–A"),
    Author("Author A–Z"),
    LastRead("Recently read"),
    Largest("Largest first"),
}

/**
 * This library in [sort] order.
 *
 * Every comparison falls back on the order it was handed, which is the shelf's
 * own: sorting is stable in Kotlin, so two books by the same author stay in the
 * order they were imported rather than swapping places between passes.
 *
 * Ordering is on the keys worked out in [asLibrary] rather than on the documents
 * themselves, so a sort is a comparison of strings that already exist rather
 * than a title being derived from a file name a thousand times over.
 */
fun List<LibraryBook>.sortedFor(sort: LibrarySort): List<LibraryBook> = when (sort) {
    LibrarySort.Added -> this
    LibrarySort.RecentlyAdded -> reversed()
    LibrarySort.Title -> sortedBy { it.sortTitle }
    LibrarySort.TitleReversed -> sortedByDescending { it.sortTitle }
    // A book that credits nobody sorts to the end rather than to the top under
    // the empty string, where it would push the books that do name an author
    // below a block of files that cannot answer the question at all.
    LibrarySort.Author -> sortedWith(
        compareBy<LibraryBook> { it.sortAuthor.isEmpty() }
            .thenBy { it.sortAuthor }
            .thenBy { it.sortTitle },
    )
    // Never opened sorts below read-but-unstamped, which is every position
    // stored before the app began recording this - see `ReadingPosition.sq`.
    LibrarySort.LastRead -> sortedByDescending { it.progress?.readAt ?: -1L }
    LibrarySort.Largest -> sortedByDescending { it.document.sizeBytes }
}
