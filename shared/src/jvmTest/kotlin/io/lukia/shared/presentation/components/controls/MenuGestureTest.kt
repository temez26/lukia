package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Asking for the menu that belongs to the thing under the pointer, on each of
 * the two kinds of machine the app runs on.
 *
 * Both gestures are tested against the same modifier because the whole point of
 * it is that neither machine is left without one - and that neither is offered
 * the other's. A press and hold with a mouse is half a second of nothing
 * followed by a menu that could have been there at once.
 */
@OptIn(ExperimentalTestApi::class)
class MenuGestureTest {

    @Test
    fun aFingerHeldOnItOpensTheMenu() = runComposeUiTest {
        val opened = mutableListOf<String>()

        setGesture(touch = true, opened = opened)

        onNodeWithTag("thing").performTouchInput { longClick() }

        assertEquals(listOf("menu"), opened)
    }

    /**
     * And on a machine with a pointer, holding opens no menu at all - it is the
     * slow click it looks like, which is what a click has always been. Half a
     * second of nothing followed by a menu is not a gesture anybody who uses a
     * desktop reaches for, and the second button is there instead.
     */
    @Test
    fun holdingAPointerDownOpensNoMenu() = runComposeUiTest {
        val opened = mutableListOf<String>()

        setGesture(touch = false, opened = opened)

        onNodeWithTag("thing").performTouchInput { longClick() }

        assertEquals(listOf("click"), opened)
    }

    /**
     * Two fingers on the trackpad, which the machine reports as a right click.
     *
     * And it is not also the click it is made of: a press that opened the menu
     * and opened the book on the way would be one gesture doing two things.
     */
    @Test
    fun theSecondButtonOpensTheMenuAndNothingElse() = runComposeUiTest {
        val opened = mutableListOf<String>()

        setGesture(touch = false, opened = opened)

        onNodeWithTag("thing").performMouseInput { rightClick() }

        assertEquals(listOf("menu"), opened)
    }

    /**
     * On a phone too. Nothing there sends a second button, so it costs nothing -
     * and a tablet with a mouse plugged into it is a machine that has both.
     */
    @Test
    fun theSecondButtonOpensTheMenuOnATouchMachineToo() = runComposeUiTest {
        val opened = mutableListOf<String>()

        setGesture(touch = true, opened = opened)

        onNodeWithTag("thing").performMouseInput { rightClick() }

        assertEquals(listOf("menu"), opened)
    }

    @Test
    fun anOrdinaryClickStillOpensTheThingItself() = runComposeUiTest {
        val opened = mutableListOf<String>()

        setGesture(touch = false, opened = opened)

        onNodeWithTag("thing").performClick()

        assertEquals(listOf("click"), opened)
    }

    private fun ComposeUiTest.setGesture(touch: Boolean, opened: MutableList<String>) {
        setContent {
            val interactionSource = remember { MutableInteractionSource() }

            Box(
                Modifier
                    .size(80.dp)
                    .testTag("thing")
                    .menuGesture(
                        interactionSource = interactionSource,
                        onClick = { opened += "click" },
                        onMenu = { opened += "menu" },
                        touch = touch,
                    ),
            )
        }
    }
}
