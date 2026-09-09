package io.lukia.features.library.presentation.components.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.domain.LibraryLayout
import io.lukia.features.library.presentation.model.LibraryFacets
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.model.OpenMenu
import io.lukia.features.library.presentation.model.displayName
import io.lukia.features.library.presentation.sessions.LibraryImportUi
import io.lukia.resources.Res
import io.lukia.resources.library_import_stop
import io.lukia.resources.library_importing
import io.lukia.resources.library_importing_count
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/** Width the shelves take when they are not filling the screen, with every segment. */
private val ShelfControlWidth: Dp = 320.dp

/** Segments [ShelfControlWidth] is sized for; fewer take proportionally less. */
private const val MaxShelfSegments = 4

/**
 * What the library is narrowed to - inside the grid, so it scrolls away rather
 * than taking a permanent row.
 *
 * Where a large title would go, and deliberately not one: a screen that shows
 * books does not need a word telling the reader they are books, and the row is
 * worth more as controls.
 *
 * Two rows for two questions. Shelves are where the reader is - part way
 * through, not started, finished - exactly one at a time, which is what a
 * segmented control says. The filters under them are properties of the books,
 * any number at once, which is what a row of chips says. Both arrive reduced to
 * what this library can offer, and an empty-handed control is left off.
 *
 * How many books there are is not here: it is in the search field, which is the
 * one place the number answers a question the reader is actually asking.
 */
@Composable
internal fun LibraryHeader(
    query: LibraryQuery,
    facets: LibraryFacets,
    onQueryChange: (LibraryQuery) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
    importing: LibraryImportUi,
    reveal: LaunchReveal,
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = !isWideWindow

    Column(modifier = modifier.padding(bottom = Space.md)) {
        if (facets.shelves.size > 1) {
            SegmentedControl(
                options = facets.shelves,
                selected = query.shelf,
                label = { it.displayName() },
                onSelect = { onQueryChange(query.copy(shelf = it)) },
                modifier = Modifier
                    .then(
                        // Filling is what a phone wants and what a desktop window
                        // must not do: stretched across one, the control is mostly
                        // the gaps between its labels.
                        if (compact) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.width(ShelfControlWidth * facets.shelves.size / MaxShelfSegments)
                        },
                    )
                    .launchReveal(reveal, index = 0),
            )

            Spacer(modifier = Modifier.height(Space.md))
        }

        LibraryFilterBar(
            layout = layout,
            onLayoutChange = onLayoutChange,
            query = query,
            facets = facets,
            onQueryChange = onQueryChange,
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
            modifier = Modifier.launchReveal(reveal, index = 1),
        )

        ImportingNotice(
            importing = importing,
            modifier = Modifier.launchReveal(reveal, index = 2),
        )
    }
}

/**
 * That an import is running, for a library that already has books in it.
 *
 * It reports itself here rather than replacing the grid, so the books already on
 * screen stay readable while the rest arrive. Present only while it is true -
 * there is nothing to say about an import that is not happening - and expanded
 * into place rather than appearing, so the grid is nudged down rather than jumping.
 *
 * It also carries the way out. A folder can hold thousands of books, each one
 * opened and its cover rendered, and without this the only way to stop is to kill
 * the app - which stops at the same book and says nothing about how far it got.
 */
@Composable
private fun ImportingNotice(
    importing: LibraryImportUi,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = importing.isImporting,
        enter = expandVertically(LukiaMotion.standard()) + fadeIn(LukiaMotion.standard()),
        exit = shrinkVertically(LukiaMotion.exit()) + fadeOut(LukiaMotion.exit()),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Space.md),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )

            Spacer(modifier = Modifier.width(Space.md))

            Text(
                // A folder can hold hundreds of books, each one opened and its
                // cover rendered. Without the count, a long import is a spinner
                // that gives no way to tell working from stuck.
                text = importing.progress
                    ?.let {
                        stringResource(Res.string.library_importing_count, it.completed, it.total)
                    }
                    ?: stringResource(Res.string.library_importing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LukiaButton(
                label = stringResource(Res.string.library_import_stop),
                onClick = importing.onCancel,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
            )
        }
    }
}
