package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * The tick beside a chosen row in a list of choices. Two strokes at the same
 * weight as [BackChevron], so a settings list reads as the same family as the
 * chrome around it.
 */
@Composable
fun CheckGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val tick = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.53f)
            lineTo(size.width * 0.42f, size.height * 0.73f)
            lineTo(size.width * 0.80f, size.height * 0.29f)
        }

        drawPath(
            path = tick,
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
 * An open book, for the control that turns a cover into the way into it.
 *
 * A play triangle would be the obvious mark and the wrong one: it is the idiom
 * of something that runs on its own while you watch, and reading is neither. An
 * open book says what pressing this does in the only vocabulary the app has.
 *
 * Drawn as two filled leaves rather than as an outline. The mark appears at
 * roughly half the size of the disc behind it, and at that size a stroked book
 * silts up into a smudge - filled pages hold their shape. The gap left between
 * them is the spine, showing the disc through rather than a drawn line.
 */
@Composable
fun OpenBookGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Half the width of the gap down the middle, as a fraction of the glyph.
        val spine = w * 0.05f

        fun between(from: Float, to: Float, at: Float): Float = from + (to - from) * at

        fun leaf(towards: Float): Path = Path().apply {
            val outer = if (towards < 0) w * 0.05f else w * 0.95f
            val inner = w * 0.5f + towards * spine

            // Each page is tallest at the spine and falls away towards its outer
            // edge. That taper is the whole mark: two untapered rectangles either
            // side of a gap read as a pause button, which is the one thing this
            // must not be mistaken for.
            moveTo(inner, h * 0.19f)
            cubicTo(
                between(inner, outer, 0.45f), h * 0.19f,
                between(inner, outer, 0.78f), h * 0.23f,
                outer, h * 0.31f
            )
            lineTo(outer, h * 0.71f)
            cubicTo(
                between(outer, inner, 0.28f), h * 0.72f,
                between(outer, inner, 0.58f), h * 0.83f,
                inner, h * 0.83f
            )
            close()
        }

        drawPath(path = leaf(towards = -1f), color = tint)
        drawPath(path = leaf(towards = 1f), color = tint)
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.30f
        val center = androidx.compose.ui.geometry.Offset(
            x = size.width / 2f,
            y = size.height / 2f
        )

        drawArc(
            color = tint,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = center.x - radius,
                y = center.y - radius
            ),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Where the sweep ends, pointing the way it was travelling: clockwise,
        // which on a y-down canvas is the direction the angle increases in.
        val endAngle = (40f + 280f) * PI.toFloat() / 180f
        val outward = androidx.compose.ui.geometry.Offset(cos(endAngle), sin(endAngle))
        val along = androidx.compose.ui.geometry.Offset(-sin(endAngle), cos(endAngle))
        val tip = androidx.compose.ui.geometry.Offset(
            x = center.x + radius * outward.x,
            y = center.y + radius * outward.y
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
 * The mark the reader already knows: two lines of a passage sitting on the same
 * soft highlight that a translated passage sits on in the book.
 *
 * Deliberately not a letter from one alphabet beside a letter from another, which
 * is the usual translate icon and says "language" rather than "this passage".
 * This says what the button does to what is on screen.
 */
@Composable
fun TranslateGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

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
        drawHighlight(
            tint = tint,
            top = size.height * 0.22f,
            bottom = size.height * 0.78f
        )

        drawTextRule(tint, y = 0.38f, from = 0.26f, to = 0.78f)
        drawTextRule(tint, y = 0.60f, from = 0.26f, to = 0.64f)
    }
}

/**
 * The same passage with an arrow under it: translating does not stop at what is
 * on screen but carries on down the page as the reader goes.
 *
 * The highlight is pulled up and shortened to make room, which also makes the
 * pair distinguishable at a glance in a toolbar where they sit side by side.
 */
@Composable
fun TranslateOnwardsGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawHighlight(
            tint = tint,
            top = size.height * 0.10f,
            bottom = size.height * 0.58f
        )

        drawTextRule(tint, y = 0.24f, from = 0.26f, to = 0.76f)
        drawTextRule(tint, y = 0.44f, from = 0.26f, to = 0.62f)

        val onwards = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.72f)
            lineTo(size.width * 0.50f, size.height * 0.88f)
            lineTo(size.width * 0.66f, size.height * 0.72f)
        }

        drawPath(
            path = onwards,
            color = tint,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Whether the translations are on the page, drawn as whether there is anything
 * written on the highlight.
 *
 * The same capsule either way, so pressing it does not change the size or the
 * position of anything in the bar - only whether it has words in it. Which is
 * exactly what pressing it does to the page.
 */
@Composable
fun TranslationsShownGlyph(
    tint: Color,
    shown: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawHighlight(
            tint = tint,
            top = size.height * 0.26f,
            bottom = size.height * 0.74f
        )

        if (!shown) return@Canvas

        drawTextRule(tint, y = 0.42f, from = 0.26f, to = 0.74f)
        drawTextRule(tint, y = 0.58f, from = 0.26f, to = 0.60f)
    }
}

/** How much of the glyph's colour the highlight behind its lines keeps. */
private const val GLYPH_HIGHLIGHT_ALPHA = 0.22f

private fun DrawScope.drawHighlight(tint: Color, top: Float, bottom: Float) {
    val height = bottom - top

    drawRoundRect(
        color = tint,
        alpha = GLYPH_HIGHLIGHT_ALPHA,
        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, top),
        size = androidx.compose.ui.geometry.Size(size.width * 0.72f, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * 0.32f)
    )
}

/** One line of the passage, at the weight the rest of the icon set is stroked at. */
private fun DrawScope.drawTextRule(tint: Color, y: Float, from: Float, to: Float) {
    drawLine(
        color = tint,
        start = androidx.compose.ui.geometry.Offset(size.width * from, size.height * y),
        end = androidx.compose.ui.geometry.Offset(size.width * to, size.height * y),
        strokeWidth = 1.8.dp.toPx(),
        cap = StrokeCap.Round
    )
}
