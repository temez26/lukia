package io.lukia.shared.presentation.components.launch

/**
 * Where the launch sparkles are and when each of them happens.
 *
 * Apart from the canvas that draws them - see [LaunchSparkles] - because that
 * file builds its star as a top-level [androidx.compose.ui.graphics.Path], which
 * on Android is the platform's own `android.graphics.Path` and needs a graphics
 * stack to load at all. In one file, asking when the third sparkle ends meant
 * initialising a drawing primitive, so the test pinning this timeline failed on
 * the unmocked framework rather than on the timeline.
 *
 * So the numbers live here, depending on nothing, and the drawing depends on
 * them.
 */

/** How long one sparkle takes to arrive, hold and go. */
internal const val SparkleLifeMillis = 420

/**
 * One sparkle's place and moment.
 *
 * [distance] and [size] are given in half-logos rather than dp, so the whole
 * arrangement scales with the mark instead of drifting inwards on a desktop
 * window where the logo is drawn larger.
 */
internal class Sparkle(
    val angleDegrees: Float,
    val distance: Float,
    val size: Float,
    val startMillis: Int,
    val tiltDegrees: Float,
) {
    val endMillis: Int get() = startMillis + SparkleLifeMillis
}

/**
 * Five of them, at unrelated angles and unrelated moments.
 *
 * Placed by hand rather than at random: random placement produces a pair sitting
 * on top of each other roughly one launch in five, and the one thing this
 * animation cannot afford is to look different every time the app opens.
 */
internal val Sparkles = listOf(
    Sparkle(angleDegrees = 28f, distance = 1.3f, size = 0.15f, startMillis = 160, tiltDegrees = 0f),
    Sparkle(angleDegrees = 152f, distance = 1.18f, size = 0.11f, startMillis = 300, tiltDegrees = 18f),
    Sparkle(angleDegrees = 205f, distance = 1.35f, size = 0.17f, startMillis = 240, tiltDegrees = -12f),
    Sparkle(angleDegrees = 320f, distance = 1.22f, size = 0.12f, startMillis = 380, tiltDegrees = 10f),
    Sparkle(angleDegrees = 95f, distance = 1.42f, size = 0.1f, startMillis = 430, tiltDegrees = -20f),
)

/**
 * How far through its life the sparkle is at [elapsedMillis], from 0 to 1.
 * Outside its window it is 0, which is also its alpha, so nothing is drawn.
 */
internal fun sparkleProgress(elapsedMillis: Float, sparkle: Sparkle): Float {
    if (elapsedMillis >= sparkle.endMillis) return 0f

    return ((elapsedMillis - sparkle.startMillis) / SparkleLifeMillis).coerceIn(0f, 1f)
}
