package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Immutable

/**
 * How many books one row of the home screen holds.
 *
 * A row scrolls sideways, so this is not about what fits - it is about where a
 * row stops being worth flicking through. Past a dozen covers the answer to
 * "where was I" is somewhere off the end of the screen, and the library tab is
 * the better way to find it. Each row's title leads through to exactly that.
 */
private const val ShelfLimit = 12

/**
 * The rows under the book being read, in the order the screen shows them.
 *
 * Only the questions worth opening the app for: what else is on the go, what
 * just arrived, what has not been started. Nearly finished, kept and done are
 * the library tab's filters rather than rows here - see ADR 0007.
 */
enum class HomeShelf {
    Reading,
    RecentlyAdded,
    Unread,
    ;

    /**
     * The library tab showing all of what this row is a sample of, as the query
     * that narrows it there - so the row and the shelf behind its title cannot
     * drift apart.
     */
    fun seeAll(): LibraryQuery = when (this) {
        Reading -> LibraryQuery(shelf = LibraryShelf.Reading, sort = LibrarySort.LastRead)
        RecentlyAdded -> LibraryQuery(sort = LibrarySort.RecentlyAdded)
        Unread -> LibraryQuery(shelf = LibraryShelf.Unread, sort = LibrarySort.RecentlyAdded)
    }
}

/** One row of the home screen: what it is called, and the books on it. */
@Immutable
data class HomeRow(
    val shelf: HomeShelf,
    val books: List<LibraryBook>,
)

/**
 * The whole home screen: the book to carry on with, and the rows under it.
 *
 * [current] is the most recently read book still being read, or null when
 * nothing is.
 */
@Immutable
data class HomeLayout(
    val current: LibraryBook?,
    val rows: List<HomeRow>,
) {
    val isEmpty: Boolean get() = current == null && rows.isEmpty()
}

/**
 * The home screen for this library.
 *
 * Each book appears once, on the first place that holds it. The rows overlap by
 * nature - the book being read is usually also the newest - and the same cover
 * three times down one screen reads as clutter rather than emphasis.
 *
 * A row with nothing left on it is left out: a heading over a blank strip
 * teaches nothing.
 *
 * The whole library goes in rather than the narrowed shelf - these rows are
 * about the library itself, and a filter left on the library tab says nothing
 * about where somebody left off reading.
 */
fun List<LibraryBook>.homeLayout(limit: Int = ShelfLimit): HomeLayout {
    val reading = filter { it.status == ReadingStatus.Reading }.sortedFor(LibrarySort.LastRead)
    val current = reading.firstOrNull()
    val shown = mutableSetOf<String>()
    current?.let { shown += it.location }

    val rows = HomeShelf.entries.mapNotNull { shelf ->
        val books = booksOn(shelf, reading)
            .filter { it.location !in shown }
            .take(limit)
        shown += books.map { it.location }

        if (books.isEmpty()) null else HomeRow(shelf, books)
    }

    return HomeLayout(current, rows)
}

private fun List<LibraryBook>.booksOn(
    shelf: HomeShelf,
    reading: List<LibraryBook>,
): List<LibraryBook> = when (shelf) {
    HomeShelf.Reading -> reading
    HomeShelf.RecentlyAdded -> sortedFor(LibrarySort.RecentlyAdded)
    HomeShelf.Unread -> filter { it.status == ReadingStatus.Unread }
        .sortedFor(LibrarySort.RecentlyAdded)
}
