package io.lukia.shared.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.lukia.core.domain.AppMotion

/**
 * How much the app moves, as the parts of the app that move actually read it.
 *
 * [LukiaMotion] stays the vocabulary - the curves and the durations everything
 * is built from - and this turns that vocabulary up or down. Keeping them apart
 * is what stops a preset from being a second set of animations to maintain: a
 * menu opening at [LukiaMotion.StandardMillis] does not know a preset exists,
 * and the four places that shape the app's *big* movements read this.
 *
 * Deliberately not applied to the small things - a press shrinking a button, a
 * badge popping, a hairline appearing. Those are feedback rather than motion:
 * they answer "did that work", they are over before they can be watched, and
 * scaling them would make a preset a change to how responsive the app feels
 * rather than to how much it moves.
 */
@Immutable
data class MotionStyle(

    /**
     * How much longer than standard an opening or a screen change takes.
     *
     * Applied to the clock rather than to any individual duration, so the
     * moments inside an animation keep their proportions to each other - see
     * `LaunchTiming`, whose written-down timeline stays true at any pace.
     */
    val stretch: Float,

    /** How far anything travels as it arrives, against the standard distance. */
    val travel: Float,

    /**
     * Whether the app is allowed its flourishes: the mark's leaves fanning open
     * and the sparkles around them, the overshoot on anything that settles, the
     * stagger as a library arrives, and a screen pushed over another sliding in
     * from the edge rather than cross-fading in place.
     *
     * One flag rather than four because they are one decision - whether motion
     * here is something to be noticed or something to be got through - and a
     * reader who has asked for the sparkles to stop has not asked to keep the
     * parallax.
     */
    val flourishes: Boolean,
)

/**
 * One of [LukiaMotion]'s durations, at the pace the reader asked for.
 *
 * Shared rather than written out at each call site, so every big movement in the
 * app is stretched by the same arithmetic - a screen pushing over another, and
 * the tab bar leaving underneath it, are one movement and have to agree.
 */
internal fun MotionStyle.millis(standard: Int): Int = (standard * stretch).toInt()

/**
 * [LukiaMotion.standard] and [LukiaMotion.exit] at the reader's pace, for the
 * big movements that have to keep step with a screen change.
 */
internal fun <T> MotionStyle.standard(): FiniteAnimationSpec<T> =
    tween(millis(LukiaMotion.StandardMillis), easing = LukiaMotion.Standard)

internal fun <T> MotionStyle.exit(): FiniteAnimationSpec<T> =
    tween(millis(LukiaMotion.ExitMillis), easing = LukiaMotion.Exit)

/**
 * For a screen's own content settling in, once the screen it is on has finished
 * arriving.
 *
 * Two animations at once is one too many. Content moving while the screen
 * carrying it is still sliding is two movements the eye has to separate, and it
 * spends on that content exactly the frames the slide needed. Waiting a
 * screen-sized move keeps the content still while the screen travels, and moves
 * it once the screen has stopped.
 *
 * The wait is written as [LukiaMotion.LargeMillis] rather than as a number of
 * its own, so it stays exactly as long as the push it is waiting for at any
 * pace the reader has chosen - see `ScreenTransitions`, which times that push
 * from the same constant.
 */
internal fun <T> MotionStyle.afterScreenChange(): FiniteAnimationSpec<T> = tween(
    durationMillis = millis(LukiaMotion.StandardMillis),
    delayMillis = millis(LukiaMotion.LargeMillis),
    easing = LukiaMotion.Standard,
)

/**
 * Neither the fastest nor the smallest values, but the ones everything else is
 * measured against: [MotionStyle] multiplies, so standard is where it does
 * nothing at all.
 */
private val StandardMotion = MotionStyle(stretch = 1f, travel = 1f, flourishes = true)

internal fun motionStyleFor(motion: AppMotion): MotionStyle = when (motion) {
    // Shorter as well as smaller. Reduced motion is not slow motion - a reader
    // who does not want to watch the app move wants it over with, not eased.
    AppMotion.Calm -> MotionStyle(stretch = 0.75f, travel = 0.3f, flourishes = false)

    AppMotion.Standard -> StandardMotion

    // Twice the distance rather than twice the time. Length is what makes an
    // animation tiring to sit through; travel is what makes it fun to watch.
    AppMotion.Playful -> MotionStyle(stretch = 1.3f, travel = 2f, flourishes = true)
}

/**
 * A CompositionLocal rather than a parameter, for the reason [LocalGlassStyle]
 * and [LocalLukiaDimens] are: it is one value settled at the root that anything
 * at any depth may need, and threading it down would put a parameter on screens
 * and components that have no opinion about it and only pass it on.
 */
val LocalMotionStyle = staticCompositionLocalOf { StandardMotion }
