package io.lukia.features.library.presentation

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.DocumentType

/**
 * Everything the reader has asked of their library: which shelf they are on,
 * what they have narrowed it to, what they typed, and the order they want it in.
 *
 * One value rather than a field per control. The library is narrowed in one
 * place from it, which is what stops a filter and a count disagreeing about the
 * same library, and it makes "clear all of this" a single expression rather than
 * something every new filter has to remember to join.
 *
 * The refinements are single-valued - one format, one category - rather than
 * sets. Two categories at once is a question a reader almost never has, and
 * offering it turns every menu row into a checkbox and the chip above it into a
 * list that no longer fits.
 */
@Immutable
data class LibraryQuery(
    val shelf: LibraryShelf = LibraryShelf.All,
    val search: String = "",
    val favouritesOnly: Boolean = false,
    /** One format, or null for every format the library holds. */
    val format: DocumentType? = null,
    /** A subject as the document wrote it; matched without regard to case. */
    val category: String? = null,
    val author: String? = null,
    /** A primary language subtag, lower-cased - see [LibraryBook.language]. */
    val language: String? = null,
    val sort: LibrarySort = LibrarySort.Added
) {
    /**
     * True when anything in the filter menu is on. What decides whether the
     * "Clear" chip is worth offering, and how the empty grid explains itself.
     */
    val isRefined: Boolean
        get() = favouritesOnly ||
            format != null ||
            category != null ||
            author != null ||
            language != null

    /**
     * True when the library on screen is anything less than all of it, by any
     * means at all.
     *
     * The import slot at the end of the grid hangs on this: a slot offering to
     * add a book to a narrowed view promises what it cannot keep, since the book
     * lands in the library but not on the screen that just took it.
     */
    val isNarrowed: Boolean
        get() = isRefined || shelf != LibraryShelf.All || search.isNotBlank()

    /** Everything in the filter menu off, leaving the shelf and the search alone. */
    fun cleared(): LibraryQuery = copy(
        favouritesOnly = false,
        format = null,
        category = null,
        author = null,
        language = null
    )

    /**
     * This query with anything [facets] can no longer offer dropped.
     *
     * Un-favouriting the last favourite, or deleting the last EPUB, takes the
     * ground out from under whoever was standing on it. Falling back here rather
     * than leaving a filter in place that nothing can match is what stops the
     * grid emptying for a reason nothing on screen explains.
     */
    fun limitedTo(facets: LibraryFacets): LibraryQuery = copy(
        shelf = shelf.takeIf { it in facets.shelves } ?: LibraryShelf.All,
        favouritesOnly = favouritesOnly && facets.hasFavourites,
        format = format?.takeIf { it in facets.formats },
        category = category?.takeIf { facets.categories.holds(it) },
        author = author?.takeIf { facets.authors.holds(it) },
        language = language?.takeIf { it in facets.languages }
    )
}

/**
 * The books this library is showing: everything [query] asks for, in the order
 * it asks for.
 *
 * Filtering before sorting on purpose - the sort then runs over what survived
 * rather than over the whole library - and the whole of it in one function so
 * that "what is on screen" has exactly one definition.
 */
fun List<LibraryBook>.matching(query: LibraryQuery): List<LibraryBook> {
    val search = query.search.trim().lowercase()

    return filter { book ->
        query.shelf.matches(book) &&
            (!query.favouritesOnly || book.document.isFavorite) &&
            (query.format == null || book.document.type == query.format) &&
            (query.category == null || book.categories.holds(query.category)) &&
            (query.author == null || book.authors.holds(query.author)) &&
            (query.language == null || book.language == query.language) &&
            (search.isEmpty() || book.searchKey.contains(search))
    }.sortedFor(query.sort)
}
