package io.lukia.features.library.presentation.components.toolbar

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.components.search.LibrarySearchField
import io.lukia.features.library.presentation.components.search.searchFieldHeight
import io.lukia.features.library.presentation.sessions.LibraryShelfUi
import io.lukia.resources.Res
import io.lukia.resources.library_title
import io.lukia.resources.search_n_books
import io.lukia.resources.search_title
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
 * The size of the library rides in here rather than taking a line of its own: it
 * is worth knowing exactly once, when you are about to look for something in it.
 *
 * It counts what is actually searchable, so a filter narrows it too.
 */
@Composable
private fun searchPlaceholder(searchableCount: Int): String {
    // Nothing to search, and "Search 0 books" would be a strange way to say so.
    if (searchableCount <= 0) return stringResource(Res.string.search_title)

    return pluralStringResource(Res.plurals.search_n_books, searchableCount, searchableCount)
}

/**
 * The library's bar: search and the actions button, on one row.
 *
 * The search field lives up here because it is the one control worth reaching
 * without scrolling back to the top of a long library. Everything that describes
 * the library rather than acting on it stays below, where it can scroll away.
 *
 * Where the field is not wanted - see [showSearch] - the row carries the
 * screen's name instead. That is a concession to the tab bar underneath and
 * nothing else: a screen one of three tabs leads to has to confirm which was
 * pressed; the only screen a desktop window has does not.
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
    /**
     * The way to the reading statistics, supplied rather than built here.
     *
     * A library bar has a place for it and no business knowing what that screen
     * is; the shell that composes both features is what fills it - see
     * `MobileShell`. Empty by default, so this bar composes in a test or a
     * preview without the statistics feature behind it.
     */
    statsButton: @Composable () -> Unit = {},
    searchFocus: FocusRequester? = null,
    /** Led with on a window's bar, where the switch to Home sits - see `DesktopShell`. */
    sections: @Composable () -> Unit = {},
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
                sections()

                SearchSlot(
                    query = query,
                    onQueryChange = onQueryChange,
                    searchableCount = searchableCount,
                    focusRequester = searchFocus,
                )
            } else {
                TitleSlot()
            }

            Spacer(modifier = Modifier.width(Space.md))

            // Before the actions button, so the row reads left to right as what
            // this screen is, how the reading is going, and what can be done to
            // the shelf.
            // On the same tonal plates as Home's header, so the two buttons
            // look the same whichever tab they are pressed from.
            TonalPlate(modifier = Modifier.padding(end = Space.md)) {
                statsButton()
            }

            TonalPlate {
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
    focusRequester: FocusRequester?,
) {
    val compact = !isWideWindow

    if (!compact) Spacer(modifier = Modifier.weight(1f))

    val sized = if (compact) Modifier.weight(1f) else Modifier.width(ToolbarSearchWidth)

    LibrarySearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = searchPlaceholder(searchableCount),
        modifier = if (focusRequester != null) sized.focusRequester(focusRequester) else sized,
    )
}

/** What the bar carries where the search field is not wanted. */
@Composable
private fun RowScope.TitleSlot() {
    Text(
        text = stringResource(Res.string.library_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}
