package io.lukia.features.reader.presentation.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
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

    /**
     * That a setting in the wide panel is still named, not just present.
     *
     * The label and its control share one row, and the control fills whatever
     * width it is offered: measured first, it took the row and left the label
     * beside it laid out at nothing. Every label was still in the tree and every
     * assertion about one still passed, while the panel drew six unlabelled
     * tracks - so this is asserted on the width rather than on existence.
     */
    @Test
    fun aWideSettingIsNamedBesideItsControl() = runComposeUiTest {
        setContent {
            // Wider than Compact, which is what puts the label beside the
            // control rather than above it.
            Box(modifier = Modifier.size(1024.dp, 768.dp)) {
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
        }

        onNodeWithText("Theme").assertWidthIsAtLeast(MinimumLabelWidth)
        onNodeWithText("Line spacing").assertWidthIsAtLeast(MinimumLabelWidth)
    }
}

/** Enough of a measure to hold a word, as against the nothing the labels had. */
private val MinimumLabelWidth = 48.dp
