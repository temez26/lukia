package io.lukia

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import io.lukia.features.bookdetail.presentation.screens.BookDetailScreen
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.reader.presentation.screens.ReaderScreen
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.settings.presentation.screens.SettingsScreen
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.LukiaTheme
import org.koin.compose.koinInject

/**
 * How far the screen being left behind travels while the new one comes in over
 * it. A fraction rather than the full width: the two screens moving at different
 * rates is the parallax that makes one read as sitting on top of the other, which
 * is what a push looks like on iOS.
 */
private const val ParallaxFraction = 0.3f

/** The outgoing screen also dims, so the incoming one is clearly in front. */
private const val ParallaxDimAlpha = 0.7f

@Composable
fun App() {
    // Injected rather than taken from a ViewModel: the theme has to be resolved
    // above the navigation host, where no destination - and so no ViewModel scope
    // - exists yet. The repository loads synchronously, so this is settled before
    // the first frame and the app never opens in the wrong colours.
    val appSettings by koinInject<AppSettingsRepository>().settings.collectAsState()

    val darkTheme = when (appSettings.theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    LukiaTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Routes.Library.route,
            // Set on the host rather than per destination: every move in this app
            // is a push onto or a pop off the library, so declaring it once is
            // both less to repeat and one less thing to forget when a screen is
            // added.
            enterTransition = { pushEnter() },
            exitTransition = { pushExit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() }
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
                    onSettings = { navController.navigate(Routes.Settings.route) }
                )
            }

            composable(Routes.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
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

/** The new screen slides in from the trailing edge, at full width. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        initialOffsetX = { width -> width }
    )

/** The screen underneath is pushed a little way aside and dimmed. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        targetOffsetX = { width -> -(width * ParallaxFraction).toInt() }
    ) + fadeOut(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        targetAlpha = ParallaxDimAlpha
    )

/** Coming back, the screen underneath returns from where it was pushed to. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        initialOffsetX = { width -> -(width * ParallaxFraction).toInt() }
    ) + fadeIn(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        initialAlpha = ParallaxDimAlpha
    )

/** And the screen being left slides back off the trailing edge. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(LukiaMotion.LargeMillis, easing = LukiaMotion.Standard),
        targetOffsetX = { width -> width }
    )

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
