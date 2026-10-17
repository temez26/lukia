package io.lukia.shared.presentation.components.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.resources.Res
import io.lukia.resources.action_cancel
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Asks before something that cannot be taken back.
 *
 * A platform dialog rather than one of this app's own panels: a modal question
 * must be unmistakably not part of the page behind it, must trap focus, and must
 * be reachable by every assistive technology on every platform. That is a great
 * deal to re-earn for a house style, on the one surface where being surprising
 * is the point.
 *
 * [confirmLabel] names the action rather than agreeing with the question - a
 * button that says "Delete" tells a reader what pressing it does even if they
 * never read the sentence above it, and "Yes" does not.
 *
 * [content] is for what the action needs filled in, under the message; while
 * [confirmEnabled] is false the action cannot be pressed.
 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    if (!visible) return

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                content?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
                enabled = confirmEnabled,
            ) {
                Text(
                    text = confirmLabel,
                    color = when {
                        !confirmEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = LukiaAlpha.Disabled)
                        destructive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
