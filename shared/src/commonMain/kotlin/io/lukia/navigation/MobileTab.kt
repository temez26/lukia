package io.lukia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.Routes
import io.lukia.shared.presentation.components.icons.BooksGlyph
import io.lukia.shared.presentation.components.icons.HomeGlyph
import io.lukia.shared.presentation.components.icons.SearchGlyph

/**
 * The three places the tab bar goes, in the order they sit along the bottom of
 * the screen.
 *
 * Ordered by how often a reader wants them rather than by how much each one
 * holds: home is where the app opens, search is what you reach for when you
 * know what you want, and the library itself - the largest of the three, and
 * the only one that was here before - is the one you go to deliberately.
 *
 * Three and no more. A fourth tab is where a bar starts being a menu, and
 * everything else this app does either belongs to one book or belongs in
 * settings.
 */
internal enum class MobileTab(
    val label: String,
    val route: String,
    val icon: @Composable (tint: Color, modifier: Modifier) -> Unit,
) {
    Home(
        label = "Home",
        route = Routes.Home.route,
        icon = { tint, modifier -> HomeGlyph(tint = tint, modifier = modifier) },
    ),
    Search(
        label = "Search",
        route = Routes.Search.route,
        icon = { tint, modifier -> SearchGlyph(tint = tint, modifier = modifier) },
    ),
    Library(
        label = "Library",
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
