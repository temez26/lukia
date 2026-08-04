package io.lukia.shared.presentation.navigation

import io.lukia.Routes
import lukia.shared.generated.resources.Res
import lukia.shared.generated.resources.home_24px
import lukia.shared.generated.resources.library_24px

val bottomNavigationItems = listOf(
    BottomNavItem(
        title = "Home",
        icon = Res.drawable.home_24px,
        route = Routes.Home
    ),
    BottomNavItem(
        title = "Library",
        icon = Res.drawable.library_24px,
        route = Routes.Library
    )
)