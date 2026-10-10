package io.lukia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.Routes
import io.lukia.resources.Res
import io.lukia.resources.nav_home
import io.lukia.resources.nav_library
import io.lukia.resources.nav_search
import io.lukia.shared.presentation.components.icons.BooksGlyph
import io.lukia.shared.presentation.components.icons.HomeGlyph
import io.lukia.shared.presentation.components.icons.SearchGlyph
import org.jetbrains.compose.resources.StringResource

/**
 * The three places the tab bar goes, in the order they sit along the bottom of
 * the screen.
 *
 * Ordered by how often a reader wants them rather than by how much each holds:
 * home is where the app opens, search is for when you know what you want, and
 * the library - the largest, and the only one that was here before - is the one
 * you go to deliberately.
 *
 * Three and no more: a fourth tab is where a bar starts being a menu, and
 * everything else belongs either to one book or in settings.
 */
internal enum class MobileTab(
    /**
     * Held as the resource rather than as the word, so the enum stays a list of
     * three destinations rather than a table of English. Resolving it needs a
     * composition, and every place a tab is drawn already has one.
     */
    val label: StringResource,
    val route: String,
    val icon: @Composable (tint: Color, modifier: Modifier) -> Unit,
) {
    Home(
        label = Res.string.nav_home,
        route = Routes.Home.route,
        icon = { tint, modifier -> HomeGlyph(tint = tint, modifier = modifier) },
    ),
    Search(
        label = Res.string.nav_search,
        route = Routes.Search.route,
        icon = { tint, modifier -> SearchGlyph(tint = tint, modifier = modifier) },
    ),
    Library(
        label = Res.string.nav_library,
        route = Routes.Library.route,
        icon = { tint, modifier -> BooksGlyph(tint = tint, modifier = modifier) },
    ),
    ;

    companion object {

        /**
         * Which tab [route] is, or null for one of the screens that pushes over
         * the whole bar - a book, its details, the settings.
         *
         * This is what decides whether the bar is drawn at all, so it is asked
         * about every destination rather than only about the three here.
         */
        fun forRoute(route: String?): MobileTab? = entries.firstOrNull { it.route == route }
    }
}
