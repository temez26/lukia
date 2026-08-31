package io.lukia.features.reader.domain.page

/**
 * The natural size of one page, in the document's own units.
 *
 * Kept as the document reports it rather than in pixels: the reader scales a
 * page to whatever width the window gives it, and the only thing it needs from
 * the original is the shape.
 */
data class PageSize(val width: Float, val height: Float) {

    /**
     * Width over height. This is what lets a page reserve its space before it
     * has rendered, so the list does not resize under the reader's thumb as
     * pages arrive.
     */
    val aspectRatio: Float
        get() = if (width <= 0f || height <= 0f) DEFAULT_ASPECT_RATIO else width / height

    private companion object {
        /** A4 portrait, for a page whose own size is missing or nonsensical. */
        const val DEFAULT_ASPECT_RATIO = 0.707f
    }
}
