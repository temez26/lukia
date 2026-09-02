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
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

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
            title = "Translate into",
            footnote = "Tap a sentence while reading to translate it, and " +
                "tap it again for the original.",
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
        title = "Pointing at the book",
        footnote = "Each does one thing, always. Doing it again on the same " +
            "passage puts the book’s own words back - except on a word you have " +
            "looked up, where holding opens more about it instead.",
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            LabelledField(label = "Tap") {
                GestureControl(
                    selected = translation.gestures.tap,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(tap = it))
                    },
                )
            }

            LabelledField(label = "Press and hold") {
                GestureControl(
                    selected = translation.gestures.longPress,
                    onSelect = {
                        actions.onGesturesChange(translation.gestures.copy(longPress = it))
                    },
                )
            }

            SettingsToggle(
                label = "Notes under the paragraph",
                detail = "The translator's asides, set small under the text they " +
                    "belong to: a remark about an ambiguous word, a dictionary's " +
                    "other senses. Turn them off to keep the page as the book set " +
                    "it — holding a word still opens everything it means. Why a " +
                    "passage could not be translated is always shown.",
                checked = translation.notes,
                onChange = actions.onNotesChange,
            )

            SettingsToggle(
                label = "Hold a word again for more about it",
                detail = "Once a word has been looked up, holding it opens every " +
                    "sense a dictionary has for it and a way through to " +
                    "${translation.wordSite.label}. Works whatever the hold above " +
                    "is set to.",
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
        title = "Translating ahead",
        footnote = "Both are new, and both are slow and expensive: they " +
            "translate a screenful at a time rather than the passage you " +
            "pointed at. Off until you ask for them, and hidden from the " +
            "reader entirely while they are.",
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            BetaToggle(
                label = "Translate what is on screen",
                detail = "Adds a button that translates every sentence you " +
                    "can see, once.",
                checked = translation.bulkTranslation.onScreen,
                onChange = {
                    actions.onBulkTranslationChange(
                        translation.bulkTranslation.copy(onScreen = it),
                    )
                },
            )

            BetaToggle(
                label = "Keep translating as you read",
                detail = "Adds a button that goes on translating whatever " +
                    "scrolls into view until you turn it off. This is the " +
                    "expensive one.",
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
