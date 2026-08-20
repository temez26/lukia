package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.AvailableModels
import io.lukia.features.settings.presentation.ConnectionTest
import io.lukia.features.settings.presentation.ModelReadiness
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val connectionTest by viewModel.connectionTest.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val modelReadiness by viewModel.modelReadiness.collectAsState()

    SettingsContent(
        settings = settings,
        translation = translation,
        connectionTest = connectionTest,
        availableModels = availableModels,
        modelReadiness = modelReadiness,
        onAppearanceChange = viewModel::setAppearance,
        onTargetLanguageChange = viewModel::setTargetLanguage,
        onProviderChange = viewModel::setProvider,
        onModelChange = viewModel::setModel,
        onModelChoose = viewModel::chooseModel,
        onApiKeyChange = viewModel::setApiKey,
        onBaseUrlChange = viewModel::onBaseUrlEdited,
        onTimeoutChange = viewModel::setTimeoutSeconds,
        onWordLookupChange = viewModel::setWordLookup,
        onGoogleApiKeyChange = viewModel::setGoogleApiKey,
        onTapGestureChange = viewModel::setTapGesture,
        onLongPressGestureChange = viewModel::setLongPressGesture,
        onTranslateOnScreenChange = viewModel::setTranslateOnScreen,
        onTranslateAsYouReadChange = viewModel::setTranslateAsYouRead,
        onTestConnection = viewModel::testConnection,
        onLoadModels = viewModel::loadAvailableModels,
        onLoadChosenModel = viewModel::loadChosenModel,
        onBack = onBack,
    )
}

/**
 * Stateless half of the screen, matching the other screens so the layout can be
 * rendered without a ViewModel or dependency graph.
 *
 * This screen keeps a title where the library dropped one. A pushed screen has to
 * say what it is - the library is the app's home and needs no introduction, but
 * arriving here from a menu does.
 */
@Composable
fun SettingsContent(
    settings: AppSettings,
    translation: TranslationSettings,
    connectionTest: ConnectionTest = ConnectionTest.Untried,
    availableModels: AvailableModels = AvailableModels.Unknown,
    modelReadiness: ModelReadiness = ModelReadiness.Cold,
    onAppearanceChange: (AppSettings) -> Unit,
    onTargetLanguageChange: (TargetLanguage) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onModelChoose: (String) -> Unit = onModelChange,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit = {},
    onWordLookupChange: (WordLookup) -> Unit = {},
    onGoogleApiKeyChange: (String) -> Unit = {},
    onTapGestureChange: (PassageUnit?) -> Unit = {},
    onLongPressGestureChange: (PassageUnit?) -> Unit = {},
    onTranslateOnScreenChange: (Boolean) -> Unit = {},
    onTranslateAsYouReadChange: (Boolean) -> Unit = {},
    onTestConnection: () -> Unit,
    onLoadModels: () -> Unit = {},
    onLoadChosenModel: () -> Unit = {},
    onBack: () -> Unit,
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Full-bleed like the detail screen, so content passes under the floating
        // back button - which means window insets are applied by hand. The
        // background above stays outside the inset so colour still reaches the
        // screen edges. Resolves to zero on desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            SettingsBody(
                settings = settings,
                translation = translation,
                connectionTest = connectionTest,
                availableModels = availableModels,
                modelReadiness = modelReadiness,
                onAppearanceChange = onAppearanceChange,
                onTargetLanguageChange = onTargetLanguageChange,
                onProviderChange = onProviderChange,
                onModelChange = onModelChange,
                onModelChoose = onModelChoose,
                onApiKeyChange = onApiKeyChange,
                onBaseUrlChange = onBaseUrlChange,
                onTimeoutChange = onTimeoutChange,
                onWordLookupChange = onWordLookupChange,
                onGoogleApiKeyChange = onGoogleApiKeyChange,
                onTapGestureChange = onTapGestureChange,
                onLongPressGestureChange = onLongPressGestureChange,
                onTranslateOnScreenChange = onTranslateOnScreenChange,
                onTranslateAsYouReadChange = onTranslateAsYouReadChange,
                onTestConnection = onTestConnection,
                onLoadModels = onLoadModels,
                onLoadChosenModel = onLoadChosenModel,
                modifier = Modifier.fillMaxSize(),
            )

            LukiaIconButton(
                label = "Go back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = Space.lg),
                variant = IconButtonVariant.Glass,
            ) { tint, glyph ->
                BackChevron(tint = tint, modifier = glyph)
            }
        }
    }
}
