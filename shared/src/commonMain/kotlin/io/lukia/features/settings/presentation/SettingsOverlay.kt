package io.lukia.features.settings.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.lukia.features.settings.presentation.screens.SettingsBody
import io.lukia.shared.presentation.components.OverlayPanel
import io.lukia.shared.presentation.components.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's settings, over whatever they were opened from rather than instead of
 * it.
 *
 * The ViewModel is resolved only once the panel is actually being shown, which is
 * what lets a caller leave this in the tree unconditionally - and what keeps it
 * out of the way of anything composing that caller without a dependency graph
 * behind it, such as a test.
 */
@Composable
fun SettingsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OverlayPanel(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        SettingsPanelBody()
    }
}

@Composable
private fun SettingsPanelBody(viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val connectionTest by viewModel.connectionTest.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val dimens = LocalLukiaDimens.current

    SettingsBody(
        settings = settings,
        translation = translation,
        connectionTest = connectionTest,
        availableModels = availableModels,
        onThemeChange = viewModel::setTheme,
        onTargetLanguageChange = viewModel::setTargetLanguage,
        onProviderChange = viewModel::setProvider,
        onModelChange = viewModel::setModel,
        onApiKeyChange = viewModel::setApiKey,
        onBaseUrlChange = viewModel::onBaseUrlEdited,
        onTestConnection = viewModel::testConnection,
        onLoadModels = viewModel::loadAvailableModels,
        modifier = Modifier.fillMaxWidth(),
        topClearance = OverlayPanelTopClearance,
        bottomClearance = dimens.sectionSpacing,
        // A step away from the panel it sits on, rather than from the window
        // background the screen version sits on.
        groupColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
