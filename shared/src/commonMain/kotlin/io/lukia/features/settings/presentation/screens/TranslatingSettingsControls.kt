package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.AvailableModels
import io.lukia.features.settings.presentation.ConnectionTest
import io.lukia.features.settings.presentation.ModelReadiness
import io.lukia.features.settings.presentation.components.ChoiceList
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * Who does the translating, what they are asked for, and into what.
 *
 * The longest of the three areas by a distance, so it is written as its three
 * questions rather than as one column of controls: what service, what to do with
 * a single word, and which language everything lands in.
 */
@Composable
internal fun TranslatingSettings(
    translation: TranslationSettings,
    connectionTest: ConnectionTest,
    availableModels: AvailableModels,
    modelReadiness: ModelReadiness,
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
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        TranslationServiceSection(
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
            groupColor = groupColor,
        )

        WordLookupSection(
            translation = translation,
            onWordLookupChange = onWordLookupChange,
            onGoogleApiKeyChange = onGoogleApiKeyChange,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        SettingsSection(
            title = "Translate into",
            footnote = "Tap a sentence while reading to translate it, and " +
                "tap it again for the original.",
            groupColor = groupColor,
            padding = Space.xs,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        ) {
            ChoiceList(
                options = TargetLanguage.entries,
                selected = translation.targetLanguage,
                label = { it.endonym },
                // The English name too, so somebody who cannot yet read the
                // language they are learning can still find it in the list.
                detail = { it.englishName.takeIf { name -> name != it.endonym } },
                onSelect = onTargetLanguageChange,
            )
        }
    }
}

/**
 * Who translates, and everything they need to be reachable: the service, where
 * it lives, what it is owed, which model, how long to wait, and a way to find
 * out whether any of that was right.
 */
@Composable
private fun TranslationServiceSection(
    translation: TranslationSettings,
    connectionTest: ConnectionTest,
    availableModels: AvailableModels,
    modelReadiness: ModelReadiness,
    onProviderChange: (TranslationProvider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onModelChoose: (String) -> Unit,
    onLoadModels: () -> Unit,
    onLoadChosenModel: () -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onTestConnection: () -> Unit,
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
                onSelect = onProviderChange,
            )

            ProviderCredentials(
                translation = translation,
                onBaseUrlChange = onBaseUrlChange,
                onApiKeyChange = onApiKeyChange,
                onModelChange = onModelChange,
            )

            ModelChoice(
                translation = translation,
                availableModels = availableModels,
                modelReadiness = modelReadiness,
                onModelChoose = onModelChoose,
                onLoadModels = onLoadModels,
                onLoadChosenModel = onLoadChosenModel,
            )

            TimeoutField(
                provider = translation.provider,
                seconds = translation.timeoutSeconds,
                onChange = onTimeoutChange,
            )

            ConnectionTestRow(
                state = connectionTest,
                enabled = translation.isConfigured,
                onTest = onTestConnection,
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
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (translation.provider.hasConfigurableEndpoint) {
            LabelledField(label = "Address") {
                SettingsTextField(
                    value = translation.baseUrl,
                    onValueChange = onBaseUrlChange,
                    placeholder = translation.provider.defaultBaseUrl,
                )
            }
        }

        val apiKeyLabel =
            if (translation.provider.requiresKey) "API key" else "API key (optional)"

        LabelledField(label = apiKeyLabel) {
            SettingsTextField(
                value = translation.apiKey.orEmpty(),
                onValueChange = onApiKeyChange,
                placeholder = if (translation.provider.requiresKey) "Required" else "None",
                secret = true,
            )
        }

        LabelledField(label = "Model") {
            SettingsTextField(
                value = translation.model,
                onValueChange = onModelChange,
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
    availableModels: AvailableModels,
    modelReadiness: ModelReadiness,
    onModelChoose: (String) -> Unit,
    onLoadModels: () -> Unit,
    onLoadChosenModel: () -> Unit,
) {
    if (translation.provider.hasConfigurableEndpoint) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            ServerModels(
                state = availableModels,
                selected = translation.model,
                onSelect = onModelChoose,
                onLoad = onLoadModels,
            )

            ModelReadinessRow(
                state = modelReadiness,
                model = translation.model,
                onLoad = onLoadChosenModel,
            )
        }
    } else if (translation.provider.suggestedModels.isNotEmpty()) {
        ChoiceList(
            options = translation.provider.suggestedModels,
            selected = translation.model,
            label = { it },
            onSelect = onModelChoose,
        )
    }
}

@Composable
private fun WordLookupSection(
    translation: TranslationSettings,
    onWordLookupChange: (WordLookup) -> Unit,
    onGoogleApiKeyChange: (String) -> Unit,
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
                onSelect = onWordLookupChange,
            )

            // Only where it does anything. Google is the one choice here with
            // something to configure, and a key field under a dictionary that
            // has never heard of one is a field that asks a question with no
            // answer.
            if (translation.wordLookup == WordLookup.Google) {
                LabelledField(label = "Cloud Translation key (optional)") {
                    SettingsTextField(
                        value = translation.googleApiKey.orEmpty(),
                        onValueChange = onGoogleApiKeyChange,
                        placeholder = "Public endpoint",
                        secret = true,
                    )
                }
            }
        }
    }
}
