package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.HomeRow
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibraryShelfUi
import io.lukia.features.library.presentation.components.HomeCategoryRow
import io.lukia.features.library.presentation.components.HomeShelfRow
import io.lukia.features.library.presentation.components.LibraryMenu
import io.lukia.features.library.presentation.components.MenuButton
import io.lukia.shared.presentation.theme.LocalLukiaDimens

/** Clears the last row of the tab bar the screen scrolls up to. */
private val HomeBottomPadding: Dp = 24.dp

/**
 * The screen itself: its name and menu, then a row for each way the library is
 * worth sampling, then the subjects the books file themselves under.
 *
 * The header scrolls away with the rows rather than sitting in a bar over them.
 * There is no title to keep on screen once a reader is three rows down - by then
 * every row names itself - and the tab bar underneath already says where they
 * are.
 */
@Composable
internal fun HomeRows(
    rows: List<HomeRow>,
    categories: List<String>,
    onOpenBook: (LibraryBook) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = HomeBottomPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
    ) {
        item(key = "header", contentType = "header") {
            Box(modifier = Modifier.padding(horizontal = dimens.screenPadding)) {
                header()
            }
        }

        items(
            items = rows,
            key = { row -> row.shelf.name },
            contentType = { "shelf" },
        ) { row ->
            HomeShelfRow(
                row = row,
                onOpenBook = onOpenBook,
                onSeeAll = onSeeAll,
            )
        }

        // Last, because it is the only row that leads somewhere by name rather
        // than by showing what is there. Everything above answers "what shall I
        // read"; this answers "what else have I got".
        if (categories.isNotEmpty()) {
            item(key = "categories", contentType = "categories") {
                HomeCategoryRow(
                    categories = categories,
                    onSelect = { category -> onSeeAll(LibraryQuery(category = category)) },
                )
            }
        }
    }
}

/**
 * The screen's name, and the library's own actions menu.
 *
 * The same menu the library tab carries, rather than a shorter one of its own.
 * Importing a book, tidying the shelf and opening the settings are things a
 * reader does to their library and not to a screen, and offering three of them
 * here and five there would make the reader learn which corner holds which.
 *
 * That the rows underneath cannot show a selection is the menu's problem rather
 * than this screen's - see [LibraryShelfUi.selectingOn].
 */
@Composable
internal fun HomeHeader(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    shelf: LibraryShelfUi,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        Box {
            MenuButton(
                expanded = menuExpanded,
                onClick = { onMenuExpandedChange(!menuExpanded) },
            )

            LibraryMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                onImport = onImport,
                onImportFolder = onImportFolder,
                shelf = shelf,
                onProfile = onProfile,
                onSettings = onSettings,
            )
        }
    }
}
