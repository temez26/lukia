package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.LukiaVersion
import io.lukia.core.domain.AppPalette
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone's settings list: each group says what it is set to, and pressing it
 * asks for that group's page. The account card is there only when the
 * navigation gives it one.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsHubTest {

    private var opened: SettingsArea? = null

    private fun ComposeUiTest.setHub(account: AccountSummary? = null) {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsHub(
                    settings = AppSettings(palette = AppPalette.Paper),
                    reader = ReaderSettings(),
                    translation = TranslationSettings(targetLanguage = TargetLanguage.Finnish),
                    onOpen = { opened = it },
                    account = account,
                )
            }
        }
    }

    @Test
    fun eachGroupSaysWhatItIsSetTo() = runComposeUiTest {
        setHub()

        onNodeWithText("Paper").assertExists()
        onNodeWithText(TargetLanguage.Finnish.endonym).assertExists()
        onNodeWithText("Off").assertExists()
        onNodeWithText(LukiaVersion).assertExists()
    }

    @Test
    fun pressingAGroupOpensItsPage() = runComposeUiTest {
        setHub()

        onNodeWithText("AI translation").performClick()

        assertEquals(SettingsArea.AiTranslation, opened)
    }

    @Test
    fun theAccountCardOpensTheAccountPage() = runComposeUiTest {
        setHub(account = AccountSummary(title = "teemu", detail = "Home server", initial = "T"))

        onNodeWithText("teemu").performClick()

        assertEquals(SettingsArea.Account, opened)
    }

    @Test
    fun noAccountCardWithoutAnAccountPage() = runComposeUiTest {
        setHub()

        onNodeWithText("Account").assertDoesNotExist()
    }
}
