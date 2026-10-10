package io.lukia.features.reader.domain.page

/**
 * A rectangle on a page, in the page's own units.
 *
 * The same space [PageSize] is measured in - points, origin at the top left,
 * before any scaling to fit a window - which is what lets a quad read off the
 * file be drawn over a page rendered at any width.
 *
 * Top-left rather than PDF's own bottom-left origin, because every platform the
 * app draws on measures downwards. The conversion belongs once, in whatever read
 * the file, rather than at each place that draws.
 */
data class TextQuad(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * One character of a page, and where it sits on it.
 *
 * The unit is whatever the platform can position - normally a character, and a
 * ligature or composed mark where a font packs several in. Nothing above cares:
 * the text is rebuilt in reading order, and a glyph carrying two characters
 * covers two.
 *
 * Deliberately not words or lines. A PDF infers word boundaries from gaps rather
 * than storing them, and every producer infers slightly differently, so it is
 * done once here rather than three ways on three platforms.
 */
data class PageGlyph(
    val text: String,
    val bounds: TextQuad,
)

/**
 * One picture on a page, and where it sits on it.
 *
 * The bytes are encoded - PNG or JPEG - rather than decoded, for the same reason
 * [TextBlock.Figure] holds encoded bytes: a book can carry hundreds, and
 * decoding each as the file is read would cost more memory than all its text.
 *
 * [bounds] puts the picture back among the words. A PDF states no reading order,
 * so where a picture belongs in the re-set text comes from where it was drawn -
 * the same thing that orders the paragraphs around it.
 *
 * Identity equality, inherited from [Any]: comparing megabytes of image data to
 * answer whether two pictures are the same picture is work nobody asked for.
 */
class PageImage(val bytes: ByteArray, val bounds: TextQuad)
