package io.lukia.core.infrastructure

/**
 * A number as the reader's own locale writes it.
 *
 * Two functions rather than one, because the app shows two kinds of number and
 * they are written differently. A measurement takes a decimal mark - "1,5 MB"
 * in German and French against "1.5 MB" in English - and a count takes a
 * grouping separator once it passes a thousand: "1,248" here, "1 248" there.
 *
 * What is deliberately not here is a formatter for an ordinal. A page number is
 * a name for a place in the book rather than a quantity of anything, and is set
 * without grouping in every language this app has met, the way a year is - see
 * the page indicator in `ReaderToolbar`, which is left as it is.
 */
internal expect fun formatDecimal(value: Double, fractionDigits: Int): String

/** A quantity of things, grouped as the locale groups them. */
internal expect fun formatCount(value: Int): String
