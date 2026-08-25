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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// The marks on the mobile tab bar. Search wears SearchGlyph, which the library's
// own field already uses, so only these two are new.

/**
 * How heavy a tab's mark is drawn.
 *
 * Thinner than the app's other glyphs, which are drawn at 2dp. A tab bar carries
 * its marks at a smaller size than a button does and repeats them across the
 * bottom of every screen, and at 2dp three of them read as a row of blobs.
 */
private val TabStrokeWidth = 1.8.dp

/**
 * A house, for the screen a reader lands on.
 *
 * The roof is drawn as an open chevron over a separate body rather than as one
 * outline. A single closed path puts a mitred join at each eave, which at this
 * size is a visible thickening exactly where the eye reads the shape.
 */
@Composable
fun HomeGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val roof = Path().apply {
            moveTo(w * 0.12f, h * 0.47f)
            lineTo(w * 0.50f, h * 0.17f)
            lineTo(w * 0.88f, h * 0.47f)
        }

        val body = Path().apply {
            moveTo(w * 0.23f, h * 0.42f)
            lineTo(w * 0.23f, h * 0.83f)
            lineTo(w * 0.77f, h * 0.83f)
            lineTo(w * 0.77f, h * 0.42f)
        }

        drawPath(path = roof, color = tint, style = tabStroke())
        drawPath(path = body, color = tint, style = tabStroke())
    }
}

/**
 * Books on a shelf, for the library.
 *
 * Two standing and one leaning against them. The leaning one is what makes the
 * mark read as books rather than as a bar chart - three upright rectangles at
 * this size are indistinguishable from one.
 */
@Composable
fun BooksGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val top = h * 0.20f
        val bottom = h * 0.82f

        val standing = Path().apply {
            addRect(
                Rect(
                    offset = Offset(w * 0.16f, top),
                    size = Size(w * 0.17f, bottom - top),
                ),
            )
            addRect(
                Rect(
                    offset = Offset(w * 0.39f, top),
                    size = Size(w * 0.17f, bottom - top),
                ),
            )
        }

        val leaning = Path().apply {
            moveTo(w * 0.64f, h * 0.34f)
            lineTo(w * 0.81f, h * 0.29f)
            lineTo(w * 0.94f, bottom)
            lineTo(w * 0.77f, bottom)
            close()
        }

        drawPath(path = standing, color = tint, style = tabStroke())
        drawPath(path = leaning, color = tint, style = tabStroke())
    }
}

private fun DrawScope.tabStroke(): Stroke = Stroke(
    width = TabStrokeWidth.toPx(),
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)
