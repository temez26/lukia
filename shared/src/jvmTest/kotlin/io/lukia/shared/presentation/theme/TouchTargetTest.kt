package io.lukia.shared.presentation.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.LukiaButton
import kotlin.test.Test

/**
 * That a control somebody presses with a thumb is big enough for one, whatever
 * size the window it is in happens to be.
 *
 * Composed rather than asserted on [metricsFor] alone, because the bug this
 * covers was in the wiring rather than in the numbers: the metrics were right
 * and were being chosen by the wrong question.
 */
@OptIn(ExperimentalTestApi::class)
class TouchTargetTest {

    /** Wider than any phone, and about what an iPad reports in landscape. */
    private val tabletWidth = 1024.dp

    @Test
    fun aTouchedControlMeetsTheMinimumInAWideWindow() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(tabletWidth, 768.dp)) {
                LukiaTheme(darkTheme = false, touch = true) {
                    LukiaButton(label = "Read", onClick = {})
                }
            }
        }

        // Apple's floor, and what every control settles on where a finger is
        // what does the pointing. This window is wide enough to have answered
        // "Expanded" to the width class, which is what used to decide.
        onNodeWithContentDescription("Read").assertHeightIsAtLeast(MinTouchTarget)
    }

    @Test
    fun aPointedControlStaysCompactInTheSameWindow() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(tabletWidth, 768.dp)) {
                LukiaTheme(darkTheme = false, touch = false) {
                    LukiaButton(label = "Read", onClick = {})
                }
            }
        }

        onNodeWithContentDescription("Read")
            .assertHeightIsEqualTo(metricsFor(touch = false).controlHeight)
    }

    @Test
    fun aTouchedControlIsUnchangedByANarrowWindow() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(390.dp, 800.dp)) {
                LukiaTheme(darkTheme = false, touch = true) {
                    LukiaButton(label = "Read", onClick = {})
                }
            }
        }

        onNodeWithContentDescription("Read").assertHeightIsEqualTo(MinTouchTarget)
    }
}
