package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.TranslationSettings

/**
 * Whichever group of settings is open, and nothing else.
 *
 * A pass-through: it holds no state and decides nothing except which of the
 * four areas is composed. It exists so that [SettingsBody] can be the page -
 * its title, its segmented control, the crossfade between groups - without also
 * being every control in all four of them.
 */
@Composable
internal fun SettingsAreaContent(
    area: SettingsArea,
    settings: AppSettings,
    reader: ReaderSettings,
    translation: TranslationSettings,
    providerState: ProviderState,
    onAppearanceChange: (AppSettings) -> Unit,
    onReaderChange: (ReaderSettings) -> Unit,
    translatingActions: TranslatingActions,
    providerActions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (area) {
            SettingsArea.Appearance -> AppearanceSettings(
                settings = settings,
                onChange = onAppearanceChange,
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
                groupColor = groupColor,
            )
        }
    }
}
