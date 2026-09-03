package io.lukia.features.library.presentation.model

/**
 * Which books are ticked while the shelf is being selected from.
 *
 * [active] is kept apart from whether anything is ticked, because the two are
 * different states to be in: a shelf somebody has just started selecting from
 * has nothing ticked and is still not the shelf they were browsing a moment ago.
 * Folding the two together would have the mode end itself the moment the last
 * book was unticked, which is not what anyone asked for.
 */
data class LibrarySelection(
    val active: Boolean = false,
    val locations: Set<String> = emptySet(),
) {

    val count: Int get() = locations.size

    val isEmpty: Boolean get() = locations.isEmpty()

    operator fun contains(location: String): Boolean = location in locations

    companion object {
        val None = LibrarySelection()
    }
}

/**
 * The one thing a bulk action is doing, which is what both the bar running above
 * the shelf and the report afterwards are about.
 *
 * An enum rather than the word: the report used to choose its verb by matching
 * the English label the bar was showing, so translating the bar would have
 * changed what the report said, silently and in the other direction.
 *
 * [Favourite] and [Unfavourite] are one action to the code and two to the
 * reader, which is why they are two entries with one use case behind them.
 */
enum class BulkOperation {
    Refresh,
    Favourite,
    Unfavourite,
    Reset,
    Delete,
}

/**
 * What a bulk action is doing, for the bar that has to stay honest while it runs.
 *
 * Counted rather than a spinner because these run book by book - refreshing
 * forty of them means reading forty files - and a bar that only says "working"
 * gives no way to tell a slow job from a stuck one.
 */
data class BulkAction(
    val operation: BulkOperation,
    val completed: Int,
    val total: Int,
)
