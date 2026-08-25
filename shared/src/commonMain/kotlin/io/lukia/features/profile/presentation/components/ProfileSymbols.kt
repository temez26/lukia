// Every number below is a point on the mark being drawn, as a fraction of
// whatever the mark is given - the same way the app's other glyphs are written,
// where the enclosing composable is what exempts them. These are drawn from
// functions of their own so that six marks can share one dispatch, which loses
// that exemption; naming sixty coordinates would name nothing.
@file:Suppress("MagicNumber")

package io.lukia.features.profile.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.lukia.features.profile.domain.ProfileSymbol
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The marks a reader can wear, drawn rather than lettered.
 *
 * Every one is laid out as a fraction of whatever it is given, and its stroke
 * scales with it, so the same drawing serves the disc in a toolbar and the disc
 * at the top of the profile page. That is what stops the app needing a second
 * set of these at a second size.
 */
@Composable
internal fun ProfileSymbolGlyph(
    symbol: ProfileSymbol,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        when (symbol) {
            ProfileSymbol.Book -> drawBook(tint)
            ProfileSymbol.Bookmark -> drawBookmark(tint)
            ProfileSymbol.Feather -> drawFeather(tint)
            ProfileSymbol.Leaf -> drawLeaf(tint)
            ProfileSymbol.Moon -> drawMoon(tint)
            ProfileSymbol.Star -> drawStar(tint)
        }
    }
}

/** Heavier than the app's chrome glyphs: this one is read against a filled disc. */
private fun DrawScope.markWidth(): Float = size.minDimension * 0.07f

private fun DrawScope.markStroke(): Stroke = Stroke(
    width = markWidth(),
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)

/**
 * An open book, seen from above: two leaves meeting at a spine.
 *
 * The spine is a separate stroke rather than the meeting of two closed shapes,
 * because at the size of a toolbar disc the two outlines converge into a blot
 * and the book stops reading as open.
 */
private fun DrawScope.drawBook(tint: Color) {
    val w = size.width
    val h = size.height

    val leaves = Path().apply {
        moveTo(w * 0.16f, h * 0.30f)
        cubicTo(w * 0.30f, h * 0.24f, w * 0.42f, h * 0.26f, w * 0.50f, h * 0.34f)
        cubicTo(w * 0.58f, h * 0.26f, w * 0.70f, h * 0.24f, w * 0.84f, h * 0.30f)
        lineTo(w * 0.84f, h * 0.72f)
        cubicTo(w * 0.70f, h * 0.66f, w * 0.58f, h * 0.68f, w * 0.50f, h * 0.76f)
        cubicTo(w * 0.42f, h * 0.68f, w * 0.30f, h * 0.66f, w * 0.16f, h * 0.72f)
        close()
    }

    drawPath(path = leaves, color = tint, style = markStroke())

    drawLine(
        color = tint,
        start = Offset(w * 0.50f, h * 0.34f),
        end = Offset(w * 0.50f, h * 0.76f),
        strokeWidth = markWidth(),
        cap = StrokeCap.Round,
    )
}

/** A ribbon with a notch cut out of its foot. */
private fun DrawScope.drawBookmark(tint: Color) {
    val w = size.width
    val h = size.height

    val ribbon = Path().apply {
        moveTo(w * 0.28f, h * 0.20f)
        lineTo(w * 0.72f, h * 0.20f)
        lineTo(w * 0.72f, h * 0.80f)
        lineTo(w * 0.50f, h * 0.62f)
        lineTo(w * 0.28f, h * 0.80f)
        close()
    }

    drawPath(path = ribbon, color = tint, style = markStroke())
}

/**
 * A quill: one shaft, and a vane swept off it.
 *
 * The vane is drawn as a closed curve rather than as barbs. Barbs at this size
 * are a comb, and a comb is what most feather icons at sixteen points look like.
 */
private fun DrawScope.drawFeather(tint: Color) {
    val w = size.width
    val h = size.height

    val vane = Path().apply {
        moveTo(w * 0.30f, h * 0.70f)
        cubicTo(w * 0.28f, h * 0.38f, w * 0.48f, h * 0.18f, w * 0.78f, h * 0.20f)
        cubicTo(w * 0.80f, h * 0.50f, w * 0.62f, h * 0.70f, w * 0.30f, h * 0.70f)
        close()
    }

    drawPath(path = vane, color = tint, style = markStroke())

    drawLine(
        color = tint,
        start = Offset(w * 0.22f, h * 0.80f),
        end = Offset(w * 0.62f, h * 0.38f),
        strokeWidth = markWidth(),
        cap = StrokeCap.Round,
    )
}

/** A leaf, with the midrib that keeps it from reading as an eye. */
private fun DrawScope.drawLeaf(tint: Color) {
    val w = size.width
    val h = size.height

    val blade = Path().apply {
        moveTo(w * 0.22f, h * 0.78f)
        cubicTo(w * 0.20f, h * 0.40f, w * 0.44f, h * 0.20f, w * 0.80f, h * 0.22f)
        cubicTo(w * 0.82f, h * 0.58f, w * 0.60f, h * 0.80f, w * 0.22f, h * 0.78f)
        close()
    }

    drawPath(path = blade, color = tint, style = markStroke())

    drawLine(
        color = tint,
        start = Offset(w * 0.30f, h * 0.70f),
        end = Offset(w * 0.68f, h * 0.32f),
        strokeWidth = markWidth() * 0.8f,
        cap = StrokeCap.Round,
    )
}

/**
 * A crescent, cut rather than drawn.
 *
 * Two circles and a blend mode, because the crescent's inner edge is an arc of a
 * different circle from its outer one - describing that as one path means four
 * curves that have to be kept in step, and the cut-out is exact for free.
 */
private fun DrawScope.drawMoon(tint: Color) {
    val radius = size.minDimension * 0.30f
    val centre = Offset(size.width * 0.54f, size.height * 0.50f)

    // Its own layer, so the second circle takes a bite out of the first rather
    // than out of everything already drawn on the disc.
    drawContext.canvas.saveLayer(bounds = Rect(Offset.Zero, size), paint = Paint())

    drawCircle(color = tint, radius = radius, center = centre)

    drawCircle(
        color = Color.Transparent,
        radius = radius * 0.92f,
        center = Offset(centre.x + radius * 0.62f, centre.y - radius * 0.28f),
        blendMode = BlendMode.Clear,
    )

    drawContext.canvas.restore()
}

/** Five points, struck from the centre. */
private fun DrawScope.drawStar(tint: Color) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension * 0.34f
    val inner = outer * 0.42f

    val star = Path()

    repeat(StarPoints * 2) { index ->
        val radius = if (index % 2 == 0) outer else inner
        // Started at the top rather than at three o'clock, which is where a
        // star's point belongs and where every drawn star has one.
        val angle = -PI.toFloat() / 2f + index * PI.toFloat() / StarPoints
        val point = Offset(
            x = centre.x + radius * cos(angle),
            y = centre.y + radius * sin(angle),
        )

        if (index == 0) star.moveTo(point.x, point.y) else star.lineTo(point.x, point.y)
    }

    star.close()

    drawPath(path = star, color = tint)
}

private const val StarPoints = 5
