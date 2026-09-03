package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.presentation.GestureControl
import io.lukia.features.translate.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.reader_gesture_long_press
import io.lukia.resources.reader_gesture_tap
import io.lukia.resources.reader_keep_translating
import io.lukia.resources.reader_notes_under_paragraph
import io.lukia.resources.reader_translate_into
import io.lukia.resources.reader_translate_on_screen
import io.lukia.resources.settings_as_you_read_detail
import io.lukia.resources.settings_hold_again
import io.lukia.resources.settings_hold_again_detail
import io.lukia.resources.settings_notes_detail
import io.lukia.resources.settings_on_screen_detail
import io.lukia.resources.settings_pointing_at_the_book
import io.lukia.resources.settings_pointing_footnote
import io.lukia.resources.settings_translate_into_footnote
import io.lukia.resources.settings_translating_ahead
import io.lukia.resources.settings_translating_ahead_footnote
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * What is translated, when, and into what.
 *
 * Everything here is a decision about the book in front of the reader rather
 * than about the machinery behind it: which language they are reading into, what
 * a tap and a press ask for, and whether they are offered a screenful at a time.
 * Who actually answers is a separate question and lives in [ProviderSettings].
 *
 * The language comes first because it is the one setting nothing works without,
 * and the one most people came here to change.
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
            footnote = stringResource(Res.string.settings_translate_into_footnote),
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

        TranslatingAheadSection(
            translation = translation,
            actions = actions,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/** What a tap and a press ask the translator for, including nothing at all. */
@Composable
private fun GestureSection(
    translation: TranslationSettings,
    actions: TranslatingActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_pointing_at_the_book),
        footnote = stringResource(Res.string.settings_pointing_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            LabelledField(label = stringResource(Res.string.reader_gesture_tap)) {
                GestureControl(
                    selected = translation.gestures.tap,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(tap = it))
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

            SettingsToggle(
                label = stringResource(Res.string.reader_notes_under_paragraph),
                detail = stringResource(Res.string.settings_notes_detail),
                checked = translation.notes,
                onChange = actions.onNotesChange,
            )

            SettingsToggle(
                label = stringResource(Res.string.settings_hold_again),
                detail = stringResource(
                    Res.string.settings_hold_again_detail,
                    translation.wordSite.displayName(),
                ),
                checked = translation.gestures.wordDetails,
                onChange = {
                    actions.onGesturesChange(translation.gestures.copy(wordDetails = it))
                },
            )
        }
    }
}

/**
 * What the reader may be asked to translate without being pointed at anything.
 *
 * Kept apart from the gestures above it: those are how the reader works the book
 * and are on for everybody, while these are unfinished, slow and spend money -
 * which is a different kind of decision and reads as one.
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
        footnote = stringResource(Res.string.settings_translating_ahead_footnote),
        groupColor = groupColor,
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
