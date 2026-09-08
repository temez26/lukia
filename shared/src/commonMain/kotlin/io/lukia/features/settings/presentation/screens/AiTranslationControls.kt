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
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.presentation.GestureControl
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.reader_gesture_long_press
import io.lukia.resources.reader_gesture_tap
import io.lukia.resources.reader_keep_translating
import io.lukia.resources.reader_translate_on_screen
import io.lukia.resources.settings_address
import io.lukia.resources.settings_api_key
import io.lukia.resources.settings_api_key_optional
import io.lukia.resources.settings_as_you_read_detail
import io.lukia.resources.settings_asking_for_more
import io.lukia.resources.settings_asking_for_more_footnote
import io.lukia.resources.settings_model
import io.lukia.resources.settings_none
import io.lukia.resources.settings_on_screen_detail
import io.lukia.resources.settings_required
import io.lukia.resources.settings_translating_ahead
import io.lukia.resources.settings_translating_ahead_footnote
import io.lukia.resources.settings_translation_service
import io.lukia.resources.settings_translation_service_footnote
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

// Everything that only means anything once the reader has found a model, paid
// for it and pointed this at it: the service itself, how much of the book a
// gesture may ask it for, and the two ways of asking it for more than the reader
// pointed at. All of it wears the beta mark.
//
// Apart from Translating, which is everything that works on an install nobody
// has configured - including the dictionary that answers a single word.

/**
 * Everything translating does that only a model can do.
 *
 * A page of its own, and the segment for it reads "Beta" - which is the
 * shortest true thing that can be said about everything on it. It was called
 * Providers, which described the fields in it - an address, a key, a model name,
 * a timeout - and said nothing about why a reader would open it. What is
 * actually behind it is one decision: whether this reader has an AI translation
 * service, and what they want it to do.
 *
 * Everything answerable without one went to Translating, including the two
 * groups that choose who answers about a single word. A word is where the line
 * falls: a dictionary has an entry for one and none for a sentence.
 *
 * The service comes first because nothing under it does anything until it is set
 * up. Then how much of the book a gesture may ask it for, and last the two ways
 * of asking it for more than the reader pointed at - which is the order of how
 * much of the reader's money each one can spend.
 */
@Composable
internal fun AiTranslationSettings(
    translation: TranslationSettings,
    state: ProviderState,
    actions: ProviderActions,
    /**
     * What is translated, as opposed to who answers - see [TranslatingActions].
     *
     * Here as well as on the Translating page because two of those settings only
     * mean anything beside a translation service: a gesture asking for a
     * sentence, and the two ways of translating ahead. They are shown under the
     * service for that reason, and they are changed through the same actions
     * wherever they are shown.
     */
    translatingActions: TranslatingActions,
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

        GestureUnitSection(
            translation = translation,
            actions = translatingActions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        TranslatingAheadSection(
            translation = translation,
            actions = translatingActions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/**
 * Who translates a passage with a model: the service, where it lives, what it is
 * owed, which model, how long to wait, and a way to find out whether any of that
 * was right.
 *
 * Marked as beta for the whole group rather than switch by switch, because what
 * is unfinished is the thing they have in common. Everything above this reaches
 * a published API that answers a word in a tenth of a second for nothing;
 * everything in here reaches a model that takes seconds, bills for them, and
 * fails in ways an address and a key cannot tell apart. A reader should know
 * which of the two they are setting up before they paste a key into it.
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


/**
 * How much of the book a tap and a hold ask for, where that question can only be
 * asked.
 *
 * Only the sentence and the paragraph, because they are the only two a model has
 * to be asked for. Whether a tap translates the word is a switch on the
 * Translating page, where the dictionary that answers a word also lives - and
 * offering the word again here would be a second control writing the same
 * setting in other words.
 *
 * So a reader whose tap is set to the word finds nothing selected in the row
 * for it. That is the honest answer rather than a gap: their tap is set,
 * elsewhere, to something smaller than anything this page can offer.
 *
 * Turning a gesture off here is the same off as the switch there. There is one
 * setting behind both, and it says what a gesture asks for, including nothing.
 */
@Composable
private fun GestureUnitSection(
    translation: TranslationSettings,
    actions: TranslatingActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_asking_for_more),
        info = stringResource(Res.string.settings_asking_for_more_footnote),
        groupColor = groupColor,
        beta = true,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            LabelledField(label = stringResource(Res.string.reader_gesture_tap)) {
                GestureControl(
                    selected = translation.gestures.tap,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(tap = it))
                    },
                    units = ModelUnits,
                )
            }

            LabelledField(label = stringResource(Res.string.reader_gesture_long_press)) {
                GestureControl(
                    selected = translation.gestures.longPress,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(longPress = it))
                    },
                    units = ModelUnits,
                )
            }
        }
    }
}

/**
 * What the reader may be asked to translate without pointing at anything.
 *
 * Under the service rather than on the Translating page beside the language,
 * because both of these work a paragraph at a time and have nowhere to go but a
 * model somebody pays by the passage. Beside the language they were two
 * expensive switches in the middle of the settings a reader came for.
 *
 * The mark is on the group as well as on each switch. The switches say that each
 * one is rough; the group says what they have in common, which is the part a
 * reader has to have set up before either of them does anything at all.
 */
@Composable
private fun TranslatingAheadSection(
    translation: TranslationSettings,
    actions: TranslatingActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_translating_ahead),
        info = stringResource(Res.string.settings_translating_ahead_footnote),
        groupColor = groupColor,
        beta = true,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            BetaToggle(
                label = stringResource(Res.string.reader_translate_on_screen),
                detail = stringResource(Res.string.settings_on_screen_detail),
                checked = translation.bulkTranslation.onScreen,
                onChange = {
                    actions.onBulkTranslationChange(
                        translation.bulkTranslation.copy(onScreen = it),
                    )
                },
            )

            BetaToggle(
                label = stringResource(Res.string.reader_keep_translating),
                detail = stringResource(Res.string.settings_as_you_read_detail),
                checked = translation.bulkTranslation.asYouRead,
                onChange = {
                    actions.onBulkTranslationChange(
                        translation.bulkTranslation.copy(asYouRead = it),
                    )
                },
            )
        }
    }
}

/**
 * The passage units nothing but a model can answer.
 *
 * The word is left out for the reason [GestureUnitSection] gives: it is the one
 * unit a dictionary has an entry for, and it is set by a switch on the
 * Translating page beside the dictionary that answers it.
 */
private val ModelUnits = listOf(PassageUnit.Sentence, PassageUnit.Paragraph)
