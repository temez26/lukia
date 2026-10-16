package io.lukia.features.library.presentation.components.toolbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.resources.Res
import io.lukia.resources.library_actions
import io.lukia.resources.library_add_folder
import io.lukia.resources.library_close_menu
import io.lukia.resources.library_duplicates
import io.lukia.resources.library_import_books
import io.lukia.resources.library_missing_files
import io.lukia.resources.library_settings
import io.lukia.resources.menu_select_books
import io.lukia.resources.menu_sync_with_server
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.AlertGlyph
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.icons.CloudGlyph
import io.lukia.shared.presentation.components.icons.CopyGlyph
import io.lukia.shared.presentation.components.icons.EllipsisGlyph
import io.lukia.shared.presentation.components.icons.FolderGlyph
import io.lukia.shared.presentation.components.icons.GearGlyph
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.menu.AnchoredMenu
import io.lukia.shared.presentation.components.menu.MenuRow
import io.lukia.shared.presentation.components.menu.MenuSeparator
import io.lukia.shared.presentation.theme.LukiaMotion
import org.jetbrains.compose.resources.stringResource

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
        label = stringResource(Res.string.library_import_books),
        icon = { tint, glyph -> PlusGlyph(tint = tint, modifier = glyph) },
        onClick = {
            onDismissRequest()
            onImport()
        },
    )

    MenuSeparator()

    MenuRow(
        label = stringResource(Res.string.library_add_folder),
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
            label = stringResource(Res.string.menu_select_books),
            icon = { tint, glyph -> CheckGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                shelf.onStart()
            },
        )

        MenuSeparator()

        UpkeepMenuRows(shelf = shelf, onDismissRequest = onDismissRequest)

        MenuRow(
            label = stringResource(Res.string.menu_sync_with_server),
            icon = { tint, glyph -> CloudGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                shelf.onSyncWithServer()
            },
        )

        MenuRow(
            label = stringResource(Res.string.library_settings),
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
            label = stringResource(Res.string.library_missing_files, upkeep.missing.size),
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
            label = stringResource(Res.string.library_duplicates, upkeep.duplicates.size),
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
        label = stringResource(
            if (expanded) Res.string.library_close_menu else Res.string.library_actions,
        ),
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
