package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The most opaque a sparkle ever gets. Anything brighter is confetti. */
private const val SparklePeakAlpha = 0.5f

/** The size a sparkle starts at, before it grows into its own. */
private const val SparkleInitialScale = 0.35f

/** How far a sparkle drifts outwards over its life, in half-logos. */
private const val SparkleDrift = 0.1f

/** How pinched the star's waist is; smaller is thinner and more delicate. */
private const val SparkleWaist = 0.2f

private const val DegreesPerRadian = 180f / PI.toFloat()

/** A four-point star at unit radius, centred on the origin. */
private val UnitSparkle = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(SparkleWaist, -SparkleWaist, 1f, 0f)
    quadraticTo(SparkleWaist, SparkleWaist, 0f, 1f)
    quadraticTo(-SparkleWaist, SparkleWaist, -1f, 0f)
    quadraticTo(-SparkleWaist, -SparkleWaist, 0f, -1f)
    close()
}

/**
 * The soft glints that come and go around the mark while it settles.
 *
 * One canvas draws all five, and the shared [elapsedMillis] clock is read inside
 * the draw block: the sparkles are the only thing on screen changing every
 * frame, and they change without recomposing anything.
 *
 * They are timed to be finished before the launch animation hands over, so the
 * screen the reader is left looking at is the app rather than the tail of an
 * effect.
 */
@Composable
internal fun LaunchSparkles(
    elapsedMillis: State<Float>,
    color: Color,
    logoSize: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val half = logoSize.toPx() / 2f

        Sparkles.forEach { sparkle ->
            drawSparkle(sparkle, sparkleProgress(elapsedMillis.value, sparkle), color, half)
        }
    }
}

/**
 * A sparkle mid-life: growing into place, drifting a little further out, and
 * brightest halfway through - `sin` over the whole life is what gives it a fade
 * in and a fade out without two curves and a crossing point between them.
 */
private fun DrawScope.drawSparkle(
    sparkle: Sparkle,
    progress: Float,
    color: Color,
    halfLogo: Float,
) {
    if (progress <= 0f) return

    val alpha = sin(progress * PI.toFloat()) * SparklePeakAlpha
    val grown = LukiaMotion.Standard.transform(progress)
    val radius = halfLogo * sparkle.size * (SparkleInitialScale + (1f - SparkleInitialScale) * grown)
    val distance = halfLogo * (sparkle.distance + SparkleDrift * grown)
    val radians = sparkle.angleDegrees / DegreesPerRadian

    translate(
        left = center.x + distance * sin(radians),
        top = center.y - distance * cos(radians),
    ) {
        scale(scale = radius, pivot = Offset.Zero) {
            rotate(degrees = sparkle.tiltDegrees, pivot = Offset.Zero) {
                drawPath(UnitSparkle, color.copy(alpha = alpha))
            }
        }
    }
}
