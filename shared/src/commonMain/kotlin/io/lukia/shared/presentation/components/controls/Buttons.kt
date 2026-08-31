package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space

/**
 * What a button is for, which is what decides how loudly it is drawn.
 *
 * Before these existed the app had a Material `Button` on the detail screen, a
 * glass capsule in the error state and a bare tinted `Text` in the reader's
 * contents panel, all doing the same job at three different heights, radii and
 * type sizes.
 */
enum class ButtonVariant {

    /** The one thing a screen is for: opening the book, confirming the change. */
    Primary,

    /** An alternative that is worth offering but not worth leading with. */
    Secondary,

    /** No container at all, for an action tucked in beside something else. */
    Ghost,

    /** Deleting. Carries the error colour so it reads as destructive on sight. */
    Destructive,
}

/** Buttons come in two weights: one that leads a screen, one that trims a panel. */
enum class ButtonSize {
    Regular,
    Compact,
}

/**
 * The inset from a button's edge to its label, by size.
 *
 * Public because a [ButtonVariant.Ghost] button has no container to mark its
 * bounds, so one placed under a block of text sits visibly indented from it. The
 * caller offsets by this to line the two up - which needs the real number rather
 * than a guess at it.
 */
fun buttonInset(size: ButtonSize): Dp = when (size) {
    ButtonSize.Regular -> Space.xl
    ButtonSize.Compact -> Space.lg
}

/**
 * The app's text button.
 *
 * Height, radius, type and every interaction state come from the theme, so a
 * button in the reader and a button on the detail screen are the same button
 * wearing different words.
 *
 * [icon] is handed the resolved content colour and an already-sized modifier, so
 * a caller never picks a glyph size of its own - that is what left the app with
 * glyphs at 13, 14, 16, 17, 18 and 20dp.
 *
 * The role is declared rather than left to `clickable`, which sets none. A
 * control drawn from scratch has to say what it is: Material's own `Button`
 * carries this for free, and without it a screen reader announces the app's
 * buttons as text somebody happens to be able to press.
 */
@Composable
fun LukiaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Regular,
    enabled: Boolean = true,
    icon: (@Composable (Color, Modifier) -> Unit)? = null,
) {
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small

    val container = when (variant) {
        ButtonVariant.Primary -> MaterialTheme.colorScheme.primary
        ButtonVariant.Secondary -> MaterialTheme.colorScheme.surfaceVariant
        ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Destructive -> MaterialTheme.colorScheme.error
    }

    val content = when (variant) {
        ButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
        ButtonVariant.Secondary -> MaterialTheme.colorScheme.onSurface
        ButtonVariant.Ghost -> MaterialTheme.colorScheme.primary
        ButtonVariant.Destructive -> MaterialTheme.colorScheme.onError
    }

    val alpha = if (enabled) 1f else LukiaAlpha.Disabled
    val tint = content.copy(alpha = alpha)

    val height = when (size) {
        ButtonSize.Regular -> metrics.controlHeight
        ButtonSize.Compact -> metrics.controlHeightSmall
    }

    val textStyle: TextStyle = when (size) {
        ButtonSize.Regular -> MaterialTheme.typography.titleSmall
        ButtonSize.Compact -> MaterialTheme.typography.labelSmall
    }

    val horizontalPadding = buttonInset(size)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(container.copy(alpha = container.alpha * alpha), shape)
            // A hairline only where there is no fill to give the button an edge.
            .then(
                if (variant == ButtonVariant.Secondary) {
                    Modifier.border(HairlineWidth, MaterialTheme.colorScheme.outlineVariant, shape)
                } else {
                    Modifier
                },
            )
            .hoverHighlight(interactionSource, shape)
            .focusRing(interactionSource, shape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding)
            .semantics { contentDescription = label },
    ) {
        if (icon != null) {
            icon(tint, Modifier.size(metrics.iconSmall))
            Spacer(modifier = Modifier.width(Space.md))
        }

        Text(
            text = label,
            style = textStyle,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * How an icon button is separated from what is behind it.
 *
 * These three used to be four hand-rolled controls - the reader's toolbar button,
 * the library bar's menu button, a book card's hover-revealed trigger and the
 * floating back control - each with its own size, background, press behaviour and
 * (mostly missing) focus treatment.
 */
enum class IconButtonVariant {

    /** A bare glyph on chrome that is already a surface: a toolbar, a panel. */
    Plain,

    /** A filled circle, for a control that has to be found against content. */
    Filled,

    /** Floating over scrolling content, where it needs a surface of its own. */
    Glass,
}

/**
 * The app's icon button: one square, comfortably sized target carrying one drawn
 * glyph.
 *
 * [label] is set as a content description as well as the click label. These
 * buttons draw a glyph and no text, so without the description a screen reader
 * has nothing to announce them by - the click label names the action, not the
 * control it belongs to.
 *
 * Presses scale rather than ripple. A ripple spreading across translucent chrome
 * reads as a smudge on the glass rather than as feedback.
 *
 * The role is declared for the reason it is on [LukiaButton]: nothing about a
 * drawn glyph in a `clickable` box says "button" on its own.
 */
@Composable
fun LukiaIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: IconButtonVariant = IconButtonVariant.Plain,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = Dp.Unspecified,
    glyphSize: Dp = Dp.Unspecified,
    enabled: Boolean = true,
    glyph: @Composable (Color, Modifier) -> Unit,
) {
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }

    val buttonSize = if (size == Dp.Unspecified) metrics.iconButton else size
    val resolvedGlyphSize = if (glyphSize == Dp.Unspecified) metrics.icon else glyphSize
    val contentTint = if (enabled) tint else tint.copy(alpha = LukiaAlpha.Disabled)

    val target: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // Room for a thumb around a mark drawn smaller than one - the
                // ✕ beside a recent search, the actions mark in a cover's
                // corner, both at [LukiaMetrics.iconButtonSmall].
                //
                // Compose already extends the hit area of anything clickable out
                // to the minimum target; what it cannot do is stop two of those
                // extensions overlapping, which is what a 32dp control with 32dp
                // of layout around it produces - a press landing in the gap
                // between two of them belongs to either. This reserves the room,
                // so the target a press is tested against is the reader's own
                // and not shared. Draws nothing, and reserves nothing at all
                // under a pointer: see where the theme provides Material's
                // floor.
                .minimumInteractiveComponentSize()
                .size(buttonSize)
                .clip(CircleShape)
                .then(
                    if (variant == IconButtonVariant.Filled) {
                        // Near-opaque: this variant exists to be findable against
                        // whatever is behind it, and a book cover is behind it as
                        // often as a plain surface is.
                        Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant
                                .copy(alpha = LukiaAlpha.OverArtwork),
                        )
                    } else {
                        Modifier
                    },
                )
                .hoverHighlight(interactionSource, CircleShape)
                .focusRing(interactionSource, CircleShape)
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick,
                )
                .semantics { contentDescription = label },
        ) {
            glyph(contentTint, Modifier.size(resolvedGlyphSize))
        }
    }

    if (variant == IconButtonVariant.Glass) {
        GlassSurface(
            shape = CircleShape,
            modifier = modifier.scaleOnPress(interactionSource),
            content = { target() },
        )
    } else {
        Box(modifier = modifier.scaleOnPress(interactionSource)) {
            target()
        }
    }
}
