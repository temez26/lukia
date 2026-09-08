package io.lukia.features.settings.presentation.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.resources.Res
import io.lukia.resources.settings_about
import io.lukia.resources.settings_area_appearance
import io.lukia.resources.settings_area_providers
import io.lukia.resources.settings_area_reading
import io.lukia.resources.settings_area_translating
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.words
import kotlin.test.Test

/**
 * Where each setting lives, as somebody looking for one meets it.
 *
 * The point of the five groups is that a reader who knows what they want to
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

    private fun ComposeUiTest.setSettings(settings: AppSettings = AppSettings()) {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsBody(
                    settings = settings,
                    reader = ReaderSettings(),
                    translation = TranslationSettings(),
                    providerState = ProviderState(),
                    onSettingsChange = {},
                    onReaderChange = {},
                    translatingActions = TranslatingActions(),
                    providerActions = ProviderActions(),
                    diagnostics = { DiagnosticsMarker() },
                )
            }
        }
    }

    /**
     * Named by resource rather than by the enum: what a section is called now
     * lives in `SettingsAreaDisplay.kt`, whose exhaustive `when` is what fails
     * when a section is added without a name.
     */
    private fun ComposeUiTest.open(area: SettingsArea) {
        onNodeWithText(words(area.titleResource())).performClick()
    }

    private fun SettingsArea.titleResource() = when (this) {
        SettingsArea.Appearance -> Res.string.settings_area_appearance
        SettingsArea.Reading -> Res.string.settings_area_reading
        SettingsArea.Translating -> Res.string.settings_area_translating
        SettingsArea.Providers -> Res.string.settings_area_providers
        SettingsArea.About -> Res.string.settings_about
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

        onNodeWithText("Looking up a word").assertExists()
        onNodeWithText("AI translation").assertExists()
    }

    /**
     * Which Lukia this is, behind a segment of its own.
     *
     * It used to sit at the foot of all four groups, so that the version a bug
     * report asks for was never behind a guess. That put a paragraph about
     * licences at the end of every page in Settings, which is a worse answer to
     * the same problem than a segment named after the question.
     */
    @Test
    fun aboutIsASegmentOfItsOwn() = runComposeUiTest {
        setSettings()

        open(SettingsArea.About)

        onNodeWithText("Open-source licences").assertExists()
    }

    /**
     * And is only there. A group repeated under every other one is a group the
     * reader scrolls past four times looking for something else.
     */
    @Test
    fun andIsNotUnderTheOtherGroups() = runComposeUiTest {
        setSettings()

        (SettingsArea.entries - SettingsArea.About).forEach { area ->
            if (area != SettingsArea.Default) open(area)

            onNodeWithText("Open-source licences").assertDoesNotExist()
        }
    }

    /**
     * The record of what went wrong is a stack trace and a log. It is what a bug
     * report is built from, and it is not one of the things somebody opens
     * Settings to change - so nothing on the page shows it until the developer
     * settings have been asked for.
     */
    @Test
    fun theDiagnosticsAreOffThePageUntilTheDeveloperSettingsAreOn() = runComposeUiTest {
        setSettings()

        open(SettingsArea.About)

        onNodeWithText(DiagnosticsWords).assertDoesNotExist()
    }

    /**
     * Under About once they are on, which is where the version line that
     * unlocked them is - not at the foot of whichever group happens to be open.
     */
    @Test
    fun theyAreUnderAboutOnceTheyAre() = runComposeUiTest {
        setSettings(AppSettings(developerMode = true))

        onNodeWithText(DiagnosticsWords).assertDoesNotExist()

        open(SettingsArea.About)

        onNodeWithText(DiagnosticsWords).assertExists()
    }

    /** Stands in for the group, which needs a view model this test has not got. */
    @Composable
    private fun DiagnosticsMarker() {
        Text(DiagnosticsWords)
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

        onNodeWithText("AI translation").assertDoesNotExist()
        onNodeWithText("Looking up a word").assertDoesNotExist()
    }

    private companion object {
        const val DiagnosticsWords = "what the app wrote down"
    }
}
