package io.lukia.features.library.presentation

/**
 * Which of the library's menus is open, if any.
 *
 * A closed set rather than a flag each: the screen has a menu on its bar, one on
 * every book, one on the import slot and one on every filter chip, at most one of
 * which may be open. A single slot that can only hold one of them makes that
 * structural instead of something every opener has to remember to enforce - and
 * it is what lets the one dimming scrim behind them all sit in a single layer.
 */
internal sealed interface OpenMenu {

    /** The actions menu in the bar. */
    data object Bar : OpenMenu

    /** The import card's "where from" menu. */
    data object Import : OpenMenu

    /** One book's actions, by the location that identifies it. */
    data class Book(val location: String) : OpenMenu

    /** The values a filter chip offers. */
    data class Chip(val facet: LibraryFacet) : OpenMenu
}

/** The chips in the header that open a menu of values to choose between. */
internal enum class LibraryFacet {
    Format,
    Category,
    Author,
    Language,
    Sort,
}
