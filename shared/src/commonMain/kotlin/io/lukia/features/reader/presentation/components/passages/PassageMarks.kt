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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.shared.presentation.theme.LocalMotionStyle

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
 * A sweep rather than a spinner: something turning in the middle of a paragraph
 * is a piece of software asking to be watched. A light moving along the words
 * says which passage is being worked on, in the place the answer will appear,
 * without ever being the thing on the page worth looking at.
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
    // Still where the app has been asked to move less. The passage keeps its
    // highlight - which is what says something was asked about and has not come
    // back yet - and loses only the light travelling along it, which is the part
    // that runs for as long as the network takes and cannot be stopped.
    if (!active || !LocalMotionStyle.current.flourishes) {
        return remember { mutableFloatStateOf(0f) }
    }

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
 * four points, and no mark fits there without landing on the neighbouring word.
 *
 * One rounded rectangle per line rather than one round the whole passage: a
 * sentence beginning halfway along one line and ending halfway along another
 * covers two ribbons of the page, and a single box round both would cover two
 * half-lines it has nothing to do with.
 */
internal fun DrawScope.drawPassageBands(
    bands: List<PassageBand>,
    colors: PassageMarkColors,
    sweep: Float,
) {
    bands.forEach { band ->
        drawPassageHighlight(band.lines, band.style, colors, sweep)
    }
}

/**
 * The highlight itself, over rectangles somebody else worked out.
 *
 * Takes rectangles rather than anything that knows about text, because the two
 * kinds of book arrive at them completely differently - one asks the layout
 * where a range of characters went, the other reads boxes out of a PDF - and
 * what a mark looks like should not be written twice for that.
 *
 * They are expected to have been worked out already rather than per frame - see
 * [PassageBands] - and to have had the empty ones taken out, which is why this
 * is reached through [drawPassageBands] rather than called directly.
 */
private fun DrawScope.drawPassageHighlight(
    drawn: List<Rect>,
    style: PassageMarkStyle,
    colors: PassageMarkColors,
    sweep: Float,
) {
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
