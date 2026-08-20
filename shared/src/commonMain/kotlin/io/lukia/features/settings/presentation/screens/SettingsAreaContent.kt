package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.AvailableModels
import io.lukia.features.settings.presentation.ConnectionTest
import io.lukia.features.settings.presentation.ModelReadiness
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup

/**
 * Whichever group of settings is open, and nothing else.
 *
 * A pass-through: it holds no state and decides nothing except which of the
 * three areas is composed. It exists so that [SettingsBody] can be the page -
 * its title, its segmented control, the crossfade between groups - without also
 * being every control in all three of them.
 */
@Composable
internal fun SettingsAreaContent(
    area: SettingsArea,
    settings: AppSettings,
    translation: TranslationSettings,
    connectionTest: ConnectionTest,
    availableModels: AvailableModels,
    modelReadiness: ModelReadiness,
    onAppearanceChange: (AppSettings) -> Unit,
    onTapGestureChange: (PassageUnit?) -> Unit,
    onLongPressGestureChange: (PassageUnit?) -> Unit,
    onTranslateOnScreenChange: (Boolean) -> Unit,
    onTranslateAsYouReadChange: (Boolean) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onModelChoose: (String) -> Unit,
    onLoadModels: () -> Unit,
    onLoadChosenModel: () -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onTestConnection: () -> Unit,
    onWordLookupChange: (WordLookup) -> Unit,
    onGoogleApiKeyChange: (String) -> Unit,
    onTargetLanguageChange: (TargetLanguage) -> Unit,
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
                translation = translation,
                onTapGestureChange = onTapGestureChange,
                onLongPressGestureChange = onLongPressGestureChange,
                onTranslateOnScreenChange = onTranslateOnScreenChange,
                onTranslateAsYouReadChange = onTranslateAsYouReadChange,
                groupColor = groupColor,
            )

            SettingsArea.Translating -> TranslatingSettings(
                translation = translation,
                connectionTest = connectionTest,
                availableModels = availableModels,
                modelReadiness = modelReadiness,
                onProviderChange = onProviderChange,
                onBaseUrlChange = onBaseUrlChange,
                onApiKeyChange = onApiKeyChange,
                onModelChange = onModelChange,
                onModelChoose = onModelChoose,
                onLoadModels = onLoadModels,
                onLoadChosenModel = onLoadChosenModel,
                onTimeoutChange = onTimeoutChange,
                onTestConnection = onTestConnection,
                onWordLookupChange = onWordLookupChange,
                onGoogleApiKeyChange = onGoogleApiKeyChange,
                onTargetLanguageChange = onTargetLanguageChange,
                groupColor = groupColor,
            )
        }
    }
}
