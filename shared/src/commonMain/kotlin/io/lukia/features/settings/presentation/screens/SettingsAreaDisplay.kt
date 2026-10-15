package io.lukia.features.settings.presentation.screens

import androidx.compose.runtime.Composable
import io.lukia.resources.Res
import io.lukia.resources.account_area
import io.lukia.resources.settings_about
import io.lukia.resources.settings_area_ai_translation
import io.lukia.resources.settings_area_appearance
import io.lukia.resources.settings_area_reading
import io.lukia.resources.settings_area_translating
import org.jetbrains.compose.resources.stringResource

/**
 * What the five segments across the top of Settings are called.
 *
 * About borrows the name of the group inside it: the segment and the group are
 * the same thing now, and two resources holding one word is two chances for a
 * translation to disagree with itself.
 *
 * The AI translation segment reads "Beta", which its resource is not named
 * after: the key says what is behind the segment, the word says what state it is
 * in - and the word is the one that has to fit beside four others.
 */
@Composable
internal fun SettingsArea.displayTitle(): String = when (this) {
    SettingsArea.Appearance -> stringResource(Res.string.settings_area_appearance)
    SettingsArea.Reading -> stringResource(Res.string.settings_area_reading)
    SettingsArea.Translating -> stringResource(Res.string.settings_area_translating)
    SettingsArea.AiTranslation -> stringResource(Res.string.settings_area_ai_translation)
    SettingsArea.About -> stringResource(Res.string.settings_about)
    SettingsArea.Account -> stringResource(Res.string.account_area)
}
