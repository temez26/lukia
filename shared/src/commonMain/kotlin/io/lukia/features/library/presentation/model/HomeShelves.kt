package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Immutable

/**
 * How many books one row of the home screen holds.
 *
 * A row scrolls sideways, so this is not about what fits - it is about where a
 * row stops being worth flicking through. Past a dozen covers the answer to
 * "where was I" is somewhere off the end of the screen, and the library tab is
 * the better way to find it. Each row's title opens the whole of it - see
 * [onShelf].
 */
private const val ShelfLimit = 12

/**
 * The rows under the book being read, in the order the screen shows them:
 * what was kept, what was opened lately, what has not been started, what just
 * arrived. Nearly finished and done are the library tab's filters rather than
 * rows here - see ADR 0007.
 */
enum class HomeShelf {
    Favorites,
    RecentlyOpened,
    Unread,
    RecentlyAdded,
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
 * nothing is. [bookCount] and [readingCount] are the whole library's, for the
 * line under the screen's title.
 */
@Immutable
data class HomeLayout(
    val current: LibraryBook?,
    val rows: List<HomeRow>,
    val bookCount: Int = 0,
    val readingCount: Int = 0,
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
    val reading = filter { it.status == ReadingStatus.Reading }
    val current = reading.sortedFor(LibrarySort.LastRead).firstOrNull()
    val shown = mutableSetOf<String>()
    current?.let { shown += it.location }

    val rows = HomeShelf.entries.mapNotNull { shelf ->
        val books = onShelf(shelf)
            .filter { it.location !in shown }
            .take(limit)
        shown += books.map { it.location }

        if (books.isEmpty()) null else HomeRow(shelf, books)
    }

    return HomeLayout(current, rows, bookCount = size, readingCount = reading.size)
}

/**
 * Every book [shelf] holds, in the order its row shows them: the whole of what
 * a home row samples, for the screen its title opens. Unlike the row, nothing
 * is left out for appearing elsewhere on Home.
 */
fun List<LibraryBook>.onShelf(shelf: HomeShelf): List<LibraryBook> = when (shelf) {
    // Newest first: a favourite marked years ago is not what somebody
    // scrolling their home screen is looking for.
    HomeShelf.Favorites -> filter { it.document.isFavorite }.sortedFor(LibrarySort.RecentlyAdded)
    HomeShelf.RecentlyOpened -> filter { it.progress != null }.sortedFor(LibrarySort.LastRead)
    HomeShelf.Unread -> filter { it.status == ReadingStatus.Unread }
        .sortedFor(LibrarySort.RecentlyAdded)
    HomeShelf.RecentlyAdded -> sortedFor(LibrarySort.RecentlyAdded)
}
