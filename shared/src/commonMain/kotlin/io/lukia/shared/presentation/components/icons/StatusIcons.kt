package io.lukia.shared.presentation.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer

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

/**
 * A tick in a ring: selecting every book on a shelf. Outlined while there is
 * more to select, filled once everything is - the same ring a cover wears when
 * it is picked, so the control reads as "all of these" at a glance.
 */
@Composable
fun SelectAllGlyph(
    tint: Color,
    all: Boolean,
    modifier: Modifier = Modifier,
) {
    // Drawn into a layer of its own, so a filled ring's tick can be cut out of
    // it rather than painted over in a colour that has to match what is behind.
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val radius = size.minDimension * 0.42f
        val stroke = glyphStroke()

        if (all) {
            drawCircle(
                color = tint,
                radius = radius,
            )
        } else {
            drawCircle(color = tint, radius = radius, style = stroke)
        }

        val tick = Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.52f)
            lineTo(size.width * 0.45f, size.height * 0.65f)
            lineTo(size.width * 0.69f, size.height * 0.38f)
        }

        drawPath(path = tick, color = tint, style = stroke, blendMode = if (all) BlendMode.Clear else BlendMode.SrcOver)
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
