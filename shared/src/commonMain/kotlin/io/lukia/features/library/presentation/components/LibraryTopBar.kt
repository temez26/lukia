package io.lukia.features.library.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.LibraryFilter
import io.lukia.shared.presentation.components.AnchoredMenu
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.FolderGlyph
import io.lukia.shared.presentation.components.GearGlyph
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.MenuRow
import io.lukia.shared.presentation.components.MenuSeparator
import io.lukia.shared.presentation.components.PlusGlyph
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.components.scaleOnPress
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.WindowWidthClass

/** Diameter of the actions button in the bar. */
private val MenuButtonSize: Dp = 34.dp

/**
 * Height of the header's own controls. Shorter than [MinTouchTarget], which is
 * for controls a thumb has to find - these sit under a pointer on the windows
 * wide enough to show them this way, and beside a full-width filter on the ones
 * that do not.
 */
internal val HeaderControlHeight: Dp = 34.dp

/** Breathing room above and below the bar's contents. */
private val NavBarVerticalPadding: Dp = 7.dp

/** Width the filter takes when it is not filling the screen, with every segment. */
private val ToolbarFilterWidth: Dp = 360.dp

/** Segments [ToolbarFilterWidth] is sized for; fewer take proportionally less. */
private const val MaxFilterSegments = 4

/**
 * Enough for a title and no more. A search field is for a word or two, and one
 * run across a desktop window invites a sentence.
 */
private val ToolbarSearchWidth: Dp = 280.dp

/**
 * How tall the bar is, which is whatever it takes to hold the search field.
 *
 * The grid reads this too, to know how far down its content has to start, so it
 * is a function rather than a constant - the field is taller on a phone, where it
 * has to be found with a thumb.
 */
@Composable
internal fun libraryNavBarHeight(): Dp = searchFieldHeight() + NavBarVerticalPadding * 2

/**
 * What the search field says before anything is typed into it.
 *
 * The size of the library rides in here rather than in a line of its own. It is a
 * number worth knowing exactly once - when you are about to look for something in
 * it - and this is the only place it answers a question instead of just occupying
 * a row.
 *
 * It counts what is actually searchable, so a filter narrows it too.
 */
private fun searchPlaceholder(searchableCount: Int): String = when {
    // Nothing to search, and "Search 0 books" would be a strange way to say so.
    searchableCount <= 0 -> "Search"
    searchableCount == 1 -> "Search 1 book"
    else -> "Search $searchableCount books"
}

/**
 * The library's bar: search and the actions button, on one row.
 *
 * The search field lives up here rather than in the scrolling content because it
 * is the one control worth being able to reach without scrolling back to the top
 * of a long library - and because a field beside the button that opens the app's
 * other actions is what the row is for. Everything that describes the library
 * rather than acting on it stays below, in the content, where it can scroll away.
 *
 * The bar carries no title. The screen does not name itself: the books are the
 * subject, and a bar that says "Library" over a library says nothing the reader
 * did not already know.
 *
 * [collapsedProgress] is a lambda rather than a value so that scrolling redraws
 * this bar without recomposing it - it is read only inside draw and layer blocks.
 */
