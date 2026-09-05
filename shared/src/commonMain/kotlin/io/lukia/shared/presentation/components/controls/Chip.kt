package io.lukia.shared.presentation.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.resources.Res
import io.lukia.resources.state_off
import io.lukia.resources.state_on
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Inset from the chip's edge to its label. */
private val ChipPadding: Dp = 10.dp

/**
 * One filter, as a small standing control.
 *
 * A chip rather than another segmented control: these are independent of one
 * another - a format and a category can both be set - and a segmented track says
 * the opposite, that exactly one of its segments is true at a time. They also
 * come and go with the library, and a row of chips can grow one without every
 * other one getting narrower.
 *
 * A chip with a [chevron] opens a menu of values and shows the chosen one in
 * place of its own name; one without is a switch that is either on or off. Both
 * read as set the same way - filled in the accent colour - so a reader can see
 * what is narrowing their library without reading any of the labels.
 *
 * Which is exactly why it is [toggleable] rather than clickable. Being set is
 * carried by a fill measuring 1.1:1 against the chip beside it and by nothing
 * else: a reader who cannot separate those two, or who is hearing the row rather
 * than looking at it, was told only that there was a button called "Fiction" -
 * never whether the shelf in front of them was filtered by it.
 */
@Composable
fun LukiaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Marks a chip that opens a menu beneath itself. */
    chevron: Boolean = false,
    icon: (@Composable (Color, Modifier) -> Unit)? = null,
) {
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small

    // Animated so that setting a filter is the chip filling in rather than a
    // cut, which is what the rest of the app's controls do under a press.
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = LukiaMotion.quick(),
        label = "chipContent",
    )

    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Selected)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = LukiaMotion.quick(),
        label = "chipContainer",
    )

    // Read before the modifier chain: `semantics` is not a composable scope.
    val state = stringResource(if (selected) Res.string.state_on else Res.string.state_off)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // A chip is drawn at 38dp, under both platforms' floor, and a row
            // of them is the one place in the library where the targets sit
            // side by side with nothing between them.
            .minimumInteractiveComponentSize()
            // A floor rather than a fixed height - see `LukiaButton`.
            .heightIn(min = metrics.controlHeightSmall)
            .clip(shape)
            .background(container, shape)
            .hoverHighlight(interactionSource, shape)
            .focusRing(interactionSource, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .toggleable(
                value = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = ChipPadding)
            .semantics { stateDescription = state },
    ) {
        if (icon != null) {
            icon(content, Modifier.size(metrics.iconSmall))
            Spacer(modifier = Modifier.width(Space.xs))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (chevron) {
            Spacer(modifier = Modifier.width(Space.xs))

            DisclosureChevron(
                tint = content,
                modifier = Modifier.size(metrics.iconSmall),
            )
        }
    }
}
