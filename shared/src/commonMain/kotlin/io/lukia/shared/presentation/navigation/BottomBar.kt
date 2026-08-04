package io.lukia.shared.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.jetbrains.compose.resources.painterResource

@Composable
fun BottomBar(
    navController: NavHostController
) {
    val currentRoute =
        navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar {

        bottomNavigationItems.forEach { item ->

            NavigationBarItem(
                selected = currentRoute == item.route.route,
                onClick = {
                    if (currentRoute != item.route.route) {
                        navController.navigate(item.route.route)
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(item.title)
                }
            )
        }
    }
}