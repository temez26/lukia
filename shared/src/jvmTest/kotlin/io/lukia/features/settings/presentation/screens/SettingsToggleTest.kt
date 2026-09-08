package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * The long label the reader reported, in a column no wider than the settings
     * page gives a group on a phone.
     *
     * Set out at a real width on purpose: the bug only exists where the label
     * does not fit, and a test host's default window is wide enough to hide it.
     */
    @Test
    fun aLabelTooLongForItsRowWrapsRatherThanRunningUnderTheSwitch() = runComposeUiTest {
        setToggle(label = LongLabel, width = PhoneGroupWidth)

        val label = onNodeWithText(LongLabel).getBoundsInRoot()
        val switch = onNodeWithContentDescription(LongLabel).getBoundsInRoot()

        assertTrue(
            label.bottom - label.top > OneLine,
            "the label stayed on one line, which is what pushed the switch out",
        )
        assertTrue(
            switch.right <= PhoneGroupWidth,
            "the switch was pushed past the end of the row: it ends at " +
                "${switch.right} in a row $PhoneGroupWidth wide",
        )
        assertTrue(
            label.right <= switch.left,
            "the label ran under the switch: it ends at ${label.right}, " +
                "the switch starts at ${switch.left}",
        )
    }

    private fun ComposeUiTest.setToggle(
        label: String = Label,
        width: Dp = Dp.Unspecified,
    ) {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsToggle(
                    label = label,
                    detail = Detail,
                    checked = false,
                    onChange = { changed = it },
                    modifier = if (width == Dp.Unspecified) {
                        Modifier
                    } else {
                        Modifier.width(width)
                    },
                )
            }
        }
    }

    private companion object {
        const val Label = "Record what goes wrong"
        const val Detail = "Notes when a page could not be read."

        /** The row that started this: it is longer than a phone's group is wide. */
        const val LongLabel = "Hold a word again for more about it"

        /** About what a settings group gets on a phone, insets taken off. */
        val PhoneGroupWidth = 320.dp

        /** Taller than this and the label is on more than one line. */
        val OneLine = 28.dp
    }
}
