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

/**
 * How far the screen being left behind travels while the new one comes in over
 * it. A fraction rather than the full width: the two screens moving at different
 * rates is the parallax that makes one read as sitting on top of the other, which
 * is what a push looks like on iOS.
 */
private const val ParallaxFraction = 0.3f

/** The outgoing screen also dims, so the incoming one is clearly in front. */
private const val ParallaxDimAlpha = 0.7f

/** The new screen slides in from the trailing edge, at full width. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        initialOffsetX = { width -> width },
    )

/** The screen underneath is pushed a little way aside and dimmed. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        targetOffsetX = { width -> -(width * ParallaxFraction).toInt() },
    ) + fadeOut(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        targetAlpha = ParallaxDimAlpha,
    )

/** Coming back, the screen underneath returns from where it was pushed to. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        initialOffsetX = { width -> -(width * ParallaxFraction).toInt() },
    ) + fadeIn(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        initialAlpha = ParallaxDimAlpha,
    )

/** And the screen being left slides back off the trailing edge. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        targetOffsetX = { width -> width },
    )

/**
 * Whether this move is one tab replacing another rather than a screen being
 * pushed onto or popped off one.
 *
 * A push says "this came from over there, and back is the way home". Tabs are
 * peers - none of them is behind any other - so sliding between them would be a
 * claim about where they sit that the bar underneath flatly contradicts. Every
 * tab bar there has ever been cross-fades instead, and so does this one.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(): Boolean =
    MobileTab.forRoute(initialState.destination.route) != null &&
        MobileTab.forRoute(targetState.destination.route) != null

internal fun tabEnter(): EnterTransition =
    fadeIn(animationSpec = tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard))

internal fun tabExit(): ExitTransition =
    fadeOut(animationSpec = tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit))