@Composable
internal fun LibraryNavBar(
    collapsedProgress: () -> Float,
    query: String,
    onQueryChange: (String) -> Unit,
    searchableCount: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalLukiaDimens.current
    val compact = LocalWindowWidthClass.current == WindowWidthClass.Compact
    val barColor = MaterialTheme.colorScheme.background
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        contentAlignment = Alignment.Center,
        // The fill and the hairline run the whole window width even where the
        // grid beneath them is capped, so the bar reads as an edge of the window
        // rather than as a floating panel.
        modifier = modifier
            .fillMaxWidth()
            .height(libraryNavBarHeight())
            .drawBehind {
                val progress = collapsedProgress()

                // Opaque rather than translucent: the grid passes directly
                // underneath, and the hand-drawn glass used elsewhere in the app
                // is not a real backdrop blur, so covers would show through it as
                // smears rather than as frosting.
                drawRect(color = barColor.copy(alpha = progress))

                drawLine(
                    color = hairlineColor.copy(alpha = progress),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
    ) {
        // Its contents follow the grid's own measure instead, so the search field
        // stays beside the books rather than out at the edge of a wide window.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = dimens.maxGridWidth)
                .fillMaxSize()
                .padding(horizontal = dimens.screenPadding)
        ) {
            // A phone gives the field the whole row, having nothing else to put
            // there. Anything wider keeps it beside the button, where a toolbar
            // puts it, rather than stretched into a text editor.
            if (!compact) Spacer(modifier = Modifier.weight(1f))

            LibrarySearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = searchPlaceholder(searchableCount),
                modifier = if (compact) {
                    Modifier.weight(1f)
                } else {
                    Modifier.width(ToolbarSearchWidth)
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                MenuButton(
                    expanded = menuExpanded,
                    onClick = { onMenuExpandedChange(!menuExpanded) }
                )

                LibraryMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    onImport = onImport,
                    onImportFolder = onImportFolder,
                    onSettings = onSettings
                )
            }
        }
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
private fun MenuButton(
    expanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val openness by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = LukiaMotion.standard(),
        label = "menuButtonOpenness"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scaleOnPress(interactionSource)
            .size(MenuButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = if (expanded) "Close menu" else "Library actions",
                onClick = onClick
            )
    ) {
        // The two glyphs cross-fade in place, and the close mark turns in as it
        // arrives, so the swap reads as one control changing rather than as two
        // controls trading places.
        EllipsisGlyph(
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer { alpha = 1f - openness }
        )

        PlusGlyph(
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(17.dp)
                .graphicsLayer {
                    alpha = openness
                    rotationZ = 45f * openness
                }
        )
    }
}

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
    onImportFolder: () -> Unit
) {
    MenuRow(
        label = "Import Book",
        icon = { tint -> PlusGlyph(tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = {
            onDismissRequest()
            onImport()
        }
    )

    MenuSeparator()

    MenuRow(
        label = "Add Folder",
        icon = { tint -> FolderGlyph(tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = {
            onDismissRequest()
            onImportFolder()
        }
    )
}

@Composable
private fun LibraryMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onSettings: () -> Unit
) {
    AnchoredMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        minWidth = 196.dp
    ) {
        ImportMenuRows(
            onDismissRequest = onDismissRequest,
            onImport = onImport,
            onImportFolder = onImportFolder
        )

        MenuSeparator()

        MenuRow(
            label = "Settings",
            icon = { tint -> GearGlyph(tint = tint, modifier = Modifier.size(18.dp)) },
            onClick = {
                onDismissRequest()
                onSettings()
            }
        )
    }
}

/**
 * What the library is narrowed to - inside the grid, so it scrolls away rather
 * than taking a permanent row.
 *
 * This is where a large title would go, and it is deliberately not one. A screen
 * that shows books does not need a word telling the reader they are books; the
 * row is worth more as controls. [filters] arrives already reduced to the ones
 * this library can actually offer, and an empty-handed control - one segment, or
 * none - is left off rather than shown with nothing to choose.
 *
 * How many books there are is not here either: it is in the search field, which
 * is the one place the number answers a question the reader is actually asking.
 */
@Composable
internal fun LibraryHeader(
    filters: List<LibraryFilter>,
    selectedFilter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    isImporting: Boolean,
    reveal: LaunchReveal,
    modifier: Modifier = Modifier
) {
    val compact = LocalWindowWidthClass.current == WindowWidthClass.Compact

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        if (filters.size > 1) {
            SegmentedControl(
                options = filters,
                selected = selectedFilter,
                label = { it.label },
                onSelect = onFilterChange,
                height = HeaderControlHeight,
                modifier = Modifier
                    .then(
                        // Filling is what a phone wants and what a desktop window
                        // must not do: stretched across one, the control is mostly
                        // the gaps between its labels.
                        if (compact) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.width(ToolbarFilterWidth * filters.size / MaxFilterSegments)
                        }
                    )
                    .launchReveal(reveal, index = 0)
            )
        }

        ImportingNotice(
            visible = isImporting,
            modifier = Modifier.launchReveal(reveal, index = 1)
        )
    }
}

/**
 * That an import is running, for a library that already has books in it.
 *
 * It reports itself here rather than replacing the grid, so the books already on
 * screen stay readable while the rest arrive. Present only while it is true -
 * there is nothing to say about an import that is not happening - and expanded
 * into place rather than appearing, so the grid is nudged down rather than jumping.
 */
@Composable
private fun ImportingNotice(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(LukiaMotion.standard()) + fadeIn(LukiaMotion.standard()),
        exit = shrinkVertically(LukiaMotion.exit()) + fadeOut(LukiaMotion.exit()),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Importing…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
