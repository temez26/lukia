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

// Glyphs for the translation controls, which share a passage-with-rules motif.

/**
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawHighlight(
            tint = tint,
            top = size.height * 0.22f,
            bottom = size.height * 0.78f,
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawHighlight(
            tint = tint,
            top = size.height * 0.10f,
            bottom = size.height * 0.58f,
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
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawHighlight(
            tint = tint,
            top = size.height * 0.26f,
            bottom = size.height * 0.74f,
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
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * 0.32f),
    )
}

/** One line of the passage, at the weight the rest of the icon set is stroked at. */
private fun DrawScope.drawTextRule(tint: Color, y: Float, from: Float, to: Float) {
    drawLine(
        color = tint,
        start = androidx.compose.ui.geometry.Offset(size.width * from, size.height * y),
        end = androidx.compose.ui.geometry.Offset(size.width * to, size.height * y),
        strokeWidth = 1.8.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
