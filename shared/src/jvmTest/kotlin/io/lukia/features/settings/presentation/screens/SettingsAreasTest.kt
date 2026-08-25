package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/**
 * Where each setting lives, as somebody looking for one meets it.
 *
 * The point of the four groups is that a reader who knows what they want to
 * change can guess which segment it is behind. That is a claim about the page
 * rather than about any control on it, so it is tested by opening each segment
 * and looking for the sections that were promised - and, as much, for the ones
 * that were promised elsewhere.
 *
 * Presence rather than visibility: a group is taller than the test window, so
 * asking whether its last section is on screen would be asking about the window
 * rather than about where the setting lives.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsAreasTest {

    private fun ComposeUiTest.setSettings() {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsBody(
                    settings = AppSettings(),
                    reader = ReaderSettings(),
                    translation = TranslationSettings(),
                    providerState = ProviderState(),
                    onAppearanceChange = {},
                    onReaderChange = {},
                    translatingActions = TranslatingActions(),
                    providerActions = ProviderActions(),
                )
            }
        }
    }

    private fun ComposeUiTest.open(area: SettingsArea) {
        onNodeWithText(area.title).performClick()
    }

    @Test
    fun readingHoldsHowABookIsSet() = runComposeUiTest {
        setSettings()

        open(SettingsArea.Reading)

        onNodeWithText("The page").assertExists()
        onNodeWithText("Text and images").assertExists()
        onNodeWithText("Opening a PDF").assertExists()
    }

    /**
     * The language, the gestures and the expensive ways of translating: what is
     * translated, rather than who does it.
     */
    @Test
    fun translatingHoldsWhatIsTranslated() = runComposeUiTest {
        setSettings()

        open(SettingsArea.Translating)

        onNodeWithText("Translate into").assertExists()
        onNodeWithText("Pointing at the book").assertExists()
        onNodeWithText("Translating ahead").assertExists()
    }

    @Test
    fun providersHoldsWhoAnswers() = runComposeUiTest {
        setSettings()

        open(SettingsArea.Providers)

        onNodeWithText("Translation service").assertExists()
        onNodeWithText("Looking up a word").assertExists()
    }

    /**
     * The whole reason Providers is a group of its own: somebody changing the
     * language they read into must not have to scroll past an API key to reach
     * it.
     */
    @Test
    fun theMachineryIsNotOnTheTranslatingPage() = runComposeUiTest {
        setSettings()

        open(SettingsArea.Translating)

        onNodeWithText("Translation service").assertDoesNotExist()
        onNodeWithText("Looking up a word").assertDoesNotExist()
    }
}
