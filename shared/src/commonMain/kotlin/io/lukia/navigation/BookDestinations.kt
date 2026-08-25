package io.lukia.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.savedstate.read
import io.lukia.Routes
import io.lukia.features.bookdetail.presentation.screens.BookDetailScreen
import io.lukia.features.reader.presentation.screens.ReaderScreen
import io.lukia.features.settings.presentation.screens.SettingsScreen

/**
 * The screens that push over whatever was showing: one book being read, one
 * book being described, and the settings.
 *
 * Registered from here rather than from each shell because they are the same
 * screens reached the same way on every platform. What differs between a desktop
 * window and a phone is what sits underneath them - a library, or a tab bar with
 * three screens behind it - and that is all either shell has to say.
 */
internal fun NavGraphBuilder.bookDestinations(navController: NavController) {
    composable(Routes.Settings.route) {
        SettingsScreen(onBack = { navController.popBackStack() })
    }

    documentComposable(Routes.Reader) { location ->
        ReaderScreen(
            location = location,
            onBack = { navController.popBackStack() },
        )
    }

    documentComposable(Routes.Detail) { location ->
        BookDetailScreen(
            location = location,
            onBack = { navController.popBackStack() },
            onRead = { navController.navigate(Routes.Reader.createRoute(location)) },
        )
    }
}

/**
 * Registers a destination that shows one book, handing [content] the location it
 * was opened with. Both such screens decode the same argument the same way, and
 * this is the one place that knows how.
 */
private fun NavGraphBuilder.documentComposable(
    route: Routes.DocumentRoute,
    content: @Composable (location: String) -> Unit,
) {
    composable(route.route) { backStackEntry ->
        val locationToken = backStackEntry.arguments
            ?.read { getStringOrNull(Routes.DocumentRoute.ARG_LOCATION_TOKEN) }
            .orEmpty()

        content(route.decodeLocation(locationToken))
    }
}
