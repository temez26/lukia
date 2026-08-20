package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.settings.domain.AppAccent
import io.lukia.features.settings.domain.AppPalette
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three appearance controls, as somebody using them meets them: a palette is
 * chosen by its name, an accent by its colour.
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceSettingsTest {

    private var chosen: AppSettings? = null

    private fun ComposeUiTest.setAppearance(settings: AppSettings = AppSettings()) {
        setContent {
            LukiaTheme(darkTheme = false, palette = settings.palette, accent = settings.accent) {
                Column {
                    AppearanceSettings(
                        settings = settings,
                        onChange = { chosen = it },
                        groupColor = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }

    @Test
    fun everyPaletteIsOfferedByNameAndBySomethingAboutIt() = runComposeUiTest {
        setAppearance()

        AppPalette.entries.forEach { palette ->
            onNodeWithText(palette.title).assertIsDisplayed()
            onNodeWithText(palette.detail).assertIsDisplayed()
        }
    }

    @Test
    fun choosingAPaletteReportsIt() = runComposeUiTest {
        setAppearance()

        onNodeWithText(AppPalette.Slate.title).performClick()

        assertEquals(AppPalette.Slate, chosen?.palette)
    }

    /**
     * The swatches carry their names for anything that cannot see a colour,
     * which is also what makes them reachable here.
     */
    @Test
    fun choosingAnAccentReportsIt() = runComposeUiTest {
        setAppearance()

        onNodeWithContentDescription(AppAccent.Green.title).performClick()

        assertEquals(AppAccent.Green, chosen?.accent)
    }

    @Test
    fun theLightAndDarkSwitchIsStillTheFirstThingHere() = runComposeUiTest {
        setAppearance()

        onNodeWithText("Dark").performClick()

        assertEquals(AppTheme.Dark, chosen?.theme)
    }
}
