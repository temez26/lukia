package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.presentation.components.settings.PanelDisclosureRow
import io.lukia.features.reader.presentation.components.settings.PanelSwitchRow
import io.lukia.features.reader.presentation.components.settings.SettingGroup
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.presentation.GestureControl
import io.lukia.resources.Res
import io.lukia.resources.reader_gesture_long_press
import io.lukia.resources.reader_gesture_tap
import io.lukia.resources.reader_hold_again
import io.lukia.resources.reader_notes_under_paragraph
import io.lukia.resources.reader_show_translations
import io.lukia.resources.reader_translate_as_you_read
import io.lukia.resources.reader_translate_into
import io.lukia.resources.reader_translate_this_page
import io.lukia.resources.reader_translation
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.ChoiceDensity
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.components.surface.SectionLabel
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.disclosureIn
import io.lukia.shared.presentation.theme.disclosureOut
import org.jetbrains.compose.resources.stringResource

/**
 * Translating, at the top of the reader's own settings panel.
 *
 * Two kinds of thing, in the order somebody reaches for them. First what
 * translating is doing right now - see [ReaderTranslationSessionControls] -
 * which a phone keeps here because its toolbar has no room for the glyphs a
 * wider window gets. Then what translating is *set* to do: the language, and
 * what a thumb on the page asks for.
 *
 * Those two settings are on the settings screen as well, and are here too
 * because they are the ones a reader wants mid-chapter: a sentence that came
 * back in a language they did not want, or a tap that keeps translating a whole
 * paragraph they were only skimming. Leaving the book, crossing two screens and
 * coming back is a long way round for a control that acts on the page in front
 * of them - and unlike everything else in Settings, both of these are answered
 * by looking at the book rather than at the setting.
 *
 * Shown at all only once something is set up to translate with. A language
 * picker for a translator that does not exist is a setting that can only
 * disappoint.
 *
 * At the top rather than after the type settings: this is the one group here
 * that changes what is written on the page rather than how it is set.
 */
@Composable
internal fun ReaderTranslationSettings(
    translation: ReaderTranslationUi,
    preferences: ReaderTranslationPreferences,
    modifier: Modifier = Modifier,
) {
    if (!translation.configured) return

    val inset = LocalLukiaDimens.current.panelPadding

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = stringResource(Res.string.reader_translation),
            modifier = Modifier.padding(start = inset, end = inset, bottom = Space.sm),
        )

        ReaderTranslationSessionControls(translation = translation, inset = inset)

        TargetLanguageSetting(preferences = preferences, inset = inset)

        // Last of the group, because they are the least likely to be changed
        // twice in an evening: what a tap does is a decision somebody makes once
        // and then stops thinking about.
        //
        // Stacked under their labels on every window, unlike everything else in
        // the panel. These are the only controls here with four segments of real
        // words in them, and the wide layout's control column cuts "Sentence"
        // and "Paragraph" to stubs - a control whose choices cannot be read is
        // worse than a row that does not line up with the ones below it.
        SettingGroup(
            label = stringResource(Res.string.reader_gesture_tap),
            wide = false,
            inset = inset,
        ) {
            GestureControl(
                selected = preferences.gestures.tap,
                onSelect = { preferences.onGesturesChange(preferences.gestures.copy(tap = it)) },
            )
        }

        SettingGroup(
            label = stringResource(Res.string.reader_gesture_long_press),
            wide = false,
            inset = inset,
        ) {
            GestureControl(
                selected = preferences.gestures.longPress,
                onSelect = {
                    preferences.onGesturesChange(preferences.gestures.copy(longPress = it))
                },
            )
        }

        // Under the hold rather than beside it, because it is about that gesture
        // - and a switch rather than a fourth segment in the control above,
        // because it is not one of the things a hold can ask for. It is what a
        // hold does on a word that has already answered, whatever it is set to.
        PanelSwitchRow(
            label = stringResource(Res.string.reader_hold_again),
            checked = preferences.gestures.wordDetails,
            onChange = {
                preferences.onGesturesChange(preferences.gestures.copy(wordDetails = it))
            },
            inset = inset,
        )

        // Next to the row above it because the two are the same trade made in
        // opposite directions: what a word has to say, in the page or out of it.
        PanelSwitchRow(
            label = stringResource(Res.string.reader_notes_under_paragraph),
            checked = preferences.notes,
            onChange = preferences.onNotesChange,
            inset = inset,
        )

        LukiaDivider(
            inset = inset,
            endInset = inset,
            modifier = Modifier.padding(vertical = Space.md),
        )
    }
}

