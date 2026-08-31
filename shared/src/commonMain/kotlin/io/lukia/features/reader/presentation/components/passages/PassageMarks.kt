package io.lukia.features.reader.presentation.components.passages

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import io.lukia.features.translate.presentation.PassageTranslation

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
 * Measured from the baseline rather than taken in from the edges of the line,
 * which is what this used to do and why the highlight sat high on the words.
 * A line box is the type plus its leading, and that leading is not shared out
 * evenly: it is split in proportion to the ascent and the descent, so most of it
 * lands above - and it is trimmed off the first line of a paragraph altogether.
 * The middle of that box is nowhere in particular, and a shape centred on it is
 * centred on nothing.
 *
 * The baseline is where the type actually sits, on every line, trimmed or not.
 * These two reach past the ascenders and past the descenders by about as much
 * again, which puts the middle of the shape slightly above the baseline - which
 * is where the weight of a line of mixed-case text is, and so where the eye
 * reads the middle of it to be.
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
 * How round the corners are.
 *
 * Generous, and capped at half the height, so a highlight this shallow comes out
 * as a capsule rather than as a rectangle with its corners taken off.
 */
private val HighlightRadius: Dp = 7.dp

/**
 * How much of the page each state lets through, and how firm its edge is.
 *
 * A fill on its own has to be strong to be seen, and a wash strong enough to be
 * noticed is a wash the reader has to read the text through. A quiet fill inside
 * a firmer edge is seen at a glance and still leaves the words sitting on
 * something rather than in something - which is why every state here is a pair
 * rather than one number, and why the fills can stay this light.
 */
private const val WAITING_FILL = 0.14f
private const val WAITING_EDGE = 0.30f
private const val TRANSLATED_FILL = 0.18f
private const val TRANSLATED_EDGE = 0.42f
private const val FAILED_FILL = 0.20f
private const val FAILED_EDGE = 0.50f

/** The hairline round a highlight, at the weight the rest of the app strokes at. */
private val EdgeStroke: Dp = 1.dp

/**
 * The light that travels along a passage while it is being translated.
 *
 * A sweep rather than a spinner, and rather than the breath this used to be: the
 * reader is looking at their own book, and something turning in the middle of a
 * paragraph is a piece of software asking to be watched. A light moving along
 * the words says which passage is being worked on and that it is still being
 * worked on, in the place the answer is going to appear, without ever being the
 * thing on the page worth looking at.
 *
 * The band is a fraction of the passage rather than a fixed width, so a word and
 * a long sentence take the same time to cross - and it starts and ends clear of
 * both edges, which leaves a moment's rest between passes instead of a light
 * that never stops.
 */
private const val SWEEP_WIDTH = 0.45f
private const val SWEEP_ALPHA = 0.30f
private const val SWEEP_MILLIS = 1500

/**
 * What a mark says about the passage it is behind.
 *
 * The three are deliberately distinguishable without colour vision: a steady
 * fill and a fill that breathes are two different things before they are two
 * different colours.
 */
internal enum class PassageMarkStyle {

    /** Being translated. The fill breathes. */
    Waiting,

    /** Translated, and the translation is what is on the page. */
    Translated,

    /** Asked for and not come back. */
    Failed,
}

/**
 * How this passage should be marked, or null when it should not be.
 *
 * Nothing is drawn behind the book's own words. A passage the reader has asked to
 * see the original of is a passage they are reading, and marking it would leave
 * the page carrying a highlight for every sentence anybody had ever tapped -
 * which is a record of what the reader has done rather than anything they can
 * use. What is unmarked is the book, exactly as it was written.
 */
internal fun PassageTranslation?.markStyle(): PassageMarkStyle? = when (this) {
    null -> null
    is PassageTranslation.Loading -> PassageMarkStyle.Waiting
    is PassageTranslation.Failed -> PassageMarkStyle.Failed
    is PassageTranslation.Ready -> PassageMarkStyle.Translated.takeIf { !showingOriginal }
}

/** One stretch of drawn text, and what its mark says. */
@Immutable
internal data class PassageMark(val start: Int, val end: Int, val style: PassageMarkStyle)

/** The three colours a mark can take, resolved once rather than per passage. */
@Immutable
internal data class PassageMarkColors(
    val waiting: Color,
    val translated: Color,
    val failed: Color,
) {
    fun of(style: PassageMarkStyle): Color = when (style) {
        PassageMarkStyle.Waiting -> waiting
        PassageMarkStyle.Translated -> translated
        PassageMarkStyle.Failed -> failed
    }
}

@Composable
internal fun passageMarkColors(): PassageMarkColors = MaterialTheme.colorScheme.let { colors ->
    remember(colors) {
        PassageMarkColors(
            waiting = colors.onSurfaceVariant,
            translated = colors.primary,
            failed = colors.error,
        )
    }
}

/**
 * How far along the passage the sweep has travelled, or nothing when nothing is
 * being translated.
 *
 * Kept off unless something actually is: an infinite animation running behind a
 * page nobody is translating anything on is a frame of work every frame, for a
 * page that is not changing.
 *
 * Restarted rather than reversed, and linear, because a light travelling one way
 * at a steady speed is a thing moving along the words - one that eased to a stop
 * and came back is a thing being animated at the reader.
 */
