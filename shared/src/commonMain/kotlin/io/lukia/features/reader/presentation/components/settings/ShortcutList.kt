package io.lukia.features.reader.presentation.components.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.lukia.resources.Res
import io.lukia.resources.shortcut_close_book
import io.lukia.resources.shortcut_close_book_keys
import io.lukia.resources.shortcut_panels
import io.lukia.resources.shortcut_panels_keys
import io.lukia.resources.shortcut_resize_fixed
import io.lukia.resources.shortcut_resize_keys
import io.lukia.resources.shortcut_resize_reflowable
import io.lukia.resources.shortcut_scroll
import io.lukia.resources.shortcut_scroll_keys
import io.lukia.resources.shortcut_start_end
import io.lukia.resources.shortcut_start_end_keys
import io.lukia.resources.shortcut_turn_page
import io.lukia.resources.shortcut_turn_page_keys
import io.lukia.resources.shortcut_word_details
import io.lukia.resources.shortcut_word_details_keys
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * What the keys do, and the one thing the mouse does that nothing else says.
 *
 * Shortcuts nobody can find are shortcuts nobody has. The bar's controls carry
 * theirs in a tooltip, but turning pages has no button to hang one off, and this
 * is the panel a reader opens to find out how the reader works. Wide layout
 * only, since only those windows have keyboards.
 *
 * Which is why the second mouse button is listed among the keys rather than
 * beside the gesture settings: it does what a press and hold does, for machines
 * with no finger to hold anything with.
 */
@Composable
internal fun ShortcutList(reflowable: Boolean, inset: Dp) {
    val shortcuts = listOf(
        stringResource(Res.string.shortcut_turn_page) to
            stringResource(Res.string.shortcut_turn_page_keys),
        stringResource(Res.string.shortcut_scroll) to
            stringResource(Res.string.shortcut_scroll_keys),
        stringResource(Res.string.shortcut_start_end) to
            stringResource(Res.string.shortcut_start_end_keys),
        // Named for what the keys do rather than for the setting they move, so
        // the panel does not carry the words "Page width" twice - and what they
        // do depends on the book, since there is no widening a re-set page.
        stringResource(
            if (reflowable) {
                Res.string.shortcut_resize_reflowable
            } else {
                Res.string.shortcut_resize_fixed
            },
        ) to stringResource(Res.string.shortcut_resize_keys),
        stringResource(Res.string.shortcut_panels) to
            stringResource(Res.string.shortcut_panels_keys),
        stringResource(Res.string.shortcut_close_book) to
            stringResource(Res.string.shortcut_close_book_keys),
        stringResource(Res.string.shortcut_word_details) to
            stringResource(Res.string.shortcut_word_details_keys),
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
                overflow = TextOverflow.Ellipsis,
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
