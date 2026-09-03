package io.lukia.features.settings.presentation.screens

import androidx.compose.runtime.Composable
import io.lukia.resources.Res
import io.lukia.resources.settings_area_appearance
import io.lukia.resources.settings_area_providers
import io.lukia.resources.settings_area_reading
import io.lukia.resources.settings_area_translating
import org.jetbrains.compose.resources.stringResource

/** What the four segments across the top of Settings are called. */
@Composable
internal fun SettingsArea.displayTitle(): String = when (this) {
    SettingsArea.Appearance -> stringResource(Res.string.settings_area_appearance)
    SettingsArea.Reading -> stringResource(Res.string.settings_area_reading)
    SettingsArea.Translating -> stringResource(Res.string.settings_area_translating)
    SettingsArea.Providers -> stringResource(Res.string.settings_area_providers)
}