/**
 * What translating is doing right now, written out as a button and two switches.
 *
 * The same three things the toolbar offers a pointed-at window - see
 * [TranslationControls] - but named. A tooltip is what tells a reader with a
 * mouse which glyph is which, and a phone has no hover to show one, so the
 * controls that were three unlabelled marks squeezed into a thumb-sized bar
 * become rows that say what they are.
 *
 * Empty on a window whose toolbar carries them, so that each control is in
 * exactly one place whatever the window is doing.
 */
@Composable
private fun ReaderTranslationSessionControls(
    translation: ReaderTranslationUi,
    inset: Dp,
) {
    if (!translation.inPanel) return

    // Not there at all until the reader has asked for them in Settings. These
    // two spend a screenful at a time - see BulkTranslation - and a control that
    // can do that is one somebody presses to find out what it does. Off is off
    // wherever it is reached from.
    if (translation.offered.onScreen) {
        LukiaButton(
            label = stringResource(Res.string.reader_translate_this_page),
            onClick = translation.onTranslateOnScreen,
            variant = ButtonVariant.Secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset),
        )
    }

    if (translation.offered.asYouRead) {
        PanelSwitchRow(
            label = stringResource(Res.string.reader_translate_as_you_read),
            checked = translation.onScreen.continuous,
            onChange = translation.onContinuousChange,
            inset = inset,
        )
    }

    // Nothing to put away until something has been translated, so the row is
    // there from the start - where the reader will look for it - but does
    // nothing until there is something for it to do.
    PanelSwitchRow(
        label = stringResource(Res.string.reader_show_translations),
        checked = translation.shown.visible,
        onChange = translation.onVisibleChange,
        inset = inset,
        enabled = translation.shown.any,
    )
}

/**
 * The language the book is read into: the one in force, and the other sixteen a
 * press away.
 *
 * Collapsed until asked for, and collapsed again the moment a language is
 * chosen. Seventeen rows is more than the whole rest of the panel put together,
 * and a panel that opens with them showing is one where the type settings have
 * been pushed off the bottom of a phone.
 *
 * Compact even so - see [ChoiceDensity]. The settings screen's rows are a page's
 * worth of row each, and seventeen of those opened inside a panel capped at a
 * few hundred points is a list twice as tall as the thing holding it: the reader
 * scrolls through languages to get back to the text size. Here they are the
 * height of the rows the panel is otherwise built from.
 */
@Composable
private fun TargetLanguageSetting(
    preferences: ReaderTranslationPreferences,
    inset: Dp,
) {
    var expanded by remember { mutableStateOf(false) }

    PanelDisclosureRow(
        label = stringResource(Res.string.reader_translate_into),
        value = preferences.targetLanguage.endonym,
        expanded = expanded,
        onClick = { expanded = !expanded },
        inset = inset,
    )

    AnimatedVisibility(
        visible = expanded,
        enter = disclosureIn(),
        exit = disclosureOut(),
    ) {
        ChoiceList(
            options = TargetLanguage.entries,
            selected = preferences.targetLanguage,
            label = { it.endonym },
            // The English name too, so somebody who cannot yet read the language
            // they are learning can still find it in the list.
            detail = { it.englishName.takeIf { name -> name != it.endonym } },
            onSelect = {
                preferences.onTargetLanguageChange(it)
                expanded = false
            },
            modifier = Modifier.padding(horizontal = inset),
            density = ChoiceDensity.Compact,
        )
    }
}
