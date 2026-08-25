package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.lukia.shared.presentation.components.ButtonVariant
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space

/**
 * The translation controls as named rows at the top of the reader's panel, which
 * is where a phone keeps them.
 *
 * The same three things the toolbar offers a pointed-at window - see
 * [TranslationControls] - but written out. A tooltip is what tells a reader with
 * a mouse which glyph is which, and a phone has no hover to show one, so the
 * controls that were three unlabelled marks squeezed into a thumb-sized bar
 * become a button and two switches that say what they are.
 *
 * At the top rather than after the type settings: this is the one group here
 * that changes what is written on the page rather than how it is set, and it is
 * what somebody opens the panel mid-chapter for.
 */
@Composable
internal fun ReaderTranslationSettings(
    translation: ReaderTranslationUi,
    modifier: Modifier = Modifier,
) {
    if (!translation.inPanel) return

    val inset = LocalLukiaDimens.current.panelPadding

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = "Translation",
            modifier = Modifier.padding(start = inset, end = inset, bottom = Space.sm),
        )

        // Not there at all until the reader has asked for them in Settings.
        // These two spend a screenful at a time - see BulkTranslation - and a
        // control that can do that is one somebody presses to find out what it
        // does. Off is off wherever it is reached from.
        if (translation.offered.onScreen) {
            LukiaButton(
                label = "Translate this page",
                onClick = translation.onTranslateOnScreen,
                variant = ButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = inset),
            )
        }

        if (translation.offered.asYouRead) {
            PanelSwitchRow(
                label = "Translate as you read",
                checked = translation.onScreen.continuous,
                onChange = translation.onContinuousChange,
                inset = inset,
            )
        }

        // Nothing to put away until something has been translated, so the row is
        // there from the start - where the reader will look for it - but does
        // nothing until there is something for it to do.
        PanelSwitchRow(
            label = "Show translations",
            checked = translation.shown.visible,
            onChange = translation.onVisibleChange,
            inset = inset,
            enabled = translation.shown.any,
        )

        LukiaDivider(
            inset = inset,
            endInset = inset,
            modifier = Modifier.padding(vertical = Space.md),
        )
    }
}

/**
 * A standing choice, named on the left and set on the right.
 *
 * A switch rather than the two-segment control the settings above it use: these
 * two are on-or-off rather than one of several, and a switch is what says that
 * without the reader having to read both segments to find out which is which.
 */
@Composable
private fun PanelSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    inset: Dp,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
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

        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
