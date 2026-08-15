package io.lukia

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import io.lukia.features.bookdetail.presentation.screens.BookDetailScreen
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.reader.presentation.screens.ReaderScreen
import io.lukia.shared.presentation.theme.LukiaTheme

@Composable
fun App() {
    LukiaTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Routes.Library.route
        ) {

            composable(Routes.Library.route) {
                LibraryScreen(
                    // Opening the book is what tapping it is for; everything
                    // else about it lives behind the card's own menu.
                    onDocumentClick = { document ->
                        navController.navigate(Routes.Reader.createRoute(document.document.location))
                    },
                    onDocumentDetails = { document ->
                        navController.navigate(Routes.Detail.createRoute(document.document.location))
                    }
                )
            }

            documentComposable(Routes.Reader) { location ->
                ReaderScreen(
                    location = location,
                    onBack = { navController.popBackStack() }
                )
            }

            documentComposable(Routes.Detail) { location ->
                BookDetailScreen(
                    location = location,
                    onBack = { navController.popBackStack() },
                    onRead = { navController.navigate(Routes.Reader.createRoute(location)) }
                )
            }
        }
    }
}

/**
 * Registers a destination that shows one book, handing [content] the location it
 * was opened with. Both such screens decode the same argument the same way, and
 * this is the one place that knows how.
 */
private fun NavGraphBuilder.documentComposable(
    route: Routes.DocumentRoute,
    content: @Composable (location: String) -> Unit
) {
    composable(route.route) { backStackEntry ->
        val locationToken = backStackEntry.arguments
            ?.read { getStringOrNull(Routes.DocumentRoute.ARG_LOCATION_TOKEN) }
            .orEmpty()

        content(route.decodeLocation(locationToken))
    }
}
