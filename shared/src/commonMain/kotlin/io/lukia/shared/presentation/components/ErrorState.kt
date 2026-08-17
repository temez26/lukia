package io.lukia.shared.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.Space

/**
 * The app's one dead-end state: something was asked for and cannot be shown.
 *
 * For failures the user can carry on past - an unsupported file, a folder that
 * imported only partly - prefer the snackbar. This is for when there is nothing
 * left on screen to carry on with.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            AlertGlyph(
                // Muted rather than full error red: this is an explanation, not
                // an alarm, and a large saturated glyph reads as the latter.
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.xl)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.sm)
            )

            if (actionLabel != null && onAction != null) {
                LukiaButton(
                    label = actionLabel,
                    onClick = onAction,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.padding(top = Space.xl)
                )
            }
        }
    }
}
