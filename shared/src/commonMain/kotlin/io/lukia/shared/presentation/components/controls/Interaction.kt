package io.lukia.shared.presentation.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.FocusRingWidth
import io.lukia.shared.presentation.theme.LukiaAlpha
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
    pressedScale: Float = 0.9f,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (interactionSource.collectIsPressedAsState().value) pressedScale else 1f,
        animationSpec = LukiaMotion.gentle(),
        label = "pressScale",
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
        label = "hoverFraction",
    )

    return hover
}

/**
 * Lifts and slightly enlarges an element by [fraction], for use with
 * [rememberHoverFraction].
 *
 * Anything that also wants to shrink under a press should ask for [pressedScale]
 * here rather than chaining [scaleOnPress] as well: two `graphicsLayer`s stacked
 * on one element is two compositing layers where one will do, and the outer of
 * the two ends up scaling the inner one's shadow.
 */
@Composable
fun Modifier.hoverLift(
    interactionSource: MutableInteractionSource,
    fraction: Float,
    lift: Dp = 4.dp,
    hoveredScale: Float = 1.03f,
    pressedScale: Float = 1f,
): Modifier {
    val press by animateFloatAsState(
        targetValue = if (interactionSource.collectIsPressedAsState().value) pressedScale else 1f,
        animationSpec = LukiaMotion.gentle(),
        label = "cardPress",
    )

    return graphicsLayer {
        val scale = (1f + (hoveredScale - 1f) * fraction) * press
        scaleX = scale
        scaleY = scale
        translationY = -lift.toPx() * fraction
    }
}

/**
 * Marks the control the keyboard is on.
 *
 * The app's controls draw no ripple and no pressed state of their own, which left
 * anyone tabbing through a screen with nothing at all to tell them where they
 * were. Focus is the one interaction that has to be visible without a pointer, so
 * every control the keyboard can reach wears this same ring.
 */
@Composable
fun Modifier.focusRing(
    interactionSource: MutableInteractionSource,
    shape: Shape,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()

    return if (focused) {
        border(FocusRingWidth, MaterialTheme.colorScheme.primary, shape)
    } else {
        this
    }
}

/**
 * The wash a row or a control takes under the pointer.
 *
 * A hover state on every clickable thing is what separates a desktop application
 * from a touch layout being shown on a monitor: without it a menu is a list of
 * words with no way to tell which one the pointer is on. Drawn in the content
 * colour so it works on any surface the app has, light or dark.
 */
@Composable
fun Modifier.hoverHighlight(
    interactionSource: MutableInteractionSource,
    shape: Shape,
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val alpha by animateFloatAsState(
        targetValue = when {
            pressed -> LukiaAlpha.Pressed
            hovered -> LukiaAlpha.Hover
            else -> 0f
        },
        animationSpec = LukiaMotion.quick(),
        label = "hoverHighlight",
    )

    return if (alpha == 0f) {
        this
    } else {
        background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), shape)
    }
}

/**
 * Everything a control does to show it is being pointed at, focused or pressed,
 * in the order the app draws them.
 *
 * These three always travelled together and were written out separately at every
 * call site, which is how three of them came to be written out with one missing:
 * the home shelf's "see all" and both of the recent-search rows had a hover wash
 * and a pointer cursor and no focus ring at all, so tabbing through the library
 * landed on them invisibly.
 *
 * Separate from [pressable] because a control that toggles or selects rather than
 * clicks needs the same feedback and its own semantics - see `LukiaChip` and
 * `SegmentedControl`, which wear this and then declare what pressing them means.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
): Modifier =
    hoverHighlight(interactionSource, shape)
        .focusRing(interactionSource, shape)
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)

/**
 * A row, a tile or a piece of drawn chrome that behaves as a button.
 *
 * [pressFeedback] plus the click itself, with the two things the app's own
 * `clickable` calls never leave out: no indication, because every control here
 * draws its own, and [Role.Button], because a `clickable` box announces itself as
 * text somebody happens to be able to press.
 *
 * [onClickLabel] names the action rather than the control - "Open Dune", not
 * "book" - since it is read after the control's own name.
 *
 * Does not clip. What a row clips, and whether it clips at all, belongs with the
 * background it is clipping: a menu row is square on purpose, and a button clips
 * before it paints its fill rather than after.
 */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    onClickLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier =
    pressFeedback(interactionSource, shape, enabled)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
