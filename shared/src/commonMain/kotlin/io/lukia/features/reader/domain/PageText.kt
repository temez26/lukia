package io.lukia.features.reader.domain

/**
 * A rectangle on a page, in the page's own units.
 *
 * The same space [PageSize] is measured in - points, origin at the top left,
 * before any scaling the reader does to fit a window. That is what lets a quad
 * read off the file be drawn over a page rendered at any width: the reader
 * scales it by the one number it already has.
 *
 * Top-left origin rather than the bottom-left one PDF itself uses, because
 * every platform the app draws on measures downwards. The conversion belongs
 * once, in whatever read the file, rather than at each place that draws.
 */
data class TextQuad(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * One character of a page, and where it sits on it.
 *
 * The unit is whatever the platform can position - normally a character, and a
 * ligature or a composed mark where a font packs several into one. Nothing above
 * this cares which: the text is rebuilt from these in reading order, and a glyph
 * carrying two characters simply covers two.
 *
 * Deliberately not words or lines. Word boundaries in a PDF are inferred from
 * gaps rather than stored, and every producer infers them slightly differently -
 * so the inference is done once here rather than differently on three platforms.
 */
data class PageGlyph(
    val text: String,
    val bounds: TextQuad
)
