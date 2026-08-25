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
import io.lukia.Routes
import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.screens.HomeScreen
import io.lukia.features.library.presentation.screens.LibraryScreen
import io.lukia.features.library.presentation.screens.SearchScreen
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaMotion
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app as a phone holds it: three screens side by side under a tab bar, with
 * a book, its details and the settings pushing over the whole thing.
 *
 * The three tabs share one [LibraryViewModel], held here rather than taken
 * separately by each destination. They are three views of one library - where
 * you left off, what you searched for, and the shelf itself - and three
 * ViewModels would mean three reads of a database whose every row carries a
 * cover image, kept in step by hope. Sharing it is also what lets "See All"
 * under a home row put the library tab into the state that row is a sample of.
 */
@Composable
internal fun MobileShell(library: LibraryViewModel = koinViewModel()) {
    val navController = rememberNavController()
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
            tabDestinations(navController = navController, library = library)

            bookDestinations(navController)
        }

        BottomBar(
            visible = tab != null,
            selected = selectedTab,
            onSelect = navController::switchTo,
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
) {
    val openBook: (DocumentInfo) -> Unit = { document ->
        navController.navigate(Routes.Reader.createRoute(document.document.location))
    }

    val openDetails: (DocumentInfo) -> Unit = { document ->
        navController.navigate(Routes.Detail.createRoute(document.document.location))
    }

    val openSettings: () -> Unit = { navController.navigate(Routes.Settings.route) }

    tabDestination(Routes.Home.route) {
        HomeScreen(
            onOpenBook = openBook,
            onSeeAll = { query ->
                library.updateQuery(query)
                navController.switchTo(MobileTab.Library)
            },
            onOpenLibrary = { navController.switchTo(MobileTab.Library) },
            onSettings = openSettings,
            viewModel = library,
        )
    }

    tabDestination(Routes.Search.route) {
        SearchScreen(
            onOpenBook = openBook,
            onShowDetails = openDetails,
            viewModel = library,
        )
    }

    tabDestination(Routes.Library.route) {
        LibraryScreen(
            viewModel = library,
            onDocumentClick = openBook,
            onDocumentDetails = openDetails,
            onSettings = openSettings,
            // Searching has a tab of its own here, and a field on this screen as
            // well would be a second search with a second answer to the same
            // question.
            showSearch = false,
        )
    }
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
 * covered by the book rather than as chrome quietly disappearing.
 */
@Composable
private fun BottomBar(
    visible: Boolean,
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(LukiaMotion.standard()) { height -> height } +
            fadeIn(LukiaMotion.standard()),
        exit = slideOutVertically(LukiaMotion.exit()) { height -> height } +
            fadeOut(LukiaMotion.exit()),
        modifier = modifier,
    ) {
        MobileNavigationBar(selected = selected, onSelect = onSelect)
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
