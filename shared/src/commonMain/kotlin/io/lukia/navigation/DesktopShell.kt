package io.lukia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.lukia.Routes
import io.lukia.core.infrastructure.AppCommand
import io.lukia.core.infrastructure.AppCommands
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.stats.presentation.StatsButton
import io.lukia.shared.presentation.theme.LocalMotionStyle
import org.koin.compose.koinInject

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
    val motion = LocalMotionStyle.current

    var requested by rememberRequestedCommand(navController)

    NavHost(
        navController = navController,
        startDestination = Routes.Library.route,
        // Set on the host rather than per destination: every move in this shell
        // is a push onto or a pop off the library, so declaring it once is
        // both less to repeat and one less thing to forget when a screen is
        // added.
        enterTransition = { if (changesInPlace(motion)) tabEnter(motion) else pushEnter(motion) },
        exitTransition = { if (changesInPlace(motion)) tabExit(motion) else pushExit(motion) },
        popEnterTransition = { if (changesInPlace(motion)) tabEnter(motion) else popEnter(motion) },
        popExitTransition = { if (changesInPlace(motion)) tabExit(motion) else popExit(motion) },
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
                command = requested,
                onClearCommand = { requested = null },
                // Filled here rather than by the library, which has a place on
                // its bar for it and no business knowing what the statistics
                // screen is. This shell is where the two features meet.
                statsButton = {
                    StatsButton(onClick = { navController.navigate(Routes.Stats.route) })
                },
            )
        }

        bookDestinations(navController)
    }
}

/**
 * What the window has been asked for and has not yet had carried out.
 *
 * Collected here rather than on the library screen, which is where all three
 * commands are actually carried out - see `LibraryScreen`. The menu bar and the
 * shortcuts standing in for it belong to the window and are live on every
 * screen, while the library leaves the composition the moment a book is opened.
 * Collected down there, a command sent from the reader would sit unread in the
 * channel until the reader happened to navigate back, and then open a file
 * panel nobody had just asked for.
 *
 * The shelf is brought forward as part of taking the command, because it is
 * what carries all three out and because somebody adding books should see them
 * arrive. A no-op when it is already showing.
 */
@Composable
private fun rememberRequestedCommand(navController: NavController): MutableState<AppCommand?> {
    val commands: AppCommands = koinInject()
    val pending = remember { mutableStateOf<AppCommand?>(null) }

    LaunchedEffect(commands) {
        commands.commands.collect { command ->
            pending.value = command

            navController.popBackStack(Routes.Library.route, inclusive = false)
        }
    }

    return pending
}
