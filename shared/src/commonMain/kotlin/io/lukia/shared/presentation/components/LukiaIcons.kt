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
