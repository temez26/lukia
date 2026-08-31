package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.menu.MenuRow
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/**
 * That the app's drawn controls say what they are.
 *
 * Every control here is built from a `clickable` box rather than from Material's
 * `Button`, `IconButton` or `DropdownMenuItem` - see [LukiaButton]. Those carry a
 * role for free; a box does not, and `clickable` sets none of its own. Without
 * one a screen reader announces the app's buttons as text that happens to be
 * pressable, which is a regression nothing else in the build would catch: the
 * control still draws, still clicks, and still passes every other test.
 *
 * Asserted through the public components rather than through a modifier helper,
 * because the role is only worth anything where it actually reaches a node.
 */
@OptIn(ExperimentalTestApi::class)
class ControlRoleTest {

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun textButtonIsAnnouncedAsAButton() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                LukiaButton(label = "Open", onClick = {})
            }
        }

        onNodeWithContentDescription("Open").assert(hasRole(Role.Button))
    }

    @Test
    fun disabledTextButtonKeepsItsRole() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                LukiaButton(label = "Open", onClick = {}, enabled = false)
            }
        }

        // A control somebody cannot use right now is still a button, and a
        // screen reader has to be able to say so before saying it is dimmed.
        onNodeWithContentDescription("Open").assert(hasRole(Role.Button))
    }

    @Test
    fun iconButtonIsAnnouncedAsAButton() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                LukiaIconButton(label = "Close", onClick = {}) { tint, glyph ->
                    PlusGlyph(tint = tint, modifier = glyph)
                }
            }
        }

        onNodeWithContentDescription("Close").assert(hasRole(Role.Button))
    }

    @Test
    fun menuRowIsAnnouncedAsAButton() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(400.dp, 400.dp)) {
                LukiaTheme(darkTheme = false) {
                    MenuRow(label = "Sort by title", onClick = {})
                }
            }
        }

        onNodeWithContentDescription("Sort by title").assert(hasRole(Role.Button))
    }

    @Test
    fun aChosenMenuRowIsStillAButton() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(400.dp, 400.dp)) {
                LukiaTheme(darkTheme = false) {
                    MenuRow(label = "Sort by title", onClick = {}, selected = true)
                }
            }
        }

        // The tick is carried by the selected flag rather than by the role - a
        // chosen menu item is how the platforms themselves announce this.
        onNodeWithContentDescription("Sort by title")
            .assert(hasRole(Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun chipIsAnnouncedAsAButton() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                LukiaChip(label = "PDF", selected = false, onClick = {})
            }
        }

        // Found by its text rather than a description: a chip's label is
        // visible, so it needs no second copy of itself for a screen reader.
        onNodeWithText("PDF").assert(hasRole(Role.Button))
    }
}
