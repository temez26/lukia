package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.LukiaTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}

private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}
