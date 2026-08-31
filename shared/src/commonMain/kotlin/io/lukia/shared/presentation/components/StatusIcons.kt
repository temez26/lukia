package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap

// Glyphs that report a state rather than offer an action.

/**
 * The tick beside a chosen row in a list of choices. Two strokes at the same
 * weight as [BackChevron], so a settings list reads as the same family as the
 * chrome around it.
 */
@Composable
fun CheckGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val tick = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.53f)
            lineTo(size.width * 0.42f, size.height * 0.73f)
            lineTo(size.width * 0.80f, size.height * 0.29f)
        }

        drawPath(path = tick, color = tint, style = glyphStroke())
    }
}

/** Exclamation in a ring, for the error state. */
@Composable
fun AlertGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = glyphStrokeWidth()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.40f

        drawCircle(
            color = tint,
            radius = radius,
            style = glyphStroke(cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(centerX, centerY - radius * 0.46f),
            end = Offset(centerX, centerY + radius * 0.10f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint,
            radius = strokeWidth * 0.62f,
            center = Offset(centerX, centerY + radius * 0.50f),
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = glyphStrokeWidth()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension * 0.40f

        drawCircle(
            color = tint,
            radius = radius,
            style = glyphStroke(cap = StrokeCap.Round),
        )
        drawCircle(
            color = tint,
            radius = strokeWidth * 0.62f,
            center = Offset(centerX, centerY - radius * 0.50f),
        )
        drawLine(
            color = tint,
            start = Offset(centerX, centerY - radius * 0.10f),
            end = Offset(centerX, centerY + radius * 0.46f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
