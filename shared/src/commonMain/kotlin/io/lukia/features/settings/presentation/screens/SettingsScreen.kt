package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.DiagnosticsGroup
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.settings.presentation.rememberProviderActions
import io.lukia.features.settings.presentation.rememberTranslatingActions
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.shared.presentation.components.controls.FloatingBackButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** The Account page - see `SettingsBody`. */
    account: AccountPage? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val reader by viewModel.readerSettings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val connectionTest by viewModel.probe.connectionTest.collectAsState()
    val availableModels by viewModel.probe.availableModels.collectAsState()
    val modelReadiness by viewModel.probe.modelReadiness.collectAsState()

    SettingsContent(
        settings = settings,
        reader = reader,
        translation = translation,
        providerState = remember(connectionTest, availableModels, modelReadiness) {
            ProviderState(connectionTest, availableModels, modelReadiness)
        },
        onSettingsChange = viewModel::setAppSettings,
        onReaderChange = viewModel::setReaderSettings,
        translatingActions = rememberTranslatingActions(viewModel),
        providerActions = rememberProviderActions(viewModel),
        onBack = onBack,
        diagnostics = { DiagnosticsGroup() },
        account = account,
    )
}

/**
 * This screen keeps a title where the library dropped one. A pushed screen has to
 * say what it is - the library is the app's home and needs no introduction, but
 * arriving here from a menu does.
 */
@Composable
fun SettingsContent(
    settings: AppSettings,
    reader: ReaderSettings,
    translation: TranslationSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onReaderChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    providerState: ProviderState = ProviderState(),
    translatingActions: TranslatingActions = TranslatingActions(),
    providerActions: ProviderActions = ProviderActions(),
    /** The diagnostics group - see `SettingsBody`. Absent leaves it off the page. */
    diagnostics: (@Composable () -> Unit)? = null,
    /** The Account page - see `SettingsBody`. */
    account: AccountPage? = null,
) {
    Box(
        modifier = modifier
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
                reader = reader,
                translation = translation,
                providerState = providerState,
                onSettingsChange = onSettingsChange,
                onReaderChange = onReaderChange,
                translatingActions = translatingActions,
                providerActions = providerActions,
                modifier = Modifier.fillMaxSize(),
                diagnostics = diagnostics,
                account = account,
            )

            FloatingBackButton(onBack = onBack)
        }
    }
}
