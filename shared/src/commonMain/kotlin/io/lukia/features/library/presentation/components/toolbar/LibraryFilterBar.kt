package io.lukia.features.library.presentation.components.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.displayName
import io.lukia.core.presentation.languageName
import io.lukia.features.library.domain.LibraryLayout
import io.lukia.features.library.presentation.model.LibraryFacet
import io.lukia.features.library.presentation.model.LibraryFacets
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.model.LibrarySort
import io.lukia.features.library.presentation.model.OpenMenu
import io.lukia.shared.presentation.components.controls.LukiaChip
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.LayoutGlyph
import io.lukia.shared.presentation.components.icons.SortGlyph
import io.lukia.shared.presentation.components.menu.AnchoredMenu
import io.lukia.shared.presentation.components.menu.MenuRow
import io.lukia.shared.presentation.components.menu.MenuSeparator
import io.lukia.shared.presentation.theme.Space

/**
 * How tall a chip's menu may grow before it scrolls.
 *
 * A library can hold hundreds of authors, and a menu listing all of them would
 * run off both ends of the window. Capped and scrolled rather than truncated:
 * a list cut short is one where the author somebody is looking for is simply
 * absent, with nothing on screen to say so.
 */
private val ChipMenuMaxHeight: Dp = 320.dp

/**
 * The row of filters under the shelves: what the library is narrowed to, and
 * what order it is in.
 *
 * Only the filters this particular library can actually offer appear - see
 * [LibraryFacets] - so a shelf of PDFs with no metadata shows a row with nothing
 * on it but the sort, while one built from EPUBs offers their categories,
 * authors and languages too.
 *
 * The row scrolls sideways rather than wrapping. Wrapping would change the
 * header's height as filters come and go, which moves the whole grid underneath
 * it; a row that keeps its height simply has more to the right of it.
 */
@Composable
internal fun LibraryFilterBar(
    query: LibraryQuery,
    facets: LibraryFacets,
    onQueryChange: (LibraryQuery) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (facets.hasFavourites) {
            LukiaChip(
                label = "Favourites",
                selected = query.favouritesOnly,
                onClick = { onQueryChange(query.copy(favouritesOnly = !query.favouritesOnly)) },
                icon = { tint, glyph ->
                    HeartGlyph(tint = tint, filled = query.favouritesOnly, modifier = glyph)
                },
            )
        }

        ValueChip(
            facet = LibraryFacet.Format,
            name = "Format",
            anyLabel = "All formats",
            options = facets.formats.map { it.displayName() },
            selectedIndex = facets.formats.indexOf(query.format).takeIf { it >= 0 },
            onSelect = { index -> onQueryChange(query.copy(format = index?.let(facets.formats::get))) },
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
        )

        ValueChip(
            facet = LibraryFacet.Category,
            name = "Category",
            anyLabel = "All categories",
            options = facets.categories,
            selectedIndex = facets.categories.indexOf(query.category).takeIf { it >= 0 },
            onSelect = { index ->
                onQueryChange(query.copy(category = index?.let(facets.categories::get)))
            },
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
        )

        ValueChip(
            facet = LibraryFacet.Author,
            name = "Author",
            anyLabel = "All authors",
            options = facets.authors,
            selectedIndex = facets.authors.indexOf(query.author).takeIf { it >= 0 },
            onSelect = { index ->
                onQueryChange(query.copy(author = index?.let(facets.authors::get)))
            },
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
        )

        ValueChip(
            facet = LibraryFacet.Language,
            name = "Language",
            anyLabel = "All languages",
            // Shown as languages rather than as the tags they are stored as:
            // `fi` is a filter nobody recognises and Finnish is one everybody does.
            options = facets.languages.map { languageName(it) ?: it.uppercase() },
            selectedIndex = facets.languages.indexOf(query.language).takeIf { it >= 0 },
            onSelect = { index ->
                onQueryChange(query.copy(language = index?.let(facets.languages::get)))
            },
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
        )

        SortChip(
            sort = query.sort,
            onSortChange = { onQueryChange(query.copy(sort = it)) },
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
        )

        LayoutChip(
            layout = layout,
            onLayoutChange = onLayoutChange,
            openMenu = openMenu,
            onOpenMenuChange = onOpenMenuChange,
        )

        // Last, and only while there is something to undo. A permanent "Clear"
        // beside filters that are all off is a control that does nothing.
        if (query.isRefined) {
            LukiaChip(
                label = "Clear",
                selected = false,
                onClick = { onQueryChange(query.cleared()) },
            )
        }
    }
}

