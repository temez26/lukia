package io.lukia.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import io.lukia.Routes
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.LibraryQuery
import io.lukia.features.library.presentation.screens.HomeScreen
import io.lukia.features.library.presentation.screens.HomeShelfScreen
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.library.presentation.screens.SearchScreen
import io.lukia.features.stats.presentation.StatsButton
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.MotionStyle
import io.lukia.shared.presentation.theme.exit
import io.lukia.shared.presentation.theme.standard
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app as a phone holds it: three screens side by side under a tab bar, with
 * a book, its details and the settings pushing over the whole thing.
 *
 * The three tabs share one [LibraryViewModel] rather than each taking its own.
 * They are three views of one library - where you left off, what you searched
 * for, the shelf itself - and three ViewModels would be three reads of a
 * database whose every row carries a cover image, kept in step by hope. Sharing
 * it also lets a home row's own screen show the same books its row does.
 */
@Composable
internal fun MobileShell(library: LibraryViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val startSync = rememberStartSync()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val tab = MobileTab.forRoute(backStackEntry?.destination?.route)

    // Read here rather than inside the transitions: those are plain lambdas
    // rather than composables, so this is the last place that can ask.
    val motion = LocalMotionStyle.current

    // Held rather than read straight from the back stack, so the bar still knows
    // which tab it is marking while it slides away under a screen pushed over it.
    var selectedTab by remember { mutableStateOf(MobileTab.Home) }

    LaunchedEffect(tab) {
        tab?.let { selectedTab = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            // A push everywhere except between the tabs themselves, which are
            // peers rather than a stack - see [changesInPlace], which is also
            // where the reader's motion preset gets its say.
            enterTransition = {
                if (changesInPlace(motion)) tabEnter(motion) else pushEnter(motion)
            },
            exitTransition = {
                if (changesInPlace(motion)) tabExit(motion) else pushExit(motion)
            },
            popEnterTransition = {
                if (changesInPlace(motion)) tabEnter(motion) else popEnter(motion)
            },
            popExitTransition = {
                if (changesInPlace(motion)) tabExit(motion) else popExit(motion)
            },
        ) {
            tabDestinations(navController = navController, library = library, startSync = startSync)

            bookDestinations(navController)
        }

        BottomBar(
            visible = tab != null,
            selected = selectedTab,
            // A tab is somewhere to go only from another one. Asking for the
            // tab already showing pops that screen and puts a restored copy of
            // it back, which the host answers - correctly, from where it sits -
            // by cross-fading a screen into itself; and a press that lands
            // while the bar is sliding out from under a book would take the
            // book straight back off again.
            onSelect = { chosen ->
                if (tab != null && chosen != tab) navController.switchTo(chosen)
            },
            motion = motion,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The three screens the bar switches between.
 *
 * They take the shared [library] rather than each resolving one of their own:
 * three ViewModels would be three reads of a database whose every row carries a
 * cover image, and a favourite marked on one tab would not be a favourite on the
 * next.
 */
private fun NavGraphBuilder.tabDestinations(
    navController: NavController,
    library: LibraryViewModel,
    startSync: () -> Unit,
) {
    val openDetails: (DocumentInfo) -> Unit = { document ->
        navController.navigate(Routes.Detail.createRoute(document.document.location))
    }

    // A server book not downloaded yet has nothing to read: its page is where
    // it is downloaded from.
    val openBook: (DocumentInfo) -> Unit = { document ->
        if (document.awaitsDownload) {
            openDetails(document)
        } else {
            navController.navigate(Routes.Reader.createRoute(document.document.location))
        }
    }

    val openSettings: () -> Unit = { navController.navigate(Routes.Settings.route) }
    val openAccountSettings: () -> Unit = { navController.navigate(Routes.AccountSettings.route) }

    // The search screen's subjects land on the library narrowed to one.
    val showInLibrary: (LibraryQuery) -> Unit = { query ->
        library.updateQuery(query)
        navController.switchTo(MobileTab.Library)
    }

    // Filled here rather than by the library screens, which have a place on
    // their bars for it and no business knowing what the statistics screen is.
    // This shell is where the two features meet.
    val statsButton: @Composable () -> Unit = {
        StatsButton(onClick = { navController.navigate(Routes.Stats.route) })
    }

    tabDestination(Routes.Home.route) {
        HomeScreen(
            onOpenBook = openBook,
            onShowDetails = openDetails,
            onOpenShelf = { shelf -> navController.navigate(Routes.HomeShelf.createRoute(shelf.name)) },
            onOpenLibrary = { navController.switchTo(MobileTab.Library) },
            onSettings = openSettings,
            statsButton = statsButton,
            onSyncWithServer = {
                startSync()
                openAccountSettings()
            },
            viewModel = library,
        )
    }

    tabDestination(Routes.Search.route) {
        SearchScreen(
            onOpenBook = openBook,
            onShowDetails = openDetails,
            onBrowse = showInLibrary,
            viewModel = library,
        )
    }

    tabDestination(Routes.Library.route) {
        LibraryScreen(
            viewModel = library,
            onDocumentClick = openBook,
            onDocumentDetails = openDetails,
            onSettings = openSettings,
            statsButton = statsButton,
            accountPage = AppAccountPage,
            onSyncWithServer = startSync,
            onAccountSettings = openAccountSettings,
            // Searching has a tab of its own here, and a field on this screen as
            // well would be a second search with a second answer to the same
            // question.
            showSearch = false,
        )
    }

    homeShelfDestination(navController, library, openBook, openDetails)
}

/**
 * One of those screens, held clear of the bar itself.
 *
 * The inset is applied here rather than around the whole host so that a book
 * being read - which is not a tab and has no bar over it - is not laid out
 * against a gap that is on its way out. It also consumes the system's own bottom
 * inset, which the bar has already accounted for, so the Scaffold inside each
 * screen does not reserve room for it a second time.
 */
private fun NavGraphBuilder.tabDestination(
    route: String,
    content: @Composable () -> Unit,
) {
    composable(route) {
        val inset = mobileNavBarHeight()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = inset)
                .consumeWindowInsets(PaddingValues(bottom = inset)),
        ) {
            content()
        }
    }
}

/**
 * The bar, arriving and leaving with the screens that own it.
 *
 * It slides down rather than fading, so opening a book reads as the bar being
 * covered rather than as chrome quietly disappearing.
 *
 * At the same pace as the screen covering it: the two are one movement, so a
 * preset stretching one must stretch the other, or the bar is still on its way
 * out from under a screen that has finished arriving.
 */
@Composable
private fun BottomBar(
    visible: Boolean,
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    motion: MotionStyle,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(motion.standard()) { height -> height } +
            fadeIn(motion.standard()),
        exit = slideOutVertically(motion.exit()) { height -> height } +
            fadeOut(motion.exit()),
        modifier = modifier,
    ) {
        MobileNavigationBar(selected = selected, onSelect = onSelect)
    }
}

