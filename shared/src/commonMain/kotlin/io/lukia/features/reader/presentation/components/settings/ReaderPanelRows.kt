package io.lukia.features.reader.presentation.components.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.lukia.resources.Res
import io.lukia.resources.reader_choose
import io.lukia.resources.reader_close_the_list
import io.lukia.resources.reader_setting_and_value
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.controls.toggleRow
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

// The rows a reader panel is built from, as opposed to the labelled controls in
// ReaderSettingControls: a setting that is on or off, and one whose choices are
// too many to sit in a row.

/**
 * A standing choice, named on the left and set on the right.
 *
 * A switch rather than the two-segment control the settings above it use: these
 * are on-or-off rather than one of several, and a switch is what says that
 * without the reader having to read both segments to find out which is which.
 */
@Composable
internal fun PanelSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    inset: Dp,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        // The row is the control, not the switch inside it - see [toggleRow].
        modifier = Modifier
            .fillMaxWidth()
            .toggleRow(
                label = label,
                checked = checked,
                onChange = onChange,
                enabled = enabled,
            )
            .padding(horizontal = inset, vertical = Space.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else LukiaAlpha.Disabled,
            ),
            modifier = Modifier.weight(1f, fill = false),
        )

        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A setting whose choices are too many for a row of segments: the one in force,
 * and the list of the rest a press away.
 *
 * The list opens underneath rather than in a menu of its own. A menu here would
 * be a popup over a popup, and on a phone a scrolling list inside a panel that
 * is already scrolling; opened in place, it is simply more of the panel, and the
 * choice that was made stays visible on the row above it.
 */
@Composable
internal fun PanelDisclosureRow(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    inset: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val metrics = LocalLukiaMetrics.current
    val shape = MaterialTheme.shapes.small

    // Read before the modifier chain: neither `semantics` nor `clickable` is
    // a composable scope.
    val spoken = stringResource(Res.string.reader_setting_and_value, label, value)
    val press = stringResource(
        if (expanded) Res.string.reader_close_the_list else Res.string.reader_choose,
    )

    // On the app's own curve rather than the default spring, which settles at a
    // rate nothing else here uses: the mark and the list under it are one
    // gesture, and two clocks running at once is most of what made opening the
    // list feel unresolved.
    val turn by animateFloatAsState(
        targetValue = if (expanded) HalfTurn else 0f,
        animationSpec = LukiaMotion.standard(),
        label = "panelDisclosure",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = inset - Space.xs, vertical = Space.xxs)
            // As tall as any other control the same thumb is aiming at. The
            // text in it is one line, and a row sized by its text is a 32dp
            // target on a device where 44dp is the floor.
            .heightIn(min = metrics.controlHeight)
            .clip(shape)
            .pressable(
                interactionSource = interactionSource,
                shape = shape,
                onClickLabel = press,
                onClick = onClick,
            )
            .padding(horizontal = Space.xs, vertical = Space.sm)
            .semantics { contentDescription = spoken },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        DisclosureChevron(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = Space.xs)
                .size(metrics.iconSmall * 0.6f)
                .rotate(turn),
        )
    }
}

/** What the chevron turns through when the list under it opens. */
private const val HalfTurn = 180f
