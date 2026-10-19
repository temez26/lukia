package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.lukia.features.library.presentation.model.HomeMenu
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.displayTitle
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.home_see_all_shelf
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.icons.UnreadBookGlyph
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
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
    onOpenShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier,
    /** Downloads under way, by book location - see `BookTransfers`. */
    transfers: Map<String, BookTransfer> = emptyMap(),
) {
    val dimens = LocalLukiaDimens.current

    // On a card of its own, as the book being read above it is, so each row
    // reads as one shelf rather than covers floating on the page.
    GroupCard(modifier = modifier.padding(horizontal = dimens.screenPadding)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.lg)) {
            RowHeader(
                shelf = row.shelf,
                title = row.shelf.displayTitle(),
                onOpen = { onOpenShelf(row.shelf) },
                modifier = Modifier.padding(horizontal = Space.lg),
            )

            LazyRow(
                // The row runs to the card's edges while its covers start at
                // the card's own inset, so a book scrolled to the end of it
                // passes under the edge rather than stopping short of one.
                contentPadding = PaddingValues(horizontal = Space.lg),
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
                        actions = actions,
                        menuExpanded = openMenu == HomeMenu.Book(book.location),
                        onMenuExpandedChange = { expanded ->
                            onOpenMenuChange(HomeMenu.Book(book.location).takeIf { expanded })
                        },
                        // Off the measurement the library grid sizes its cells
                        // from, so both screens agree how big a book is.
                        modifier = Modifier.width(dimens.bookCellMinWidth),
                        transfer = transfers[book.location],
                    )
                }
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
 * opens a screen holding the whole of what the row samples.
 */
@Composable
private fun RowHeader(
    shelf: HomeShelf,
    title: String,
    onOpen: () -> Unit,
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
                onClick = onOpen,
            )
            .padding(vertical = Space.xxs),
    ) {
        ShelfBadge(shelf = shelf, size = chevronSize * BadgeScale)

        Spacer(modifier = Modifier.size(Space.sm))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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

/** How much bigger than the chevron the row's own mark is drawn, plate and all. */
private const val BadgeScale = 1.6f

/**
 * What the row holds, as a glyph on a wash of the accent: the same marks the
 * menus use for favouriting, reading and adding, so a row is recognised before
 * its title is read.
 */
@Composable
private fun ShelfBadge(shelf: HomeShelf, size: Dp) {
    val tint = MaterialTheme.colorScheme.primary
    val glyph = Modifier.size(size * GlyphShare)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = LukiaAlpha.Tonal)),
    ) {
        when (shelf) {
            HomeShelf.Favorites -> HeartGlyph(tint = tint, filled = true, modifier = glyph)
            HomeShelf.RecentlyOpened -> OpenBookGlyph(tint = tint, modifier = glyph)
            HomeShelf.Unread -> UnreadBookGlyph(tint = tint, modifier = glyph)
            HomeShelf.RecentlyAdded -> PlusGlyph(tint = tint, modifier = glyph)
        }
    }
}

/** The glyph's share of its plate. */
private const val GlyphShare = 0.62f
