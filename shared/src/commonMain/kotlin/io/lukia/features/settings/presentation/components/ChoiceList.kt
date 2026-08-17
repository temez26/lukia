package io.lukia.features.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.CheckGlyph
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.theme.Space

/** The tick's box, sized so every row's text starts at the same place. */
private val CheckSize: Dp = 18.dp

private val RowPadding: Dp = 12.dp

/**
 * One choice from a list too long to be a row of segments.
 *
 * A list rather than a menu because that is what this is for - seventeen
 * languages chosen once, in a screen that already scrolls. A popup would hide
 * the setting behind a tap and put a scrolling list inside a scrolling page.
 *
 * Each row carries radio-button semantics, so assistive technology reads the
 * group as a single choice rather than as a column of unrelated buttons.
 */
@Composable
internal fun <T> ChoiceList(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** A second line under the label, for a choice whose name is not the whole story. */
    detail: (T) -> String? = { null }
) {
    Column(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                // Inset past the tick so the rules line up under the labels
                // rather than cutting across the whole card.
                LukiaDivider(
                    modifier = Modifier.padding(start = CheckSize + Space.md)
                )
            }

            ChoiceRow(
                label = label(option),
                detail = detail(option),
                selected = option == selected,
                onSelect = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = RowPadding)
    ) {
        // The tick's space is held whether or not it is drawn, so choosing a
        // different row moves nothing but the tick.
        if (selected) {
            CheckGlyph(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CheckSize)
            )
        } else {
            Spacer(modifier = Modifier.size(CheckSize))
        }

        Spacer(modifier = Modifier.width(Space.md))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
