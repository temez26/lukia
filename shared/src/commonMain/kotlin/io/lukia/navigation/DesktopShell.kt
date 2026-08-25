package io.lukia.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.lukia.Routes
import io.lukia.features.library.presentation.screens.LibraryScreen

/**
 * The app as a window: the library, with everything else pushed on top of it.
 *
 * There is no tab bar here and deliberately so. A desktop window has room to
 * show a book's details beside the library rather than instead of it - see
 * `LibraryScreen` - so the shelf never leaves the screen, and a bar for
 * returning to a screen you never left is a row of chrome doing nothing.
 */
@Composable
internal fun DesktopShell() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Library.route,
        // Set on the host rather than per destination: every move in this shell
        // is a push onto or a pop off the library, so declaring it once is
        // both less to repeat and one less thing to forget when a screen is
        // added.
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
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
                },
                onSettings = { navController.navigate(Routes.Settings.route) },
            )
        }

        bookDestinations(navController)
    }
}
