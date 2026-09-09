package io.lukia.core.infrastructure

/**
 * A number as the reader's own locale writes it.
 *
 * Two functions because the app shows two kinds of number: a measurement takes a
 * decimal mark ("1,5 MB" against "1.5 MB") and a count takes a grouping
 * separator past a thousand ("1,248", "1 248").
 *
 * Deliberately no ordinal formatter. A page number names a place in the book
 * rather than a quantity, and is set without grouping in every language this app
 * has met, the way a year is - see the page indicator in `ReaderToolbar`.
 */
internal expect fun formatDecimal(value: Double, fractionDigits: Int): String

/** A quantity of things, grouped as the locale groups them. */
internal expect fun formatCount(value: Int): String
