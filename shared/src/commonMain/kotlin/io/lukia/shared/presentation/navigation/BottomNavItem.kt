package io.lukia.shared.presentation.navigation

import io.lukia.Routes
import org.jetbrains.compose.resources.DrawableResource

data class BottomNavItem(
    val title: String,
    val icon: DrawableResource,
    val route: Routes
)