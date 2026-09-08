package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What part of a settings row is the control, which is the switch and only the
 * switch.
 *
 * The whole row used to be. That reads well on a phone's own settings list,
 * where a row is a label and a switch and nothing else, and badly here: these
 * rows carry a beta mark, a mark that opens a paragraph about the setting, and
 * often a line of detail underneath. A reader reaching for any of them turned
 * the setting on instead - and worse, turned it on without being able to say
 * what they had pressed.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsToggleTest {

    private var changed: Boolean? = null

    @Test
    fun theWordsAboutASettingDoNotChangeIt() = runComposeUiTest {
        setToggle()

        onNodeWithText(Label).performClick()

        assertNull(changed, "reading the label turned the setting on")
    }

    @Test
    fun norDoesTheLineUnderThem() = runComposeUiTest {
        setToggle()

        onNodeWithText(Detail).performClick()

        assertNull(changed, "reading the detail turned the setting on")
    }

    /**
     * The switch does, and it is named after the setting - so a screen reader
     * reaches one control that says what it is and what state it is in, rather
     * than a switch with no name beside words with no control.
     */
    @Test
    fun theSwitchDoesAndItCarriesTheName() = runComposeUiTest {
        setToggle()

        onNodeWithContentDescription(Label).performClick()

        assertEquals(true, changed)
    }

    private fun ComposeUiTest.setToggle() {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsToggle(
                    label = Label,
                    detail = Detail,
                    checked = false,
                    onChange = { changed = it },
                )
            }
        }
    }

    private companion object {
        const val Label = "Record what goes wrong"
        const val Detail = "Notes when a page could not be read."
    }
}
