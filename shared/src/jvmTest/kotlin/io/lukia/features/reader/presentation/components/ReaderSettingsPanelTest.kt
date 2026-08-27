package io.lukia.features.reader.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Keeping the screen awake, which is the one setting in the reader's panel that
 * is about the device rather than about the book.
 *
 * The panel is rendered on its own here because the switch is offered by the
 * platform rather than by the screen: this test host is a desktop, where a
 * screen locking itself is not the reader's business and the row is not there at
 * all.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderSettingsPanelTest {

    @Test
    fun aDeviceThatLocksItselfIsOfferedTheSwitch() = runComposeUiTest {
        var settings = ReaderSettings()

        setContent {
            LukiaTheme {
                ReaderSettingsPanel(
                    expanded = true,
                    settings = settings,
                    typographyEnabled = true,
                    layout = null,
                    onSettingsChange = { settings = it },
                    onLayoutChange = {},
                    onDismissRequest = {},
                    keepAwakeOffered = true,
                )
            }
        }

        onNodeWithText("Keep the screen awake").assertIsDisplayed()

        onNode(isToggleable()).performClick()

        assertFalse(settings.keepScreenAwake, "the switch reported nothing")
    }

    @Test
    fun aDesktopIsNotOfferedIt() = runComposeUiTest {
        setContent {
            LukiaTheme {
                ReaderSettingsPanel(
                    expanded = true,
                    settings = ReaderSettings(),
                    typographyEnabled = true,
                    layout = null,
                    onSettingsChange = {},
                    onLayoutChange = {},
                    onDismissRequest = {},
                    keepAwakeOffered = false,
                )
            }
        }

        assertEquals(
            0,
            onAllNodes(hasText("Keep the screen awake")).fetchSemanticsNodes().size,
        )
    }
}
