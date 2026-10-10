package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.Space

/** The tick's box on a screen, sized so every row's text starts at the same place. */
private val CheckSize: Dp = 18.dp

private val RowPadding: Dp = 12.dp

/**
 * How much room a list of choices is given.
 *
 * [Comfortable] is a settings screen: a page of its own, scrolled to on purpose,
 * where a choice is worth a full row and a line of explanation under it.
 *
 * [Compact] is a panel over something else - the reader's, capped at a few
 * hundred points with a dozen other settings under this one, where seventeen
 * languages are twice the panel's height and push everything below off the
 * bottom. So the rows shrink to a menu row's height, the second line comes up
 * beside the first, and the rules between them go: a list this long reads as one
 * without them, and seventeen hairlines in a small panel are texture rather than
 * structure.
 */
enum class ChoiceDensity {
    Comfortable,
    Compact,
}

/**
 * One choice from a list too long to be a row of segments.
 *
 * A list rather than a menu, this being for seventeen languages chosen once in a
 * screen that already scrolls: a popup would hide the setting behind a tap and
 * put a scrolling list inside a scrolling page.
 *
 * Each row carries radio-button semantics, so assistive technology reads the
 * group as one choice rather than a column of unrelated buttons.
 */
@Composable
fun <T> ChoiceList(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** A second line under the label, for a choice whose name is not the whole story. */
    detail: @Composable (T) -> String? = { null },
    /** How much room the rows are given - see [ChoiceDensity]. */
    density: ChoiceDensity = ChoiceDensity.Comfortable,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            if (index > 0 && density == ChoiceDensity.Comfortable) {
                // Inset past the tick so the rules line up under the labels
                // rather than cutting across the whole card.
                LukiaDivider(
                    modifier = Modifier.padding(start = CheckSize + Space.md),
                )
            }

            ChoiceRow(
                label = label(option),
                detail = detail(option),
                selected = option == selected,
                density = density,
                onSelect = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    density: ChoiceDensity,
    onSelect: () -> Unit,
) {
    val compact = density == ChoiceDensity.Compact
    val metrics = LocalLukiaMetrics.current
    val checkSize = if (compact) metrics.iconSmall else CheckSize

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .pointerHoverIcon(PointerIcon.Hand)
            // A compact row is sized by what is pointing at it rather than by its
            // own single line of text, which on a phone is a 24dp target.
            .then(if (compact) Modifier.heightIn(min = metrics.rowHeight) else Modifier)
            .padding(vertical = if (compact) Space.xs else RowPadding),
    ) {
        // The tick's space is held whether or not it is drawn, so choosing a
        // different row moves nothing but the tick.
        if (selected) {
            CheckGlyph(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(checkSize),
            )
        } else {
            Spacer(modifier = Modifier.size(checkSize))
        }

        Spacer(modifier = Modifier.width(Space.md))

        if (compact) {
            CompactLabels(label = label, detail = detail, selected = selected)
        } else {
            StackedLabels(label = label, detail = detail, selected = selected)
        }
    }
}

/** The name, and under it whatever the name does not say. */
@Composable
private fun StackedLabels(label: String, detail: String?, selected: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The same two, on one line: the name at the start of the row and the detail at
 * the end of it, where a value sits on every other row of a panel.
 *
 * Halving the height of a row is most of what makes a list of seventeen fit in a
 * panel at all, and the detail is a translation of the name rather than a
 * qualification of it - so beside it reads as well as under it.
 */
@Composable
private fun RowScope.CompactLabels(label: String, detail: String?, selected: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )

    if (detail != null) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}
