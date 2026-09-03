package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.core.domain.diagnostics.DiagnosticEntry
import io.lukia.core.infrastructure.diagnostics.asReportLine
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space

/**
 * How many entries the group shows without being asked twice.
 *
 * The buffer holds several times this, and Copy takes all of it. What is shown
 * is a look at the kind of thing the record holds rather than the whole of it -
 * nobody decides whether to hand something over by reading two hundred lines
 * inside a settings group, and the entry they came about is the last one anyway.
 */
private const val ShownEntries = 40

/**
 * What the app wrote down, and whether it writes anything down at all.
 *
 * Sits under whichever group is open rather than becoming a fifth one. It is not
 * one of the four things somebody comes to Settings to change - it is the thing
 * a reader is asked to turn on by somebody trying to work out what went wrong,
 * and the rest of the time it should be a line at the foot of the page.
 */
@Composable
internal fun DiagnosticsSettings(
    enabled: Boolean,
    entries: List<DiagnosticEntry>,
    onEnabledChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = "Diagnostics",
        // Says where it goes, because that is the whole question somebody has
        // about a setting with this name. Nothing here sends anything: copying
        // is the only way out, and the reader chooses what to paste it into.
        footnote = "Kept on this device until you copy it, and never sent anywhere. " +
            "Holds no book text, titles, file names or keys.",
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SettingsToggle(
                label = "Record what goes wrong",
                detail = "Notes when a cover, a page or a translation could not be read, " +
                    "so a problem you can describe has something behind it. " +
                    "Turning this off clears what was recorded.",
                checked = enabled,
                onChange = onEnabledChange,
            )

            AnimatedVisibility(visible = enabled) {
                RecordedEntries(entries = entries, onCopy = onCopy, onClear = onClear)
            }
        }
    }
}

/**
 * The record itself, shown as the lines it would be copied as.
 *
 * The same formatter does both - see `asReportLine` - so that what a reader
 * reads before deciding is what they hand over, rather than a summary of it.
 */
@Composable
private fun RecordedEntries(
    entries: List<DiagnosticEntry>,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "Nothing recorded yet. Use the app as you did when it went wrong, " +
                    "then come back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            return@Column
        }

        EntryLines(entries = entries.asReversed().take(ShownEntries))

        if (entries.size > ShownEntries) {
            Text(
                text = "Showing the last $ShownEntries of ${entries.size}. Copy takes all of them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.xs),
        ) {
            LukiaButton(
                label = "Copy report",
                onClick = onCopy,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
            )

            LukiaButton(
                label = "Clear",
                onClick = onClear,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
            )
        }
    }
}

/**
 * The lines, newest first and scrolling sideways rather than wrapping.
 *
 * An entry is a fixed set of columns and reads as one only while it stays on one
 * line; wrapped, a long detail turns the record into prose. The page itself must
 * not scroll sideways, so the scrolling is held inside this.
 */
@Composable
private fun EntryLines(entries: List<DiagnosticEntry>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier.fillMaxWidth().horizontalScroll(scroll),
    ) {
        entries.forEach { entry ->
            Text(
                text = entry.asReportLine(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
