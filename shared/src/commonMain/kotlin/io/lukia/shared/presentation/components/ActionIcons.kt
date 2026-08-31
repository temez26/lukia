package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Glyphs for controls the reader can press.

/** A lidded bin for the destructive menu row. */
@Composable
fun TrashGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = glyphStroke()

        val lidWithHandle = Path().apply {
            moveTo(w * 0.13f, h * 0.28f)
            lineTo(w * 0.87f, h * 0.28f)
            moveTo(w * 0.38f, h * 0.28f)
            lineTo(w * 0.38f, h * 0.16f)
            lineTo(w * 0.62f, h * 0.16f)
            lineTo(w * 0.62f, h * 0.28f)
        }

        // Tapered rather than square: the slight inward slope is what makes a
        // rectangle read as a bin at 18.dp.
        val body = Path().apply {
            moveTo(w * 0.22f, h * 0.28f)
            lineTo(w * 0.28f, h * 0.84f)
            lineTo(w * 0.72f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.28f)
        }

        val ticks = Path().apply {
            moveTo(w * 0.42f, h * 0.42f)
            lineTo(w * 0.44f, h * 0.71f)
            moveTo(w * 0.58f, h * 0.42f)
            lineTo(w * 0.56f, h * 0.71f)
        }

        drawPath(path = lidWithHandle, color = tint, style = stroke)
        drawPath(path = body, color = tint, style = stroke)
        drawPath(path = ticks, color = tint, style = stroke)
    }
}

/**
 * The magnifier in the library's search field. Ring plus handle, drawn at the
 * same stroke weight as the chevrons so the field's furniture reads as one set.
 */
