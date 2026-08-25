package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.components.ChoiceList
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * Who answers, and everything they need to be reachable.
 *
 * Its own group rather than the tail of Translating, because these two are the
 * only settings in the app that can be filled in wrongly: an address, a key, a
 * model name, a timeout. Everything about them - the fields, the catalogue, the
 * connection test - is machinery, and a reader who only wants to change which
 * language they read into should never have to scroll past it.
 *
 * Two services rather than one, because a single word and a whole sentence are
 * different questions: a dictionary answers the first well and cannot answer the
 * second at all.
 */
@Composable
internal fun ProviderSettings(
    translation: TranslationSettings,
    state: ProviderState,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        TranslationServiceSection(
            translation = translation,
            state = state,
            actions = actions,
            groupColor = groupColor,
        )

        WordLookupSection(
            translation = translation,
            actions = actions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/**
 * Who translates a passage: the service, where it lives, what it is owed, which
 * model, how long to wait, and a way to find out whether any of that was right.
 */
@Composable
private fun TranslationServiceSection(
    translation: TranslationSettings,
    state: ProviderState,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = "Translation service",
        footnote = translation.provider.footnote(),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SegmentedControl(
                options = TranslationProvider.entries,
                selected = translation.provider,
                label = { it.label },
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
            LabelledField(label = "Address") {
                SettingsTextField(
                    value = translation.baseUrl,
                    onValueChange = actions.onBaseUrlChange,
                    placeholder = translation.provider.defaultBaseUrl,
                )
            }
        }

        val apiKeyLabel =
            if (translation.provider.requiresKey) "API key" else "API key (optional)"

        LabelledField(label = apiKeyLabel) {
            SettingsTextField(
                value = translation.apiKey.orEmpty(),
                onValueChange = actions.onApiKeyChange,
                placeholder = if (translation.provider.requiresKey) "Required" else "None",
                secret = true,
            )
        }

        LabelledField(label = "Model") {
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

/** Who answers about a single word, which need not be who translates a sentence. */
@Composable
private fun WordLookupSection(
    translation: TranslationSettings,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = "Looking up a word",
        footnote = translation.wordLookup.footnote,
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SegmentedControl(
                options = WordLookup.entries,
                selected = translation.wordLookup,
                label = { it.label },
                onSelect = actions.onWordLookupChange,
            )

            // Only where it does anything. Google is the one choice here with
            // something to configure, and a key field under a dictionary that
            // has never heard of one is a field that asks a question with no
            // answer.
            if (translation.wordLookup == WordLookup.Google) {
                LabelledField(label = "Cloud Translation key (optional)") {
                    SettingsTextField(
                        value = translation.googleApiKey.orEmpty(),
                        onValueChange = actions.onGoogleApiKeyChange,
                        placeholder = "Public endpoint",
                        secret = true,
                    )
                }
            }
        }
    }
}
