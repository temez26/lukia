package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.features.settings.presentation.DiagnosticsGroup
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.features.settings.presentation.rememberProviderActions
import io.lukia.features.settings.presentation.rememberTranslatingActions
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.resources.Res
import io.lukia.resources.reader_translate_into
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Settings as a phone shows them: a list of the groups, and each group as a page
// pushed over it. Wider windows open `SettingsOverlay` instead - see [SettingsHub].

/** The list of settings groups - the page Settings opens on, on a phone. */
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpen: (SettingsArea) -> Unit,
    modifier: Modifier = Modifier,
    account: AccountSummary? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val reader by viewModel.readerSettings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()

    SettingsPageFrame(onBack = onBack, modifier = modifier) {
        ScrollingPage {
            SettingsHub(
                settings = settings,
                reader = reader,
                translation = translation,
                onOpen = onOpen,
                account = account,
            )
        }
    }
}

/**
 * One group of settings as a page of its own, named in the bar above it rather
 * than set large: the list it was opened from already introduced it.
 */
@Composable
fun SettingsAreaScreen(
    area: SettingsArea,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The Account page - see `SettingsBody`. */
    account: AccountPage? = null,
    /** Opens the languages to translate into - see [TargetLanguageScreen]. */
    onPickLanguage: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val reader by viewModel.readerSettings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val title = area.pageTitle()

    SettingsPageFrame(
        onBack = onBack,
        modifier = modifier,
        title = title,
        // The groups under it drop the heading that would have carried it.
        actions = { if (area == SettingsArea.AiTranslation) BetaBadge() },
    ) {
        ScrollingPage {
            CompositionLocalProvider(
                LocalPhoneSettingsPage provides remember(title, onPickLanguage) {
                    PhoneSettingsPage(title = title, onPickLanguage = onPickLanguage)
                },
            ) {
                SettingsAreaContent(
                    area = area,
                    settings = settings,
                    reader = reader,
                    translation = translation,
                    providerState = rememberProviderState(viewModel),
                    onSettingsChange = viewModel::setAppSettings,
                    onReaderChange = viewModel::setReaderSettings,
                    translatingActions = rememberTranslatingActions(viewModel),
                    providerActions = rememberProviderActions(viewModel),
                    groupColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = LocalLukiaDimens.current.screenPadding),
                    diagnostics = { DiagnosticsGroup() },
                    account = account,
                )
            }
        }
    }
}

/**
 * The languages a book can be translated into, as a page of its own. Choosing
 * one goes back, since the choice was the only thing the page was for.
 */
@Composable
fun TargetLanguageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val translation by viewModel.translationSettings.collectAsState()
    val actions = rememberTranslatingActions(viewModel)

    SettingsPageFrame(
        onBack = onBack,
        modifier = modifier,
        title = stringResource(Res.string.reader_translate_into),
    ) {
        ScrollingPage {
            GroupCard(modifier = Modifier.padding(horizontal = LocalLukiaDimens.current.screenPadding)) {
                ChoiceList(
                    options = TargetLanguage.entries,
                    selected = translation.targetLanguage,
                    label = { it.endonym },
                    // The English name too, so somebody who cannot yet read the
                    // language they are learning can still find it in the list.
                    detail = { it.englishName.takeIf { name -> name != it.endonym } },
                    onSelect = {
                        actions.onTargetLanguageChange(it)
                        onBack()
                    },
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                )
            }
        }
    }
}

/** The scrolling column under the bar, held to a readable width on a wide phone. */
@Composable
private fun ColumnScope.ScrollingPage(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = Space.sm, bottom = Clearance.pageBottom),
    ) {
        Box(modifier = Modifier.widthIn(max = LocalLukiaDimens.current.maxDetailWidth)) {
            content()
        }
    }
}
