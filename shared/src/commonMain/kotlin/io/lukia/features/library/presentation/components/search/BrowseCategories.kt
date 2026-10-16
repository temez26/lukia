package io.lukia.features.library.presentation.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.resources.Res
import io.lukia.resources.search_browse_by_category
import io.lukia.shared.presentation.components.controls.LukiaChip
import io.lukia.shared.presentation.components.surface.SectionLabel
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The subjects the library's own books declare, as a way into each of them.
 *
 * On the search screen rather than home: browsing by subject is looking for
 * something, and home is about the books already on the go - see ADR 0007.
 *
 * Chips that wrap rather than a sideways row: this screen scrolls down, and a
 * second scroll direction inside it would hide most of the subjects.
 *
 * Nothing is selected: pressing one leaves for the library tab, so a chip
 * marked as set would describe a screen the reader is on their way off. The
 * categories arrive already reduced to the ones that divide the library - see
 * `LibraryFacets`.
 */
@Composable
internal fun BrowseCategories(
    categories: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = stringResource(Res.string.search_browse_by_category),
            modifier = Modifier.padding(bottom = Space.md),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            categories.forEach { category ->
                LukiaChip(
                    label = category,
                    selected = false,
                    onClick = { onSelect(category) },
                )
            }
        }
    }
}
