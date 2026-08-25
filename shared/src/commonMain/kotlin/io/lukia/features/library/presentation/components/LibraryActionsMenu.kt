package io.lukia.features.library.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.lukia.features.library.presentation.LibraryShelfUi
import io.lukia.shared.presentation.components.AlertGlyph
import io.lukia.shared.presentation.components.AnchoredMenu
import io.lukia.shared.presentation.components.CheckGlyph
import io.lukia.shared.presentation.components.CopyGlyph
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.FolderGlyph
import io.lukia.shared.presentation.components.GearGlyph
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.MenuRow
import io.lukia.shared.presentation.components.MenuSeparator
import io.lukia.shared.presentation.components.PlusGlyph
import io.lukia.shared.presentation.theme.LukiaMotion

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

/**
 * Everything the library can do that is not about one book.
 *
 * An ellipsis rather than a plus: this menu holds more than ways to get books in,
 * and a plus would promise only one of them. It is also what Apple's own apps put
 * in this corner when a screen's actions do not reduce to a single verb.
 *
 * The glyph fades into a close mark while the menu is open, so the control says
 * what pressing it again would do.
 */
@Composable
internal fun MenuButton(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val openness by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = LukiaMotion.standard(),
        label = "menuButtonOpenness",
    )

    LukiaIconButton(
        label = if (expanded) "Close menu" else "Library actions",
        onClick = onClick,
        variant = IconButtonVariant.Filled,
    ) { tint, glyph ->
        // The two glyphs cross-fade in place, and the close mark turns in as it
        // arrives, so the swap reads as one control changing rather than as two
        // controls trading places.
        Box(contentAlignment = Alignment.Center, modifier = glyph) {
            EllipsisGlyph(
                tint = tint,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - openness },
            )

            PlusGlyph(
                tint = tint,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = openness
                        rotationZ = 45f * openness
                    },
            )
        }
    }
}
