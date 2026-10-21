package io.lukia.features.settings.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lukia.features.settings.presentation.screens.AccountPage
import io.lukia.features.settings.presentation.screens.SettingsArea
import io.lukia.features.settings.presentation.screens.SettingsBody
import io.lukia.shared.presentation.components.surface.OverlayPanel
import io.lukia.shared.presentation.components.surface.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.koin.compose.viewmodel.koinViewModel

/** Room for the sidebar and a group at a comfortable measure beside it. */
private val SheetWidth: Dp = 820.dp

/** Tall enough for most groups without scrolling, short of filling a large monitor. */
private val SheetMaxHeight: Dp = 720.dp

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
    modifier: Modifier = Modifier,
    /** The Account page - see `SettingsBody`. */
    account: AccountPage? = null,
    /** The page it opens on. */
    initialArea: SettingsArea = SettingsArea.Default,
) {
    OverlayPanel(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        widthCap = SheetWidth,
    ) {
        SettingsPanelBody(account = account, initialArea = initialArea)
    }
}

@Composable
private fun SettingsPanelBody(
    account: AccountPage?,
    initialArea: SettingsArea,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val reader by viewModel.readerSettings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val connectionTest by viewModel.probe.connectionTest.collectAsState()
    val availableModels by viewModel.probe.availableModels.collectAsState()
    val modelReadiness by viewModel.probe.modelReadiness.collectAsState()
    val dimens = LocalLukiaDimens.current

    SettingsBody(
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
        // One height for every group, so the sidebar never moves under the
        // pointer - see [SettingsBody].
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = SheetMaxHeight)
            .fillMaxHeight(),
        topClearance = OverlayPanelTopClearance,
        bottomClearance = dimens.sectionSpacing,
        // A step away from the panel it sits on, rather than from the window
        // background the screen version sits on.
        groupColor = MaterialTheme.colorScheme.surfaceVariant,
        diagnostics = { DiagnosticsGroup(groupColor = MaterialTheme.colorScheme.surfaceVariant) },
        account = account,
        initialArea = initialArea,
    )
}
