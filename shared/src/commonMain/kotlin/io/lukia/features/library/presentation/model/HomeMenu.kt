package io.lukia.features.library.presentation.model

/**
 * Which of the home screen's menus is open, if any.
 *
 * A closed set rather than a flag each, for the reason [OpenMenu] is one on the
 * shelf: the header, the empty state and every cover on every row each carry
 * one, at most one may be open, and a single slot makes that structural rather
 * than something every opener must remember.
 *
 * Not [OpenMenu] itself, because a book is not identified the same way here.
 */
internal sealed interface HomeMenu {

    /** The library's actions menu, in the header. */
    data object Bar : HomeMenu

    /** The empty screen's "where from" menu. */
    data object Import : HomeMenu

    /**
     * One book's actions.
     *
     * Named by the row as well as by the book. A shelf shows a book once, so
     * there its location is enough; the home rows are samples of the same
     * library taken different ways, and the book you are still reading is
     * usually also the book that arrived most recently - so a location alone
     * would open a menu on every row the book appears on at once.
     */
    data class Book(val shelf: HomeShelf, val location: String) : HomeMenu
}
