package io.lukia.features.reader.presentation.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.translate.presentation.PassageTranslation

/**
 * How big the marks are, and how far off the words they sit.
 *
 * Small enough to read as punctuation the book might have had rather than as
 * something the app has drawn on top of it, and far enough out that they never
 * touch a descender. Fixed rather than scaled with the type: a mark is a mark
 * at every text size, and one that grew with a book set at 200% would stop
 * being a quiet aside.
 */
private val DotRadius: Dp = 2.5.dp
private val DotGap: Dp = 3.dp
private val RingStroke: Dp = 1.2.dp

/**
 * How far above the middle of the line a mark sits, as a fraction of the line's
 * own height.
 *
 * Set on the middle of the line, a mark is at exactly the height of the letters
 * on either side of it - and the room beside a sentence is often only the single
 * space before the next one, which is narrower than the mark and its gap
 * together. The dot then lands on the first letter of the sentence after it.
 *
 * Lifting it to about the height of a capital puts it in the space above the
 * lowercase letters, which is empty on nearly every line. Sitting there it reads
 * as a superscript mark - which is what it is - and no longer competes with any
 * character for the same few points of the line.
 *
 * A fraction rather than a distance, so it follows a book set at any size.
 */
private const val DOT_RISE = 0.22f

/**
 * How faint the marks on a passage still being translated go, and how long one
 * breath takes.
 *
 * A pulse rather than a spinner: the reader is looking at their own book, and
 * something turning in the middle of a paragraph is a piece of software asking
 * to be watched. This says the same thing at the edge of attention.
 */
private const val PULSE_MIN_ALPHA = 0.25f
private const val PULSE_MILLIS = 900

/**
 * What a mark says about the passage it brackets.
 *
 * The four are deliberately distinguishable without colour vision: a pair of
 * filled dots, a pair of rings, and a pulse are three different shapes of thing
 * before they are three different colours.
 */
internal enum class PassageMarkStyle {

    /** Being translated. The dots breathe. */
    Waiting,

    /** Translated, and the translation is what is on the page. */
    Translated,

    /**
     * Translated, but the reader has asked for the book's own words back.
     *
     * Marked at all - and marked in the same places, at the same size - because
     * a sentence flipped back to the original is not an untouched sentence:
     * there is an answer waiting behind it, already paid for, one tap away. A
     * ring rather than a dot is what says so without saying it loudly.
     */
    Original,

    /** Asked for and not come back. */
    Failed
}

/** How this passage should be marked, or null when nobody has touched it. */
internal fun PassageTranslation?.markStyle(): PassageMarkStyle? = when (this) {
    null -> null
    is PassageTranslation.Loading -> PassageMarkStyle.Waiting
    is PassageTranslation.Failed -> PassageMarkStyle.Failed
    is PassageTranslation.Ready ->
        if (showingOriginal) PassageMarkStyle.Original else PassageMarkStyle.Translated
}

/** One stretch of drawn text, and what its marks say. */
@Immutable
internal data class PassageMark(val start: Int, val end: Int, val style: PassageMarkStyle)

/**
 * The three colours a mark can take, resolved once rather than per passage.
 *
 * Translated and Original share one: they are the same passage in two states,
 * and giving them separate colours would say they were different things.
 */
@Immutable
internal data class PassageMarkColors(
    val waiting: Color,
    val translated: Color,
    val failed: Color
) {
    fun of(style: PassageMarkStyle): Color = when (style) {
        PassageMarkStyle.Waiting -> waiting
        PassageMarkStyle.Translated, PassageMarkStyle.Original -> translated
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
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "passage-pulse-alpha"
    )
}

/**
 * Draws a small dot at each end of every marked passage.
 *
 * A pair of dots rather than a wash behind the words, because the words are the
 * book: a highlighter laid over a sentence changes how it reads, and a reader
 * working through a page ends up reading a page of highlighter. Two marks at
 * the ends say exactly as much - where the passage starts, where it stops, and
 * what has become of it - and leave the type alone.
 *
 * Placed against the first and last characters rather than the passage's box,
 * so a sentence that wraps is marked at its first word and its last word rather
 * than at the corners of the block it happens to cover - and lifted clear of
 * the letters beside them, see [DOT_RISE].
 */
internal fun DrawScope.drawPassageMarks(
    layout: TextLayoutResult,
    marks: List<PassageMark>,
    colors: PassageMarkColors,
    pulse: Float
) {
    val lastOffset = layout.layoutInput.text.length - 1
    if (lastOffset < 0) return

    marks.forEach { mark ->
        drawPassageDots(
            opening = layout.getBoundingBox(mark.start.coerceIn(0, lastOffset)),
            closing = layout.getBoundingBox((mark.end - 1).coerceIn(0, lastOffset)),
            style = mark.style,
            colors = colors,
            pulse = pulse
        )
    }
}

/**
 * The pair itself: one dot off the start of [opening], one off the end of
 * [closing].
 *
 * Takes rectangles rather than anything that knows about text, because the two
 * kinds of book arrive at them completely differently - one asks the layout
 * where a character went, the other reads a box out of a PDF - and what a mark
 * looks like should not be written twice for that.
 */
internal fun DrawScope.drawPassageDots(
    opening: Rect,
    closing: Rect,
    style: PassageMarkStyle,
    colors: PassageMarkColors,
    pulse: Float
) {
    val radius = DotRadius.toPx()
    val gap = DotGap.toPx()
    val stroke = RingStroke.toPx()

    val color = colors.of(style)
    val alpha = if (style == PassageMarkStyle.Waiting) pulse else 1f
    val hollow = style == PassageMarkStyle.Original

    drawDot(
        // Pulled back inside the drawing area, since a passage that starts a
        // line would otherwise have its opening dot in the margin, where it
        // would be clipped away.
        center = Offset(
            x = (opening.left - gap - radius).coerceAtLeast(radius),
            y = opening.risenCenter
        ),
        radius = radius,
        color = color,
        alpha = alpha,
        hollow = hollow,
        stroke = stroke
    )

    drawDot(
        center = Offset(
            x = (closing.right + gap + radius).coerceAtMost(size.width - radius),
            y = closing.risenCenter
        ),
        radius = radius,
        color = color,
        alpha = alpha,
        hollow = hollow,
        stroke = stroke
    )
}

/** Where a mark sits against one line of text. See [DOT_RISE]. */
private val Rect.risenCenter: Float get() = center.y - height * DOT_RISE

private fun DrawScope.drawDot(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    hollow: Boolean,
    stroke: Float
) {
    if (hollow) {
        // Drawn inside the same radius a filled dot uses, so flipping between
        // the two never moves the mark or changes how much room it takes.
        drawCircle(
            color = color,
            radius = radius - stroke / 2,
            center = center,
            alpha = alpha,
            style = Stroke(width = stroke)
        )
    } else {
        drawCircle(color = color, radius = radius, center = center, alpha = alpha)
    }
}
