package io.lukia.shared.presentation.components.surface

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asks before something that cannot be taken back.
 *
 * A platform dialog rather than one of this app's own panels, and deliberately
 * so: a modal question is the one piece of chrome that must be unmistakably not
 * part of the page behind it, must trap focus, and must be reachable by every
 * assistive technology on every platform. That is a great deal to re-earn for
 * the sake of matching a house style, on the one surface where being surprising
 * is the point.
 *
 * [confirmLabel] names the action rather than agreeing with the question - a
 * button that says "Delete" tells a reader what pressing it does even if they
 * never read the sentence above it, and "Yes" does not.
 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = false,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
