package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drawn rather than imported: this project does not depend on the Material icons
 * artifact, and a two-segment stroke matches Apple's thin chevron more closely
 * than the heavier Material back arrow anyway.
 */
@Composable
fun BackChevron(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val chevron = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.20f)
            lineTo(size.width * 0.34f, size.height * 0.50f)
            lineTo(size.width * 0.62f, size.height * 0.80f)
        }

        drawPath(
            path = chevron,
            color = tint,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * A downward chevron, marking a control that opens something beneath it. Drawn
 * at the same weight as [BackChevron] so the two read as one family.
 */
@Composable
fun DisclosureChevron(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val chevron = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.35f)
            lineTo(size.width * 0.50f, size.height * 0.68f)
            lineTo(size.width * 0.85f, size.height * 0.35f)
        }

        drawPath(
            path = chevron,
            color = tint,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * A folder outline for the library-path action. Drawn as a single stroked path
 * so it sits at the same visual weight as [PlusGlyph] beside it.
 */
@Composable
fun FolderGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val tabTop = size.height * 0.22f
        val bodyTop = size.height * 0.36f
        val bottom = size.height * 0.80f

        val folder = Path().apply {
            moveTo(left, bodyTop)
            lineTo(left, tabTop)
            lineTo(left + size.width * 0.26f, tabTop)
            // The short diagonal is what reads as a folder tab rather than a
            // plain rectangle at this size.
            lineTo(left + size.width * 0.36f, bodyTop)
            lineTo(right, bodyTop)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }

        drawPath(
            path = folder,
            color = tint,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * The favourite mark. One glyph serves both states: stroked while the book is
 * not favourited, [filled] once it is, so the badge and the menu row can share a
 * single drawing.
 */
@Composable
fun HeartGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val heart = Path().apply {
            // Drawn from the bottom tip up each side, so both lobes stay
            // symmetrical about the vertical centre line.
            moveTo(w * 0.50f, h * 0.86f)
            cubicTo(w * 0.04f, h * 0.53f, w * 0.07f, h * 0.16f, w * 0.29f, h * 0.16f)
            cubicTo(w * 0.41f, h * 0.16f, w * 0.50f, h * 0.26f, w * 0.50f, h * 0.32f)
            cubicTo(w * 0.50f, h * 0.26f, w * 0.59f, h * 0.16f, w * 0.71f, h * 0.16f)
            cubicTo(w * 0.93f, h * 0.16f, w * 0.96f, h * 0.53f, w * 0.50f, h * 0.86f)
            close()
        }

        if (filled) {
            drawPath(path = heart, color = tint)
        } else {
            drawPath(
                path = heart,
                color = tint,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/** A lidded bin for the destructive menu row. */
@Composable
fun TrashGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

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

/** Exclamation in a ring, for the error state. */
@Composable
fun AlertGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.40f

        drawCircle(
            color = tint,
            radius = radius,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(centerX, centerY - radius * 0.46f),
            end = androidx.compose.ui.geometry.Offset(centerX, centerY + radius * 0.10f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = tint,
            radius = strokeWidth * 0.62f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY + radius * 0.50f)
        )
    }
}

/**
 * Lowercase "i" in a ring, for the row that leads to a book's details. Drawn as
 * [AlertGlyph] inverted - dot above stem - which is the only thing separating
 * "here is more about this" from "something is wrong" at this size.
 */
@Composable
fun InfoGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.40f

        drawCircle(
            color = tint,
            radius = radius,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawCircle(
            color = tint,
            radius = strokeWidth * 0.62f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY - radius * 0.50f)
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(centerX, centerY - radius * 0.10f),
            end = androidx.compose.ui.geometry.Offset(centerX, centerY + radius * 0.46f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * The magnifier in the library's search field. Ring plus handle, drawn at the
 * same stroke weight as the chevrons so the field's furniture reads as one set.
 */
@Composable
fun SearchGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val radius = size.minDimension * 0.30f
        val center = androidx.compose.ui.geometry.Offset(
            x = size.width * 0.42f,
            y = size.height * 0.42f
        )

        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Starts on the ring rather than at its centre, so the two strokes meet
        // cleanly instead of the handle appearing to pass through the glass.
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(
                x = center.x + radius * 0.72f,
                y = center.y + radius * 0.72f
            ),
            end = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.84f,
                y = size.height * 0.84f
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val arm = radius * 0.42f

        drawCircle(color = tint, radius = radius, center = center)

        listOf(
            arm to arm,
            arm to -arm
        ).forEach { (dx, dy) ->
            drawLine(
                color = knockout,
                start = androidx.compose.ui.geometry.Offset(center.x - dx, center.y - dy),
                end = androidx.compose.ui.geometry.Offset(center.x + dx, center.y + dy),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.6.dp.toPx()
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val rimRadius = size.minDimension * 0.27f

        drawCircle(
            color = tint,
            radius = rimRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Wide enough that the stroke leaves a hole rather than closing over it,
        // which is the difference between a gear and a spoked sun.
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.105f,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        val toothInner = rimRadius + strokeWidth * 0.25f
        val toothOuter = size.minDimension * 0.43f

        repeat(TOOTH_COUNT) { index ->
            val angle = index * (2f * PI / TOOTH_COUNT)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()

            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(
                    x = center.x + dx * toothInner,
                    y = center.y + dy * toothInner
                ),
                end = androidx.compose.ui.geometry.Offset(
                    x = center.x + dx * toothOuter,
                    y = center.y + dy * toothOuter
                ),
                strokeWidth = strokeWidth * 1.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

private const val TOOTH_COUNT = 6

/** Three dots, used as the hover-revealed menu trigger on a book card. */
@Composable
fun EllipsisGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.10f
        val centerY = size.height / 2f

        listOf(0.22f, 0.50f, 0.78f).forEach { fraction ->
            drawCircle(
                color = tint,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(size.width * fraction, centerY)
            )
        }
    }
}

/**
 * A simple stacked-books mark used for the empty state and the import action.
 * Kept as vector drawing so it inherits the current content colour.
 */
@Composable
fun PlusGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val arm = size.minDimension * 0.30f

        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(centerX - arm, centerY),
            end = androidx.compose.ui.geometry.Offset(centerX + arm, centerY),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(centerX, centerY - arm),
            end = androidx.compose.ui.geometry.Offset(centerX, centerY + arm),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}
