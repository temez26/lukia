package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// The mark for the reader themselves, as opposed to for anything they own.

/**
 * Head and shoulders, for the row that leads to the reader's own profile.
 *
 * The shoulders are an arc rather than a half-circle: swept much past the
 * horizontal they close under the head and the mark reads as a keyhole, and cut
 * much shorter it reads as a hat. The head sits clear of them for the same
 * reason the gear's teeth stop at its rim - two strokes that touch at this size
 * become one blot.
 */
@Composable
fun PersonGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val width = size.width
        val height = size.height

        drawCircle(
            color = tint,
            radius = size.minDimension * 0.17f,
            center = Offset(width / 2f, height * 0.33f),
            style = Stroke(width = strokeWidth),
        )

        val shoulders = Path().apply {
            arcTo(
                rect = Rect(
                    offset = Offset(width * 0.19f, height * 0.60f),
                    size = Size(width * 0.62f, height * 0.44f),
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
        }

        drawPath(
            path = shoulders,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
