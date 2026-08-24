package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import io.lukia.shared.presentation.theme.Space

/**
 * What the keys do.
 *
 * Shortcuts nobody can find are shortcuts nobody has. The bar's own controls
 * carry theirs in a tooltip, but turning pages has no button to hang one off -
 * and this is the panel a reader opens when they want to know how the reader
 * works. Only in the wide layout, since only the windows with room for it have
 * keyboards.
 */
@Composable
internal fun ShortcutList(reflowable: Boolean, inset: Dp) {
    val shortcuts = listOf(
        "Turn page" to "Space  PgDn  PgUp",
        "Scroll" to "↑  ↓",
        "Start, end" to "Home  End",
        // Named for what the keys do rather than for the setting they move, so
        // the panel does not carry the words "Page width" twice - and what they
        // do depends on the book, since there is no widening a re-set page.
        (if (reflowable) "Larger, smaller" else "Wider, narrower") to "+  −  0",
        "Contents, settings" to "C  T",
        "Close book" to "Esc",
    )

    shortcuts.forEach { (action, keys) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = Space.xxs),
        ) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
