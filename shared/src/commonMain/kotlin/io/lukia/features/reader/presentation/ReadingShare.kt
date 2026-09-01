package io.lukia.features.reader.presentation

import kotlin.math.roundToInt

/** The whole book, as a share of itself. */
private const val Whole = 100

/**
 * How far through a book a page is, as a whole percentage.
 *
 * Written once because it is stated in two places that have to agree: the
 * scrubber in the contents panel, and the figure over the bottom edge of the
 * page - see `PositionScrubber` and `ReaderReadingStatus`. Two roundings of the
 * same position disagreeing by a point is exactly the kind of thing a reader
 * notices and cannot explain.
 *
 * [position] is one-based, so the last page of a book is all of it rather than
 * one page short of it. A document of a single page is read or unread with
 * nothing in between, so it counts as all of it too.
 */
internal fun percentThrough(position: Int, pageCount: Int): Int =
    if (pageCount <= 1) Whole else (position * Whole.toFloat() / pageCount).roundToInt()