/**
 * All of one home row, pushed over the tabs rather than one of them, so the bar
 * steps aside - see [MobileTab.forRoute].
 */
private fun NavGraphBuilder.homeShelfDestination(
    navController: NavController,
    library: LibraryViewModel,
    openBook: (DocumentInfo) -> Unit,
    openDetails: (DocumentInfo) -> Unit,
) {
    composable(Routes.HomeShelf.route) { entry ->
        val name = entry.arguments?.read { getStringOrNull(Routes.ARG_SHELF) }
        val shelf = HomeShelf.entries.firstOrNull { it.name == name }

        // Only a route written by hand could name no row; there is nothing to show.
        if (shelf == null) {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return@composable
        }

        HomeShelfScreen(
            shelf = shelf,
            onBack = { navController.popBackStack() },
            onOpenBook = openBook,
            onShowDetails = openDetails,
            // Off this screen first, so the library tab is not left with it
            // saved on top of Home's stack.
            onOpenLibrary = {
                navController.popBackStack()
                navController.switchTo(MobileTab.Library)
            },
            viewModel = library,
        )
    }
}

/**
 * Moves to [tab], keeping the one being left behind exactly as it was.
 *
 * Everything above the home tab is cleared rather than stacked, so the tabs
 * never pile up behind each other and back always means "out of here" rather
 * than "the tab before this one". `saveState` and `restoreState` are what make
 * returning to the library find it scrolled where it was left.
 */
private fun NavController.switchTo(tab: MobileTab) {
    navigate(tab.route) {
        popUpTo(Routes.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
