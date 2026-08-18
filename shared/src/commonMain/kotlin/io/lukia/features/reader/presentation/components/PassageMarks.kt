package io.lukia.features.reader.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.translate.presentation.PassageTranslation

/**
 * How far the highlight is drawn outside the words it sits behind.
 *
 * Sideways, enough that the first and last letters are not touching its edge - a
 * highlight cut flush to the text reads as a box drawn round the words rather
 * than as the words sitting on something.
 *
 * Vertically it is drawn well *inside* the line, because a line box is the type
 * plus its leading and filling all of it makes a tall slab with the words
 * floating in the middle of it. Taken in to about where the letters themselves
 * start and stop, the shape follows the writing rather than the layout - and a
 * passage running down three lines reads as three marked lines rather than as
 * one block of colour, because there is a clear band of page between them.
 */
private val HighlightBleed: Dp = 2.5.dp
private const val LINE_INSET = 0.14f

/**
 * How round the corners are.
 *
 * Generous, and capped at half the height, so a highlight this shallow comes out
 * as a capsule rather than as a rectangle with its corners taken off.
 */
private val HighlightRadius: Dp = 7.dp

/**
 * How much of the page each state lets through.
 *
 * All of them are faint on purpose. What is behind a highlight is the book, and
 * a wash strong enough to be noticed on its own is a wash the reader has to read
 * the text through. These are the weights at which a marked passage is findable
 * at a glance and invisible while the eye is on the words.
 */
private const val WAITING_FILL = 0.11f
private const val TRANSLATED_FILL = 0.14f
private const val FAILED_FILL = 0.16f

/**
 * How far a waiting passage fades at the bottom of its breath, and how long one
 * breath takes.
 *
 * A pulse rather than a spinner: the reader is looking at their own book, and
 * something turning in the middle of a paragraph is a piece of software asking
 * to be watched. This says the same thing at the edge of attention.
 *
 * Eased rather than linear, and not all the way to nothing - a fill that
 * vanished at the bottom of every breath would flicker rather than breathe.
 */
private const val PULSE_MIN = 0.45f
private const val PULSE_MILLIS = 1100

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
    Failed
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
    val failed: Color
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
            failed = colors.error
        )
    }
}

/**
 * The breath a waiting mark takes, or a still value when nothing is waiting.
 *
 * Kept off unless something is actually being translated: an infinite animation
 * running behind a page nobody is translating anything on is a frame of work
 * every frame, for a page that is not changing.
 */
@Composable
internal fun passagePulse(active: Boolean): State<Float> {
    if (!active) return remember { mutableFloatStateOf(1f) }

    val transition = rememberInfiniteTransition(label = "passage-pulse")

    return transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "passage-pulse-alpha"
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
    pulse: Float
) {
    if (layout.layoutInput.text.isEmpty()) return

    marks.forEach { mark ->
        drawPassageHighlight(
            lines = layout.linesOf(mark, bleed = HighlightBleed.toPx()),
            style = mark.style,
            colors = colors,
            pulse = pulse
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
    pulse: Float
) {
    val color = colors.of(style)
    val alpha = style.fill() * if (style == PassageMarkStyle.Waiting) pulse else 1f

    lines.forEach { line ->
        if (line.width <= 0f || line.height <= 0f) return@forEach

        drawRoundRect(
            color = color,
            alpha = alpha,
            topLeft = line.topLeft,
            size = line.size,
            cornerRadius = CornerRadius(minOf(HighlightRadius.toPx(), line.height / 2))
        )
    }
}

private fun PassageMarkStyle.fill(): Float = when (this) {
    PassageMarkStyle.Waiting -> WAITING_FILL
    PassageMarkStyle.Translated -> TRANSLATED_FILL
    PassageMarkStyle.Failed -> FAILED_FILL
}

/**
 * The stretch of each line this passage covers, as the shape to draw behind it.
 *
 * Built from the layout's own lines rather than from `getPathForRange`, which
 * hands back one path with square corners and no way to round them. Trailing
 * whitespace is left out, so a highlight ends at the last letter of a line
 * rather than running on to where the next word would have started.
 */
private fun TextLayoutResult.linesOf(mark: PassageMark, bleed: Float): List<Rect> {
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
        val inset = (getLineBottom(line) - getLineTop(line)) * LINE_INSET

        Rect(
            // A right-to-left line hands these back the other way round.
            left = minOf(near, far) - bleed,
            top = getLineTop(line) + inset,
            right = maxOf(near, far) + bleed,
            bottom = getLineBottom(line) - inset
        )
    }
}
