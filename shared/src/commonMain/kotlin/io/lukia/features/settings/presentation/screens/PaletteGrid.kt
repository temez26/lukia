package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette
import io.lukia.core.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.settings_type_sample
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.colorSchemeFor
import io.lukia.shared.presentation.theme.rememberTypography
import io.lukia.shared.presentation.theme.shapesFor
import org.jetbrains.compose.resources.stringResource

private val PreviewHeight: Dp = 72.dp
private val SelectedBorder: Dp = 2.dp
private val AccentDotSize: Dp = 10.dp
private const val COLUMNS = 2

/**
 * The palettes as a phone shows them: each a small picture of itself - its
 * background, a card on it, a heading in its own type and its accent - two to a
 * row, with its name under it.
 *
 * The window's list described each palette in a sentence. On a phone the four
 * sentences were most of a screen, and "warm paper and ink" is still not as
 * quick to judge as seeing it.
 */
@Composable
internal fun PaletteGrid(
    selected: AppPalette,
    accent: AppAccent,
    darkTheme: Boolean,
    onSelect: (AppPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg), modifier = modifier.fillMaxWidth()) {
        AppPalette.entries.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                row.forEach { palette ->
                    PaletteTile(
                        palette = palette,
                        accent = accent,
                        darkTheme = darkTheme,
                        selected = palette == selected,
                        onSelect = { onSelect(palette) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a last, lone tile at half width rather than stretched.
                repeat(COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PaletteTile(
    palette: AppPalette,
    accent: AppAccent,
    darkTheme: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        val colors = remember(palette, darkTheme, accent) { colorSchemeFor(palette, darkTheme, accent) }

        // The palette's own colours, shapes and type, for the picture only.
        MaterialTheme(
            colorScheme = colors,
            shapes = shapesFor(palette),
            typography = rememberTypography(palette),
        ) {
            PalettePreview(selected = selected, ring = ring, outline = outline)
        }

        Text(
            text = palette.displayName(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
        )
    }
}

/** The palette in miniature: its background, a card on it, a heading in its type, its accent. */
@Composable
private fun PalettePreview(selected: Boolean, ring: Color, outline: Color) {
    // The palette's own corners, which are as much a part of it as its colours.
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = if (selected) SelectedBorder else HairlineWidth,
                color = if (selected) ring else outline,
                shape = shape,
            )
            .padding(Space.md)
            .clearAndSetSemantics {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Text(
                text = stringResource(Res.string.settings_type_sample),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(AccentDotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
