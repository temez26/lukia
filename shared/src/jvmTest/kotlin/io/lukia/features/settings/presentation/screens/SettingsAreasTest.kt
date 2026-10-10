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
import io.lukia.resources.settings_area_ai_translation
import io.lukia.resources.settings_area_appearance
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

    private fun ComposeUiTest.setSettings(
        settings: AppSettings = AppSettings(),
        /**
         * With AI switched on unless a test says otherwise, because most of what
         * this file asks about is behind that switch - see `AiSwitchSection`.
         */
        translation: TranslationSettings = TranslationSettings().withAiTranslation(true),
    ) {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsBody(
                    settings = settings,
                    reader = ReaderSettings(),
                    translation = translation,
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
        SettingsArea.AiTranslation -> Res.string.settings_area_ai_translation
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
     * Everything a reader has on the day they install the app: the language, what
     * a thumb on the page does, and who answers about a single word - all of it
     * answerable by a dictionary over an ordinary web API.
     */
    @Test
    fun translatingHoldsWhatAnybodyCanUse() = runComposeUiTest {
        setSettings()

        open(SettingsArea.Translating)

        onNodeWithText("Translate into").assertExists()
        onNodeWithText("Pointing at the book").assertExists()
        onNodeWithText("Looking up a word").assertExists()
        onNodeWithText("Reading more about a word").assertExists()
    }

    /**
     * And the other page is everything that only a model can do: the service, how
     * much of the book a gesture may ask it for, and the two ways of asking it
     * for more than the reader pointed at.
     */
    @Test
    fun aiTranslationHoldsWhatOnlyAModelCanDo() = runComposeUiTest {
        setSettings()

        open(SettingsArea.AiTranslation)

        onNodeWithText("Translation service").assertExists()
        onNodeWithText("Asking for more than a word").assertExists()
        onNodeWithText("Translating ahead").assertExists()
    }

    /**
     * And with the switch off, that page is the switch and nothing else. The
     * point of it is that Lukia is then the app it is without a model, and a
     * page still offering an API key is not that.
     */
    @Test
    fun andNoneOfItWhileTheSwitchIsOff() = runComposeUiTest {
        setSettings(translation = TranslationSettings())

        open(SettingsArea.AiTranslation)

        onNodeWithText("Use a translation service").assertExists()
        onNodeWithText("Translation service").assertDoesNotExist()
        onNodeWithText("Asking for more than a word").assertDoesNotExist()
        onNodeWithText("Translating ahead").assertDoesNotExist()
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
     * The whole reason AI translation is a page of its own: somebody changing the
     * language they read into must not have to scroll past an API key to reach
     * it, or past two switches that spend money a screenful at a time.
     */
    @Test
    fun theMachineryIsNotOnTheTranslatingPage() = runComposeUiTest {
        setSettings()

        open(SettingsArea.Translating)

        onNodeWithText("Translation service").assertDoesNotExist()
        onNodeWithText("Asking for more than a word").assertDoesNotExist()
        onNodeWithText("Translating ahead").assertDoesNotExist()
    }

    /**
     * And the dictionary is not on the page about the model. It needs no key, no
     * address and no money, which is the whole reason the two pages are apart.
     */
    @Test
    fun norIsTheDictionaryOnTheOneAboutTheModel() = runComposeUiTest {
        setSettings()

        open(SettingsArea.AiTranslation)

        onNodeWithText("Looking up a word").assertDoesNotExist()
        onNodeWithText("Reading more about a word").assertDoesNotExist()
    }

    private companion object {
        const val DiagnosticsWords = "what the app wrote down"
    }
}
