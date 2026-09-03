package io.lukia.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

/**
 * What the tab bar does when the screen is turned.
 *
 * A phone in landscape is the one window in the app where vertical room is what
 * has run out, and the bar is the fixed cost every screen behind it pays. These
 * cover the trade it makes there: it gets shorter, and it does not get shorter
 * by becoming something a thumb cannot hit.
 */
@OptIn(ExperimentalTestApi::class)
class MobileNavigationBarTest {

    /** A 390x844 phone, upright. */
    private val portrait = 390.dp to 844.dp

    /** ...and the same phone turned on its side. */
    private val landscape = 844.dp to 390.dp

    private fun tallTabHeight(): Dp = MinTouchTarget + Space.md

    @Test
    fun anUprightPhoneStacksEachTabsNameUnderItsMark() = runComposeUiTest {
        setBar(portrait)

        onNodeWithContentDescription("Library").assertHeightIsEqualTo(tallTabHeight())
    }

    @Test
    fun aTurnedPhoneGivesTheBookBackTheRoomTheStackWasTaking() = runComposeUiTest {
        setBar(landscape)

        // Set beside its mark rather than under it - which is the shape both
        // platforms give a tab bar on a screen this short.
        onNodeWithContentDescription(nameOf(MobileTab.Library)).assertHeightIsEqualTo(MinTouchTarget)
    }

    @Test
    fun aShorterBarIsStillSomethingAThumbCanHit() = runComposeUiTest {
        setBar(landscape)

        // The room comes out of the padding around the mark, never out of the
        // target: Apple's floor holds at every window the app opens in.
        MobileTab.entries.forEach { tab ->
            onNodeWithContentDescription(nameOf(tab)).assertHeightIsAtLeast(MinTouchTarget)
        }
    }

    @Test
    fun everyTabKeepsItsNameOnAShortScreen() = runComposeUiTest {
        setBar(landscape)

        // Dropping the labels is the other way to save the height, and the wrong
        // one: it asks the reader to learn what a house means in this app.
        MobileTab.entries.forEach { tab ->
            onNodeWithContentDescription(nameOf(tab)).assertIsDisplayed()
        }
    }

    /**
     * A tab's name as the bar announces it. Read out of the resources rather
     * than written here again, so the test asserts that the bar shows the name
     * the app holds rather than that two English tables agree.
     */
    private fun nameOf(tab: MobileTab): String = runBlocking { getString(tab.label) }

    private fun ComposeUiTest.setBar(window: Pair<Dp, Dp>) {
        setContent {
            Box(modifier = Modifier.size(window.first, window.second)) {
                LukiaTheme(darkTheme = false, touch = true) {
                    MobileNavigationBar(selected = MobileTab.Home, onSelect = {})
                }
            }
        }
    }
}
