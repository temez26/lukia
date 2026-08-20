package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * What a tap and a press do to a book, and what the reader may be asked to
 * translate without being pointed at anything.
 *
 * Two sections rather than one: the gestures are how the reader works the book
 * and are on for everybody, while what is below them is unfinished, slow and
 * spends money - which is a different kind of decision and reads as one.
 */
@Composable
internal fun ReadingSettings(
    translation: TranslationSettings,
    onTapGestureChange: (PassageUnit?) -> Unit,
    onLongPressGestureChange: (PassageUnit?) -> Unit,
    onTranslateOnScreenChange: (Boolean) -> Unit,
    onTranslateAsYouReadChange: (Boolean) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSection(
            title = "Pointing at the book",
            footnote = "Each does one thing, always. Doing it again on the same " +
                "passage puts the book’s own words back.",
            groupColor = groupColor,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                LabelledField(label = "Tap") {
                    GestureControl(
                        selected = translation.gestures.tap,
                        onSelect = onTapGestureChange,
                    )
                }

                LabelledField(label = "Press and hold") {
                    GestureControl(
                        selected = translation.gestures.longPress,
                        onSelect = onLongPressGestureChange,
                    )
                }
            }
        }

        SettingsSection(
            title = "Translating ahead",
            footnote = "Both are new, and both are slow and expensive: they " +
                "translate a screenful at a time rather than the passage you " +
                "pointed at. Off until you ask for them, and hidden from the " +
                "reader entirely while they are.",
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                BetaToggle(
                    label = "Translate what is on screen",
                    detail = "Adds a button that translates every sentence you " +
                        "can see, once.",
                    checked = translation.bulkTranslation.onScreen,
                    onChange = onTranslateOnScreenChange,
                )

                BetaToggle(
                    label = "Keep translating as you read",
                    detail = "Adds a button that goes on translating whatever " +
                        "scrolls into view until you turn it off. This is the " +
                        "expensive one.",
                    checked = translation.bulkTranslation.asYouRead,
                    onChange = onTranslateAsYouReadChange,
                )
            }
        }
    }
}
