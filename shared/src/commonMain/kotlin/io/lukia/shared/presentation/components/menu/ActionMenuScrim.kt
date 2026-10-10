package io.lukia.shared.presentation.components.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.resources.Res
import io.lukia.resources.action_dismiss
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import org.jetbrains.compose.resources.stringResource

/**
 * Tap-anywhere-to-dismiss backdrop for an open [AnchoredMenu], which is a
 * non-focusable popup and so cannot catch the press itself. Also what dims the
 * screen behind an [OverlayPanel].
 *
 * Uses no ripple, so it reads as a surface rather than as a button.
 *
 * [label] is what a screen reader announces it as. A backdrop is a real way out
 * - often the only one a menu offers - so it is named rather than hidden, and
 * named for what pressing it does: without the label it is a full-screen node
 * that can be activated and cannot be described.
 */
@Composable
fun ActionMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.action_dismiss),
) {
    val interactionSource = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(LukiaMotion.StandardMillis, easing = LukiaMotion.Standard)),
        exit = fadeOut(tween(LukiaMotion.ExitMillis, easing = LukiaMotion.Exit)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = LukiaAlpha.Scrim))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = label,
                    onClick = onDismiss,
                ),
        )
    }
}