@Composable
internal fun passageSweep(active: Boolean): State<Float> {
    if (!active) return remember { mutableFloatStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "passage-sweep")

    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWEEP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "passage-sweep-progress",
    )
}

/**
 * Draws a soft rounded highlight behind every marked passage.
 *
 * Behind the words rather than beside them, because there is nowhere beside them
 * to stand: the room between one sentence and the next is a single space, about
 * four points, and no mark and its gaps will fit in that without landing on the
 * neighbouring word. Anything drawn behind the text has exactly as much room as
 * the text does.
 *
 * One rounded rectangle per line rather than one round the whole passage. A
 * sentence beginning halfway along one line and ending halfway along another
 * covers two ribbons of the page, and a single box round both of them would
 * cover two half-lines it has nothing to do with.
 */
internal fun DrawScope.drawPassageMarks(
    layout: TextLayoutResult,
    marks: List<PassageMark>,
    colors: PassageMarkColors,
    sweep: Float,
) {
    if (layout.layoutInput.text.isEmpty()) return

    marks.forEach { mark ->
        drawPassageHighlight(
            lines = layout.linesOf(mark, bleed = HighlightBleed.toPx(), type = typeSize(layout)),
            style = mark.style,
            colors = colors,
            sweep = sweep,
        )
    }
}

/**
 * The highlight itself, over rectangles somebody else worked out.
 *
 * Takes rectangles rather than anything that knows about text, because the two
 * kinds of book arrive at them completely differently - one asks the layout
 * where a range of characters went, the other reads boxes out of a PDF - and
 * what a mark looks like should not be written twice for that.
 */
internal fun DrawScope.drawPassageHighlight(
    lines: List<Rect>,
    style: PassageMarkStyle,
    colors: PassageMarkColors,
    sweep: Float,
) {
    val drawn = lines.filter { it.width > 0f && it.height > 0f }
    if (drawn.isEmpty()) return

    val color = colors.of(style)
    val stroke = EdgeStroke.toPx()

    // Measured across the whole passage rather than per line, so the light
    // travels once along a sentence instead of racing separately down each of
    // the lines it happens to be broken over.
    val travelling = if (style == PassageMarkStyle.Waiting) drawn.sweep(color, sweep) else null

    drawn.forEach { line ->
        val radius = CornerRadius(minOf(HighlightRadius.toPx(), line.height / 2))

        drawRoundRect(
            color = color,
            alpha = style.fill(),
            topLeft = line.topLeft,
            size = line.size,
            cornerRadius = radius,
        )

        travelling?.let {
            drawRoundRect(brush = it, topLeft = line.topLeft, size = line.size, cornerRadius = radius)
        }

        // Inside the fill rather than around it, so the edge is part of the shape
        // and not a second shape a hairline larger than it.
        drawRoundRect(
            color = color,
            alpha = style.edge(),
            topLeft = Offset(line.left + stroke / 2, line.top + stroke / 2),
            size = Size(line.width - stroke, line.height - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
    }
}

/**
 * The light partway along this passage, as a brush to lay over its fill.
 *
 * Transparent everywhere but the band, and transparent in the passage's own
 * colour rather than in nothing - a gradient fading to an unrelated clear colour
 * takes a detour through it on the way, which on a warm page reads as a grey
 * smear travelling along the words.
 */
private fun List<Rect>.sweep(color: Color, progress: Float): Brush {
    val left = minOf { it.left }
    val width = maxOf { it.right } - left
    val band = width * SWEEP_WIDTH

    // Begins one band clear of the start and ends one clear of the end, so each
    // pass arrives, crosses, and leaves.
    val head = left - band + progress * (width + band * 2)

    return Brush.horizontalGradient(
        0f to color.copy(alpha = 0f),
        0.5f to color.copy(alpha = SWEEP_ALPHA),
        1f to color.copy(alpha = 0f),
        startX = head,
        endX = head + band,
    )
}

private fun PassageMarkStyle.fill(): Float = when (this) {
    PassageMarkStyle.Waiting -> WAITING_FILL
    PassageMarkStyle.Translated -> TRANSLATED_FILL
    PassageMarkStyle.Failed -> FAILED_FILL
}

private fun PassageMarkStyle.edge(): Float = when (this) {
    PassageMarkStyle.Waiting -> WAITING_EDGE
    PassageMarkStyle.Translated -> TRANSLATED_EDGE
    PassageMarkStyle.Failed -> FAILED_EDGE
}

/**
 * The size the type is set at, for measuring the highlight against.
 *
 * Read off the layout rather than passed down, so that nothing above this has to
 * know the mark is measured in ems at all. A style naming its size in anything
 * but points cannot be read this way, and the line it produced is the next best
 * thing to measure - see [TYPE_OF_LINE].
 */
private fun DrawScope.typeSize(layout: TextLayoutResult): Float {
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
