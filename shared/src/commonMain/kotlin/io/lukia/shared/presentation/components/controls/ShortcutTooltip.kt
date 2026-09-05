package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lukia.resources.Res
import io.lukia.resources.shortcut_tooltip
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.WindowWidthClass
import org.jetbrains.compose.resources.stringResource

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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The same content is emitted from two different places in the tree - a bare
    // Box on a touch screen, a TooltipBox where there is a pointer - and a window
    // dragged across the boundary between them moves it from one to the other.
    // Made movable so it takes its state with it: without this, resizing a
    // desktop window past the compact width resets whatever the wrapped control
    // remembers, because Compose sees the second call site as a different thing.
    val slot = remember(content) { movableContentOf(content) }

    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        Box(modifier = modifier) { slot() }
        return
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = stringResource(Res.string.shortcut_tooltip, description, shortcut),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        state = rememberTooltipState(),
        focusable = false,
        enableUserInput = true,
        modifier = modifier,
    ) {
        slot()
    }
}
