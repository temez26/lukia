package io.lukia.features.reader.presentation.components.passages

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

// Where a mark lands on the page, as opposed to what it looks like once it is
// there - see PassageMarks for that. The two are split because they change on
// completely different clocks: what a highlight looks like is settled by the
// theme, and where it goes is settled by the layout, which is the half that has
// to be kept from being worked out again on every frame of a sweep.

/**
 * How far the highlight is drawn outside the words it sits behind, sideways -
 * enough that the first and last letters are not touching its edge. A highlight
 * cut flush to the text reads as a box drawn round the words rather than as the
 * words sitting on something.
 */
private val HighlightBleed: Dp = 2.5.dp

/**
 * How far above and below the baseline the highlight reaches, in multiples of
 * the size the type is set at.
 *
 * Measured from the baseline rather than in from the edges of the line, which is
 * what this used to do and why the highlight sat high on the words. A line box
 * is the type plus its leading, and that leading is split in proportion to the
 * ascent and descent - so most lands above, and it is trimmed off a paragraph's
 * first line altogether. The middle of that box is nowhere in particular.
 *
 * The baseline is where the type sits on every line, trimmed or not. These two
 * reach past the ascenders and descenders by about as much again, putting the
 * middle of the shape slightly above the baseline - where the weight of a line
 * of mixed-case text is, and so where the eye reads its middle to be.
 */
private const val BAND_ABOVE_BASELINE = 0.86f
private const val BAND_BELOW_BASELINE = 0.30f

/**
 * What the band is measured against when the text has no size of its own to
 * measure it in - a style built in ems, or one that names no size at all. A line
 * is half again as tall as its type in nearly every book this will open.
 */
private const val TYPE_OF_LINE = 0.66f

/**
 * One passage's mark, as the rectangles it covers rather than as the text it
 * was worked out from.
 *
 * Held rather than derived where it is drawn, because drawing happens far more
 * often than the geometry changes: a passage waiting on a translation repaints
 * every frame of the sweep, while where the words went was settled by the layout
 * and does not move. Per frame, a page of marked sentences asked the layout the
 * same questions and allocated the same rectangles for the same answers.
 */
@Immutable
internal data class PassageBand(val style: PassageMarkStyle, val lines: List<Rect>)

/**
 * Where every mark in [marks] lands in [layout], ready to be drawn.
 *
 * Empty rectangles are dropped here rather than at the point of drawing, for the
 * same reason the rest of this is: a line with no width is not going to grow one
 * between two frames of a sweep.
 */
internal fun Density.passageBands(
    layout: TextLayoutResult,
    marks: List<PassageMark>,
): List<PassageBand> {
    if (layout.layoutInput.text.isEmpty() || marks.isEmpty()) return emptyList()

    val bleed = HighlightBleed.toPx()
    val type = typeSize(layout)

    return marks.mapNotNull { mark ->
        layout.linesOf(mark, bleed = bleed, type = type)
            .filter { it.width > 0f && it.height > 0f }
            .takeIf { it.isNotEmpty() }
            ?.let { PassageBand(mark.style, it) }
    }
}

/**
 * The bands last worked out, and what they were worked out from.
 *
 * A cache of one, remembered beside the text it belongs to, because the question
 * is asked from inside a draw block: reading the layout in composition instead
 * would tie a recomposition to every re-layout, which during a pinch is every
 * block on the page on every frame - the opposite of the trade this is making.
 */
@Stable
internal class PassageBands {

    private var forLayout: TextLayoutResult? = null
    private var forMarks: List<PassageMark> = emptyList()
    private var bands: List<PassageBand> = emptyList()

    fun of(density: Density, layout: TextLayoutResult, marks: List<PassageMark>): List<PassageBand> {
        // The layout by identity, because a re-layout hands back a new result
        // whether or not it says the same thing; the marks by value, because a
        // page rebuilds an equal list of them whenever anything else recomposes.
        if (layout === forLayout && marks == forMarks) return bands

        forLayout = layout
        forMarks = marks
        bands = density.passageBands(layout, marks)

        return bands
    }
}

/**
 * The size the type is set at, for measuring the highlight against.
 *
 * Read off the layout rather than passed down, so that nothing above this has to
 * know the mark is measured in ems at all. A style naming its size in anything
 * but points cannot be read this way, and the line it produced is the next best
 * thing to measure - see [TYPE_OF_LINE].
 */
private fun Density.typeSize(layout: TextLayoutResult): Float {
    val declared = layout.layoutInput.style.fontSize

    if (declared.type == TextUnitType.Sp) return declared.toPx()

    return (layout.getLineBottom(0) - layout.getLineTop(0)) * TYPE_OF_LINE
}

/**
 * The stretch of each line this passage covers, as the shape to draw behind it.
 *
 * Built from the layout's own lines rather than from `getPathForRange`, which
 * hands back one path with square corners and no way to round them. Trailing
 * whitespace is left out, so a highlight ends at the last letter of a line
 * rather than running on to where the next word would have started.
 */
private fun TextLayoutResult.linesOf(mark: PassageMark, bleed: Float, type: Float): List<Rect> {
    val length = layoutInput.text.length
    val start = mark.start.coerceIn(0, length)
    val end = mark.end.coerceIn(start, length)
    if (start == end) return emptyList()

    val firstLine = getLineForOffset(start)
    val lastLine = getLineForOffset(end - 1)

    return (firstLine..lastLine).mapNotNull { line ->
        val from = maxOf(start, getLineStart(line))
        val to = minOf(end, getLineEnd(line, visibleEnd = true))
        if (from >= to) return@mapNotNull null

        val near = getHorizontalPosition(from, usePrimaryDirection = true)
        val far = getHorizontalPosition(to, usePrimaryDirection = true)

        // Measured against the type, but never taller than the line: a book set
        // with its leading closed right up has nowhere to put a band that would
        // overflow into the lines either side of it.
        val height = minOf(type, getLineBottom(line) - getLineTop(line))
        val baseline = getLineBaseline(line)

        Rect(
            // A right-to-left line hands these back the other way round.
            left = minOf(near, far) - bleed,
            top = baseline - height * BAND_ABOVE_BASELINE,
            right = maxOf(near, far) + bleed,
            bottom = baseline + height * BAND_BELOW_BASELINE,
        )
    }
}
