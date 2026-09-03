package io.lukia.features.library.domain

/**
 * How the shelf is laid out: as covers, or as rows.
 *
 * A grid of covers is how a library is browsed - a book is recognised by its
 * jacket long before its title is read - and it is what the shelf opens as. A
 * list is how a large one is searched: it fits three times as many books on a
 * screen, and it can show the author, the format and how far through each one
 * is, none of which a cover has room for.
 *
 * The choice is remembered, because it follows how big somebody's library is
 * rather than what they are doing this minute.
 */
enum class LibraryLayout {
    Grid,
    List,
}
