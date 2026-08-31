package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.document.DocumentType

/**
 * What this particular library can usefully be narrowed by.
 *
 * A filter is only worth offering when it divides the library. One that matches
 * nothing is a dead end, and one that matches everything is the unfiltered
 * library under another name - so a shelf, a format or a category earns its
 * place only when some books have it and some do not. A facet left empty is one
 * the screen leaves off entirely rather than showing with nothing to choose.
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
    val authors: List<String> = emptyList(),
    /** Primary language subtags, lower-cased - see [LibraryBook.language]. */
    val languages: List<String> = emptyList(),
    val hasFavourites: Boolean = false,
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

    return LibraryFacets(
        shelves = listOf(LibraryShelf.All) + LibraryShelf.entries
            .filter { it != LibraryShelf.All && divides { book -> it.matches(book) } },
        formats = DocumentType.entries.filter { type -> divides { it.document.type == type } },
        categories = dividingValues { it.categories },
        authors = dividingValues { it.authors },
        languages = dividingValues { listOfNotNull(it.language) },
        hasFavourites = divides { it.document.isFavorite },
    )
}

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
 * that differ only in case - `Fiction` and `fiction` are one category filed
 * twice, and offering both is offering the same row twice.
 *
 * Counted in a single pass rather than by asking [divides] once per value. The
 * two arrive at the same answer; the difference is what they cost. A pass per
 * value is the whole library read once for every distinct author in it, which
 * on a shelf of a few thousand books is millions of comparisons - and this is
 * re-run whenever the library changes at all, down to one book being
 * favourited.
 */
private inline fun List<LibraryBook>.dividingValues(
    values: (LibraryBook) -> List<String>,
): List<String> {
    val spellings = mutableMapOf<String, String>()
    val books = mutableMapOf<String, Int>()

    forEach { book ->
        // Counted once per book however often that book repeats the value,
        // because carrying it is what a filter matches on - see [holds].
        val carried = mutableSetOf<String>()

        values(book).forEach { value ->
            val key = value.lowercase()

            spellings.getOrPut(key) { value }
            if (carried.add(key)) books[key] = (books[key] ?: 0) + 1
        }
    }

    // Anything here is carried by at least one book, so the only half of
    // "divides" left to ask is whether it is carried by all of them.
    return spellings
        .filterKeys { (books[it] ?: 0) < size }
        .values
        .sortedBy { it.lowercase() }
}
