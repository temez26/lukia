package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.LukiaVersion
import io.lukia.features.settings.domain.Attribution
import io.lukia.features.settings.domain.LukiaAttributions
import io.lukia.resources.Res
import io.lukia.resources.settings_about
import io.lukia.resources.settings_hide
import io.lukia.resources.settings_licensed_under
import io.lukia.resources.settings_open_source
import io.lukia.resources.settings_open_source_footnote
import io.lukia.resources.settings_show
import io.lukia.resources.settings_version
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Which Lukia this is, and what it was built out of.
 *
 * Sits at the foot of the page beside Diagnostics rather than becoming a fifth
 * segment, for the reason that group gives: the four segments are the things
 * somebody came to Settings to change, and neither of these is one of them. The
 * version is here because it is the first question every bug report asks and the
 * app had nowhere to answer it - `LukiaVersion` reached a diagnostic report and a
 * crash note and never a screen.
 *
 * Unlike Diagnostics it needs nothing from the graph, so it is composed directly
 * rather than passed in as a slot. [version] and [attributions] are parameters
 * with the real values as defaults, which is what keeps this previewable and lets
 * a test hand it a list it wrote itself.
 */
@Composable
internal fun AboutSettings(
    groupColor: Color,
    modifier: Modifier = Modifier,
    version: String = LukiaVersion,
    attributions: List<Attribution> = LukiaAttributions,
) {
    Column(modifier = modifier) {
        SettingsSection(
            title = stringResource(Res.string.settings_about),
            groupColor = groupColor,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    text = AppName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(Res.string.settings_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(Res.string.settings_licensed_under),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OpenSourceSection(
            attributions = attributions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = Space.lg),
        )
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
 * What the app is built from, folded away until somebody asks.
 *
 * Seventeen names with a licence and a copyright line each is most of a screen,
 * and the page it sits at the foot of is one somebody opened to change something
 * else. Nothing is hidden by it: the list is the whole of what `NOTICE` carries,
 * one press away, rather than a summary with a link out of the app.
 */
@Composable
private fun OpenSourceSection(
    attributions: List<Attribution>,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    var shown by rememberSaveable { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(Res.string.settings_open_source),
        footnote = stringResource(Res.string.settings_open_source_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            // Show and Hide rather than the section's own name, which is already
            // above it: the group says what the list is, so the button is only
            // ever answering whether it is open.
            LukiaButton(
                label = stringResource(
                    if (shown) Res.string.settings_hide else Res.string.settings_show,
                ),
                onClick = { shown = !shown },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
            )

            AnimatedVisibility(visible = shown) {
                AttributionList(attributions = attributions)
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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
