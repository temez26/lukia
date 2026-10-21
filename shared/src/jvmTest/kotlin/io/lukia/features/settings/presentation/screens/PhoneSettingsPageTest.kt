package io.lukia.features.settings.presentation.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The groups as a phone lays them out: the language list behind one row, and a
 * sample of the page above the reading settings. The same groups in a window
 * are covered by the tests beside this one.
 */
@OptIn(ExperimentalTestApi::class)
class PhoneSettingsPageTest {

    private var pickedLanguage = false

    private val phone = PhoneSettingsPage(title = "Translating", onPickLanguage = { pickedLanguage = true })

    @Test
    fun translatingShowsTheLanguageAsOneRowThatOpensTheList() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPhoneSettingsPage provides phone) {
                    TranslatingSettings(
                        translation = TranslationSettings(targetLanguage = TargetLanguage.Finnish),
                        actions = TranslatingActions(),
                        providerActions = ProviderActions(),
                        groupColor = Color.White,
                    )
                }
            }
        }

        onNodeWithText(TargetLanguage.German.endonym).assertDoesNotExist()

        onNodeWithText(TargetLanguage.Finnish.endonym).performClick()

        assertTrue(pickedLanguage)
    }

    @Test
    fun readingOpensOnASampleOfThePage() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                CompositionLocalProvider(LocalPhoneSettingsPage provides phone) {
                    ReadingSettings(reader = ReaderSettings(), onReaderChange = {}, groupColor = Color.White)
                }
            }
        }

        onNodeWithText("The rain had stopped", substring = true).assertExists()
    }
}
