package io.lukia.features.stats.presentation.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MotionStyle
import io.lukia.shared.presentation.theme.standard

/**
 * How anything on a chart moves to where the reader just put it: a spring when
 * the app may flourish, and the plain standard tween when the reader asked for
 * motion to be got through - see [MotionStyle.flourishes].
 */
internal fun <T> MotionStyle.settle(): FiniteAnimationSpec<T> =
    if (flourishes) LukiaMotion.gentle() else standard()

/**
 * Selects a day with a light tick under the finger, but only when it is a new
 * day: tapping the one already selected changes nothing, so it says nothing.
 */
@Composable
internal fun <T> daySelector(selected: T, onSelect: (T) -> Unit): (T) -> Unit {
    val haptics = LocalHapticFeedback.current

    return { day ->
        if (day != selected) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelect(day)
        }
    }
}
