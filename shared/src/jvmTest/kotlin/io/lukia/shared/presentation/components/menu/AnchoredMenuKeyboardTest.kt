package io.lukia.shared.presentation.components.menu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * A desktop menu driven from the keyboard alone. The menu used to be a
 * non-focusable popup, so the arrow keys moved focus around the page behind it
 * and no row could be reached.
 */
@OptIn(ExperimentalTestApi::class)
class AnchoredMenuKeyboardTest {

    private var expanded by mutableStateOf(true)

    private fun ComposeUiTest.showMenu() {
        setContent {
            AnchoredMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MenuRow(label = First, onClick = {})
                MenuRow(label = Second, onClick = {})
                MenuRow(label = Last, onClick = {})
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithContentDescription(First).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun downFromAnOpenedMenuLandsOnItsFirstRow() = runComposeUiTest {
        showMenu()

        press(Key.DirectionDown)

        onNodeWithContentDescription(First).assertIsFocused()
    }

    @Test
    fun downAndUpMoveRowByRow() = runComposeUiTest {
        showMenu()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        onNodeWithContentDescription(Second).assertIsFocused()

        press(Key.DirectionUp)
        onNodeWithContentDescription(First).assertIsFocused()
    }

    @Test
    fun endAndHomeJumpToTheEnds() = runComposeUiTest {
        showMenu()

        press(Key.MoveEnd)
        onNodeWithContentDescription(Last).assertIsFocused()

        press(Key.MoveHome)
        onNodeWithContentDescription(First).assertIsFocused()
    }

    @Test
    fun escapeClosesTheMenu() = runComposeUiTest {
        showMenu()

        press(Key.Escape)

        assertFalse(expanded, "Escape left the menu open")
    }

    private companion object {
        const val First = "First"
        const val Second = "Second"
        const val Last = "Last"
    }
}
