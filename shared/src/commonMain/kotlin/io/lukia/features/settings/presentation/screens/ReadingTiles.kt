package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.presentation.displayName
import io.lukia.features.reader.presentation.theme.readerPageColorScheme
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.resources.Res
import io.lukia.resources.settings_type_sample
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

// The Reading page's choices shown as what they look like - see [PhoneReadingSettings].

private val TileHeight: Dp = 56.dp
private val SelectedBorder: Dp = 2.dp

@Composable
internal fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth(), content = content)
}

/**
 * One reader theme, as its own page and ink. The colours are read from the
 * reader's page scheme rather than by theming the tile with it, so the ring and
 * the name under the tile stay the app's own.
 */
@Composable
internal fun ThemeTile(
    theme: ReaderTheme,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SampleTile(
        label = theme.displayName(),
        background = readerPageStyle(theme).pageColor,
        ink = readerPageColorScheme(theme).onSurface,
        fontFamily = FontFamily.Default,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** One typeface, as its own letters. */
@Composable
internal fun TypefaceTile(
    typeface: ReaderTypeface,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SampleTile(
        label = typeface.displayName(),
        background = MaterialTheme.colorScheme.surfaceVariant,
        ink = MaterialTheme.colorScheme.onSurface,
        fontFamily = when (typeface) {
            ReaderTypeface.Serif -> FontFamily.Serif
            ReaderTypeface.SansSerif -> FontFamily.SansSerif
        },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * A choice shown as what it looks like, with its name under it. The ring in the
 * accent marks the chosen one, and its name turns bold, so the choice reads
 * without colour too.
 */
@Composable
private fun SampleTile(
    label: String,
    background: Color,
    ink: Color,
    fontFamily: FontFamily,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.medium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(TileHeight)
                .clip(shape)
                .background(background)
                .border(
                    width = if (selected) SelectedBorder else HairlineWidth,
                    color = if (selected) ring else outline,
                    shape = shape,
                )
                // A sample, not a name: the label under it is what is read out.
                .clearAndSetSemantics {},
        ) {
            Text(
                text = stringResource(Res.string.settings_type_sample),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = fontFamily,
                color = ink,
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
        )
    }
}
