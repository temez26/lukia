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
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.presentation.GestureControl
import io.lukia.features.translate.presentation.PassageUnitControl
import io.lukia.resources.Res
import io.lukia.resources.reader_gesture_long_press
import io.lukia.resources.reader_gesture_tap
import io.lukia.resources.reader_keep_translating
import io.lukia.resources.reader_translate_on_screen
import io.lukia.resources.settings_ai_translation
import io.lukia.resources.settings_as_you_read_detail
import io.lukia.resources.settings_asking_for_more
import io.lukia.resources.settings_asking_for_more_footnote
import io.lukia.resources.settings_on_screen_detail
import io.lukia.resources.settings_translating_ahead
import io.lukia.resources.settings_translating_ahead_footnote
import io.lukia.resources.settings_use_ai
import io.lukia.resources.settings_use_ai_detail
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
 * A switch over the whole of it comes first, and while it is off it is the only
 * thing here - see [AiSwitchSection]. Then the service, because nothing under
 * that does anything until it is set up; then how much of the book a gesture may
 * ask it for; and last the two ways of asking it for more than the reader
 * pointed at, which is the order of how much of the reader's money each one can
 * spend.
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
        AiSwitchSection(
            on = translation.aiTranslation,
            onChange = actions.onAiTranslationChange,
            groupColor = groupColor,
        )

        // Nothing below the switch while it is off. The point of the switch is
        // that Lukia is then the app it is without a model, and a page still
        // offering an API key and a button that spends money a screenful at a
        // time is not that app with the feature turned off - it is that app with
        // the feature turned off in one place.
        if (!translation.aiTranslation) return@Column

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
 * The one switch this whole page is behind.
 *
 * First, and alone while it is off. Everything under it costs money, needs a
 * service the reader has to go and find, and is unfinished - so there is a
 * single place to say no to all of it, and saying no leaves an app that
 * translates single words through a free dictionary and does nothing else
 * anywhere: no key fields here, no glyphs in the reader's bar, no gesture asking
 * for a paragraph nothing can answer.
 *
 * Nothing is thrown away by turning it off. What the reader set up is still
 * stored and comes back with the switch.
 */
@Composable
private fun AiSwitchSection(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_ai_translation),
        groupColor = groupColor,
        beta = true,
        modifier = modifier,
    ) {
        SettingsToggle(
            label = stringResource(Res.string.settings_use_ai),
            // Shown rather than behind a mark: this is the switch that decides
            // whether the app can spend money at all, and that is worth reading
            // before pressing rather than after.
            detail = stringResource(Res.string.settings_use_ai_detail),
            checked = on,
            onChange = onChange,
        )
    }
}

/**
 * How much of the book a tap and a hold ask for, where that question can only be
 * asked.
 *
 * The two rows are not the same shape, because the two gestures are not set in
 * the same number of places.
 *
 * A tap has a switch on the Translating page saying whether it translates at
 * all, so this row says only how much it asks for: the word, the sentence or the
 * paragraph, with no off. An off here would be a second page turning that switch
 * off, which is what this pair used to do to each other.
 *
 * A hold has no switch anywhere else - translating by holding is offered here
 * and nowhere else - so its row keeps its own off, and the word with it, since
 * nothing else can turn the gesture on.
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
                // No off: whether a tap translates is the switch on the
                // Translating page, and a row here that could turn it off would
                // be two pages writing one setting - which is exactly what this
                // pair used to be. The word is this row's own way of asking for
                // nothing more than a word.
                PassageUnitControl(
                    selected = translation.gestures.tapUnit,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(tapUnit = it))
                    },
                )
            }

            LabelledField(label = stringResource(Res.string.reader_gesture_long_press)) {
                GestureControl(
                    selected = translation.gestures.longPress,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(longPress = it))
                    },
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
