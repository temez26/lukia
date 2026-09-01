package io.lukia.shared.presentation.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment

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
 * A list opening underneath the row that disclosed it - see `PanelDisclosureRow`.
 *
 * Height only, and downwards. Left to itself `AnimatedVisibility` grows a child
 * from its bottom end, which uncovers the list from its top left corner and
 * reads as a list being dragged diagonally out of the row above it. Nothing
 * moves sideways when a panel gets taller, so nothing here should draw as
 * though it does.
 *
 * [LukiaMotion.LargeMillis] rather than the standard duration, because what
 * opens here is not menu-sized. Seventeen languages are some seven hundred
 * points of rows, and seven hundred points in [LukiaMotion.StandardMillis] is
 * three thousand a second - fast enough that the eye reads it as the panel
 * jumping to a new height rather than as a list opening. This is what that
 * duration is for.
 *
 * The fade is shorter than the growth on purpose. It keeps a half-height row
 * from being briefly readable in a space too short to hold it, and finishing
 * first means the rows are solid while the space around them is still settling -
 * one movement to watch rather than two ending together.
 */
fun disclosureIn(): EnterTransition = expandVertically(
    animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
    expandFrom = Alignment.Top,
) + fadeIn(animationSpec = tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard))

/**
 * Closing again, at the pace anything leaving gets.
 *
 * Not the [LukiaMotion.LargeMillis] its entrance took: a list is collapsed by
 * somebody who has just chosen from it and wants the panel back, and the height
 * it gives up is height they have stopped looking at.
 */
fun disclosureOut(): ExitTransition = shrinkVertically(
    animationSpec = tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit),
    shrinkTowards = Alignment.Top,
) + softFadeOut()

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
