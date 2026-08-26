package io.lukia.features.reader.domain

/**
 * How far through reading a PDF's text the app has got.
 *
 * Worth reporting because it is the one thing in the reader that takes long
 * enough to watch: the text of every page has to be read out of the file before
 * the first of them can be laid out, and a long book is thousands of pages of
 * that. A spinner alone would leave somebody wondering whether a six-hundred
 * page book had hung.
 */
data class TextExtraction(val pagesRead: Int, val pageCount: Int)
