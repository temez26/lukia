package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.document.DocumentType

/**
 * What this particular library can usefully be narrowed by.
 *
 * A filter is only worth offering when it divides the library: one matching
 * nothing is a dead end, one matching everything is the unfiltered library under
 * another name. So a facet earns its place only when some books have it and some
 * do not, and an empty one is left off rather than shown with nothing to choose.
 *
 * The lists are also what a stale query is measured against: see
 * [LibraryQuery.limitedTo].
 */
@Immutable
data class LibraryFacets(
    /** Always leads with [LibraryShelf.All]; a list of one means no control. */
    val shelves: List<LibraryShelf> = listOf(LibraryShelf.All),
    val formats: List<DocumentType> = emptyList(),
    val categories: List<String> = emptyList(),
    /**
     * The few [categories] worth browsing by: those at least two books share,
     * the most shared first in choosing, then shown alphabetically. A subject
     * one book files itself under is a filter, not a shelf to browse.
     */
    val browseCategories: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    /** Primary language subtags, lower-cased - see [LibraryBook.language]. */
    val languages: List<String> = emptyList(),
    val hasFavourites: Boolean = false,
    /**
     * Some books are on a sync server and some on this device are not. Asked
     * only of a shelf with server books on it, so a reader who never signed in
     * is never offered a filter about a server.
     */
    val hasDeviceOnly: Boolean = false,
    /** Some books are on the sync server and not downloaded here, and some are. */
    val hasServerOnly: Boolean = false,
) {
    companion object {
        val Empty = LibraryFacets()
    }
}

/**
 * The filters worth offering for this library.
 *
 * Values are ordered alphabetically rather than by how many books carry them.
 * A menu that reorders itself as the library grows is one where the row a reader
 * reaches for is somewhere else the next time they look.
 */
fun List<LibraryBook>.facets(): LibraryFacets {
    if (isEmpty()) return LibraryFacets.Empty

    val categories = dividingValues(key = ::categoryKey) { it.categories }

    return LibraryFacets(
        shelves = listOf(LibraryShelf.All) + LibraryShelf.entries
            .filter { it != LibraryShelf.All && divides { book -> it.matches(book) } },
        formats = DocumentType.entries.filter { type -> divides { it.document.type == type } },
        categories = categories.map { it.spelling },
        browseCategories = categories
            .filter { it.books >= MinBrowseBooks }
            .sortedByDescending { it.books }
            .take(MaxBrowseCategories)
            .map { it.spelling }
            .sortedBy { it.lowercase() },
        authors = dividingValues { it.authors }.map { it.spelling },
        languages = dividingValues(key = ::languageKey) { listOfNotNull(it.language) }.map { it.spelling },
        hasFavourites = divides { it.document.isFavorite },
        hasDeviceOnly = any { it.document.onServer } && divides { it.document.exists && !it.document.onServer },
        hasServerOnly = divides { it.document.awaitsDownload },
    )
}

/** Fewer than this and a subject is one book's own label rather than a shelf. */
private const val MinBrowseBooks = 2

/** A screenful of chips; past it the library tab's filter has the rest. */
private const val MaxBrowseCategories = 20

/** One value a filter can offer, as first spelled, and how many books carry it. */
private data class FacetValue(val spelling: String, val books: Int)

/**
 * True when [predicate] holds for some of this library but not all of it, which
 * is what makes a filter worth offering.
 */
private inline fun List<LibraryBook>.divides(predicate: (LibraryBook) -> Boolean): Boolean {
    val matches = count(predicate)
    return matches > 0 && matches < size
}

/**
 * The values worth offering as filters: every one that some of this library
 * carries and some does not, alphabetically, keeping the first spelling of any
 * that share a [key] - `Fiction` and `fiction` are one category filed twice,
 * and offering both is offering the same row twice.
 *
 * Counted in a single pass rather than by asking [divides] once per value: same
 * answer, different cost. A pass per value reads the whole library once for
 * every distinct author in it - millions of comparisons on a shelf of a few
 * thousand - and this re-runs whenever the library changes at all, down to one
 * book being favourited.
 */
private inline fun List<LibraryBook>.dividingValues(
    key: (String) -> String = String::lowercase,
    values: (LibraryBook) -> List<String>,
): List<FacetValue> {
    val spellings = mutableMapOf<String, String>()
    val books = mutableMapOf<String, Int>()

    forEach { book ->
        // Counted once per book however often that book repeats the value,
        // because carrying it is what a filter matches on - see [holds].
        val carried = mutableSetOf<String>()

        values(book).forEach { value ->
            val filed = key(value)

            spellings.getOrPut(filed) { value }
            if (carried.add(filed)) books[filed] = (books[filed] ?: 0) + 1
        }
    }

    // Anything here is carried by at least one book, so the only half of
    // "divides" left to ask is whether it is carried by all of them.
    return spellings
        .mapNotNull { (filed, spelling) ->
            val count = books[filed] ?: 0
            if (count < size) FacetValue(spelling, count) else null
        }
        .sortedBy { it.spelling.lowercase() }
}
