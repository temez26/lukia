package io.lukia.features.library.presentation.model

/**
 * Which of the home screen's menus is open, if any.
 *
 * A closed set rather than a flag each, for the reason [OpenMenu] is one on the
 * shelf: the way to add books and every cover on every row each carry
 * one, at most one may be open, and a single slot makes that structural rather
 * than something every opener must remember.
 *
 * Not [OpenMenu] itself, because a book is not identified the same way here.
 */
internal sealed interface HomeMenu {

    /** Where books are added from: the sheet behind "Add books", or the empty screen's menu. */
    data object Import : HomeMenu

    /**
     * One book's actions.
     *
     * The location alone names it: the home screen shows each book once - see
     * `homeLayout`.
     */
    data class Book(val location: String) : HomeMenu
}
