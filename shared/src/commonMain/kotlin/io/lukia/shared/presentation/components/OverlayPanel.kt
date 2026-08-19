package io.lukia.shared.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.Space

/** Room at the top of a panel for the controls that float over its content. */
internal val OverlayPanelTopClearance: Dp = 52.dp

/** A panel never fills the window: what it opened from has to stay visible around it. */
private const val MaxHeightFraction = 0.88f

/**
 * Something shown over the screen it was opened from, rather than instead of it.
 *
 * This is the wide-window answer to a pushed screen. On a phone, going to a
 * screen and coming back is right, because a phone has room for one thing at a
 * time. On a desktop it is a poor trade: what you were doing disappears and has
 * to be rebuilt in your head on the way back. Here it stays where it was, dimmed
 * behind, and the panel is a glance rather than a journey.
 *
 * Draws nothing at all once closed and settled, so callers can leave it in the
 * tree unconditionally.
 *
 * [trailing] is for a second control opposite the close button - a book's actions
 * menu, say. Anything that must draw *above* the panel, such as the dimming
 * behind an open menu, belongs in a box around this rather than inside it.
 */
@Composable
fun OverlayPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthCap: Dp = Dp.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val progress = rememberPopoverProgress(visible, label = "overlayPanel")

    if (!visible && progress == 0f) return

    val dimens = LocalLukiaDimens.current
    val resolvedCap = if (widthCap == Dp.Unspecified) dimens.maxDetailWidth else widthCap

    DismissOnBack(enabled = visible, onDismiss = onDismiss)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Dimming what is behind rather than hiding it: that is the context the
        // panel was opened from, and it should stay legible as the place the
        // reader is about to return to.
        ActionMenuScrim(visible = visible, onDismiss = onDismiss)

        // Measured against the window rather than fixed, so the panel stays a
        // panel - inset from every edge - at any size, and scrolls inside itself
        // rather than growing past a short window.
        val panelWidth = minOf(resolvedCap, maxWidth - dimens.screenPadding * 2)

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            // The raised surface, not the window background: in the dark theme the
            // background is true black, and a panel painted in it over a dimmed
            // black screen has no edge at all.
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = LukiaElevation.panel,
            modifier = Modifier
                .align(Alignment.Center)
                // Grows from its own middle rather than a corner: nothing on
                // screen anchors it, so it belongs to the window rather than to a
                // control.
                .popoverTransition(progress, TransformOrigin.Center)
                .width(panelWidth)
                .heightIn(max = maxHeight * MaxHeightFraction),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()

                // A plus turned on its side, which is the close mark the library
                // bar's button already turns into - one shape for "close this"
                // throughout the app.
                LukiaIconButton(
                    label = "Close",
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = dimens.screenPadding, top = Space.lg),
                    variant = IconButtonVariant.Glass,
                ) { tint, glyph ->
                    PlusGlyph(
                        tint = tint,
                        modifier = glyph.graphicsLayer { rotationZ = 45f },
                    )
                }

                if (trailing != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = dimens.screenPadding, top = Space.lg),
                    ) {
                        trailing()
                    }
                }
            }
        }
    }
}
