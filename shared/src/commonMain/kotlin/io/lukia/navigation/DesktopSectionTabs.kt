package io.lukia.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.lukia.features.library.presentation.components.toolbar.libraryNavBarHeight
import io.lukia.shared.presentation.components.controls.pressFeedback
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The two places a window switches between. Search is the library's own field here. */
internal val DesktopSections = listOf(MobileTab.Home, MobileTab.Library)

/**
 * Home and the library, as the names of two pages at the start of the bar.
 *
 * Words rather than a segmented control: the library opens on one of its own
 * (all, reading, unread) just under the bar, and two of them stacked read as one
 * choice in two halves. The page being shown is in the full text colour and the
 * other is muted, as a window's tabs are.
 */
@Composable
internal fun DesktopSectionTabs(
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.selectableGroup(),
    ) {
        DesktopSections.forEach { section ->
            SectionTab(
                label = stringResource(section.label),
                selected = section == selected,
                onClick = { if (section != selected) onSelect(section) },
            )
        }
    }
}

@Composable
private fun SectionTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small

    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .pressFeedback(interactionSource, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

/**
 * Home's bar in a window: the same height and measure as the library's, so the
 * tabs sit in the same place on both pages and switching does not move them.
 */
@Composable
internal fun DesktopHomeBar(
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(libraryNavBarHeight()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = dimens.maxGridWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
        ) {
            DesktopSectionTabs(selected = selected, onSelect = onSelect)
        }
    }
}
