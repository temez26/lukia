package io.lukia.features.library.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import io.lukia.features.library.presentation.LibraryShelfUi
import io.lukia.shared.presentation.components.AlertGlyph
import io.lukia.shared.presentation.components.AnchoredMenu
import io.lukia.shared.presentation.components.CheckGlyph
import io.lukia.shared.presentation.components.CopyGlyph
import io.lukia.shared.presentation.components.FolderGlyph
import io.lukia.shared.presentation.components.GearGlyph
import io.lukia.shared.presentation.components.MenuRow
import io.lukia.shared.presentation.components.MenuSeparator
import io.lukia.shared.presentation.components.PlusGlyph

// Everything the library can do that is not about one book, and the rows that
// offer it. Apart from the bar that opens it - see [LibraryNavBar] - because the
// bar is a row of two controls and this is the whole of the app's shelf-keeping.

/**
 * The ways a book gets into the library.
 *
 * Shared, because they are offered from two places - the bar's menu and the
 * import card in the grid - and a reader who learns them in one should find the
 * same two, worded the same way, in the other.
 *
 * Each closes the menu before acting, so it is never left open behind the
 * platform file dialog the action opens.
 */
@Composable
internal fun ColumnScope.ImportMenuRows(
    onDismissRequest: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
) {
    MenuRow(
        label = "Import Books",
        icon = { tint, glyph -> PlusGlyph(tint = tint, modifier = glyph) },
        onClick = {
            onDismissRequest()
            onImport()
        },
    )

    MenuSeparator()

    MenuRow(
        label = "Add Folder",
        icon = { tint, glyph -> FolderGlyph(tint = tint, modifier = glyph) },
        onClick = {
            onDismissRequest()
            onImportFolder()
        },
    )
}

/** The bar's menu. Opened by the actions button in [LibraryNavBar], and nowhere else. */
@Composable
internal fun LibraryMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    shelf: LibraryShelfUi,
    onSettings: () -> Unit,
) {
    AnchoredMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        ImportMenuRows(
            onDismissRequest = onDismissRequest,
            onImport = onImport,
            onImportFolder = onImportFolder,
        )

        MenuSeparator()

        // Also here, and not only on a book: reaching a shelf-wide mode by
        // holding down one particular book is a way in nobody finds by looking.
        MenuRow(
            label = "Select Books",
            icon = { tint, glyph -> CheckGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                shelf.onStart()
            },
        )

        MenuSeparator()

        UpkeepMenuRows(shelf = shelf, onDismissRequest = onDismissRequest)

        MenuRow(
            label = "Settings",
            icon = { tint, glyph -> GearGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                onSettings()
            },
        )
    }
}

/**
 * What is worth tidying on the shelf, offered only where there is any.
 *
 * The count is on the row rather than behind it, so the menu answers "is my
 * library in a mess" without anything being pressed - and so a row that would
 * find nothing is never there to be pressed and do nothing. Both lead to the
 * books themselves, ticked and waiting to be looked at, rather than to a report.
 */
@Composable
private fun UpkeepMenuRows(
    shelf: LibraryShelfUi,
    onDismissRequest: () -> Unit,
) {
    val upkeep = shelf.upkeep

    if (upkeep.missing.isNotEmpty()) {
        MenuRow(
            label = "Missing Files (${upkeep.missing.size})",
            icon = { tint, glyph -> AlertGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                shelf.onSelectMissing()
            },
        )

        MenuSeparator()
    }

    if (upkeep.duplicates.isNotEmpty()) {
        MenuRow(
            label = "Duplicates (${upkeep.duplicates.size})",
            icon = { tint, glyph -> CopyGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                shelf.onSelectDuplicates()
            },
        )

        MenuSeparator()
    }
}
