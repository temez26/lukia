package io.lukia.shared.presentation.components.controls

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/**
 * The bargain the settings pages make: nothing is lost, it just arrives when it
 * is asked for.
 *
 * Both halves are worth holding. A mark that showed its paragraph all along
 * would be a mark for nothing, and a mark whose paragraph could not be reached
 * would have deleted the app's explanation of itself rather than moved it.
 */
@OptIn(ExperimentalTestApi::class)
class InfoButtonTest {

    private fun ComposeUiTest.setInfo() {
        setContent {
            LukiaTheme(darkTheme = false) {
                InfoButton(label = Label, text = Explanation)
            }
        }
    }

    @Test
    fun theExplanationIsNotOnThePageUntilTheMarkIsPressed() = runComposeUiTest {
        setInfo()

        onNodeWithText(Explanation).assertDoesNotExist()
    }

    @Test
    fun pressingTheMarkOpensIt() = runComposeUiTest {
        setInfo()

        onNodeWithContentDescription(Label).performClick()

        onNodeWithText(Explanation).assertExists()
    }

    /**
     * The mark is the way out as well as the way in. A card opened by a press on
     * something that then ignores the next one is a card the reader has to guess
     * how to close.
     */
    @Test
    fun pressingItAgainCloses() = runComposeUiTest {
        setInfo()

        onNodeWithContentDescription(Label).performClick()
        onNodeWithContentDescription(Label).performClick()

        onNodeWithText(Explanation).assertDoesNotExist()
    }

    private companion object {
        const val Label = "More about Motion"
        const val Explanation = "How the app moves when it opens and when screens change."
    }
}
