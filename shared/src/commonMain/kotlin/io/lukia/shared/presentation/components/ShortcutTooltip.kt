package io.lukia.shared.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.WindowWidthClass

/**
 * What a control does, and the key that does it, on hover.
 *
 * Only where there is a pointer to hover with. A tooltip is how a desktop app
 * teaches its shortcuts - the alternative is a help screen nobody opens - but on
 * a touch screen it is a label that never appears, attached to a shortcut that
 * cannot be typed. Window width stands in for the pointer, as it does elsewhere
 * in the app: the platforms with keyboards are the ones with room.
 *
 * The content keeps its own semantics. This adds a hover affordance and nothing
 * a screen reader has to hear twice - the control underneath already names
 * itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutTooltip(
    description: String,
    shortcut: String,
    content: @Composable () -> Unit
) {
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        content()
        return
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = "$description  $shortcut",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        state = rememberTooltipState(),
        focusable = false,
        enableUserInput = true
    ) {
        Box(modifier = Modifier) { content() }
    }
}
