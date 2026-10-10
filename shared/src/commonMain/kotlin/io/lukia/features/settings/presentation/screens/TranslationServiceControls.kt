package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.settings_address
import io.lukia.resources.settings_api_key
import io.lukia.resources.settings_api_key_optional
import io.lukia.resources.settings_model
import io.lukia.resources.settings_none
import io.lukia.resources.settings_required
import io.lukia.resources.settings_translation_service
import io.lukia.resources.settings_translation_service_footnote
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

// Who does the translating, and everything they need to be reachable: the
// service, its address, its key, the model to ask for, how long to wait, and a
// way to find out whether any of that was right.
//
// The one group on the AI translation page that can be filled in wrongly, which
// is why it is a file of its own - see AiTranslationControls for the page it
// sits on and the switch the whole of it is behind.

/**
 * Who translates a passage with a model: the service, where it lives, what it is
 * owed, which model, how long to wait, and a way to find out whether any of that
 * was right.
 *
 * Marked beta for the whole group rather than switch by switch, because what is
 * unfinished is what they have in common. Everything above this reaches a
 * published API that answers a word in a tenth of a second for nothing; this
 * reaches a model that takes seconds, bills for them, and fails in ways an
 * address and a key cannot tell apart. Worth knowing before pasting in a key.
 */
@Composable
internal fun TranslationServiceSection(
    translation: TranslationSettings,
    state: ProviderState,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_translation_service),
        // Shown rather than hidden behind the mark, which is where every other
        // group's prose goes: this is the one that costs money, and a warning
        // nobody asked for is still a warning worth giving.
        footnote = stringResource(Res.string.settings_translation_service_footnote),
        info = translation.provider.footnote(),
        groupColor = groupColor,
        beta = true,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SegmentedControl(
                options = TranslationProvider.entries,
                selected = translation.provider,
                label = { it.displayName() },
                onSelect = actions.onProviderChange,
            )

            ProviderCredentials(translation = translation, actions = actions)

            ModelChoice(translation = translation, state = state, actions = actions)

            TimeoutField(
                provider = translation.provider,
                seconds = translation.timeoutSeconds,
                onChange = actions.onTimeoutChange,
            )

            ConnectionTestRow(
                state = state.connectionTest,
                enabled = translation.isConfigured,
                onTest = actions.onTestConnection,
            )
        }
    }
}

/**
 * Where the service lives and what it is owed: an address for the ones that have
 * a configurable endpoint, a key, and the model to ask for.
 */
@Composable
private fun ProviderCredentials(
    translation: TranslationSettings,
    actions: ProviderActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (translation.provider.hasConfigurableEndpoint) {
            LabelledField(label = stringResource(Res.string.settings_address)) {
                SettingsTextField(
                    value = translation.baseUrl,
                    onValueChange = actions.onBaseUrlChange,
                    placeholder = translation.provider.defaultBaseUrl,
                )
            }
        }

        val apiKeyLabel = stringResource(
            if (translation.provider.requiresKey) {
                Res.string.settings_api_key
            } else {
                Res.string.settings_api_key_optional
            },
        )

        LabelledField(label = apiKeyLabel) {
            SettingsTextField(
                value = translation.apiKey.orEmpty(),
                onValueChange = actions.onApiKeyChange,
                placeholder = stringResource(
                    if (translation.provider.requiresKey) {
                        Res.string.settings_required
                    } else {
                        Res.string.settings_none
                    },
                ),
                secret = true,
            )
        }

        LabelledField(label = stringResource(Res.string.settings_model)) {
            SettingsTextField(
                value = translation.model,
                onValueChange = actions.onModelChange,
                placeholder = translation.provider.defaultModel,
            )
        }
    }
}

/**
 * Which model, offered the way the provider allows it to be: a server can be
 * asked what it holds, while a hosted service can only be named from a list this
 * app carries.
 */
@Composable
private fun ModelChoice(
    translation: TranslationSettings,
    state: ProviderState,
    actions: ProviderActions,
) {
    if (translation.provider.hasConfigurableEndpoint) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            ServerModels(
                state = state.availableModels,
                selected = translation.model,
                onSelect = actions.onModelChoose,
                onLoad = actions.onLoadModels,
            )

            ModelReadinessRow(
                state = state.modelReadiness,
                model = translation.model,
                onLoad = actions.onLoadChosenModel,
            )
        }
    } else if (translation.provider.suggestedModels.isNotEmpty()) {
        ChoiceList(
            options = translation.provider.suggestedModels,
            selected = translation.model,
            label = { it },
            onSelect = actions.onModelChoose,
        )
    }
}
