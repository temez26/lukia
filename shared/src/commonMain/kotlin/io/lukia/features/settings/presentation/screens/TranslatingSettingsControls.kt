package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.lookup.WordSite
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.settings.WordLookup
import io.lukia.features.translate.presentation.displayFootnote
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.reader_notes_under_paragraph
import io.lukia.resources.reader_translate_into
import io.lukia.resources.settings_address
import io.lukia.resources.settings_cloud_key
import io.lukia.resources.settings_hold_again
import io.lukia.resources.settings_looking_up_a_word
import io.lukia.resources.settings_pointing_at_the_book
import io.lukia.resources.settings_pointing_footnote
import io.lukia.resources.settings_public_endpoint
import io.lukia.resources.settings_reading_more
import io.lukia.resources.settings_reading_more_footnote
import io.lukia.resources.settings_site_address_example
import io.lukia.resources.settings_tap_to_translate
import io.lukia.resources.settings_translate_into_footnote
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * What is translated, when, and into what.
 *
 * Everything a reader can use on the day they install the app and nothing else:
 * the language, what a thumb on the page does, what answers about a single word,
 * and where they are sent to read more - all over an ordinary web API, free,
 * with nothing to fill in.
 *
 * What only a model can do is a separate page - see [AiTranslationSettings]. A
 * word is the one thing a service that knows nothing about the book can be
 * relied on for, and the only thing a dictionary has an entry for, so the line
 * falls exactly there: a word here; a sentence, a paragraph and a screenful at a
 * time over there.
 *
 * The language comes first because it is the one setting nothing works without,
 * and the one most people came here to change.
 *
 * Nothing here is on to begin with - see
 * [io.lukia.features.translate.domain.settings.TranslationGestures]. A book
 * opens as a book, and the reader says what a thumb on it should ask for.
 */
@Composable
internal fun TranslatingSettings(
    translation: TranslationSettings,
    actions: TranslatingActions,
    /**
     * Who answers about a word, as opposed to what is translated - see
     * [ProviderActions].
     *
     * Here because the two groups at the foot of this page are a choice between
     * services, and the actions are still grouped by what they change rather
     * than by which page happens to show them.
     */
    providerActions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSection(
            title = stringResource(Res.string.reader_translate_into),
            info = stringResource(Res.string.settings_translate_into_footnote),
            groupColor = groupColor,
            padding = Space.xs,
        ) {
            ChoiceList(
                options = TargetLanguage.entries,
                selected = translation.targetLanguage,
                label = { it.endonym },
                // The English name too, so somebody who cannot yet read the
                // language they are learning can still find it in the list.
                detail = { it.englishName.takeIf { name -> name != it.endonym } },
                onSelect = actions.onTargetLanguageChange,
            )
        }

        GestureSection(
            translation = translation,
            actions = actions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        WordLookupSection(
            translation = translation,
            actions = providerActions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        WordSiteSection(
            translation = translation,
            actions = providerActions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/**
 * What a thumb on the page does: one word translated, and that word asked to say
 * more.
 *
 * Two gestures with one job each, written as switches rather than a row of units
 * to choose between. The units still exist - a tap can be raised to the sentence
 * or the paragraph - but only a translation service can answer either, so that
 * choice went where the service is, leaving the question a reader can answer on
 * day one: does a tap do anything, and does a hold.
 *
 * The switch says only whether a tap translates. How much it asks for is a
 * separate setting on the AI translation page - see
 * [io.lukia.features.translate.domain.settings.TranslationGestures.tapTranslates]
 * for why the two are apart. Turning the tap off here leaves the unit alone, so
 * turning it back on gives the reader what they had rather than the word.
 */
@Composable
private fun GestureSection(
    translation: TranslationSettings,
    actions: TranslatingActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_pointing_at_the_book),
        info = stringResource(Res.string.settings_pointing_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            SettingsToggle(
                label = stringResource(Res.string.settings_tap_to_translate),
                checked = translation.gestures.tapTranslates,
                onChange = {
                    actions.onGesturesChange(translation.gestures.copy(tapTranslates = it))
                },
            )

            SettingsToggle(
                label = stringResource(Res.string.settings_hold_again),
                checked = translation.gestures.wordDetails,
                onChange = {
                    actions.onGesturesChange(translation.gestures.copy(wordDetails = it))
                },
            )

            SettingsToggle(
                label = stringResource(Res.string.reader_notes_under_paragraph),
                checked = translation.notes,
                onChange = actions.onNotesChange,
            )
        }
    }
}

/**
 * Who answers about a single word, which need not be who translates a sentence.
 *
 * On this page rather than beside the translation service, because both answers
 * are ordinary web APIs that need nothing filled in - which is the whole of what
 * a reader gets on an app they have just installed, and the reason a word is
 * where the two settings pages divide.
 *
 * The service used to be a third choice here. It is the one thing on this page
 * that could not answer until the reader had gone and set something up, for the
 * one unit that never needed it - see [WordLookup].
 */
@Composable
private fun WordLookupSection(
    translation: TranslationSettings,
    actions: ProviderActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_looking_up_a_word),
        info = translation.wordLookup.displayFootnote(),
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
        info = stringResource(Res.string.settings_reading_more_footnote),
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
