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
import io.lukia.features.profile.presentation.ProfileAvatarButton
import io.lukia.features.profile.presentation.components.ProfileFace
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How the reader reaches their own page from the library.
 *
 * The disc used to be a row in the actions menu, which is the app's list of
 * things done to a shelf - importing, tidying, settings. A profile is not one of
 * those, so it has a control of its own on the bar, and these pin both halves of
 * that: that it is there, and that it is no longer in the menu.
 *
 * The bar takes the control as a slot rather than building it, so what is filled
 * in here is what `MobileShell` and `DesktopShell` fill in for real.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryProfileEntryTest {

    private var openedProfile = false

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
                        profileButton = {
                            ProfileAvatarButton(
                                face = ProfileFace(initials = "AL"),
                                onClick = { openedProfile = true },
                            )
                        },
                    )
                }
            }
        }
    }

    @Test
    fun theReadersOwnDiscIsOnTheBar() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Your profile").assertIsDisplayed()
    }

    @Test
    fun pressingItOpensTheProfile() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Your profile").performClick()

        assertTrue(openedProfile)
    }

    @Test
    fun theActionsMenuNoLongerCarriesIt() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Library actions").performClick()

        onNodeWithText("Settings").assertIsDisplayed()
        onNodeWithText("Profile").assertDoesNotExist()
    }
}
