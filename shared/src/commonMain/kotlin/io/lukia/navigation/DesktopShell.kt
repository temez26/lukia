package io.lukia.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.lukia.Routes
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.infrastructure.AppCommand
import io.lukia.core.infrastructure.AppCommands
import io.lukia.features.library.presentation.BookDetailSide
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.screens.HomeScreen
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.settings.presentation.SettingsOverlay
import io.lukia.features.settings.presentation.screens.SettingsArea
import io.lukia.features.stats.presentation.StatsButton
import io.lukia.features.stats.presentation.components.WeekGlance
import io.lukia.shared.presentation.components.surface.behindOverlay
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LocalMotionStyle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app as a window: Home and the library as two pages under one bar, with
 * a book, the statistics and a row's own screen pushed over them.
 *
 * The phone's Home, rows and all, rather than a desktop page of its own: the
 * book being read and the shelves are the same answer on either, and the two
 * pages share one [LibraryViewModel] for the reason `MobileShell` gives.
 *
 * Settings open in place over whichever page is showing, so changing a theme
 * does not throw the shelf away - see [SettingsOverlay].
 */
@Composable
internal fun DesktopShell(library: LibraryViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val startSync = rememberStartSync()
    val motion = LocalMotionStyle.current

    val commands: AppCommands = koinInject()
    var requested by rememberRequestedCommand(commands, navController)
    val settings = rememberSettingsOverlayState()

    SettingsCommands(requested, settings, onTake = { requested = null })

    // The book whose details are docked beside the page, by location - see
    // [BookDetailSide], which looks it up again on every pass.
    var detailLocation by rememberSaveable { mutableStateOf<String?>(null) }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route

    val pages = DesktopPages(
        navController = navController,
        openSettings = { area -> settings.show(area) },
        showDetails = { location -> detailLocation = location },
        startSync = startSync,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().behindOverlay(settings.open)) {
            NavHost(
                modifier = Modifier.weight(1f),
                navController = navController,
                startDestination = Routes.Home.route,
                // Set on the host rather than per destination: every move in
                // this shell is a push onto or a pop off a page, or between the
                // two pages, so declaring it once is less to forget.
                enterTransition = { if (changesInPlace(motion)) tabEnter(motion) else pushEnter(motion) },
                exitTransition = { if (changesInPlace(motion)) tabExit(motion) else pushExit(motion) },
                popEnterTransition = { if (changesInPlace(motion)) tabEnter(motion) else popEnter(motion) },
                popExitTransition = { if (changesInPlace(motion)) tabExit(motion) else popExit(motion) },
            ) {
                homePage(pages, library)
                libraryPage(pages, library, command = requested, onClearCommand = { requested = null })
                homeShelfDestination(navController, library, pages.openBook, pages.openDetails)
                bookDestinations(navController)
                windowSettingsDestinations(navController)
            }

            // Beside the pages and the shelf screens only: a book being read or
            // the statistics take the window to themselves.
            // The same LibraryViewModel the pages share: resolved from the same
            // owner, outside the host.
            BookDetailSide(
                location = detailLocation.takeIf { route in PagesWithDetails },
                onDismiss = { detailLocation = null },
                onRead = { document ->
                    detailLocation = null
                    pages.openBook(document)
                },
            )
        }

        SettingsOverlay(
            visible = settings.open,
            onDismiss = { settings.open = false },
            account = AppAccountPage,
            initialArea = settings.area,
        )
    }
}

/** Where a book's details can be docked beside what is showing. */
private val PagesWithDetails = setOf(Routes.Home.route, Routes.Library.route, Routes.HomeShelf.route)

/** What both pages open, written once so a book opens the same way from either. */
private class DesktopPages(
    val navController: NavController,
    val openSettings: (SettingsArea) -> Unit,
    showDetails: (String) -> Unit,
    val startSync: () -> Unit,
) {
    val openDetails: (DocumentInfo) -> Unit = { document -> showDetails(document.document.location) }

    // A server book not downloaded yet has nothing to read: its page is where
    // it is downloaded from.
    val openBook: (DocumentInfo) -> Unit = { document ->
        if (document.awaitsDownload) {
            openDetails(document)
        } else {
            navController.navigate(Routes.Reader.createRoute(document.document.location))
        }
    }

    val openStats: () -> Unit = { navController.navigate(Routes.Stats.route) }

    val tabs: @Composable (MobileTab) -> Unit = { selected ->
        DesktopSectionTabs(selected = selected, onSelect = navController::switchTo)
    }
}