/**
 * A chip offering one value out of a list, or none of them.
 *
 * Draws nothing at all when the library has no such values to choose between -
 * the same rule the shelves follow, and what keeps a library of loose PDFs from
 * showing an Author filter with nothing behind it.
 */
@Composable
private fun ValueChip(
    facet: LibraryFacet,
    name: String,
    anyLabel: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
) {
    if (options.isEmpty()) return

    ChipMenu(
        facet = facet,
        // The chosen value takes the chip's place rather than sitting beside its
        // name: "Fiction" is what the reader chose and what they need to see, and
        // "Category: Fiction" spends half a narrow chip on a word they can infer.
        label = selectedIndex?.let(options::get) ?: name,
        selected = selectedIndex != null,
        openMenu = openMenu,
        onOpenMenuChange = onOpenMenuChange,
    ) { dismiss ->
        MenuRow(
            label = anyLabel,
            selected = selectedIndex == null,
            onClick = {
                dismiss()
                onSelect(null)
            },
        )

        options.forEachIndexed { index, option ->
            MenuSeparator()

            MenuRow(
                label = option,
                selected = index == selectedIndex,
                onClick = {
                    dismiss()
                    onSelect(index)
                },
            )
        }
    }
}

@Composable
private fun SortChip(
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
) {
    ChipMenu(
        facet = LibraryFacet.Sort,
        label = sort.label,
        // Never filled in: a library is always in some order, so an order is not
        // a narrowing of it and marking it as one would leave a chip permanently
        // lit beside the filters that are actually on.
        selected = false,
        icon = { tint, glyph -> SortGlyph(tint = tint, modifier = glyph) },
        openMenu = openMenu,
        onOpenMenuChange = onOpenMenuChange,
    ) { dismiss ->
        LibrarySort.entries.forEachIndexed { index, option ->
            if (index > 0) MenuSeparator()

            MenuRow(
                label = option.label,
                selected = option == sort,
                onClick = {
                    dismiss()
                    onSortChange(option)
                },
            )
        }
    }
}

/**
 * A chip with a menu hanging under it, holding whichever rows [content] puts
 * there and handing them the way to close it again.
 *
 * The open menu is the screen's state rather than this chip's, so opening one
 * closes any other - including the ones on the bar and on every book.
 */
@Composable
private fun ChipMenu(
    facet: LibraryFacet,
    label: String,
    selected: Boolean,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
    icon: (@Composable (Color, Modifier) -> Unit)? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val menu = remember(facet) { OpenMenu.Chip(facet) }
    val expanded = openMenu == menu
    val dismiss = { onOpenMenuChange(null) }

    Box {
        LukiaChip(
            label = label,
            selected = selected,
            chevron = true,
            icon = icon,
            onClick = { onOpenMenuChange(if (expanded) null else menu) },
        )

        AnchoredMenu(expanded = expanded, onDismissRequest = dismiss) {
            Column(
                modifier = Modifier
                    .heightIn(max = ChipMenuMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                content(dismiss)
            }
        }
    }
}

/**
 * Covers or rows, chosen the way an order is chosen - beside it, and marked the
 * same way.
 *
 * Never filled in, for the same reason [SortChip] is not: a shelf is always laid
 * out somehow, so a layout is not a narrowing of it, and a chip permanently lit
 * beside the filters that are actually on would say the opposite.
 */
@Composable
private fun LayoutChip(
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    openMenu: OpenMenu?,
    onOpenMenuChange: (OpenMenu?) -> Unit,
) {
    ChipMenu(
        facet = LibraryFacet.Layout,
        label = layout.label,
        selected = false,
        icon = { tint, glyph -> LayoutGlyph(tint = tint, list = layout == LibraryLayout.List, modifier = glyph) },
        openMenu = openMenu,
        onOpenMenuChange = onOpenMenuChange,
    ) { dismiss ->
        LibraryLayout.entries.forEachIndexed { index, option ->
            if (index > 0) MenuSeparator()

            MenuRow(
                label = option.label,
                selected = option == layout,
                onClick = {
                    dismiss()
                    onLayoutChange(option)
                },
            )
        }
    }
}
