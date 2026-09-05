package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.features.library.presentation.components.home.HomeCategoryRow
import io.lukia.features.library.presentation.components.home.HomeShelfRow
import io.lukia.features.library.presentation.components.toolbar.LibraryMenu
import io.lukia.features.library.presentation.components.toolbar.MenuButton
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.resources.Res
import io.lukia.resources.home_title
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The first reveal slot belongs to the header; the rows follow it down the page. */
private const val RowRevealOffset = 1

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
    actions: HomeBookActions,
    openMenu: HomeMenu?,
    onOpenMenuChange: (HomeMenu?) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The app's opening, which this screen is what is opened onto - see
     * [LaunchReveal]. A row takes its turn by its place down the page, so the
     * page settles from the top rather than all at once.
     */
    reveal: LaunchReveal = LaunchReveal.Finished,
    header: @Composable () -> Unit,
) {
    val dimens = LocalLukiaDimens.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Clearance.listBottom),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
    ) {
        item(key = "header", contentType = "header") {
            Box(
                modifier = Modifier
                    .padding(horizontal = dimens.screenPadding)
                    .launchReveal(reveal, index = 0),
            ) {
                header()
            }
        }

        itemsIndexed(
            items = rows,
            key = { _, row -> row.shelf.name },
            contentType = { _, _ -> "shelf" },
        ) { index, row ->
            HomeShelfRow(
                row = row,
                actions = actions,
                openMenu = openMenu,
                onOpenMenuChange = onOpenMenuChange,
                onSeeAll = onSeeAll,
                modifier = Modifier.launchReveal(reveal, index = index + RowRevealOffset),
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
                    modifier = Modifier.launchReveal(
                        reveal,
                        index = rows.size + RowRevealOffset,
                    ),
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
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** The way to the reader's own page, supplied by the shell - see [LibraryNavBar]. */
    statsButton: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        Box(modifier = Modifier.padding(end = Space.md)) {
            statsButton()
        }

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
                onSettings = onSettings,
            )
        }
    }
}
