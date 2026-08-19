package io.lukia.features.library.presentation

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.DocumentType

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
        categories = distinctValues { it.categories }
            .filter { value -> divides { it.categories.holds(value) } },
        authors = distinctValues { it.authors }
            .filter { value -> divides { it.authors.holds(value) } },
        languages = distinctValues { listOfNotNull(it.language) }
            .filter { value -> divides { it.language == value } },
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
 * Every value the books carry, in alphabetical order, keeping the first spelling
 * of any that differ only in case - `Fiction` and `fiction` are one category
 * filed twice, and offering both is offering the same row twice.
 */
private inline fun List<LibraryBook>.distinctValues(
    values: (LibraryBook) -> List<String>,
): List<String> = flatMap(values)
    .distinctBy { it.lowercase() }
    .sortedBy { it.lowercase() }
