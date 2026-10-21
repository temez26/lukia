package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppMotion
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme
import io.lukia.core.presentation.displayDetail
import io.lukia.core.presentation.displayName
import io.lukia.features.settings.domain.AppSettings
import io.lukia.resources.Res
import io.lukia.resources.settings_accent
import io.lukia.resources.settings_accent_footnote
import io.lukia.resources.settings_appearance
import io.lukia.resources.settings_appearance_footnote
import io.lukia.resources.settings_motion
import io.lukia.resources.settings_motion_footnote
import io.lukia.resources.settings_palette
import io.lukia.resources.settings_palette_footnote
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.accentSwatchColor
import org.jetbrains.compose.resources.stringResource

private val SwatchSize: Dp = 30.dp

/** Above this, a background counts as a light one. */
private const val LightSurfaceLuminance = 0.5f

/**
 * Everything about what the app looks like: how light it is, what its surfaces
 * are made of, and the one colour it highlights with.
 *
 * Four controls in the order somebody reaches for them - the light and dark
 * switch is what most people came here for, the palette is a decision made once,
 * the accent only means anything once the other two are settled, and motion is
 * about how the app behaves rather than what it looks like, so it goes last.
 */
@Composable
internal fun AppearanceSettings(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    // Which way the swatches have to be read is a question about the colours on
    // screen rather than about the setting: "System" at night and "Dark" look
    // the same to a swatch, and only one of them is written down.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < LightSurfaceLuminance

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance),
            // Named for what it does rather than what it is called, since
            // "System" is the only option whose meaning is not obvious from the
            // word alone.
            info = stringResource(Res.string.settings_appearance_footnote),
            groupColor = groupColor,
        ) {
            SegmentedControl(
                options = AppTheme.entries,
                selected = settings.theme,
                label = { it.displayName() },
                onSelect = { onChange(settings.copy(theme = it)) },
            )
        }

        PaletteSection(
            settings = settings,
            onChange = onChange,
            darkTheme = darkTheme,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        SettingsSection(
            title = stringResource(Res.string.settings_accent),
            info = stringResource(Res.string.settings_accent_footnote),
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        ) {
            AccentSwatches(
                selected = settings.accent,
                palette = settings.palette,
                darkTheme = darkTheme,
                onSelect = { onChange(settings.copy(accent = it)) },
            )
        }

        MotionSection(
            selected = settings.motion,
            onSelect = { onChange(settings.copy(motion = it)) },
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/** The palettes: a grid of pictures of themselves on a phone, a described list in a window. */
@Composable
private fun PaletteSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    darkTheme: Boolean,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_palette),
        info = stringResource(Res.string.settings_palette_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        PaletteGrid(
            selected = settings.palette,
            accent = settings.accent,
            darkTheme = darkTheme,
            onSelect = { onChange(settings.copy(palette = it)) },
        )
    }
}

/**
 * How much the app moves.
 *
 * Last of the four because it is the only one here that is not about colour, and
 * because it is the only one whose effect cannot be seen from the settings
 * screen - the answer arrives the next time the app opens or a screen changes.
 */
@Composable
private fun MotionSection(
    selected: AppMotion,
    onSelect: (AppMotion) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_motion),
        // Says what the setting reaches, because it does not reach everything: a
        // button still shrinks under a finger on every one of these, and
        // promising otherwise would be a promise the app breaks the first time
        // somebody presses something.
        info = stringResource(Res.string.settings_motion_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        ChoiceList(
            options = AppMotion.entries,
            selected = selected,
            label = { it.displayName() },
            detail = { it.displayDetail() },
            onSelect = onSelect,
        )
    }
}

/**
 * The accents, as the colours themselves rather than as a list of their names.
 *
 * A row of swatches because this is the one setting here whose options cannot be
 * described in words better than they can be shown - "Rose" tells nobody whether
 * they will like it against paper.
 */
@Composable
private fun AccentSwatches(
    selected: AppAccent,
    palette: AppPalette,
    darkTheme: Boolean,
    onSelect: (AppAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        AppAccent.entries.forEach { accent ->
            AccentSwatch(
                color = accentSwatchColor(accent, darkTheme, palette),
                label = accent.displayName(),
                selected = accent == selected,
                onSelect = { onSelect(accent) },
            )
        }
    }
}

/**
 * One swatch. The tick is drawn over the colour rather than beside it, and in
 * white, which is legible on every accent here - a ring around the chosen one
 * disappears against a surface of a similar tone.
 */
@Composable
private fun AccentSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(color)
            // A hairline, so a swatch the same tone as the card it sits on is
            // still a swatch rather than a hole.
            .border(
                width = HairlineWidth,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = CircleShape,
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = label },
    ) {
        if (selected) {
            CheckGlyph(
                tint = Color.White,
                modifier = Modifier.size(SwatchSize * 0.5f),
            )
        }
    }
}
