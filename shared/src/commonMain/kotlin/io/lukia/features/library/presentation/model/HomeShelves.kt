package io.lukia.features.library.presentation.model

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
 * How far through a book has to be before it counts as nearly done.
 *
 * High enough that the row is a short list of books worth finishing this week
 * rather than a second copy of Recent. A book two thirds read is still being
 * read; one at nine tenths is one evening from the end.
 */
private const val AlmostDoneFraction = 0.7f

/**
 * The questions the home screen answers, in the order it asks them.
 *
 * Where a reader left off comes first, opening the app to carry on being the
 * commonest reason to open it. What arrived most recently comes next - a book
 * just imported is about to be started - and the rest run down the arc a book
 * takes: nearly finished, never started, kept, done.
 *
 * Every row is a sample, which is why each carries a way through to the library
 * tab showing all of it.
 */
enum class HomeShelf {
    Recent,
    RecentlyAdded,
    AlmostDone,
    Unread,
    Favorites,
    Finished,
    ;

    /**
     * The library tab showing all of what this row is a sample of, as the query
     * that narrows it there.
     *
     * Rows and the shelves behind them are defined in one place this way, so
     * "See All" under Recent lands on Reading - what Recent samples - and cannot
     * drift from it.
     *
     * [AlmostDone] lands on Reading too: there is no shelf for "nearly done" and
     * should not be, it being a nudge rather than a category, and Reading is the
     * smallest true shelf containing all of it.
     */
    fun seeAll(): LibraryQuery = when (this) {
        Recent -> LibraryQuery(shelf = LibraryShelf.Reading, sort = LibrarySort.LastRead)
        RecentlyAdded -> LibraryQuery(sort = LibrarySort.RecentlyAdded)
        AlmostDone -> LibraryQuery(shelf = LibraryShelf.Reading, sort = LibrarySort.LastRead)
        Unread -> LibraryQuery(shelf = LibraryShelf.Unread, sort = LibrarySort.RecentlyAdded)
        Favorites -> LibraryQuery(favouritesOnly = true)
        Finished -> LibraryQuery(shelf = LibraryShelf.Finished, sort = LibrarySort.LastRead)
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
 * An empty row is worse than a missing one: a heading over a blank strip teaches
 * nothing, and six of them on a fresh library looks broken rather than new.
 *
 * The whole library goes in rather than the narrowed shelf - these rows are
 * about the library itself, and a filter left on the library tab says nothing
 * about where somebody left off reading.
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
    // Closest to the end first, which is the only order this row can be in: it
    // exists to answer "what can I finish", and the answer is the top of it.
    HomeShelf.AlmostDone -> filter { it.isAlmostDone }.sortedByDescending { it.fraction }
    HomeShelf.Unread -> filter { it.status == ReadingStatus.Unread }
        .sortedFor(LibrarySort.RecentlyAdded)
    // Newest first, like the row above it. A favourite marked years ago is not
    // what somebody scrolling their home screen is looking for.
    HomeShelf.Favorites -> filter { it.document.isFavorite }.sortedFor(LibrarySort.RecentlyAdded)
    HomeShelf.Finished -> filter { it.status == ReadingStatus.Finished }
        .sortedFor(LibrarySort.LastRead)
}

/** How far through, or zero for a book nobody has opened. */
private val LibraryBook.fraction: Float
    get() = progress?.fraction ?: 0f

/**
 * A book its reader is within sight of the end of.
 *
 * Finished books are excluded rather than counted as the most nearly done of
 * all: this row is a list of things still to do, and a book already read is not
 * one of them - it has a row of its own.
 */
private val LibraryBook.isAlmostDone: Boolean
    get() = status == ReadingStatus.Reading && fraction >= AlmostDoneFraction
