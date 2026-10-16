package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.model.displayTitle
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.home_see_all_shelf
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The chevron after a row's title, at the size a heading wears rather than a
 * button. In sp so it grows with the title at a large system font size.
 */
private val TitleChevronSize: TextUnit = 18.sp

/**
 * One row of the home screen: its title, which leads through to all of it, and
 * the books themselves scrolling sideways.
 *
 * Sideways because a home screen is a summary. Three rows down the page, each
 * showing what will fit and no more, answers "what am I reading, what is new,
 * what did I keep" in one screen - where three vertical lists would answer the
 * first question and bury the other two.
 */
@Composable
internal fun HomeShelfRow(
    row: HomeRow,
    actions: HomeBookActions,
    openMenu: HomeMenu?,
    onOpenMenuChange: (HomeMenu?) -> Unit,
    onSeeAll: (LibraryQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        RowHeader(
            title = row.shelf.displayTitle(),
            onSeeAll = { onSeeAll(row.shelf.seeAll()) },
            modifier = Modifier.padding(horizontal = dimens.screenPadding),
        )

        LazyRow(
            // The row runs to the window's edges while its cards start at the
            // screen's own inset, so a book scrolled to the end of it passes off
            // the edge rather than stopping short of one.
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.lg),
        ) {
            items(
                items = row.books,
                key = { book -> book.location },
                contentType = { "homeBook" },
            ) { book ->
                HomeBookCard(
                    book = book,
                    // Off the measurement the library grid sizes its cells
                    // from, so both screens agree how big a book is.
                    width = dimens.bookCellMinWidth,
                    actions = actions,
                    menuExpanded = openMenu == HomeMenu.Book(book.location),
                    onMenuExpandedChange = { expanded ->
                        onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                    },
                )
            }
        }
    }
}

/**
 * The row's name, as the way to the whole of what it is a sample of.
 *
 * The title itself is the link, with a quiet chevron after it, rather than a
 * "See All" label beside it: one per row put the accent colour down the screen
 * six times, competing with the covers it was meant to lead past. The press
 * lands on the library tab already narrowed to this row - see [HomeShelf.seeAll].
 */
@Composable
private fun RowHeader(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall
    val chevronSize = with(LocalDensity.current) { TitleChevronSize.toDp() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .pressable(
                interactionSource = interactionSource,
                shape = shape,
                onClickLabel = stringResource(Res.string.home_see_all_shelf, title),
                onClick = onSeeAll,
            )
            .padding(vertical = Space.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(modifier = Modifier.size(Space.xs))

        // The app's one disclosure mark, turned to point the way this control
        // goes rather than drawn a second time facing sideways.
        DisclosureChevron(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(chevronSize)
                .graphicsLayer { rotationZ = -90f },
        )
    }
}
