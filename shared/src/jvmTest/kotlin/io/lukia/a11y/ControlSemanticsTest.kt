package io.lukia.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.LukiaChip
import io.lukia.shared.presentation.components.controls.toggleRow
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/**
 * That the controls this app draws for itself say what they are and what they
 * are set to.
 *
 * Every one of these was a `clickable` with a colour change: the app knew a
 * filter was on, drew it as a fill measuring 1.1:1 against the chip beside it,
 * and told a screen reader only that there was a button called "Fiction". A
 * switch beside a label was worse, announcing "on, switch" with the word saying
 * *what* was on a separate stop before it.
 *
 * Asserted through the semantics tree, which is what assistive technology reads:
 * a state that is only painted is a state only some people have.
 */
@OptIn(ExperimentalTestApi::class)
class ControlSemanticsTest {

    private fun hasRole(role: Role) = SemanticsMatcher("has role $role") { node ->
        node.config.getOrNull(SemanticsProperties.Role) == role
    }

    private fun hasStateDescription(state: String) =
        SemanticsMatcher("state description '$state'") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription) == state
        }

    @Test
    fun aFilterChipSaysWhetherItIsSet() = runComposeUiTest {
        var selected by mutableStateOf(false)

        setContent {
            LukiaTheme(darkTheme = false) {
                Box(modifier = Modifier.size(400.dp)) {
                    LukiaChip(
                        label = "Fiction",
                        selected = selected,
                        onClick = { selected = !selected },
                    )
                }
            }
        }

        // Named by its own label, which the toggle merges - the chip has one
        // word in it and repeating that word as a description would have a
        // screen reader say it twice.
        onNodeWithText("Fiction").assertIsOff()
        onNodeWithText("Fiction").assert(hasRole(Role.Checkbox))
        onNodeWithText("Fiction").assert(hasStateDescription("Off"))

        onNodeWithText("Fiction").performClick()

        onNodeWithText("Fiction").assertIsOn()
        onNodeWithText("Fiction").assert(hasStateDescription("On"))
    }

    @Composable
    private fun SettingRow(checked: Boolean, onChange: (Boolean) -> Unit) {
        LukiaTheme(darkTheme = false) {
            Box(modifier = Modifier.size(400.dp)) {
                Box(
                    modifier = Modifier.toggleRow(
                        label = "Keep the screen awake",
                        checked = checked,
                        onChange = onChange,
                    ),
                ) {
                    Text("Keep the screen awake")
                    Switch(checked = checked, onCheckedChange = null)
                }
            }
        }
    }

    @Test
    fun aSettingsSwitchIsNamedByItsRow() = runComposeUiTest {
        var checked by mutableStateOf(false)

        setContent { SettingRow(checked = checked, onChange = { checked = it }) }

        // The name comes from the row; the switch inside it is drawing only.
        onNodeWithContentDescription("Keep the screen awake").assertIsOff()
        onNodeWithContentDescription("Keep the screen awake").assert(hasRole(Role.Switch))

        onNodeWithContentDescription("Keep the screen awake").performClick()

        onNodeWithContentDescription("Keep the screen awake").assertIsOn()
    }
}
