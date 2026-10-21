package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.ReadingProgress

/** The whole book, as a share of itself. */
private const val Whole = 100

/**
 * How far through a book a page is, as a whole percentage.
 *
 * Written once because two places have to agree: the scrubber in the contents
 * panel and the figure in the reader's bar - see `PositionScrubber` and
 * `PageIndicatorButton`. Two roundings of one position disagreeing by a point is
 * what a reader notices and cannot explain.
 *
 * The same rule as the library's - see [ReadingProgress.percent] - rather than
 * a rounding of its own: rounded to the nearest, the reader said 43% of a book
 * Home called 42%, and 100% of one a page short of its end.
 *
 * [position] is one-based, so the last page of a book is all of it rather than
 * one page short of it. A document of a single page is read or unread with
 * nothing in between, so it counts as all of it too.
 */
internal fun percentThrough(position: Int, pageCount: Int): Int =
    ReadingProgress(pageIndex = position - 1, pageCount = pageCount).percent ?: Whole
