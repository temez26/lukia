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
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.lookup.WordSite
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.settings.WordLookup
import io.lukia.features.translate.presentation.displayFootnote
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.settings_address
import io.lukia.resources.settings_api_key
import io.lukia.resources.settings_api_key_optional
import io.lukia.resources.settings_cloud_key
import io.lukia.resources.settings_looking_up_a_word
import io.lukia.resources.settings_model
import io.lukia.resources.settings_none
import io.lukia.resources.settings_public_endpoint
import io.lukia.resources.settings_reading_more
import io.lukia.resources.settings_reading_more_footnote
import io.lukia.resources.settings_required
import io.lukia.resources.settings_site_address_example
import io.lukia.resources.settings_translation_service
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Who answers, and everything they need to be reachable.
 *
 * Its own group rather than the tail of Translating, because these two are the
 * only settings in the app that can be filled in wrongly: an address, a key, a
 * model name, a timeout. Everything about them - the fields, the catalogue, the
 * connection test - is machinery, and a reader who only wants to change which
 * language they read into should never have to scroll past it.
 *
 * Three groups rather than one, because a single word and a whole sentence are
 * different questions: a dictionary answers the first well and cannot answer the
 * second at all - and neither of them answers the third, which is where somebody
 * goes to read properly about a word they have stopped on.
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

        WordSiteSection(
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
        title = stringResource(Res.string.settings_translation_service),
        footnote = translation.provider.footnote(),
        groupColor = groupColor,
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

/** Who answers about a single word, which need not be who translates a sentence. */
@Composable
private fun WordLookupSection(
    translation: TranslationSettings,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_looking_up_a_word),
        footnote = translation.wordLookup.displayFootnote(),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SegmentedControl(
                options = WordLookup.entries,
                selected = translation.wordLookup,
                label = { it.displayName() },
                onSelect = actions.onWordLookupChange,
            )

            // Only where it does anything. Google is the one choice here with
            // something to configure, and a key field under a dictionary that
            // has never heard of one is a field that asks a question with no
            // answer.
            if (translation.wordLookup == WordLookup.Google) {
                LabelledField(label = stringResource(Res.string.settings_cloud_key)) {
                    SettingsTextField(
                        value = translation.googleApiKey.orEmpty(),
                        onValueChange = actions.onGoogleApiKeyChange,
                        placeholder = stringResource(Res.string.settings_public_endpoint),
                        secret = true,
                    )
                }
            }
        }
    }
}

/**
 * Where the reader goes when the line over the word was not enough.
 *
 * A list rather than a row of segments, because a choice here carries a line
 * saying what is at the other end - these are other people's websites, and their
 * names alone do not say which is a dictionary and which is a translator.
 */
@Composable
private fun WordSiteSection(
    translation: TranslationSettings,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_reading_more),
        footnote = stringResource(Res.string.settings_reading_more_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            ChoiceList(
                options = WordSite.entries,
                selected = translation.wordSite,
                label = { it.displayName() },
                detail = { it.displayFootnote() },
                onSelect = actions.onWordSiteChange,
            )

            // Only where it does anything - the same reasoning as the Google key
            // above. Every other choice here already knows its own address.
            if (translation.wordSite == WordSite.Custom) {
                LabelledField(label = stringResource(Res.string.settings_address)) {
                    SettingsTextField(
                        value = translation.wordSiteUrlTemplate.orEmpty(),
                        onValueChange = actions.onWordSiteUrlChange,
                        placeholder = stringResource(Res.string.settings_site_address_example),
                    )
                }
            }
        }
    }
}
