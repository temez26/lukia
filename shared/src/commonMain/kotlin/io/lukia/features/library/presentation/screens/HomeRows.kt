package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.features.library.presentation.components.home.ContinueFacts
import io.lukia.features.library.presentation.components.home.ContinueHero
import io.lukia.features.library.presentation.components.home.HomeContinueCard
import io.lukia.features.library.presentation.components.home.HomeShelfRow
import io.lukia.features.library.presentation.components.toolbar.LibraryMenu
import io.lukia.features.library.presentation.components.toolbar.MenuButton
import io.lukia.features.library.presentation.components.toolbar.TonalPlate
import io.lukia.features.library.presentation.model.HomeLayout
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.resources.Res
import io.lukia.resources.home_book_count
import io.lukia.resources.home_reading_count
import io.lukia.resources.home_title
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.OnPageColors
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.SystemBarIcons
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
    /** A glance at the week's reading, under the book being read - supplied by the shell. */
    stats: @Composable (Modifier) -> Unit = {},
    header: @Composable () -> Unit,
) {
    val dimens = LocalLukiaDimens.current
    val listState = rememberLazyListState()
    // The hero is the first item, so while it is the first one shown it is what
    // the status bar sits over.
    val heroUnderStatusBar by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val current = home.current

    if (current != null) {
        SystemBarIcons(dark = !heroUnderStatusBar && MaterialTheme.colorScheme.background.luminance() > LightPage)
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Clearance.listBottom),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        ) {
            if (current != null) {
                item(key = "current", contentType = "current") {
                    ContinueTop(
                        book = current,
                        actions = actions,
                        openMenu = openMenu,
                        onOpenMenuChange = onOpenMenuChange,
                        reveal = reveal,
                        transfer = transfers[current.location],
                        header = header,
                    )
                }
            } else {
                item(key = "header", contentType = "header") { PageHeader(reveal = reveal, header = header) }
            }

            item(key = "stats", contentType = "stats") {
                stats(
                    Modifier
                        .padding(horizontal = dimens.screenPadding)
                        .launchReveal(reveal, index = RowRevealOffset + 1),
                )
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
                    modifier = Modifier.launchReveal(reveal, index = index + RowRevealOffset + 2),
                    transfers = transfers,
                )
            }
        }

        StatusBarBacking(visible = !heroUnderStatusBar)
    }
}

/** The header on the page itself, with no book being read to carry it under the status bar. */
@Composable
private fun PageHeader(reveal: LaunchReveal, header: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = LocalLukiaDimens.current.screenPadding)
            .launchReveal(reveal, index = 0),
    ) {
        header()
    }
}

/**
 * The page's colour behind the status bar. The list runs under the bar, for the
 * hero's sake; once the hero has gone, the bar gets the page behind it again
 * rather than the shelves passing under the clock.
 */
@Composable
private fun StatusBarBacking(visible: Boolean) {
    if (!visible) return

    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background),
    )
}

/** Above this, the page is light, and the status bar's icons are drawn dark over it. */
private const val LightPage = 0.5f

/**
 * The top of Home while a book is being read: the header, the book and the facts
 * about its file, all on the book's own colour.
 */
@Composable
private fun ContinueTop(
    book: LibraryBook,
    actions: HomeBookActions,
    openMenu: HomeMenu?,
    onOpenMenuChange: (HomeMenu?) -> Unit,
    reveal: LaunchReveal,
    transfer: BookTransfer?,
    header: @Composable () -> Unit,
) {
    val dimens = LocalLukiaDimens.current

    Column {
        ContinueHero(book = book) {
            Box(
                modifier = Modifier
                    .padding(horizontal = dimens.screenPadding)
                    .launchReveal(reveal, index = 0),
            ) {
                header()
            }

            HomeContinueCard(
                book = book,
                actions = actions,
                menuExpanded = openMenu == HomeMenu.Book(book.location),
                onMenuExpandedChange = { expanded ->
                    onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                },
                modifier = Modifier
                    .padding(horizontal = dimens.screenPadding)
                    .padding(top = Space.lg)
                    .launchReveal(reveal, index = RowRevealOffset),
                transfer = transfer,
            )

            ContinueFacts(
                document = book.document,
                modifier = Modifier
                    .padding(horizontal = dimens.screenPadding)
                    .padding(top = Space.lg)
                    .launchReveal(reveal, index = RowRevealOffset),
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
 * The way to the reading statistics is not here but on a card of its own under
 * the book being read - see `WeekGlance`.
 *
 * The menu sits on a tonal plate: this header is on the screen's background
 * rather than in a bar, and a bare glyph at its edge reads as decoration. On
 * the book being read it goes bare and white instead, which the deepened colour
 * there reads better than a faint plate - see `ContinueHero`.
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
    /** What the library holds, under the title - see [homeSummary]. Null for none. */
    summary: String? = null,
    /** Whether it is drawn on the book being read rather than on the page. */
    onHero: Boolean = false,
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

        val menu: @Composable () -> Unit = {
            HeaderMenu(
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                onImport = onImport,
                onImportFolder = onImportFolder,
                shelf = shelf,
                onSettings = onSettings,
            )
        }

        if (onHero) menu() else TonalPlate { menu() }
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

        OnPageColors {
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
