package io.lukia.features.import.infrastructure.cover

/**
 * [coverBytes] re-encoded no larger than [maxEdge] along its longer side.
 *
 * Null means there is nothing to put in its place: either the image already fits
 * within [maxEdge], or it is not one this platform can decode at all. Both leave
 * the caller holding what it started with.
 *
 * What comes back is JPEG. Cover art is photographic and a book cover has
 * nothing behind it to show through, so the format that halves the bytes is
 * worth the transparency it cannot keep - each platform paints white underneath
 * before encoding, rather than letting a see-through corner come out black.
 */
internal expect fun shrinkCoverImage(coverBytes: ByteArray, maxEdge: Int): ByteArray?

/** A width and a height, so the scaling arithmetic can be shared and tested once. */
internal data class CoverSize(val width: Int, val height: Int)

/**
 * The size a cover of [width] by [height] is stored at, given a longest edge of
 * [maxEdge], or null when it is already that small and re-encoding it would cost
 * quality for nothing.
 */
internal fun coverTargetSize(width: Int, height: Int, maxEdge: Int): CoverSize? {
    val longest = maxOf(width, height)

    if (longest <= 0 || longest <= maxEdge) return null

    val scale = maxEdge.toFloat() / longest

    // At least one pixel each way: artwork long and thin enough for its short
    // edge to round to nothing is still an image that has to have a size.
    return CoverSize(
        width = (width * scale).toInt().coerceAtLeast(1),
        height = (height * scale).toInt().coerceAtLeast(1),
    )
}