@Composable
fun SearchGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = glyphStrokeWidth()
        val radius = size.minDimension * 0.30f
        val center = Offset(
            x = size.width * 0.42f,
            y = size.height * 0.42f,
        )

        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = glyphStroke(cap = StrokeCap.Round),
        )
        // Starts on the ring rather than at its centre, so the two strokes meet
        // cleanly instead of the handle appearing to pass through the glass.
        drawLine(
            color = tint,
            start = Offset(
                x = center.x + radius * 0.72f,
                y = center.y + radius * 0.72f,
            ),
            end = Offset(
                x = size.width * 0.84f,
                y = size.height * 0.84f,
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A cross knocked out of a filled circle: the clear button inside the search
 * field, and the standard iOS mark for emptying a text field.
 *
 * [knockout] is the colour the cross is cut in, and has to be the colour behind
 * the circle rather than a fixed white - the field this sits in is dark in the
 * dark theme, where a white cross on a grey circle would barely read at all.
 */
@Composable
fun ClearGlyph(
    tint: Color,
    knockout: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val arm = radius * 0.42f

        drawCircle(color = tint, radius = radius, center = center)

        listOf(
            arm to arm,
            arm to -arm,
        ).forEach { (dx, dy) ->
            drawLine(
                color = knockout,
                start = Offset(center.x - dx, center.y - dy),
                end = Offset(center.x + dx, center.y + dy),
                strokeWidth = glyphStrokeWidth(GlyphWeight.Light),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A gear, for the row that leads to the app's settings.
 *
 * The teeth start where the rim ends rather than crossing it - run them through
 * the rim and the mark reads as a spoked wheel instead of a gear. Six of them,
 * not eight: at 18.dp, eight teeth and the gaps between them fall below a pixel
 * apart and silt up into a disc.
 */
@Composable
fun GearGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = glyphStrokeWidth(GlyphWeight.Light)
        val center = Offset(size.width / 2f, size.height / 2f)
        val rimRadius = size.minDimension * 0.27f

        drawCircle(
            color = tint,
            radius = rimRadius,
            center = center,
            style = glyphStroke(GlyphWeight.Light, cap = StrokeCap.Butt),
        )

        // Wide enough that the stroke leaves a hole rather than closing over it,
        // which is the difference between a gear and a spoked sun.
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.105f,
            center = center,
            style = glyphStroke(GlyphWeight.Light, cap = StrokeCap.Butt),
        )

        val toothInner = rimRadius + strokeWidth * 0.25f
        val toothOuter = size.minDimension * 0.43f

        repeat(TOOTH_COUNT) { index ->
            val angle = index * (2f * PI / TOOTH_COUNT)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()

            drawLine(
                color = tint,
                start = Offset(
                    x = center.x + dx * toothInner,
                    y = center.y + dy * toothInner,
                ),
                end = Offset(
                    x = center.x + dx * toothOuter,
                    y = center.y + dy * toothOuter,
                ),
                strokeWidth = strokeWidth * 1.4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val TOOTH_COUNT = 6

/** Three dots, used as the hover-revealed menu trigger on a book card. */
@Composable
fun EllipsisGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.10f
        val centerY = size.height / 2f

        listOf(0.22f, 0.50f, 0.78f).forEach { fraction ->
            drawCircle(
                color = tint,
                radius = radius,
                center = Offset(size.width * fraction, centerY),
            )
        }
    }
}

/**
 * A circling arrow, for reading a book's details out of its file again.
 *
 * The ring is deliberately broken rather than closed: the gap is what the
 * arrowhead grows out of, and without it the mark reads as a plain circle with
 * something stuck to it. The head is filled where the rest of the icon set is
 * stroked, because at 18.dp two strokes meeting at a point silt up into a blob.
 */
@Composable
fun RefreshGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.30f
        val center = Offset(
            x = size.width / 2f,
            y = size.height / 2f,
        )

        drawArc(
            color = tint,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(
                x = center.x - radius,
                y = center.y - radius,
            ),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = glyphStroke(cap = StrokeCap.Round),
        )

        // Where the sweep ends, pointing the way it was travelling: clockwise,
        // which on a y-down canvas is the direction the angle increases in.
        val endAngle = (40f + 280f) * PI.toFloat() / 180f
        val outward = Offset(cos(endAngle), sin(endAngle))
        val along = Offset(-sin(endAngle), cos(endAngle))
        val tip = Offset(
            x = center.x + radius * outward.x,
            y = center.y + radius * outward.y,
        )
        val head = radius * 0.62f

        val arrow = Path().apply {
            moveTo(tip.x + along.x * head, tip.y + along.y * head)
            lineTo(tip.x + outward.x * head * 0.62f, tip.y + outward.y * head * 0.62f)
            lineTo(tip.x - outward.x * head * 0.62f, tip.y - outward.y * head * 0.62f)
            close()
        }

        drawPath(path = arrow, color = tint)
    }
}

/**
 * Two arrows pointing opposite ways, marking the control that decides the order
 * a list is in.
 *
 * A pair rather than a single arrow: one arrow says "this way", which is a claim
 * about a particular ordering, while the control it labels is about ordering in
 * general.
 */
@Composable
fun SortGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = glyphStroke(GlyphWeight.Light)

        val top = size.height * 0.18f
        val bottom = size.height * 0.82f
        val head = size.width * 0.16f

        // Up on the left, down on the right, which is how the pair is set
        // everywhere it appears in a toolbar.
        val up = Path().apply {
            moveTo(size.width * 0.30f, bottom)
            lineTo(size.width * 0.30f, top)
            moveTo(size.width * 0.30f - head, top + head)
            lineTo(size.width * 0.30f, top)
            lineTo(size.width * 0.30f + head, top + head)
        }

        val down = Path().apply {
            moveTo(size.width * 0.70f, top)
            lineTo(size.width * 0.70f, bottom)
            moveTo(size.width * 0.70f - head, bottom - head)
            lineTo(size.width * 0.70f, bottom)
            lineTo(size.width * 0.70f + head, bottom - head)
        }

        drawPath(path = up, color = tint, style = stroke)
        drawPath(path = down, color = tint, style = stroke)
    }
}
