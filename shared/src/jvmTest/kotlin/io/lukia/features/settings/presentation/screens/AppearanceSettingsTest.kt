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
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme
import io.lukia.features.settings.domain.AppSettings
import io.lukia.resources.Res
import io.lukia.resources.accent_green
import io.lukia.resources.palette_crisp
import io.lukia.resources.palette_crisp_detail
import io.lukia.resources.palette_paper
import io.lukia.resources.palette_paper_detail
import io.lukia.resources.palette_slate
import io.lukia.resources.palette_slate_detail
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.words
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

        // Named by resource rather than by walking the enum: what a palette
        // is called now lives in `ThemeChoicesDisplay.kt`, whose exhaustive
        // `when` is what fails when a palette is added without copy.
        PaletteWords.forEach { (name, detail) ->
            onNodeWithText(words(name)).assertIsDisplayed()
            onNodeWithText(words(detail)).assertIsDisplayed()
        }
    }

    @Test
    fun choosingAPaletteReportsIt() = runComposeUiTest {
        setAppearance()

        onNodeWithText(words(Res.string.palette_slate)).performClick()

        assertEquals(AppPalette.Slate, chosen?.palette)
    }

    /**
     * The swatches carry their names for anything that cannot see a colour,
     * which is also what makes them reachable here.
     */
    @Test
    fun choosingAnAccentReportsIt() = runComposeUiTest {
        setAppearance()

        onNodeWithContentDescription(words(Res.string.accent_green)).performClick()

        assertEquals(AppAccent.Green, chosen?.accent)
    }

    @Test
    fun theLightAndDarkSwitchIsStillTheFirstThingHere() = runComposeUiTest {
        setAppearance()

        onNodeWithText("Dark").performClick()

        assertEquals(AppTheme.Dark, chosen?.theme)
    }
    private companion object {
        /** Each palette's name and the line under it, in the order they appear. */
        val PaletteWords = listOf(
            Res.string.palette_crisp to Res.string.palette_crisp_detail,
            Res.string.palette_paper to Res.string.palette_paper_detail,
            Res.string.palette_slate to Res.string.palette_slate_detail,
        )
    }
}
