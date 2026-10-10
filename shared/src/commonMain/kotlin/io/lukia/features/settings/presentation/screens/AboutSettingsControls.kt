package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.LukiaVersion
import io.lukia.features.settings.domain.Attribution
import io.lukia.features.settings.domain.LukiaAttributions
import io.lukia.resources.Res
import io.lukia.resources.action_explain
import io.lukia.resources.settings_about
import io.lukia.resources.settings_developer
import io.lukia.resources.settings_licensed_under
import io.lukia.resources.settings_open_source
import io.lukia.resources.settings_open_source_footnote
import io.lukia.resources.settings_version
import io.lukia.shared.presentation.components.controls.InfoButton
import io.lukia.shared.presentation.components.controls.InfoProse
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * How many presses on the version open the developer settings.
 *
 * The number both platforms have settled on for the same gesture, and the point
 * of it is that nobody arrives at it by accident. Diagnostics is not a setting a
 * reader goes looking for - it is one somebody is asked to turn on while a bug
 * is being worked out - so it is reached by being told how, not by scrolling
 * past it.
 */
private const val DeveloperTaps = 7

/**
 * Which Lukia this is, and what it was built out of.
 *
 * At the foot of the page beside Diagnostics rather than a fifth segment, for
 * the reason that group gives: the four segments are what somebody came to
 * Settings to change, and neither of these is. The version is here because it is
 * the first question every bug report asks and the app had nowhere to answer it
 * - `LukiaVersion` reached a diagnostic report and a crash note, never a screen.
 *
 * Unlike Diagnostics it needs nothing from the graph, so it is composed directly
 * rather than passed as a slot. [version] and [attributions] default to the real
 * values, which keeps this previewable and lets a test supply its own list.
 */
@Composable
internal fun AboutSettings(
    groupColor: Color,
    modifier: Modifier = Modifier,
    version: String = LukiaVersion,
    attributions: List<Attribution> = LukiaAttributions,
    developerMode: Boolean = false,
    onDeveloperModeChange: (Boolean) -> Unit = {},
) {
    SettingsSection(
        title = stringResource(Res.string.settings_about),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                text = AppName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            VersionLine(
                version = version,
                developerMode = developerMode,
                onUnlock = { onDeveloperModeChange(true) },
            )

            Text(
                text = stringResource(Res.string.settings_licensed_under),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OpenSourceRow(attributions = attributions)

            if (developerMode) {
                SettingsToggle(
                    label = stringResource(Res.string.settings_developer),
                    checked = true,
                    onChange = onDeveloperModeChange,
                    modifier = Modifier.padding(top = Space.md),
                )
            }
        }
    }
}

/**
 * The app's own name, which is not a translated string.
 *
 * It is what the installers, the launcher and the window list call it - see
 * `packageName` in desktopApp/build.gradle.kts and `app_name` in the Android
 * resources - and a name that read differently in Finnish would be a different
 * app rather than a translation of this one.
 */
private const val AppName = "Lukia"

/**
 * The version, and the way in to the settings a reader is only ever asked for.
 *
 * Pressed [DeveloperTaps] times it turns the developer settings on; after that it
 * is a line of text again, because the switch it revealed is how they go back
 * off. Nothing marks it, and nothing counts out loud - a hint would make it a
 * feature of the page rather than something somebody is told over a bug report.
 */
@Composable
private fun VersionLine(
    version: String,
    developerMode: Boolean,
    onUnlock: () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = stringResource(Res.string.settings_version, version),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            // Draws nothing. This is a line of text that happens to count, and
            // a version number that lit up under a finger would be a control.
            indication = null,
            enabled = !developerMode,
        ) {
            taps += 1

            if (taps >= DeveloperTaps) {
                taps = 0
                onUnlock()
            }
        },
    )
}

/**
 * What the app is built from, behind a mark rather than behind a button.
 *
 * Seventeen names with a licence and a copyright line each is most of a screen,
 * and the page it sits at the foot of is one somebody opened to change something
 * else. Nothing is hidden by it: the list is the whole of what `NOTICE` carries,
 * one press away, rather than a summary with a link out of the app - which is
 * what Apache-2.0 section 4 asks a distribution to do.
 */
@Composable
private fun OpenSourceRow(attributions: List<Attribution>, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.settings_open_source)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = Space.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        InfoButton(label = stringResource(Res.string.action_explain, title)) {
            InfoProse(text = stringResource(Res.string.settings_open_source_footnote))

            AttributionList(
                attributions = attributions,
                modifier = Modifier.padding(top = Space.lg),
            )
        }
    }
}

/**
 * One block per dependency: what it is, what it is used under, and whose it is.
 *
 * The licence and the copyright line are never translated - a licence is the name
 * of a document and a copyright line is somebody's claim in the words they made
 * it, so both arrive from `config/attribution/dependencies.txt` as written.
 */
@Composable
private fun AttributionList(attributions: List<Attribution>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        attributions.forEach { attribution ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    text = attribution.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = attribution.licence,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = attribution.copyright,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
