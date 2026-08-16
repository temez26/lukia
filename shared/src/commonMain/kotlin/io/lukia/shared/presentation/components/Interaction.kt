package io.lukia.shared.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaMotion

/**
 * Apple's press feedback: the control shrinks slightly under the finger and
 * settles back when it is let go.
 *
 * Used on the drawn chrome - the library's add button, the reader's toolbar - in
 * place of a Material ripple, which is the wrong idiom for a translucent circular
 * control and looks it.
 *
 * The caller passes the same [interactionSource] to its `clickable`, which is
 * what connects the press to the scale.
 */
@Composable
fun Modifier.scaleOnPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.9f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (interactionSource.collectIsPressedAsState().value) pressedScale else 1f,
        animationSpec = LukiaMotion.gentle(),
        label = "pressScale"
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * How far this element is hovered, from 0 to 1, animated.
 *
 * Desktop-only in practice: touch platforms never report hover, so anything
 * driven by this stays at rest on phones and tablets without needing a platform
 * check of its own.
 *
 * Returned as a value rather than applied as a modifier because callers use it
 * for more than one thing at once - a lift, a shadow that grows with it, a glyph
 * that warms to the accent colour - and all of them should move together.
 */
@Composable
fun rememberHoverFraction(interactionSource: MutableInteractionSource): Float {
    val hover by animateFloatAsState(
        targetValue = if (interactionSource.collectIsHoveredAsState().value) 1f else 0f,
        animationSpec = LukiaMotion.gentle(),
        label = "hoverFraction"
    )

    return hover
}

/** Lifts and slightly enlarges an element by [fraction], for use with [rememberHoverFraction]. */
fun Modifier.hoverLift(
    fraction: Float,
    lift: Dp = 4.dp,
    hoveredScale: Float = 1.03f
): Modifier = graphicsLayer {
    val scale = 1f + (hoveredScale - 1f) * fraction
    scaleX = scale
    scaleY = scale
    translationY = -lift.toPx() * fraction
}
