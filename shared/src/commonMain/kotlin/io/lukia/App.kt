package io.lukia

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.lukia.features.home.presentation.screens.HomeScreen
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.reader.presentation.screens.ReaderScreen
import io.lukia.shared.presentation.navigation.BottomBar

@Composable
fun App() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomBar(navController)
        }
    ) { paddingValues ->

        NavHost(
            modifier = Modifier.padding(paddingValues),
            navController = navController,
            startDestination = Routes.Home.route
        ) {

            composable(Routes.Home.route) {
                HomeScreen()
            }

            composable(Routes.Library.route) {
                LibraryScreen()
            }

            composable(Routes.Reader.route) {
                ReaderScreen()
            }
        }
    }
}