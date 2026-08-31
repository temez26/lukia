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

    /** How a bulk action should read: "8 books", or the one book by itself. */
    val subject: String get() = if (count == 1) "1 book" else "$count books"

    companion object {
        val None = LibrarySelection()
    }
}

/**
 * What a bulk action is doing, for the bar that has to stay honest while it runs.
 *
 * Counted rather than a spinner because these run book by book - refreshing
 * forty of them means reading forty files - and a bar that only says "working"
 * gives no way to tell a slow job from a stuck one.
 */
data class BulkAction(
    val label: String,
    val completed: Int,
    val total: Int,
) {
    val message: String get() = "$label $completed of $total…"
}
