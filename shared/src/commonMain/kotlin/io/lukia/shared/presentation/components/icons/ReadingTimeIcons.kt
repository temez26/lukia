package io.lukia.shared.presentation.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.min

/** A clock face with its hands at ten past two: time spent. */
@Composable
fun ClockGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) * 0.36f

        drawCircle(color = tint, radius = radius, center = centre, style = glyphStroke())
        drawLine(
            color = tint,
            start = centre,
            end = Offset(centre.x, centre.y - radius * 0.62f),
            strokeWidth = glyphStrokeWidth(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = centre,
            end = Offset(centre.x + radius * 0.48f, centre.y + radius * 0.18f),
            strokeWidth = glyphStrokeWidth(),
            cap = StrokeCap.Round,
        )
    }
}

/** Three bars of different heights over a baseline: how a day usually goes. */
@Composable
fun ChartGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val base = size.height * 0.80f
        val stroke = glyphStrokeWidth()

        listOf(0.30f to 0.46f, 0.50f to 0.26f, 0.70f to 0.56f).forEach { (x, top) ->
            drawLine(
                color = tint,
                start = Offset(size.width * x, base),
                end = Offset(size.width * x, size.height * top),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            color = tint,
            start = Offset(size.width * 0.16f, base + stroke * 1.5f),
            end = Offset(size.width * 0.84f, base + stroke * 1.5f),
            strokeWidth = glyphStrokeWidth(GlyphWeight.Light),
            cap = StrokeCap.Round,
        )
    }
}

/** A line climbing to its highest point, marked: the best day there was. */
@Composable
fun PeakGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val points = listOf(0.14f to 0.78f, 0.38f to 0.52f, 0.54f to 0.64f, 0.80f to 0.24f)
        val stroke = glyphStroke()

        points.zipWithNext().forEach { (from, to) ->
            drawLine(
                color = tint,
                start = Offset(size.width * from.first, size.height * from.second),
                end = Offset(size.width * to.first, size.height * to.second),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }

        val (x, y) = points.last()
        drawCircle(color = tint, radius = stroke.width * 1.2f, center = Offset(size.width * x, size.height * y))
    }
}
