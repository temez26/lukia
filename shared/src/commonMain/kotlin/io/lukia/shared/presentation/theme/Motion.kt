package io.lukia.shared.presentation.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * The app's motion vocabulary.
 *
 * Every animation in Lukia is built from what is here rather than from a
 * hand-picked duration at the call site. The point is not the individual
 * numbers - it is that a menu opening, a screen arriving and a card being
 * pressed all move at rates that are recognisably related, which is most of what
 * makes motion read as one piece of software rather than several.
 *
 * The timings follow Apple's convention that things leave faster than they
 * arrive: an entrance is being watched, an exit is already being scrolled past.
 */
object LukiaMotion {

    /**
     * Apple's interface curve: almost all of the ease is on the way out, so a
     * move starts decisively and settles rather than drifting into place.
     * Material's own easings are noticeably slower off the mark by comparison.
     */
    val Standard: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** For things leaving, where there is nothing to settle into. */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** A press, a hover, a hairline appearing - too short to be watched. */
    const val QuickMillis = 140

    /** The default: a menu, a panel, a cross-dissolve. */
    const val StandardMillis = 240

    /** Screen-sized moves, where the distance travelled needs the extra time. */
    const val LargeMillis = 340

    /** What an exit gets when its entrance took [StandardMillis]. */
    const val ExitMillis = 160

    /** Settling with a trace of overshoot: a badge popping, a cover springing back. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Settling with no overshoot, for anything tracking a pointer or a press. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QuickMillis, easing = Standard)

    fun <T> standard(): FiniteAnimationSpec<T> = tween(StandardMillis, easing = Standard)

    fun <T> large(): FiniteAnimationSpec<T> = tween(LargeMillis, easing = Standard)

    fun <T> exit(): FiniteAnimationSpec<T> = tween(ExitMillis, easing = Exit)
}

/** What anything the app is finished with does: leaves in place, quickly. */
fun softFadeOut(durationMillis: Int = LukiaMotion.ExitMillis): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis, easing = LukiaMotion.Exit))

/**
 * Cross-dissolve for swapping one state of a screen for another - loading for
 * content, an empty library for a full one.
 *
 * The slight scale is what keeps it from reading as a cut. Callers pair this with
 * `using null` so that `AnimatedContent` does not also animate the container's
 * bounds and slide the content sideways mid-transition.
 */
fun stateSwapIn(durationMillis: Int = LukiaMotion.StandardMillis): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis, easing = LukiaMotion.Standard)) +
        scaleIn(
            animationSpec = tween(durationMillis, easing = LukiaMotion.Standard),
            initialScale = 0.97f,
        )

/**
 * A sheet or bar entering from the top edge of its container, used by the
 * reader's floating chrome.
 */
fun topChromeIn(): EnterTransition = slideInVertically(
    animationSpec = tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard),
    initialOffsetY = { -it },
) + fadeIn(animationSpec = tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard))

fun topChromeOut(): ExitTransition = slideOutVertically(
    animationSpec = tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit),
    targetOffsetY = { -it },
) + fadeOut(animationSpec = tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit))
