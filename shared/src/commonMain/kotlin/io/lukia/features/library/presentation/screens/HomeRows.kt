package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.features.library.presentation.components.home.HomeContinueCard
import io.lukia.features.library.presentation.components.home.HomeShelfRow
import io.lukia.features.library.presentation.components.toolbar.LibraryMenu
import io.lukia.features.library.presentation.components.toolbar.MenuButton
import io.lukia.features.library.presentation.model.HomeLayout
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.resources.Res
import io.lukia.resources.home_book_count
import io.lukia.resources.home_reading_count
import io.lukia.resources.home_title
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalIconButtonShape
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The first reveal slot belongs to the header; the card and rows follow it down the page. */
private const val RowRevealOffset = 1

/**
 * The screen itself: its name and menu, the book being read, then a row for each
 * way the rest of the library is worth sampling.
 *
 * The header scrolls away with the rows rather than sitting in a bar over them.
 * There is no title to keep on screen once a reader is three rows down - by then
 * every row names itself - and the tab bar underneath already says where they
 * are.
 */
@Composable
internal fun HomeRows(
    home: HomeLayout,
    actions: HomeBookActions,
    openMenu: HomeMenu?,
    onOpenMenuChange: (HomeMenu?) -> Unit,
    onOpenShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The app's opening, which this screen is what is opened onto - see
     * [LaunchReveal]. A row takes its turn by its place down the page, so the
     * page settles from the top rather than all at once.
     */
    reveal: LaunchReveal = LaunchReveal.Finished,
    /** Downloads under way, by book location - see `BookTransfers`. */
    transfers: Map<String, BookTransfer> = emptyMap(),
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

        home.current?.let { book ->
            item(key = "current", contentType = "current") {
                HomeContinueCard(
                    book = book,
                    actions = actions,
                    menuExpanded = openMenu == HomeMenu.Book(book.location),
                    onMenuExpandedChange = { expanded ->
                        onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                    },
                    modifier = Modifier
                        .padding(horizontal = dimens.screenPadding)
                        .launchReveal(reveal, index = RowRevealOffset),
                    transfer = transfers[book.location],
                )
            }
        }

        itemsIndexed(
            items = home.rows,
            key = { _, row -> row.shelf.name },
            contentType = { _, _ -> "shelf" },
        ) { index, row ->
            HomeShelfRow(
                row = row,
                actions = actions,
                openMenu = openMenu,
                onOpenMenuChange = onOpenMenuChange,
                onOpenShelf = onOpenShelf,
                modifier = Modifier.launchReveal(reveal, index = index + RowRevealOffset + 1),
                transfers = transfers,
            )
        }
    }
}

/**
 * The screen's name, and the library's own actions menu.
 *
 * The same menu the library tab carries rather than a shorter one: importing,
 * tidying and the settings are done to a library rather than to a screen, and
 * three here against five there would make the reader learn which corner holds
 * which.
 *
 * That the rows underneath cannot show a selection is the menu's problem rather
 * than this screen's - see [LibraryShelfUi.selectingOn].
 *
 * The controls sit on tonal plates: this header is on the screen's background
 * rather than in a bar, and two bare glyphs at its edge read as decoration.
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
    /** What the library holds, under the title - see [homeSummary]. Null for none. */
    summary: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        TonalPlate(modifier = Modifier.padding(end = Space.md)) { statsButton() }

        TonalPlate {
            HeaderMenu(
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                onImport = onImport,
                onImportFolder = onImportFolder,
                shelf = shelf,
                onSettings = onSettings,
            )
        }
    }
}

/**
 * A wash of the accent behind one of the header's buttons, in the buttons' own
 * shape. The statistics button arrives as a slot from the shell, so the header
 * dresses it rather than asking for a different one.
 */
@Composable
private fun TonalPlate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = LocalIconButtonShape.current

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Tonal)),
    ) {
        content()
    }
}

@Composable
private fun HeaderMenu(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    shelf: LibraryShelfUi,
    onSettings: () -> Unit,
) {
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

/** Separates the facts in [homeSummary], as the people list sets its own. */
private const val SummarySeparator = " · "

/**
 * What the library holds, as one line under Home's title: how many books, and
 * how many are being read. Null for an empty library, which says so itself.
 */
@Composable
internal fun homeSummary(home: HomeLayout): String? {
    if (home.bookCount == 0) return null

    val books = pluralStringResource(Res.plurals.home_book_count, home.bookCount, home.bookCount)
    val reading = home.readingCount.takeIf { it > 0 }?.let {
        pluralStringResource(Res.plurals.home_reading_count, it, it)
    }

    return listOfNotNull(books, reading).joinToString(SummarySeparator)
}
