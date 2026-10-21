package io.lukia.shared.presentation.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

// Glyphs that name a group of settings rather than an action.

/** How far in a spark's sides pinch, as a share of its reach. */
private const val SparkWaist = 0.22f

/**
 * A ring half filled: light and dark, and what the app looks like in each.
 * Drawn at [InfoGlyph]'s ring size so the two sit level in one list.
 */
@Composable
fun AppearanceGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.40f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = tint, radius = radius, center = center, style = glyphStroke())
        drawArc(
            color = tint,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
    }
}

/**
 * A four-pointed spark with a smaller one beside it: the shorthand most apps
 * now use for "a model does this".
 */
@Composable
fun SparkleGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawPath(
            path = spark(Offset(size.width * 0.44f, size.height * 0.56f), size.minDimension * 0.34f),
            color = tint,
            style = glyphStroke(),
        )
        drawPath(
            path = spark(Offset(size.width * 0.78f, size.height * 0.22f), size.minDimension * 0.13f),
            color = tint,
        )
    }
}

/** Four points pinched towards [center], each [reach] from it. */
private fun spark(center: Offset, reach: Float): Path {
    val waist = reach * SparkWaist
    return Path().apply {
        moveTo(center.x, center.y - reach)
        quadraticTo(center.x + waist, center.y - waist, center.x + reach, center.y)
        quadraticTo(center.x + waist, center.y + waist, center.x, center.y + reach)
        quadraticTo(center.x - waist, center.y + waist, center.x - reach, center.y)
        quadraticTo(center.x - waist, center.y - waist, center.x, center.y - reach)
        close()
    }
}
