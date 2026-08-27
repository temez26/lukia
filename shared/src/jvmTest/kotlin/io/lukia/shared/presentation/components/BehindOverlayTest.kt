package io.lukia.shared.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/**
 * That a panel which looks modal is modal.
 *
 * [OverlayPanel] dims the screen behind it and covers the pointer with a scrim,
 * which is where its modality used to stop. Tab walked through the dimming into
 * the library underneath, and a screen reader's swipe-next left the panel after
 * its last row and carried on through the shelf behind it - out of a panel that
 * was still open, with nothing announcing that it was.
 *
 * Asserted through the semantics tree because that is what a screen reader
 * traverses: a node that is not in it cannot be reached, focused or read.
 */
@OptIn(ExperimentalTestApi::class)
class BehindOverlayTest {

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click action labelled '$label'") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    @Composable
    private fun Screen(panelOpen: Boolean) {
        LukiaTheme(darkTheme = false) {
            Box(modifier = Modifier.size(900.dp, 800.dp)) {
                Box(modifier = Modifier.fillMaxSize().behindOverlay(panelOpen)) {
                    Text("A book on the shelf")
                }

                OverlayPanel(visible = panelOpen, onDismiss = {}) {
                    Text("Inside the panel")
                }
            }
        }
    }

    @Test
    fun theShelfIsReachableWithNoPanelOpen() = runComposeUiTest {
        setContent { Screen(panelOpen = false) }

        onNodeWithText("A book on the shelf").assertIsDisplayed()
    }

    @Test
    fun anOpenPanelTakesTheShelfOutOfTheTree() = runComposeUiTest {
        setContent { Screen(panelOpen = true) }

        // Still drawn - the panel dims what is behind rather than hiding it -
        // but no longer something assistive technology or the keyboard can land
        // on, which is the whole difference between dimmed and modal.
        onNodeWithText("A book on the shelf").assertDoesNotExist()
    }

    @Test
    fun thePanelItselfStaysReachable() = runComposeUiTest {
        setContent { Screen(panelOpen = true) }

        onNodeWithText("Inside the panel").assertIsDisplayed()
        onNodeWithContentDescription("Close").assertHasClickAction()
    }

    @Test
    fun theWayOutOfThePanelIsNamed() = runComposeUiTest {
        setContent { Screen(panelOpen = true) }

        // Matched on the click action's own label rather than a content
        // description: the backdrop is not a thing, it is a way out, and what
        // has to be announced is what pressing it does.
        onNode(hasClickLabel("Close this panel")).assertExists()
    }

    @Test
    fun closingThePanelGivesTheShelfBack() = runComposeUiTest {
        val open = mutableStateOf(true)

        setContent { Screen(panelOpen = open.value) }

        onNodeWithText("A book on the shelf").assertDoesNotExist()

        open.value = false

        // The panel stays mounted through its exit animation, so this also says
        // the screen comes back as the panel starts leaving rather than once it
        // has gone - a reader tabbing after pressing Close should not have to
        // wait out an animation to find anything to tab to.
        onNodeWithText("A book on the shelf").assertIsDisplayed()
    }
}
