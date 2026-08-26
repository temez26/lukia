package io.lukia.features.stats.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.LukiaIconButton

/**
 * The way in to the reading statistics: a mark on the library's bar.
 *
 * A control of its own rather than a row in the actions menu. That menu is about
 * the library - importing books, tidying the shelf, the app's settings - and how
 * much somebody has read is not one of the things done to a shelf.
 *
 * It reaches for nothing and holds no state. Every screen in this app splits
 * into a half that resolves dependencies and a half that draws, and a control
 * that injected would put a dependency graph behind every bar it sits on.
 */
@Composable
fun StatsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LukiaIconButton(
        label = "Your reading",
        onClick = onClick,
        modifier = modifier,
    ) { tint, glyph ->
        ChartGlyph(tint = tint, modifier = glyph)
    }
}

/**
 * Three rising bars, which is the chart this control opens.
 *
 * Capped round and rising left to right, so the mark on the bar and the week on
 * the page behind it are recognisably the same drawing - a reader who has seen
 * one knows what the other will be. Drawn rather than closed shapes because at
 * seventeen points three filled rectangles silt together into a block.
 */
@Composable
private fun ChartGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val width = 2.2.dp.toPx()
        val bottom = size.height * 0.80f

        listOf(0.22f to 0.58f, 0.50f to 0.36f, 0.78f to 0.18f).forEach { (x, top) ->
            drawLine(
                color = tint,
                start = Offset(size.width * x, bottom),
                end = Offset(size.width * x, size.height * top),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}
