package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.lukia.resources.Res
import io.lukia.resources.state_off
import io.lukia.resources.state_on
import org.jetbrains.compose.resources.stringResource

/**
 * Makes a row carrying a label and a switch into one control that says what it
 * is as well as what it is set to.
 *
 * A `Switch` composed beside a `Text` is two things to a screen reader and one
 * thing to everybody else. The switch knows its role and its value and has no
 * name; the label is a line of prose that happens to sit next to it. What a
 * reader hears is "on, switch" - the word saying *what* is on is a separate stop
 * before it, and on a settings screen of eleven of these that is eleven switches
 * announced identically.
 *
 * So the row is the control. It carries the name, the role and the state, and
 * the switch inside it is handed `onCheckedChange = null` - which is how
 * Material says "drawn here, but something outside it is the control". A press
 * anywhere along the row toggles it, which is what both platforms do with their
 * own settings rows and what a thumb expects of one.
 *
 * [label] is the name and nothing else. Whatever sentence explains the setting
 * stays where it is: it is help rather than a name, and a name read out with a
 * paragraph behind it is a name nobody waits through.
 */
@Composable
fun Modifier.toggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val state = stringResource(if (checked) Res.string.state_on else Res.string.state_off)

    return minimumInteractiveComponentSize()
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .toggleable(
            value = checked,
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Switch,
            onValueChange = onChange,
        )
        .semantics {
            contentDescription = label
            stateDescription = state
        }
}
