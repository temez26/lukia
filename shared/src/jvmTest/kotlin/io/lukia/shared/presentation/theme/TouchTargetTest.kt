package io.lukia.shared.presentation.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.PlusGlyph
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

    @Test
    fun aMarkDrawnSmallerThanAThumbIsStillHitByOne() = runComposeUiTest {
        // The ✕ beside a recent search and the actions mark in a cover's corner
        // are drawn at [LukiaMetrics.iconButtonSmall] on purpose - a 44dp disc
        // beside a one-line row is a control shouting over what it acts on. What
        // is small is the drawing; what a thumb aims at is not.
        setContent {
            Box(modifier = Modifier.size(390.dp, 800.dp)) {
                LukiaTheme(darkTheme = false, touch = true) {
                    LukiaIconButton(
                        label = "Forget",
                        onClick = {},
                        modifier = Modifier.testTag("forget"),
                        size = metricsFor(touch = true).iconButtonSmall,
                    ) { tint, glyph -> PlusGlyph(tint = tint, modifier = glyph) }
                }
            }
        }

        // Asserted on what the button reserves rather than on what it draws.
        // The mark inside is still the small one - that is the point - and the
        // room around it is what stops this target and its neighbour's from
        // overlapping, which is the ambiguity a press in the gap resolves badly.
        onNodeWithTag("forget")
            .assertHeightIsAtLeast(MinTouchTarget)
            .assertWidthIsAtLeast(MinTouchTarget)

        onNodeWithContentDescription("Forget")
            .assertHeightIsEqualTo(metricsFor(touch = true).iconButtonSmall)
    }

    @Test
    fun theSameMarkTakesNoExtraRoomUnderAPointer() = runComposeUiTest {
        // Where a mouse is doing the pointing there is nothing to enforce, and
        // padding a 24dp mark out to 44 is how a desktop window comes to look
        // like a scaled-up phone.
        val pointer = metricsFor(touch = false)

        setContent {
            Box(modifier = Modifier.size(tabletWidth, 768.dp)) {
                LukiaTheme(darkTheme = false, touch = false) {
                    LukiaIconButton(
                        label = "Forget",
                        onClick = {},
                        modifier = Modifier.testTag("forget"),
                        size = pointer.iconButtonSmall,
                    ) { tint, glyph -> PlusGlyph(tint = tint, modifier = glyph) }
                }
            }
        }

        onNodeWithTag("forget").assertHeightIsEqualTo(pointer.iconButtonSmall)
    }
}
