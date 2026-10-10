package io.lukia.features.library.domain

/**
 * How the shelf is laid out: as covers, or as rows.
 *
 * A grid of covers is how a library is browsed - a jacket is recognised before a
 * title is read - and is what the shelf opens as. A list is how a large one is
 * searched: three times as many books on a screen, with the author, format and
 * position a cover has no room for.
 *
 * Remembered, because the choice follows how big somebody's library is rather
 * than what they are doing this minute.
 */
enum class LibraryLayout {
    Grid,
    List,
}
