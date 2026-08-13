package io.lukia

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import io.lukia.features.bookdetail.presentation.screens.BookDetailScreen
import io.lukia.features.home.presentation.screens.HomeScreen
import io.lukia.features.reader.presentation.screens.ReaderScreen
import io.lukia.shared.presentation.theme.LukiaTheme

@Composable
fun App() {
    LukiaTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Routes.Home.route
        ) {

            composable(Routes.Home.route) {
                HomeScreen(
                    onDocumentClick = { document ->
                        navController.navigate(Routes.Detail.createRoute(document.document.location))
                    }
                )
            }

            composable(Routes.Reader.route) {
                ReaderScreen()
            }

            composable(Routes.Detail.route) { backStackEntry ->
                val locationToken = backStackEntry.arguments
                    ?.read { getStringOrNull(Routes.Detail.ARG_LOCATION_TOKEN) }
                    .orEmpty()

                BookDetailScreen(
                    location = Routes.Detail.decodeLocation(locationToken),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
