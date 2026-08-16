package io.lukia.features.reader.presentation

/**
 * Ceiling on how wide a page is ever drawn, whatever the window does.
 *
 * High enough that a zoomed-in page stays sharp on a phone, where the point of
 * zooming is to read small print. Beyond it the extra pixels are past what a
 * page's own detail supports, while the memory is not: a page is four bytes per
 * pixel.
 */
internal const val MAX_RENDER_WIDTH_PX = 2048

/**
 * Render widths are rounded to this step.
 *
 * Pinching changes the page's width continuously, and rendering every one of
 * those widths would mean a new render on every frame of the gesture. Rounding
 * means a pinch crosses a handful of widths instead of hundreds: the page is
 * laid out at its exact width and the bitmap stretched the last few pixels,
 * which at this granularity is not visible.
 */
private const val RENDER_WIDTH_STEP_PX = 128

/** The width a page is actually drawn at, for a page laid out at [widthPx]. */
internal fun renderWidthFor(widthPx: Int): Int {
    val stepped = ((widthPx + RENDER_WIDTH_STEP_PX / 2) / RENDER_WIDTH_STEP_PX) *
        RENDER_WIDTH_STEP_PX

    return stepped.coerceIn(RENDER_WIDTH_STEP_PX, MAX_RENDER_WIDTH_PX)
}
