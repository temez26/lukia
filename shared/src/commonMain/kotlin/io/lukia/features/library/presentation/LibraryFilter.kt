package io.lukia.features.library.presentation

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType

/**
 * The ways the library can be narrowed from the header.
 *
 * Deliberately a closed set rather than an arbitrary predicate: these are the
 * segments of a control the reader can see, and a segmented control with an
 * open-ended number of options is not one.
 */
enum class LibraryFilter(val label: String) {
    All("All"),
    Favourites("Favourites"),
    Pdf("PDF"),
    Epub("EPUB");

    fun matches(document: DocumentInfo): Boolean = when (this) {
        All -> true
        Favourites -> document.isFavorite
        Pdf -> document.type == DocumentType.PDF
        Epub -> document.type == DocumentType.EPUB
    }
}

/**
 * The filters worth offering for this library.
 *
 * A segment that can only ever lead to an empty grid is a dead end, and one that
 * selects the whole library is the [LibraryFilter.All] segment under another
 * name - so a format only earns a segment when there is more than one format
 * present, and favourites only when there are some.
 *
 * A list of one means there is nothing to choose between; callers take that as
 * the signal to leave the control off the screen entirely.
 */
fun List<DocumentInfo>.availableFilters(): List<LibraryFilter> {
    val documents = this

    val formats = listOf(LibraryFilter.Pdf, LibraryFilter.Epub)
        .filter { format -> documents.any(format::matches) }

    val favourites = if (documents.any { it.isFavorite }) {
        listOf(LibraryFilter.Favourites)
    } else {
        emptyList()
    }

    // One format present is the whole library, which the All segment already is.
    return listOf(LibraryFilter.All) + favourites + if (formats.size > 1) formats else emptyList()
}

/** The books this library is showing, once [filter] and [query] are both applied. */
fun List<DocumentInfo>.matching(filter: LibraryFilter, query: String): List<DocumentInfo> =
    filter(filter::matches).matchingQuery(query)
