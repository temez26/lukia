package io.lukia.shared.presentation.components.launch

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.withSign

/**
 * The grid `icons/lukia-icon.svg` is drawn on. Everything below is written in
 * those coordinates and scaled at draw time, so the artwork here can be compared
 * line for line with the master file rather than translated into dp by hand.
 */
private const val ArtboardSize = 1024f

/** Where all four leaves meet, which is the fan's binding. */
private const val SpineX = 396.06f
private const val SpineY = 834.56f

/** The same point, as the thing every leaf turns about. */
private val Spine = Offset(SpineX, SpineY)

/** How long one leaf takes to swing out, as a share of the whole fan. */
private const val LeafOpenSpan = 0.72f

/** The gap between one leaf starting to swing and the next, on the same scale. */
private const val LeafStagger = 0.14f

/**
 * The superellipse exponent behind Apple's continuous corners, and the number
 * that keeps the tile sitting comfortably beside system icons - see
 * `icons/README.md`.
 */
private const val SquircleExponent = 5f

/** Corners this shallow need the samples; fewer and the curve reads as facets. */
private const val TileCorners = 128

/** Weight of the rim light, in artboard units. */
private const val RimWidth = 6f

private val TwoPi = 2f * PI.toFloat()

/**
 * Where the light falls on the fan. Every leaf shares these endpoints rather
 * than gradienting across its own bounds, which is what makes the four of them
 * read as one object catching one light.
 */
private val SheetLightStart = Offset(130.5f, -103.4f)
private val SheetLightEnd = Offset(893.5f, 1127.4f)

/** The light on the tile: down and slightly right, as in the master artwork. */
private val TileBrush = Brush.linearGradient(
    colorStops = arrayOf(0f to Color(0xFF1F1F23), 1f to Color(0xFF08080A)),
    start = Offset(272.8f, -171.4f),
    end = Offset(751.2f, 1195.4f),
)

/**
 * The rim the rendered PNGs carry and the flat SVG does not. It is not
 * decoration here: on the Crisp palette's true black the tile would otherwise
 * have no edge at all, and the fan would appear to float on nothing.
 */
private val RimColor = Color.White.copy(alpha = 0.09f)

/**
 * One page leaf: its outline, the gradient it is lit with, and how far it swings
 * out of the stack to reach that outline.
 *
 * [sweep] is the angle the artwork already sits at - the leaves stand at 0, 13,
 * 25 and 36 degrees from vertical, see `icons/README.md` - so turning a leaf
 * back by its own sweep is what closes the fan, and no second set of angles has
 * to be kept in step with the first.
 */
private class Leaf(val path: Path, val brush: Brush, val sweep: Float)

/**
 * The four leaves, back to front, each a pair of cubics running out from the
 * spine and back to it.
 */
private val Leaves = listOf(
    Leaf(
        path = leafPath {
            cubicTo(355.1f, 623.35f, 576.26f, 408.15f, 746.65f, 441.22f)
            cubicTo(690.05f, 581.55f, 500.11f, 780.55f, SpineX, SpineY)
        },
        brush = sheetBrush(0f to Color(0xFF989691), 1f to Color(0xFF5C5B59)),
        sweep = 36f,
    ),
    Leaf(
        path = leafPath {
            cubicTo(309.51f, 620.08f, 498.75f, 347.63f, 685.33f, 347.58f)
            cubicTo(654.4f, 507.27f, 494.78f, 756.22f, SpineX, SpineY)
        },
        brush = sheetBrush(0f to Color(0xFFC1BEB8), 1f to Color(0xFF7C7A77)),
        sweep = 25f,
    ),
    Leaf(
        path = leafPath {
            cubicTo(256.29f, 627.15f, 395.2f, 296.42f, 592.55f, 254.41f)
            cubicTo(595.73f, 430.29f, 482.87f, 729.5f, SpineX, SpineY)
        },
        brush = sheetBrush(0f to Color(0xFFE4E1DB), 1f to Color(0xFFA6A39D)),
        sweep = 13f,
    ),
    Leaf(
        path = leafPath {
            cubicTo(199.45f, 651.06f, 264.99f, 270.95f, 461.6f, 179.2f)
            cubicTo(507.47f, 362.7f, 461.6f, 703.49f, SpineX, SpineY)
        },
        brush = sheetBrush(
            0f to Color(0xFFFFFEFB),
            0.55f to Color(0xFFF2EFE9),
            1f to Color(0xFFC9C5BE),
        ),
        // The front leaf is what the others fan out from, so it never moves.
        sweep = 0f,
    ),
)

private val TilePath = Path().apply {
    val half = ArtboardSize / 2f

    repeat(TileCorners) { step ->
        val angle = TwoPi * step / TileCorners
        val x = half + half * superellipse(cos(angle))
        val y = half + half * superellipse(sin(angle))

        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * The app's mark, drawn rather than loaded: four page leaves fanning open from a
 * shared spine, on the near-black tile the launcher icon wears.
 *
 * Vector geometry rather than a packaged bitmap: this is the one image drawn at
 * whatever size the window asks for, a splash that must decode a PNG first
 * arrives after the moment it exists for, and every platform gets the same mark
 * from one source. It is also what makes [fan] possible - a leaf can only turn
 * about the spine while it is still a leaf rather than a pixel.
 *
 * [fan] is how far open the fan is: 0 leaves the leaves stacked behind the front
 * one, a closed book, and 1 is the mark as the icon draws it. It is a lambda
 * rather than a value so that an animating caller is read in the draw pass -
 * the mark redraws each frame without recomposing.
 *
 * Decorative: the app's name is not something the screen reader learns from its
 * icon, so the mark stays out of the accessibility tree.
 */
@Composable
fun LukiaMark(modifier: Modifier = Modifier, fan: () -> Float = { 1f }) {
    Canvas(modifier) {
        val extent = size.minDimension
        val open = fan()

        translate(
            left = (size.width - extent) / 2f,
            top = (size.height - extent) / 2f,
        ) {
            scale(scale = extent / ArtboardSize, pivot = Offset.Zero) {
                drawPath(TilePath, TileBrush)

                Leaves.forEachIndexed { index, leaf ->
                    // Negative: a leaf is turned *back* onto the front one to
                    // close the fan, and released to its own angle as it opens.
                    rotate(degrees = -leaf.sweep * (1f - leafOpen(open, index)), pivot = Spine) {
                        drawPath(leaf.path, leaf.brush)
                    }
                }

                drawPath(TilePath, RimColor, style = Stroke(width = RimWidth))
            }
        }
    }
}

/**
 * How far the leaf at [index] has swung out when the fan as a whole is [open].
 *
 * The leaves are staggered from the back of the stack forwards, which is the
 * direction a thumb opens a book in, and each one is eased on its own so the
 * cascade settles rather than stopping.
 */
private fun leafOpen(open: Float, index: Int): Float {
    val start = index * LeafStagger

    return LukiaMotion.Standard.transform(((open - start) / LeafOpenSpan).coerceIn(0f, 1f))
}

/** A leaf outline, which always opens and closes at the spine. */
private fun leafPath(curves: Path.() -> Unit): Path = Path().apply {
    moveTo(SpineX, SpineY)
    curves()
    close()
}

private fun sheetBrush(vararg stops: Pair<Float, Color>): Brush = Brush.linearGradient(
    colorStops = stops,
    start = SheetLightStart,
    end = SheetLightEnd,
)

private fun superellipse(value: Float): Float =
    abs(value).pow(2f / SquircleExponent).withSign(value)
