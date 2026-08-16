package io.lukia.shared.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.lukia.shared.presentation.theme.MinTouchTarget

/**
 * A circular glass button carrying a single drawn glyph, for chrome that floats
 * over scrolling content - the back control on the detail and settings screens.
 *
 * Presses scale rather than ripple. A ripple spreading across a translucent
 * surface reads as a smudge on the glass rather than as feedback.
 *
 * [label] is set as a content description as well as the click label: these
 * buttons draw a glyph and no text, so without the description a screen reader
 * has nothing to announce them by - the click label names the action, not the
 * control it belongs to.
 */
@Composable
fun GlassChromeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    GlassSurface(
        shape = CircleShape,
        modifier = modifier.scaleOnPress(interactionSource)
    ) {
        Box(
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = label,
                    onClick = onClick
                )
                .size(MinTouchTarget)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            glyph()
        }
    }
}
