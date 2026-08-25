package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Glyphs for the translation controls.
//
// The three sit side by side and answer three different questions, so they are
// drawn as three different shapes rather than as three arrangements of one. An
// earlier set gave all three the same highlighted-passage capsule and varied
// only what was written inside it, which at the 17-20dp these are drawn at made
// them one button repeated three times.
//
// What they share instead is the filled bar: the mark a translated passage
// leaves in the book. It sits inside a page for "this page", under a pair of
// chevrons for "and the ones after it", and is absent from the eye, which is
// about seeing rather than about translating.

/** The weight the whole set is stroked at. */
private val GlyphStroke = 1.8.dp

/** The rule standing for the untranslated line, a little lighter than the frame. */
private val RuleStroke = 1.6.dp

/**
 * A page with one line of it translated: what the button does, to what is on
 * screen, once.
 *
 * The frame is the point. It is what separates this from the standing order
 * beside it - this one stops at the edge of the page.
 */
@Composable
fun TranslateGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.16f, size.height * 0.10f),
            size = Size(size.width * 0.68f, size.height * 0.80f),
            cornerRadius = CornerRadius(size.width * 0.14f),
            style = Stroke(width = GlyphStroke.toPx(), join = StrokeJoin.Round),
        )

        drawLine(
            color = tint,
            start = Offset(size.width * 0.31f, size.height * 0.33f),
            end = Offset(size.width * 0.69f, size.height * 0.33f),
            strokeWidth = RuleStroke.toPx(),
            cap = StrokeCap.Round,
        )

        // Inset from the frame it sits inside, where the standing order's bar
        // runs the full width of the glyph.
        drawPassageBar(tint = tint, top = 0.53f, left = 0.29f, width = 0.42f)
    }
}

/**
 * The same translated passage with two chevrons under it: translating does not
 * stop at the edge of the page but carries on down the book as the reader goes.
 *
 * Unframed, where the one beside it is framed. A standing order has no page to
 * be bounded by, and at this size that difference is legible in a way that a
 * changed arrangement of lines inside the same capsule was not.
 */
@Composable
fun TranslateOnwardsGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawPassageBar(tint = tint, top = 0.12f, left = 0.16f, width = 0.68f)

        drawChevron(tint = tint, top = 0.44f)
        drawChevron(tint = tint, top = 0.68f)
    }
}

/**
 * Whether the translations are on the page, drawn as an eye.
 *
 * The one control of the three that is about looking rather than about
 * translating, and an eye is what every other app on the reader's device uses to
 * say so. The struck-through form keeps the same bounds as the open one, so
 * pressing it moves nothing in the bar.
 */
@Composable
fun TranslationsShownGlyph(
    tint: Color,
    shown: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Two mirrored curves rather than an ellipse: an eye is pointed at both
        // corners, and a flattened circle reads as a coin.
        val outline = Path().apply {
            moveTo(w * 0.08f, h * 0.50f)
            cubicTo(w * 0.26f, h * 0.22f, w * 0.74f, h * 0.22f, w * 0.92f, h * 0.50f)
            cubicTo(w * 0.74f, h * 0.78f, w * 0.26f, h * 0.78f, w * 0.08f, h * 0.50f)
            close()
        }

        drawPath(
            path = outline,
            color = tint,
            style = Stroke(
                width = GlyphStroke.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        drawCircle(color = tint, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.50f))

        if (shown) return@Canvas

        drawLine(
            color = tint,
            start = Offset(w * 0.16f, h * 0.86f),
            end = Offset(w * 0.84f, h * 0.14f),
            strokeWidth = GlyphStroke.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** How thick the passage bar is, as a fraction of the glyph. */
private const val BAR_HEIGHT = 0.14f

/**
 * The mark a translated passage leaves in the book, filled rather than outlined
 * so that it survives being drawn at 17dp.
 */
private fun DrawScope.drawPassageBar(tint: Color, top: Float, left: Float, width: Float) {
    val height = size.height * BAR_HEIGHT

    drawRoundRect(
        color = tint,
        topLeft = Offset(size.width * left, size.height * top),
        size = Size(size.width * width, height),
        cornerRadius = CornerRadius(height / 2f),
    )
}

/** Where a chevron reaches across the glyph, and how far it dips in the middle. */
private const val CHEVRON_LEFT = 0.28f
private const val CHEVRON_MIDDLE = 0.50f
private const val CHEVRON_RIGHT = 0.72f
private const val CHEVRON_DEPTH = 0.20f

/** One step further down the book. */
private fun DrawScope.drawChevron(tint: Color, top: Float) {
    val y = size.height * top

    val chevron = Path().apply {
        moveTo(size.width * CHEVRON_LEFT, y)
        lineTo(size.width * CHEVRON_MIDDLE, y + size.height * CHEVRON_DEPTH)
        lineTo(size.width * CHEVRON_RIGHT, y)
    }

    drawPath(
        path = chevron,
        color = tint,
        style = Stroke(
            width = GlyphStroke.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
