package io.lukia.features.reader.domain

/**
 * What the reader knows about a book's chapters.
 *
 * Reading them can mean scanning the whole file, so this carries the wait as a
 * state of its own rather than leaving the contents panel to guess whether an
 * empty list means "none" or "not yet".
 */
sealed interface ReaderOutline {

    data object Loading : ReaderOutline

    /** This platform cannot read outlines, so nothing can be said either way. */
    data object Unavailable : ReaderOutline

    /** What the document declares - empty when it declares nothing. */
    data class Chapters(val entries: List<OutlineEntry>) : ReaderOutline
}
