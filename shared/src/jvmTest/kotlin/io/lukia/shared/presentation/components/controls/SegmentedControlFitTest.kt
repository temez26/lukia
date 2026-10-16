package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.LukiaTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SegmentedControlFitTest {

    /** A label that fits is set whole, at full size. */
    @Test
    fun aLabelThatFitsIsSetWholeAtFullSize() = runComposeUiTest {
        val options = listOf("Appearance", "Reading", "Translating", "Beta", "About")
        setContent {
            LukiaTheme {
                SegmentedControl(options, options[0], { it }, {}, Modifier.width(300.dp))
            }
        }

        val translating = onNodeWithText("Translating").layout()

        assertFalse(translating.hasVisualOverflow)
        assertEquals(LukiaTypography.labelSmall.fontSize, translating.layoutInput.style.fontSize)
    }

    /** Too wide at full size, a label shrinks until it fits rather than being cut short. */
    @Test
    fun aLabelTooWideForItsSegmentShrinksWhole() = runComposeUiTest {
        showSettingsTabs(width = 300)

        val appearance = onNodeWithText("Appearance").layout()
        val reading = onNodeWithText("Reading").layout()

        assertFalse(appearance.hasVisualOverflow, "the label was cut short")
        // Auto-sizing does not show in the layout's style, so the line's height
        // is what says it was set smaller than its full-size neighbour.
        assertTrue(appearance.size.height < reading.size.height, "the label was not shrunk")
    }

    /** Only a label that does not fit even at the smallest size is ellipsised, at that size. */
    @Test
    fun aLabelThatCannotFitAtAnySizeIsEllipsisedAtTheSmallest() = runComposeUiTest {
        showSettingsTabs(width = 150)

        assertEquals(8.sp, onNodeWithText("Translating").layout().layoutInput.style.fontSize)
    }

    private fun ComposeUiTest.showSettingsTabs(width: Int) {
        val options = listOf("Appearance", "Reading", "Translating", "Beta", "About")
        setContent {
            LukiaTheme {
                SegmentedControl(options, options[0], { it }, {}, Modifier.width(width.dp))
            }
        }
    }
}

private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
