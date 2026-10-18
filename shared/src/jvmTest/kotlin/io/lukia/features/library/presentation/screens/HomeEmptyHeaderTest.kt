package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.features.library.presentation.model.HomeLayout
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.features.stats.presentation.StatsButton
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Home with no books yet. The header carries settings, the reading statistics
 * and the import menu, so it has to be there before the first book is.
 */
@OptIn(ExperimentalTestApi::class)
class HomeEmptyHeaderTest {

    @Test
    fun anEmptyHomeKeepsItsHeader() = runComposeUiTest {
        var openedStats = false

        setContent {
            Box(modifier = Modifier.size(400.dp, 800.dp)) {
                LukiaTheme(darkTheme = false) {
                    HomeContent(
                        uiState = UiState.Success(emptyList()),
                        home = HomeLayout(current = null, rows = emptyList()),
                        actions = HomeBookActions.None,
                        onOpenShelf = {},
                        onImport = {},
                        onImportFolder = {},
                        onRetry = {},
                        statsButton = { StatsButton(onClick = { openedStats = true }) },
                    )
                }
            }
        }

        onNodeWithText("Home").assertIsDisplayed()
        onNodeWithText("Your library is empty").assertIsDisplayed()

        onNodeWithContentDescription("Your reading").performClick()
        assertTrue(openedStats)
    }
}
