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
import io.lukia.resources.Res
import io.lukia.resources.action_delete
import io.lukia.resources.filter_clear
import io.lukia.resources.settings_copy_report
import io.lukia.resources.settings_copy_whole_report
import io.lukia.resources.settings_crashed
import io.lukia.resources.settings_diagnostics
import io.lukia.resources.settings_diagnostics_footnote
import io.lukia.resources.settings_last_crash
import io.lukia.resources.settings_last_crash_footnote
import io.lukia.resources.settings_nothing_recorded
import io.lukia.resources.settings_record_problems
import io.lukia.resources.settings_record_problems_detail
import io.lukia.resources.settings_showing_last
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

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
 * How much of a crash report is shown before Copy.
 *
 * Enough to see what it is and that it names nothing about the reader - the
 * header, what it says about itself, and the top of the trace. The rest is more
 * frames, which nobody reads inside a settings group.
 */
private const val ShownCrashLines = 12

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
    /** What the app left behind the last time it died, if it died. */
    lastCrash: String? = null,
    onCopyCrash: () -> Unit = {},
    onDiscardCrash: () -> Unit = {},
) {
    Column(modifier = modifier) {
        // Above the setting, and shown whether or not the setting is on. A
        // reader who never turned anything on is exactly who this is for, and
        // finding out the app died is not something to make them go looking for.
        lastCrash?.let {
            LastCrashSection(
                report = it,
                onCopy = onCopyCrash,
                onDiscard = onDiscardCrash,
                groupColor = groupColor,
                modifier = Modifier.padding(bottom = Space.lg),
            )
        }

        DiagnosticsRecordSection(
            enabled = enabled,
            entries = entries,
            onEnabledChange = onEnabledChange,
            onCopy = onCopy,
            onClear = onClear,
            groupColor = groupColor,
        )
    }
}

/**
 * What the app left behind when it died, for the reader who finds it next time.
 *
 * Nothing sent it anywhere and nothing will: it is a file in the app's own
 * storage, and Copy and Delete are the only two things that can happen to it.
 */
@Composable
private fun LastCrashSection(
    report: String,
    onCopy: () -> Unit,
    onDiscard: () -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_last_crash),
        info = stringResource(Res.string.settings_last_crash_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(
                text = stringResource(Res.string.settings_crashed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            ReportLines(lines = report.lines().take(ShownCrashLines))

            if (report.lines().size > ShownCrashLines) {
                Text(
                    text = stringResource(Res.string.settings_copy_whole_report),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(top = Space.xs),
            ) {
                LukiaButton(
                    label = stringResource(Res.string.settings_copy_report),
                    onClick = onCopy,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Compact,
                )

                LukiaButton(
                    label = stringResource(Res.string.action_delete),
                    onClick = onDiscard,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Compact,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsRecordSection(
    enabled: Boolean,
    entries: List<DiagnosticEntry>,
    onEnabledChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_diagnostics),
        // Says where it goes, because that is the whole question somebody has
        // about a setting with this name. Nothing here sends anything: copying
        // is the only way out, and the reader chooses what to paste it into.
        info = stringResource(Res.string.settings_diagnostics_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SettingsToggle(
                label = stringResource(Res.string.settings_record_problems),
                info = stringResource(Res.string.settings_record_problems_detail),
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
                text = stringResource(Res.string.settings_nothing_recorded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            return@Column
        }

        ReportLines(lines = entries.asReversed().take(ShownEntries).map { it.asReportLine() })

        if (entries.size > ShownEntries) {
            Text(
                text = stringResource(
                    Res.string.settings_showing_last,
                    ShownEntries,
                    entries.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.xs),
        ) {
            LukiaButton(
                label = stringResource(Res.string.settings_copy_report),
                onClick = onCopy,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
            )

            LukiaButton(
                label = stringResource(Res.string.filter_clear),
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
private fun ReportLines(lines: List<String>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier.fillMaxWidth().horizontalScroll(scroll),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
