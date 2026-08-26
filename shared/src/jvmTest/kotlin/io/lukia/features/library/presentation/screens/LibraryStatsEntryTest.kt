package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.features.library.presentation.LibraryFacets
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.stats.presentation.StatsButton
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How the reader reaches their reading statistics from the library.
 *
 * The way in used to be a row in the actions menu, which is the app's list of
 * things done to a shelf - importing, tidying, settings. How much somebody has
 * read is not one of those, so it has a control of its own on the bar, and these
 * pin both halves of that: that it is there, and that it is not in the menu.
 *
 * The bar takes the control as a slot rather than building it, so what is filled
 * in here is what `MobileShell` and `DesktopShell` fill in for real.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryStatsEntryTest {

    private var openedStats = false

    private fun ComposeUiTest.setLibrary() {
        setContent {
            Box(modifier = Modifier.size(1000.dp, 900.dp)) {
                LukiaTheme(darkTheme = false) {
                    LibraryContent(
                        uiState = UiState.Success(emptyList()),
                        books = emptyList(),
                        facets = LibraryFacets.Empty,
                        query = LibraryQuery(),
                        onQueryChange = {},
                        onImport = {},
                        onImportFolder = {},
                        onDocumentClick = {},
                        onDocumentDetails = {},
                        onToggleFavorite = {},
                        onRefresh = {},
                        onDelete = {},
                        onRetry = {},
                        isImporting = false,
                        statsButton = {
                            StatsButton(onClick = { openedStats = true })
                        },
                    )
                }
            }
        }
    }

    @Test
    fun theWayIntoTheStatisticsIsOnTheBar() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Your reading").assertIsDisplayed()
    }

    @Test
    fun pressingItOpensTheStatistics() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Your reading").performClick()

        assertTrue(openedStats)
    }

    @Test
    fun theActionsMenuStaysAboutTheShelf() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Library actions").performClick()

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Your reading").assertDoesNotExist()
    }
}
