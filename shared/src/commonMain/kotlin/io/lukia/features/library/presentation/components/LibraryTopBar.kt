package io.lukia.features.library.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.LibraryShelfUi
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.PlusGlyph
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow

/** Breathing room above and below the bar's contents. */
private val NavBarVerticalPadding: Dp = Space.sm

/**
 * Enough for a title and no more. A search field is for a word or two, and one
 * run across a desktop window invites a sentence.
 */
private val ToolbarSearchWidth: Dp = 260.dp

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
 * Where the field is not wanted - see [showSearch] - the row carries the
 * screen's name instead. That is a concession to the tab bar underneath it and
 * to nothing else: a screen one of three tabs leads to has to confirm which of
 * the three was pressed, where the only screen a desktop window has does not.
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
    /** How the shelf is worked with, which is what this bar's menu offers. */
    shelf: LibraryShelfUi,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the field belongs on this bar. False where searching has a screen
     * of its own, which is the mobile app - see `MobileShell`. Two fields
     * answering the same question with two different answers is worse than one
     * of them being somewhere else.
     */
    showSearch: Boolean = true,
) {
    val dimens = LocalLukiaDimens.current
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
                    strokeWidth = 1f,
                )
            },
    ) {
        // Its contents follow the grid's own measure instead, so the search field
        // stays beside the books rather than out at the edge of a wide window.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = dimens.maxGridWidth)
                .fillMaxSize()
                .padding(horizontal = dimens.screenPadding),
        ) {
            if (showSearch) {
                SearchSlot(
                    query = query,
                    onQueryChange = onQueryChange,
                    searchableCount = searchableCount,
                )
            } else {
                TitleSlot()
            }

            Spacer(modifier = Modifier.width(Space.md))

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
}

/**
 * The field, sized for what is pointing at it.
 *
 * A phone gives it the whole row, having nothing else to put there. Anything
 * wider keeps it beside the button, where a toolbar puts it, rather than
 * stretched into a text editor.
 */
@Composable
private fun RowScope.SearchSlot(
    query: String,
    onQueryChange: (String) -> Unit,
    searchableCount: Int,
) {
    val compact = !isWideWindow

    if (!compact) Spacer(modifier = Modifier.weight(1f))

    LibrarySearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = searchPlaceholder(searchableCount),
        modifier = if (compact) Modifier.weight(1f) else Modifier.width(ToolbarSearchWidth),
    )
}

/** What the bar carries where the search field is not wanted. */
@Composable
private fun RowScope.TitleSlot() {
    Text(
        text = "Library",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
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
