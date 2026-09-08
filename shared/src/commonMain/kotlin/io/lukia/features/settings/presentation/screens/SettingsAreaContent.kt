package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.shared.presentation.theme.LocalLukiaDimens

/**
 * Whichever group of settings is open, and nothing else.
 *
 * A pass-through: it holds no state and decides nothing except which of the
 * five areas is composed. It exists so that [SettingsBody] can be the page -
 * its title, its segmented control, the crossfade between groups - without also
 * being every control in all five of them.
 */
@Composable
internal fun SettingsAreaContent(
    area: SettingsArea,
    settings: AppSettings,
    reader: ReaderSettings,
    translation: TranslationSettings,
    providerState: ProviderState,
    onSettingsChange: (AppSettings) -> Unit,
    onReaderChange: (ReaderSettings) -> Unit,
    translatingActions: TranslatingActions,
    providerActions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
    /**
     * The diagnostics group, which lives under About - see [SettingsBody] for
     * why it arrives as a slot rather than as more parameters.
     */
    diagnostics: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (area) {
            SettingsArea.Appearance -> AppearanceSettings(
                settings = settings,
                onChange = onSettingsChange,
                groupColor = groupColor,
            )

            SettingsArea.Reading -> ReadingSettings(
                reader = reader,
                onReaderChange = onReaderChange,
                groupColor = groupColor,
            )

            SettingsArea.Translating -> TranslatingSettings(
                translation = translation,
                actions = translatingActions,
                groupColor = groupColor,
            )

            SettingsArea.Providers -> ProviderSettings(
                translation = translation,
                state = providerState,
                actions = providerActions,
                translatingActions = translatingActions,
                groupColor = groupColor,
            )

            SettingsArea.About -> AboutArea(
                settings = settings,
                onSettingsChange = onSettingsChange,
                groupColor = groupColor,
                diagnostics = diagnostics,
            )
        }
    }
}

/**
 * What Lukia is, and - once somebody has been talked through turning them on -
 * what it wrote down when something went wrong.
 *
 * The two are together because they answer the same question from opposite
 * ends: a bug report asks which version this is, and then asks what happened.
 * Diagnostics is under About rather than beside it because About is where the
 * gesture that reveals it lives.
 */
@Composable
private fun AboutArea(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    groupColor: Color,
    diagnostics: (@Composable () -> Unit)?,
) {
    val dimens = LocalLukiaDimens.current

    AboutSettings(
        groupColor = groupColor,
        developerMode = settings.developerMode,
        onDeveloperModeChange = { onSettingsChange(settings.copy(developerMode = it)) },
    )

    if (settings.developerMode) {
        diagnostics?.let {
            Box(modifier = Modifier.padding(top = dimens.sectionSpacing)) { it() }
        }
    }
}