private fun NavGraphBuilder.homePage(pages: DesktopPages, library: LibraryViewModel) {
    composable(Routes.Home.route) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DesktopHomeBar(selected = MobileTab.Home, onSelect = pages.navController::switchTo)

            // At the library's measure, so the two pages line up under the
            // tabs and Home's rows are not stretched across a whole monitor.
            HomeScreen(
                modifier = Modifier.widthIn(max = LocalLukiaDimens.current.maxGridWidth),
                onOpenBook = pages.openBook,
                onShowDetails = pages.openDetails,
                onOpenShelf = { shelf -> pages.navController.navigate(Routes.HomeShelf.createRoute(shelf.name)) },
                onOpenLibrary = { pages.navController.switchTo(MobileTab.Library) },
                onSettings = { pages.openSettings(SettingsArea.Default) },
                stats = { WeekGlance(onClick = pages.openStats) },
                onSyncWithServer = {
                    pages.startSync()
                    pages.openSettings(SettingsArea.Account)
                },
                viewModel = library,
            )
        }
    }
}

private fun NavGraphBuilder.libraryPage(
    pages: DesktopPages,
    library: LibraryViewModel,
    command: AppCommand?,
    onClearCommand: () -> Unit,
) {
    composable(Routes.Library.route) {
        LibraryScreen(
            viewModel = library,
            onDocumentClick = pages.openBook,
            onDocumentDetails = pages.openDetails,
            onSettings = { pages.openSettings(SettingsArea.Default) },
            command = command,
            onClearCommand = onClearCommand,
            // Filled here rather than by the library, which has a place on its
            // bar for it and no business knowing what the statistics screen is.
            statsButton = { StatsButton(onClick = pages.openStats) },
            onSyncWithServer = pages.startSync,
            onAccountSettings = { pages.openSettings(SettingsArea.Account) },
            sections = { pages.tabs(MobileTab.Library) },
        )
    }
}

/**
 * The commands that are about the settings. They open over whichever page is
 * showing, so the shell carries that one out itself; Find is for the shelf
 * behind the settings, so it closes them.
 */
@Composable
private fun SettingsCommands(requested: AppCommand?, settings: SettingsOverlayState, onTake: () -> Unit) {
    val take by rememberUpdatedState(onTake)

    LaunchedEffect(requested) {
        when (requested) {
            AppCommand.OpenSettings -> {
                settings.show(SettingsArea.Default)
                take()
            }
            AppCommand.FindInLibrary -> settings.open = false
            else -> Unit
        }
    }
}

/** Whether the settings are open over the window, and on which page. */
private class SettingsOverlayState(
    private val visible: MutableState<Boolean>,
    private val page: MutableState<SettingsArea>,
) {
    var open: Boolean by visible
    val area: SettingsArea get() = page.value

    fun show(area: SettingsArea) {
        page.value = area
        open = true
    }
}

@Composable
private fun rememberSettingsOverlayState(): SettingsOverlayState {
    val visible = rememberSaveable { mutableStateOf(false) }
    val page = rememberSaveable { mutableStateOf(SettingsArea.Default) }
    return remember(visible, page) { SettingsOverlayState(visible, page) }
}

/**
 * What the window has been asked for and has not yet had carried out.
 *
 * Collected here rather than on the library screen, which is where all of the
 * commands are actually carried out - see `LibraryScreen`. The menu bar and the
 * shortcuts standing in for it belong to the window and are live on every
 * screen, while the library leaves the composition the moment a book is opened.
 * Collected down there, a command sent from the reader would sit unread in the
 * channel until the reader happened to navigate back, and then open a file
 * panel nobody had just asked for.
 *
 * The shelf is brought forward as part of taking any command but Settings,
 * which open over whichever page is showing, because it is what carries them out and because somebody adding books should see them
 * arrive. Popped back to where it is on the stack, or switched to from Home
 * where it is not.
 */
@Composable
internal fun rememberRequestedCommand(
    commands: AppCommands,
    navController: NavController,
): MutableState<AppCommand?> {
    val pending = remember { mutableStateOf<AppCommand?>(null) }

    LaunchedEffect(commands) {
        commands.commands.collect { command ->
            pending.value = command

            val showing = navController.currentDestination?.route == Routes.Library.route

            if (command != AppCommand.OpenSettings &&
                !showing &&
                !navController.popBackStack(Routes.Library.route, inclusive = false)
            ) {
                navController.switchTo(MobileTab.Library)
            }
        }
    }

    return pending
}
