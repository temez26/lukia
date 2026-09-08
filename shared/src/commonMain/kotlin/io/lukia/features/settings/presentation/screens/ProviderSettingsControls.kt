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
import io.lukia.features.translate.domain.lookup.WordSite
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.settings.WordLookup
import io.lukia.features.translate.presentation.displayFootnote
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.settings_address
import io.lukia.resources.settings_cloud_key
import io.lukia.resources.settings_looking_up_a_word
import io.lukia.resources.settings_public_endpoint
import io.lukia.resources.settings_reading_more
import io.lukia.resources.settings_reading_more_footnote
import io.lukia.resources.settings_site_address_example
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Who answers, everything they need to be reachable, and everything that only
 * they can do.
 *
 * Its own group rather than the tail of Translating, because these are the
 * settings that can be filled in wrongly: an address, a key, a model name, a
 * timeout. Everything about them - the fields, the catalogue, the connection
 * test - is machinery, and a reader who only wants to change which language they
 * read into should never have to scroll past it.
 *
 * Two halves, in that order, which is not the order they were in.
 *
 * First what a single word goes to and where the reader is sent to read more
 * about one, because that is the part that works: a dictionary and Google are
 * ordinary web APIs, free, and set up already on a Lukia nobody has configured.
 * A word is the one thing a service that knows nothing about the book can be
 * relied on for.
 *
 * Then the translation service and everything that exists only because of it -
 * a gesture raised past the word, and translating a screenful at a time. All of
 * it is marked, all of it is a model the reader has to find, pay for and point
 * this at, and none of it is anybody's first afternoon with the app. The two
 * settings at the foot used to sit on the Translating page beside the language,
 * where they were controls that did nothing for most readers and said nothing
 * about why.
 */
@Composable
internal fun ProviderSettings(
    translation: TranslationSettings,
    state: ProviderState,
    actions: ProviderActions,
    /**
     * What is translated, as opposed to who answers - see [TranslatingActions].
     *
     * Here as well as on the Translating page because two of those settings
     * only mean anything beside a translation service: a gesture asking for a
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
        WordLookupSection(
            translation = translation,
            actions = actions,
            groupColor = groupColor,
        )

        WordSiteSection(
            translation = translation,
            actions = actions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        TranslationServiceSection(
            translation = translation,
            state = state,
            actions = actions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
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
 * Who answers about a single word, which need not be who translates a sentence.
 *
 * First on the page, because two of the three answer over an ordinary web API
 * with nothing to fill in - which is the whole of what a reader gets on an app
 * they have just installed. The third is the translation service further down,
 * and choosing it is choosing to set that up, so the group wears the mark while
 * it is chosen and not otherwise.
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
        beta = translation.wordLookup == WordLookup.Translator,
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
