package io.lukia.features.library.presentation

import androidx.compose.runtime.Immutable

/**
 * How many books one row of the home screen holds.
 *
 * A row scrolls sideways, so this is not about what fits - it is about where a
 * row stops being worth flicking through. Past a dozen covers the answer to
 * "where was I" is somewhere off the end of the screen, and the library tab is
 * the better way to find it. Each row ends in a way through to exactly that.
 */
private const val ShelfLimit = 12

/**
 * The three questions the home screen answers, in the order it asks them.
 *
 * Where a reader left off comes first, because opening the app to carry on is
 * the commonest reason to open it at all. What arrived most recently comes
 * next - a book just imported is a book about to be started - and what has been
 * marked worth keeping comes last, since it is the one a reader goes looking
 * for deliberately rather than stumbles back into.
 */
enum class HomeShelf(val title: String) {
    Recent("Recent"),
    RecentlyAdded("Recently Added"),
    Favorites("Favorites"),
    ;

    /**
     * The library tab showing all of what this row is a sample of, as the query
     * that narrows it there.
     *
     * The rows and the shelves behind them are defined in one place this way:
     * "See All" under Recent lands on the Reading shelf because that is what
     * Recent is a sample of, and cannot drift away from it.
     */
    fun seeAll(): LibraryQuery = when (this) {
        Recent -> LibraryQuery(shelf = LibraryShelf.Reading, sort = LibrarySort.LastRead)
        RecentlyAdded -> LibraryQuery(sort = LibrarySort.RecentlyAdded)
        Favorites -> LibraryQuery(favouritesOnly = true)
    }
}

/** One row of the home screen: what it is called, and the books on it. */
@Immutable
data class HomeRow(
    val shelf: HomeShelf,
    val books: List<LibraryBook>,
)

/**
 * The home screen's rows, with any that would be empty left out entirely.
 *
 * An empty row is worse than a missing one. A reader with no favourites has
 * nothing to learn from a heading over a blank strip, and three of them on a
 * fresh library is a screen that looks broken rather than new.
 *
 * The whole library goes in rather than the shelf as it is currently narrowed:
 * these rows are about the library itself, and a filter left on the library tab
 * has nothing to say about where somebody left off reading.
 */
fun List<LibraryBook>.homeRows(limit: Int = ShelfLimit): List<HomeRow> =
    HomeShelf.entries.mapNotNull { shelf ->
        val books = booksOn(shelf).take(limit)

        if (books.isEmpty()) null else HomeRow(shelf, books)
    }

private fun List<LibraryBook>.booksOn(shelf: HomeShelf): List<LibraryBook> = when (shelf) {
    // Everything that has been opened, not only what is unfinished: "Recent" is
    // a record of what a reader has been doing, and a book finished last night
    // is the truest thing on it.
    HomeShelf.Recent -> filter { it.progress != null }.sortedFor(LibrarySort.LastRead)
    HomeShelf.RecentlyAdded -> sortedFor(LibrarySort.RecentlyAdded)
    // Newest first, like the row above it. A favourite marked years ago is not
    // what somebody scrolling their home screen is looking for.
    HomeShelf.Favorites -> filter { it.document.isFavorite }.sortedFor(LibrarySort.RecentlyAdded)
}
