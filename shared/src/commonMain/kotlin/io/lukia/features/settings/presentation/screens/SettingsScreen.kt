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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.settings.presentation.rememberProviderActions
import io.lukia.features.settings.presentation.rememberTranslatingActions
import io.lukia.features.translate.domain.TranslationSettings
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
    val reader by viewModel.readerSettings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val connectionTest by viewModel.connectionTest.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val modelReadiness by viewModel.modelReadiness.collectAsState()

    SettingsContent(
        settings = settings,
        reader = reader,
        translation = translation,
        providerState = remember(connectionTest, availableModels, modelReadiness) {
            ProviderState(connectionTest, availableModels, modelReadiness)
        },
        onAppearanceChange = viewModel::setAppearance,
        onReaderChange = viewModel::setReaderSettings,
        translatingActions = rememberTranslatingActions(viewModel),
        providerActions = rememberProviderActions(viewModel),
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
    reader: ReaderSettings,
    translation: TranslationSettings,
    onAppearanceChange: (AppSettings) -> Unit,
    onReaderChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    providerState: ProviderState = ProviderState(),
    translatingActions: TranslatingActions = TranslatingActions(),
    providerActions: ProviderActions = ProviderActions(),
) {
    val dimens = LocalLukiaDimens.current

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
                onAppearanceChange = onAppearanceChange,
                onReaderChange = onReaderChange,
                translatingActions = translatingActions,
                providerActions = providerActions,
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
