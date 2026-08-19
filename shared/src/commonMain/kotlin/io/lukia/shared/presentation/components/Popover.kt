package io.lukia.shared.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import io.lukia.shared.presentation.theme.LukiaMotion

/**
 * How far a popover is scaled down at the start of its entrance. Small enough to
 * read as growing out of its anchor, large enough not to read as a zoom.
 */
private const val PopoverInitialScale = 0.92f

/**
 * How open a popover is, from 0 (gone) to 1 (fully out).
 *
 * Every panel in the app that hangs off a control - the book actions menu, the
 * reader's settings and contents panels, the library's nav bar menu - animates
 * this way rather than through `AnimatedVisibility`. The reason is the same in each
 * case: `AnimatedVisibility` measures its child at zero size until the entrance
 * begins. In a `Popup` that means the platform window is created 0x0 and, on
 * Android, never recovers; in-window it means the panel flickers through a
 * collapsed layout on the way in. Driving a `graphicsLayer` instead keeps the
 * content at full size throughout and only ever changes how it is drawn.
 *
 * Callers hold the panel mounted until this reaches 0 on the way out:
 *
 * ```
 * val progress = rememberPopoverProgress(expanded, "myPanel")
 * if (!expanded && progress == 0f) return
 * ```
 */
@Composable
fun rememberPopoverProgress(expanded: Boolean, label: String): Float {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) {
            tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard)
        } else {
            tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit)
        },
        label = label,
    )

    return progress
}

/**
 * Draws a popover at [progress], growing out of [transformOrigin] - which should
 * be the corner it hangs from, so the panel appears to come from the control that
 * opened it rather than from its own middle.
 */
fun Modifier.popoverTransition(
    progress: Float,
    transformOrigin: TransformOrigin,
): Modifier = graphicsLayer {
    alpha = progress
    val scale = PopoverInitialScale + (1f - PopoverInitialScale) * progress
    scaleX = scale
    scaleY = scale
    this.transformOrigin = transformOrigin
}

/**
 * Closes a popover on Back (Android) or Escape (desktop).
 *
 * These panels are either non-focusable popups or plain in-window content, so
 * none of them route either key on their own.
 *
 * Compose's replacement for this - `NavigationEventHandler` - ships in a further
 * artifact and is shaped around predictive-back progress, which a panel that only
 * ever needs "close" has no use for. Suppressed here, in the one place that uses
 * it, so the deprecation stays visible everywhere else.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DismissOnBack(
    enabled: Boolean,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onDismiss)
}
