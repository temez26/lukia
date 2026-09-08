package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.reader_notes_under_paragraph
import io.lukia.resources.reader_translate_into
import io.lukia.resources.settings_hold_again
import io.lukia.resources.settings_hold_again_detail
import io.lukia.resources.settings_notes_detail
import io.lukia.resources.settings_pointing_at_the_book
import io.lukia.resources.settings_pointing_footnote
import io.lukia.resources.settings_tap_to_translate
import io.lukia.resources.settings_tap_to_translate_info
import io.lukia.resources.settings_translate_into_footnote
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * What is translated, when, and into what.
 *
 * Everything here is a decision about the book in front of the reader rather
 * than about the machinery behind it: which language they are reading into, and
 * what a thumb on the page asks for. Who actually answers is a separate question
 * and lives in [ProviderSettings] - and so, now, is everything that only a model
 * can do.
 *
 * That is the whole of why this page is two groups rather than four. A word can
 * be answered by a dictionary that needs nothing set up, so a tap and a hold on
 * one belong here, to everybody. A sentence, a paragraph and a screenful at a
 * time cannot be answered by anything but a translation service - offered here,
 * they were three settings that did nothing for most readers and said nothing
 * about why.
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
    }
}

/**
 * What a thumb on the page does: one word translated, and that word asked to say
 * more.
 *
 * Two gestures with one job each, written as switches rather than as a row of
 * units to choose between. The units are still there - a tap can be raised to
 * the sentence or the paragraph - but only a translation service can answer
 * either, so that choice went where the service is and this became the question
 * a reader can actually answer on the day they install the app: does a tap do
 * anything, and does a hold.
 *
 * A tap sets the word rather than whatever the reader last raised it to. The
 * switch is the plain version of the setting, and a switch that turned a gesture
 * back on as "paragraph" because of something set months ago on another page is
 * a switch that did something it did not say.
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
                info = stringResource(Res.string.settings_tap_to_translate_info),
                checked = translation.gestures.tap != null,
                onChange = { on ->
                    actions.onGesturesChange(
                        translation.gestures.copy(tap = if (on) PassageUnit.Word else null),
                    )
                },
            )

            SettingsToggle(
                label = stringResource(Res.string.settings_hold_again),
                info = stringResource(
                    Res.string.settings_hold_again_detail,
                    translation.wordSite.displayName(),
                ),
                checked = translation.gestures.wordDetails,
                onChange = {
                    actions.onGesturesChange(translation.gestures.copy(wordDetails = it))
                },
            )

            SettingsToggle(
                label = stringResource(Res.string.reader_notes_under_paragraph),
                info = stringResource(Res.string.settings_notes_detail),
                checked = translation.notes,
                onChange = actions.onNotesChange,
            )
        }
    }
}
