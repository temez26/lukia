package io.lukia.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MotionStyle
import io.lukia.shared.presentation.theme.millis

/** A screen-sized move, which is what every change registered here is. */
private fun MotionStyle.large(): Int = millis(LukiaMotion.LargeMillis)

/**
 * How far the screen being left behind travels while the new one comes in over
 * it. A fraction rather than the full width: the two screens moving at different
 * rates is the parallax that makes one read as sitting on top of the other, which
 * is what a push looks like on iOS.
 */
private const val ParallaxFraction = 0.3f

/** The outgoing screen also dims, so the incoming one is clearly in front. */
private const val ParallaxDimAlpha = 0.7f

/**
 * The new screen slides in from the trailing edge, at full width.
 *
 * The distance here is not the reader's to scale, unlike everything else in this
 * file. A push travelling a quarter of the width does not read as a smaller
 * push - it reads as the new screen appearing already three quarters arrived.
 * Either a screen comes in from the edge or it does not, which is what
 * [changesInPlace] decides.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(
    motion: MotionStyle,
): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(motion.large(), easing = LukiaMotion.Standard),
        initialOffsetX = { width -> width },
    )

/** The screen underneath is pushed a little way aside and dimmed. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(
    motion: MotionStyle,
): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(motion.large(), easing = LukiaMotion.Standard),
        targetOffsetX = { width -> -(width * ParallaxFraction).toInt() },
    ) + fadeOut(
        animationSpec = tween(motion.large(), easing = LukiaMotion.Standard),
        targetAlpha = ParallaxDimAlpha,
    )

/** Coming back, the screen underneath returns from where it was pushed to. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(
    motion: MotionStyle,
): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(motion.large(), easing = LukiaMotion.Standard),
        initialOffsetX = { width -> -(width * ParallaxFraction).toInt() },
    ) + fadeIn(
        animationSpec = tween(motion.large(), easing = LukiaMotion.Standard),
        initialAlpha = ParallaxDimAlpha,
    )

/** And the screen being left slides back off the trailing edge. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(
    motion: MotionStyle,
): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(motion.large(), easing = LukiaMotion.Standard),
        targetOffsetX = { width -> width },
    )

/**
 * Whether this move should change one screen for another where it stands rather
 * than push one over the other.
 *
 * True between tabs, which are peers - none of them is behind any other, and a
 * push would be a claim about where they sit that the bar underneath flatly
 * contradicts. True for every move on the calmest of the motion presets, where
 * the fact that a book sits on top of the library is not worth a screen-sized
 * movement to say.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.changesInPlace(
    motion: MotionStyle,
): Boolean = !motion.flourishes || betweenTabs()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    MobileTab.forRoute(initialState.destination.route) != null &&
        MobileTab.forRoute(targetState.destination.route) != null

/**
 * How far a tab shifts as it is replaced, as a fraction of the screen's width.
 *
 * Deliberately a nudge rather than a slide - around 30dp on a phone. A push
 * travels the full width because it is a claim that one screen now sits on top
 * of another; this only has to say which way along the bar the reader went, and
 * a movement large enough to be read as travel would say the tabs are stacked,
 * which the bar underneath flatly contradicts.
 */
private const val TabShiftFraction = 0.08f

/**
 * How long the arriving tab waits before it starts.
 *
 * Without it both screens sit at half opacity in the middle of the change and a
 * grid of covers ghosts through another grid of covers. Letting the old one get
 * most of the way out first is what turns two screens overlapping into one
 * screen replacing another.
 *
 * Written as what is left of a screen-sized move once the arrival has taken its
 * own time, rather than as a number of its own, so that changing tabs and
 * pushing a book over them take exactly as long as each other.
 */
private const val TabEnterDelayMillis = LukiaMotion.LargeMillis - LukiaMotion.StandardMillis

/**
 * Which way along the bar this move goes: 1 towards the library, -1 towards
 * home, 0 where either end of the move is not a tab at all.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = MobileTab.forRoute(initialState.destination.route) ?: return 0
    val to = MobileTab.forRoute(targetState.destination.route) ?: return 0

    return (to.ordinal - from.ordinal).coerceIn(-1, 1)
}

/**
 * The arriving tab, entering from the side it sits on in the bar.
 *
 * Tabs are peers, so none of this layers one over another the way a push does.
 * What the bar does assert is an order - home, then search, then the library -
 * and moving with that order is the difference between a screen that was
 * navigated to and one that simply appeared. Going right along the bar carries
 * the content left, as a strip of three would move under a window onto it.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(
    motion: MotionStyle,
): EnterTransition {
    val duration = motion.millis(LukiaMotion.StandardMillis)
    val delay = motion.millis(TabEnterDelayMillis)
    val shift = TabShiftFraction * motion.travel * tabDirection()

    return fadeIn(
        animationSpec = tween(duration, delay, LukiaMotion.Standard),
    ) + slideInHorizontally(
        animationSpec = tween(duration, delay, LukiaMotion.Standard),
        initialOffsetX = { width -> (width * shift).toInt() },
    )
}

/** And the tab being left, going the other way. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(
    motion: MotionStyle,
): ExitTransition {
    val duration = motion.millis(LukiaMotion.ExitMillis)
    val shift = TabShiftFraction * motion.travel * tabDirection()

    return fadeOut(
        animationSpec = tween(duration, easing = LukiaMotion.Exit),
    ) + slideOutHorizontally(
        animationSpec = tween(duration, easing = LukiaMotion.Exit),
        targetOffsetX = { width -> -(width * shift).toInt() },
    )
}
